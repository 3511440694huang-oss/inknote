package com.inknote.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inknote.app.data.InkSettings
import com.inknote.app.ui.math.MathBlockView
import com.inknote.app.ui.math.linearizeMath
import com.inknote.app.ui.theme.InkPalette
import com.inknote.app.ui.theme.LocalInkPalette
import com.inknote.app.ui.theme.LocalInkSettings
import com.inknote.app.ui.theme.inkBodyFont

/* ------------------------------------------------------------------ */
/* 一、解析模型                                                         */
/* ------------------------------------------------------------------ */

/** 表格列对齐方式（GFM 的 `:---` / `:---:` / `---:`）。 */
enum class MdTableAlign { Default, Left, Center, Right }

/**
 * 渲染块。
 *
 * 旧变体（Heading / Paragraph / ListItem / Quote / Code / Table / Image / Rule）
 * 的字段全部保留（只追加带默认值的字段），保证既有调用不受影响；
 * 1.1.0 新增 [Math] 与 [QuoteBlock]。
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock

    data class Paragraph(val text: String) : MdBlock

    data class ListItem(
        val text: String,
        val ordered: Boolean,
        val marker: String,
        val depth: Int,
        val checked: Boolean?,
        /** 列表项内部的代码块 / 引用 / 公式等子块（缩进超过标记的续行）。 */
        val children: List<MdBlock> = emptyList(),
    ) : MdBlock

    /** 简单引用：单一/多段纯文字（多段用 `\n\n` 分隔）。 */
    data class Quote(val text: String) : MdBlock

    /** 复杂引用：内部含嵌套引用 / 代码块 / 公式等块。 */
    data class QuoteBlock(val blocks: List<MdBlock>) : MdBlock

    data class Code(val lang: String, val text: String) : MdBlock

    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        /** 与列一一对应；全部为默认对齐时为空列表（兼容旧的两参数构造）。 */
        val alignments: List<MdTableAlign> = emptyList(),
    ) : MdBlock

    data class Image(val alt: String, val src: String) : MdBlock

    /** 块级公式：`$$...$$` / `\[...\]`。 */
    data class Math(val latex: String) : MdBlock

    data object Rule : MdBlock
}

/**
 * 行内节点。解析与 Compose 完全解耦，方便纯 JVM 单元测试。
 */
sealed interface MdInline {
    data class Text(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strike: Boolean = false,
        val highlight: Boolean = false,
        val code: Boolean = false,
    ) : MdInline

    /** 链接：target 为 `url:<href>` / `wiki:<标题>[#<小节>]` / `tag:<标签>`。 */
    data class Link(val children: List<MdInline>, val target: String) : MdInline

    /** `#标签`（name 不含 `#`）。 */
    data class Tag(val name: String) : MdInline

    /** 行内公式（渲染时交给 linearizeMath）。 */
    data class InlineMath(val latex: String) : MdInline

    /** 行内图片占位（不加载图片）。 */
    data class ImagePlaceholder(val alt: String) : MdInline
}

/* ------------------------------------------------------------------ */
/* 二、行内解析                                                         */
/* ------------------------------------------------------------------ */

private const val F_BOLD = 1
private const val F_ITALIC = 2
private const val F_STRIKE = 4
private const val F_HIGHLIGHT = 8

/** CommonMark 可转义标点。 */
private const val ESCAPABLE =
    "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

private const val LINK_TAG = "INK_LINK"

private val EMAIL_LIKE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

private fun textRun(text: String, flags: Int, code: Boolean = false): MdInline.Text = MdInline.Text(
    text = text,
    bold = flags and F_BOLD != 0,
    italic = flags and F_ITALIC != 0,
    strike = flags and F_STRIKE != 0,
    highlight = flags and F_HIGHLIGHT != 0,
    code = code,
)

/** 解析一段行内 Markdown（纯 JVM，无 Compose 依赖）。 */
internal fun parseInline(source: String): List<MdInline> {
    val out = ArrayList<MdInline>()
    parseInlineRange(source, 0, source.length, 0, out)
    return out
}

/** 行内节点降级为纯文本（用于标题匹配、测试断言等）。 */
internal fun plainInline(inlines: List<MdInline>): String {
    val sb = StringBuilder()
    fun emit(list: List<MdInline>) {
        for (item in list) {
            when (item) {
                is MdInline.Text -> sb.append(item.text)
                is MdInline.Link -> emit(item.children)
                is MdInline.Tag -> sb.append('#').append(item.name)
                is MdInline.InlineMath -> sb.append(linearizeMath(item.latex))
                is MdInline.ImagePlaceholder -> {
                    sb.append("[图片]")
                    if (item.alt.isNotEmpty()) sb.append(' ').append(item.alt)
                }
            }
        }
    }
    emit(inlines)
    return sb.toString()
}

private fun parseInlineRange(
    s: String,
    from: Int,
    to: Int,
    flags: Int,
    out: MutableList<MdInline>,
) {
    val buf = StringBuilder()

    fun flush() {
        if (buf.isEmpty()) return
        out.add(textRun(buf.toString(), flags))
        buf.clear()
    }

    var i = from
    while (i < to) {
        val c = s[i]

        // \( ... \) 行内公式（在反斜杠转义之前处理）
        if (c == '\\' && i + 1 < to && s[i + 1] == '(') {
            val end = s.indexOf("\\)", i + 2)
            if (end > i + 2 && end < to) {
                flush()
                out.add(MdInline.InlineMath(s.substring(i + 2, end).trim()))
                i = end + 2
                continue
            }
        }

        // 反斜杠转义
        if (c == '\\') {
            if (i + 1 < to) {
                val n = s[i + 1]
                if (n == '\n') {
                    buf.append('\n')
                    i += 2
                    continue
                }
                if (n in ESCAPABLE) {
                    buf.append(n)
                    i += 2
                    continue
                }
            }
            buf.append('\\')
            i++
            continue
        }

        // 行内代码：内容不解析 Markdown
        if (c == '`') {
            var run = 1
            while (i + run < to && s[i + run] == '`') run++
            var j = i + run
            var close = -1
            while (j < to) {
                if (s[j] == '`') {
                    var k = 1
                    while (j + k < to && s[j + k] == '`') k++
                    if (k == run) {
                        close = j
                        break
                    }
                    j += k
                } else {
                    j++
                }
            }
            if (close >= 0) {
                flush()
                var content = s.substring(i + run, close)
                if (content.length >= 2 && content.startsWith(" ") && content.endsWith(" ") && content.isNotBlank()) {
                    content = content.substring(1, content.length - 1)
                }
                out.add(textRun(content, flags, code = true))
                i = close + run
                continue
            }
        }

        // ![alt](src) 行内图片占位
        if (c == '!' && i + 1 < to && s[i + 1] == '[') {
            val link = parseBracketLink(s, i + 1, to)
            if (link != null) {
                flush()
                out.add(MdInline.ImagePlaceholder(s.substring(link.textStart, link.textEnd).trim()))
                i = link.end
                continue
            }
        }

        // [[双链]] / [[双链|别名]] / [[笔记#小节]]
        if (c == '[' && i + 1 < to && s[i + 1] == '[') {
            val end = s.indexOf("]]", i + 2)
            if (end > i + 2 && end < to) {
                val inner = s.substring(i + 2, end).trim()
                if (inner.isNotEmpty()) {
                    val pipe = inner.indexOf('|')
                    val targetPart = if (pipe >= 0) inner.substring(0, pipe).trim() else inner
                    val alias = if (pipe >= 0) inner.substring(pipe + 1).trim() else ""
                    val hash = targetPart.indexOf('#')
                    val title = if (hash >= 0) targetPart.substring(0, hash).trim() else targetPart.trim()
                    val anchor = if (hash >= 0) targetPart.substring(hash + 1).trim() else ""
                    if (title.isNotEmpty() || anchor.isNotEmpty()) {
                        flush()
                        val target = "wiki:" + title + if (anchor.isNotEmpty()) "#$anchor" else ""
                        val display = if (alias.isNotEmpty()) alias else title.ifEmpty { anchor }
                        val children = ArrayList<MdInline>()
                        parseInlineRange(display, 0, display.length, flags, children)
                        out.add(MdInline.Link(flattenLinks(children), target))
                        i = end + 2
                        continue
                    }
                }
            }
        }

        // [文字](链接)
        if (c == '[') {
            val link = parseBracketLink(s, i, to)
            if (link != null) {
                flush()
                val label = s.substring(link.textStart, link.textEnd)
                val children = ArrayList<MdInline>()
                parseInlineRange(label, 0, label.length, flags, children)
                out.add(MdInline.Link(flattenLinks(children), "url:" + link.href))
                i = link.end
                continue
            }
        }

        // <https://…> 自动链接
        if (c == '<') {
            val close = s.indexOf('>', i + 1)
            if (close > i + 1 && close < to) {
                val inner = s.substring(i + 1, close)
                if (inner.isNotEmpty() && inner.none { it.isWhitespace() } && isAutolinkTarget(inner)) {
                    flush()
                    val display = if (inner.startsWith("mailto:")) inner.removePrefix("mailto:") else inner
                    out.add(MdInline.Link(listOf(textRun(display, flags)), "url:$inner"))
                    i = close + 1
                    continue
                }
            }
        }

        // $...$ / $$...$$ 行内公式
        if (c == '$') {
            if (i + 1 < to && s[i + 1] == '$') {
                val end = s.indexOf("$$", i + 2)
                if (end > i + 2) {
                    flush()
                    out.add(MdInline.InlineMath(s.substring(i + 2, end).trim()))
                    i = end + 2
                    continue
                }
            } else {
                val lineEnd = s.indexOf('\n', i).let { if (it < 0 || it > to) to else it }
                val end = s.indexOf('$', i + 1)
                if (end > i + 1 && end < lineEnd) {
                    val latex = s.substring(i + 1, end).trim()
                    if (latex.isNotEmpty()) {
                        flush()
                        out.add(MdInline.InlineMath(latex))
                        i = end + 1
                        continue
                    }
                }
            }
        }

        // 裸 URL（http:// https:// www.）
        if ((c == 'h' || c == 'w') && isBareUrlBoundary(s, i, from)) {
            val prefix = when {
                s.startsWith("https://", i) -> "https://"
                s.startsWith("http://", i) -> "http://"
                s.startsWith("www.", i) -> "www."
                else -> null
            }
            if (prefix != null) {
                var j = i
                while (j < to && !isUrlStop(s[j])) j++
                var end = j
                end = trimUrlTail(s, i, end)
                if (end > i + prefix.length) {
                    flush()
                    val raw = s.substring(i, end)
                    val target = if (prefix == "www.") "url:https://$raw" else "url:$raw"
                    out.add(MdInline.Link(listOf(textRun(raw, flags)), target))
                    i = end
                    continue
                }
            }
        }

        // #标签
        if (c == '#') {
            if (isTagLead(s, i, from)) {
                var j = i + 1
                while (j < to) {
                    val ch = s[j]
                    if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '/' || ch == '·') j++ else break
                }
                if (j > i + 1) {
                    val name = s.substring(i + 1, j)
                    val hasNonDigit = name.any { !it.isDigit() }
                    // 十六进制颜色值（如 color: #FFFFFF）不当标签
                    val hexLike = name.length in intArrayOf(3, 4, 6, 8) &&
                        name.all { it in "0123456789abcdefABCDEF" }
                    if (hasNonDigit && !(hexLike && isAfterColonOrEquals(s, i, from))) {
                        flush()
                        out.add(MdInline.Tag(name))
                        i = j
                        continue
                    }
                }
            }
        }

        // 强调 / 删除线 / 高亮
        if (c == '*' || c == '_' || c == '~' || c == '=') {
            val marker = when {
                s.startsWith("***", i) -> "***"
                s.startsWith("**", i) -> "**"
                s.startsWith("__", i) -> "__"
                s.startsWith("~~", i) -> "~~"
                s.startsWith("==", i) -> "=="
                c == '*' -> "*"
                c == '_' -> "_"
                else -> ""
            }
            if (marker.isNotEmpty() && canOpenEmphasis(s, i, marker, to)) {
                val close = findEmphasisClose(s, i + marker.length, marker, to)
                if (close >= 0) {
                    flush()
                    val add = when (marker) {
                        "***" -> F_BOLD or F_ITALIC
                        "**", "__" -> F_BOLD
                        "~~" -> F_STRIKE
                        "==" -> F_HIGHLIGHT
                        else -> F_ITALIC
                    }
                    parseInlineRange(s, i + marker.length, close, flags or add, out)
                    i = close + marker.length
                    continue
                }
                // 未闭合 → 按普通文本输出
                buf.append(marker)
                i += marker.length
                continue
            }
        }

        buf.append(c)
        i++
    }
    flush()
}

private fun flattenLinks(list: List<MdInline>): List<MdInline> {
    val out = ArrayList<MdInline>(list.size)
    for (item in list) {
        when (item) {
            is MdInline.Link -> out.addAll(flattenLinks(item.children))
            else -> out.add(item)
        }
    }
    return out
}

private class BracketLink(
    val textStart: Int,
    val textEnd: Int,
    val href: String,
    val end: Int,
)

/** 从 '[' 开始解析 `[文字](链接)`，括号可嵌套（维基百科 URL）。 */
private fun parseBracketLink(s: String, open: Int, to: Int): BracketLink? {
    if (open >= to || s[open] != '[') return null
    var depth = 0
    var i = open
    var close = -1
    while (i < to) {
        val ch = s[i]
        if (ch == '\\') {
            i += 2
            continue
        }
        if (ch == '[') {
            depth++
        } else if (ch == ']') {
            depth--
            if (depth == 0) {
                close = i
                break
            }
        }
        i++
    }
    if (close < 0 || close + 1 >= to || s[close + 1] != '(') return null
    var parenDepth = 0
    var j = close + 2
    var parenEnd = -1
    while (j < to) {
        val ch = s[j]
        if (ch == '\\') {
            j += 2
            continue
        }
        if (ch == '(') {
            parenDepth++
        } else if (ch == ')') {
            if (parenDepth == 0) {
                parenEnd = j
                break
            }
            parenDepth--
        }
        j++
    }
    if (parenEnd < 0) return null
    val href = extractHref(s.substring(close + 2, parenEnd))
    if (href.isEmpty()) return null
    return BracketLink(open + 1, close, href, parenEnd + 1)
}

private fun extractHref(raw: String): String {
    var href = raw.trim()
    if (href.startsWith("<") && href.contains('>')) {
        href = href.substring(1, href.indexOf('>')).trim()
    }
    if (href.isEmpty()) return ""
    val space = href.indexOfFirst { it == ' ' || it == '\t' }
    if (space > 0) {
        val rest = href.substring(space + 1).trimStart()
        if (rest.startsWith("\"") || rest.startsWith("'") || rest.startsWith("(")) {
            href = href.substring(0, space).trim()
        }
    }
    return href
}

private fun isAutolinkTarget(inner: String): Boolean {
    val lower = inner.lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://") ||
        lower.startsWith("mailto:") || lower.startsWith("tel:") ||
        EMAIL_LIKE.matches(inner)
}

/** 裸 URL 扫描终止符：空白、尖括号、引号、中文标点与中日韩文字（避免吞掉后文）。 */
private fun isUrlStop(ch: Char): Boolean {
    if (ch.isWhitespace() || ch == '<' || ch == '>' || ch == '"' || ch == '`' || ch == '\'') return true
    if (ch in "，。；：！？、（）【】「」『』《》〈〉…—·“”‘’") return true
    return ch.code in 0x3000..0x9FFF || ch.code in 0xFF00..0xFFEF
}

private fun isBareUrlBoundary(s: String, i: Int, from: Int): Boolean {
    if (i <= from) return true
    val prev = s[i - 1]
    // 只挡住 ASCII 字母/数字/下划线，中文正文里 URL 前不空格也能识别
    return !(prev.code < 128 && (prev.isLetterOrDigit() || prev == '_'))
}

private fun trimUrlTail(s: String, start: Int, endRaw: Int): Int {
    var end = endRaw
    val tailPunct = ".,;:!?。，；：！？、…"
    val closers = mapOf(
        ')' to '(', ']' to '[', '}' to '{',
        '）' to '（', '】' to '【', '」' to '「', '》' to '《',
    )
    while (end > start) {
        val last = s[end - 1]
        if (last in tailPunct) {
            end--
            continue
        }
        val opener = closers[last]
        if (opener != null) {
            val sub = s.substring(start, end)
            val opens = sub.count { it == opener }
            val closes = sub.count { it == last }
            if (closes > opens) {
                end--
                continue
            }
            break
        }
        if (last == '"' || last == '\'' || last == '”' || last == '’' || last == '」') {
            end--
            continue
        }
        break
    }
    return end
}

/** `#` 前是否隔着空白出现 `:` / `=`（用于排除 `color: #FFFFFF` 这类颜色值）。 */
private fun isAfterColonOrEquals(s: String, i: Int, from: Int): Boolean {
    var k = i - 1
    while (k >= from && (s[k] == ' ' || s[k] == '\t')) k--
    return k >= from && (s[k] == ':' || s[k] == '：' || s[k] == '=')
}

private fun isTagLead(s: String, i: Int, from: Int): Boolean {
    if (i <= from) return true
    val prev = s[i - 1]
    return prev.isWhitespace() || prev in "（(【[「『“\"'《〈，。；：！？、…—"
}

private fun canOpenEmphasis(s: String, i: Int, marker: String, to: Int): Boolean {
    if (i + marker.length >= to) return false
    val next = s[i + marker.length]
    if (next.isWhitespace()) return false
    if (marker.startsWith("_")) {
        // 词内下划线（snake_case_name）不当作斜体
        if (i > 0) {
            val prev = s[i - 1]
            if (prev.isLetterOrDigit()) return false
        }
    }
    return true
}

private fun findEmphasisClose(s: String, from: Int, marker: String, to: Int): Int {
    var j = from
    val limit = to - marker.length
    while (j <= limit) {
        if (s[j] == '\\') {
            j += 2
            continue
        }
        if (s.startsWith(marker, j)) {
            val prev = s[j - 1]
            if (!prev.isWhitespace()) {
                if (marker.endsWith("_")) {
                    // 结束下划线后面必须是词边界
                    val after = if (j + marker.length < to) s[j + marker.length] else '\n'
                    if (after.isLetterOrDigit()) {
                        j++
                        continue
                    }
                }
                return j
            }
        }
        j++
    }
    return -1
}

/* ------------------------------------------------------------------ */
/* 三、块级解析                                                         */
/* ------------------------------------------------------------------ */

private const val TAB_WIDTH = 4

private val ATX_HEADING = Regex("^(#{1,6})(?:[ \\t]+(.*))?$")
private val THEMATIC_BREAK = Regex("^ {0,3}((\\*[ \\t]*){3,}|(-[ \\t]*){3,}|(_[ \\t]*){3,})$")
private val LIST_ITEM = Regex("^(\\s*)([-*+]|\\d{1,9}[.)])[ \\t]+(.*)$")
private val TASK_PREFIX = Regex("^\\[([ xX])][ \\t]+(.*)$")
private val SETEXT_UNDERLINE = Regex("^(=+|-+)[ \\t]*$")
private val TABLE_DELIMITER = Regex("^\\s*\\|?\\s*:?-{1,}:?\\s*(\\|\\s*:?-{1,}:?\\s*)*\\|?\\s*$")
private val HEADING_TRAILING_HASHES = Regex("[ \\t]+#+$")

/**
 * 解析 Markdown 源码为渲染块列表。
 *
 * 支持：ATX/Setext 标题、段落、嵌套列表、任务清单、多行列表项、引用（可嵌套/多段）、
 * 围栏代码（含 info string）、缩进代码、表格（对齐 / 转义竖线）、分隔线、
 * 行内图片占位、块级公式（`$$` / `\[`）、YAML frontmatter 跳过。
 */
fun parseMarkdown(source: String): List<MdBlock> {
    val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.split('\n')

    // 跳过 YAML frontmatter（必须有闭合行，否则按普通内容解析）
    var start = 0
    if (lines.isNotEmpty() && lines[0].trim() == "---") {
        var j = 1
        while (j < lines.size && j <= 120) {
            val t = lines[j].trim()
            if (t == "---" || t == "...") {
                start = j + 1
                break
            }
            j++
        }
    }
    return parseBlocks(lines.subList(start, lines.size))
}

private fun leadingWidth(line: String): Int {
    var width = 0
    for (ch in line) {
        when (ch) {
            ' ' -> width++
            '\t' -> width += TAB_WIDTH
            else -> return width
        }
    }
    return width
}

private fun removeIndent(line: String, width: Int): String {
    var removed = 0
    var i = 0
    while (i < line.length && removed < width) {
        when (line[i]) {
            ' ' -> {
                removed++
                i++
            }

            '\t' -> {
                removed += TAB_WIDTH
                i++
            }

            else -> return line.substring(i)
        }
    }
    return line.substring(i)
}

private fun dedentLines(lines: List<String>): List<String> {
    val content = lines.filter { it.isNotBlank() }
    if (content.isEmpty()) return lines
    val width = content.minOf { leadingWidth(it) }
    if (width <= 0) return lines
    return lines.map { if (it.isBlank()) "" else removeIndent(it, width) }
}

private data class Fence(val char: Char, val length: Int, val info: String)

private fun fenceOpen(trimmed: String): Fence? {
    if (trimmed.isEmpty()) return null
    val ch = trimmed[0]
    if (ch != '`' && ch != '~') return null
    var k = 0
    while (k < trimmed.length && trimmed[k] == ch) k++
    if (k < 3) return null
    val info = trimmed.substring(k).trim()
    if (ch == '`' && info.contains('`')) return null
    return Fence(ch, k, info)
}

private fun isFenceClose(line: String, fence: Fence): Boolean {
    val t = line.trimStart()
    var k = 0
    while (k < t.length && t[k] == fence.char) k++
    return k >= fence.length && t.substring(k).isBlank()
}

/** 判断 `$$` / `\[` 是否是开启多行块级公式的行；返回闭合标记（无则空串）。 */
private fun blockMathCloser(trimmed: String): String = when {
    trimmed.startsWith("\\[") && !trimmed.endsWith("\\]") -> "\\]"
    trimmed.startsWith("$$") && !(trimmed.length > 4 && trimmed.endsWith("$$")) -> "$$"
    else -> ""
}

/** 解析块级公式；不是完整公式时返回 null（未闭合不吞掉后续内容）。 */
private fun readBlockMath(lines: List<String>, start: Int): Pair<MdBlock.Math, Int>? {
    val n = lines.size
    val t = lines[start].trim()

    if (t.startsWith("$$")) {
        if (t.length > 4 && t.endsWith("$$")) {
            return MdBlock.Math(t.substring(2, t.length - 2).trim()) to start + 1
        }
        if (t.length == 2) {
            val body = StringBuilder()
            var j = start + 1
            while (j < n) {
                if (lines[j].trim() == "$$") {
                    return MdBlock.Math(body.toString().trim()) to j + 1
                }
                if (body.isNotEmpty()) body.append('\n')
                body.append(lines[j].trimEnd())
                j++
            }
            return null
        }
        // $$ 后紧跟内容，闭合可能在后续行的结尾
        val body = StringBuilder(t.substring(2))
        var j = start + 1
        while (j < n) {
            val s = lines[j].trim()
            if (s.endsWith("$$")) {
                body.append('\n').append(s.dropLast(2))
                return MdBlock.Math(body.toString().trim()) to j + 1
            }
            body.append('\n').append(lines[j].trimEnd())
            j++
        }
        return null
    }

    if (t.startsWith("\\[")) {
        if (t.length > 4 && t.endsWith("\\]")) {
            return MdBlock.Math(t.substring(2, t.length - 2).trim()) to start + 1
        }
        if (t.length == 2) {
            val body = StringBuilder()
            var j = start + 1
            while (j < n) {
                if (lines[j].trim() == "\\]") {
                    return MdBlock.Math(body.toString().trim()) to j + 1
                }
                if (body.isNotEmpty()) body.append('\n')
                body.append(lines[j].trimEnd())
                j++
            }
            return null
        }
        val body = StringBuilder(t.substring(2))
        var j = start + 1
        while (j < n) {
            val s = lines[j].trim()
            if (s.endsWith("\\]")) {
                body.append('\n').append(s.dropLast(2))
                return MdBlock.Math(body.toString().trim()) to j + 1
            }
            body.append('\n').append(lines[j].trimEnd())
            j++
        }
        return null
    }
    return null
}

private fun isBlockStart(line: String): Boolean {
    val t = line.trim()
    if (t.isEmpty()) return false
    if (fenceOpen(t) != null) return true
    if (t.startsWith("$$") || t.startsWith("\\[")) return true
    if (ATX_HEADING.matches(t)) return true
    if (THEMATIC_BREAK.matches(t)) return true
    if (t.startsWith(">")) return true
    if (LIST_ITEM.matches(line)) return true
    if (t.startsWith("|")) return true
    if (t.startsWith("![")) {
        val link = parseBracketLink(t, 1, t.length)
        if (link != null && link.end == t.length) return true
    }
    return false
}

private fun isChildBlockStart(line: String): Boolean {
    val t = line.trimStart()
    if (t.isEmpty()) return false
    if (fenceOpen(t) != null) return true
    if (t.startsWith("$$") || t.startsWith("\\[")) return true
    if (t.startsWith(">")) return true
    if (ATX_HEADING.matches(t)) return true
    if (THEMATIC_BREAK.matches(t)) return true
    if (t.startsWith("|")) return true
    return false
}

private fun parseBlocks(lines: List<String>): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    var i = 0
    val n = lines.size

    while (i < n) {
        val line = lines[i]
        if (line.isBlank()) {
            i++
            continue
        }
        val trimmed = line.trimStart()

        // 1. 围栏代码块
        val fence = fenceOpen(trimmed)
        if (fence != null) {
            val code = StringBuilder()
            var j = i + 1
            while (j < n) {
                if (isFenceClose(lines[j], fence)) {
                    j++
                    break
                }
                code.append(lines[j]).append('\n')
                j++
            }
            out.add(MdBlock.Code(fence.info, code.toString().trimEnd('\n')))
            i = j
            continue
        }

        // 2. 块级公式
        val math = readBlockMath(lines, i)
        if (math != null) {
            out.add(math.first)
            i = math.second
            continue
        }

        // 3. ATX 标题
        val atx = ATX_HEADING.matchEntire(trimmed)
        if (atx != null) {
            val text = (atx.groupValues[2]).trimEnd()
                .replace(HEADING_TRAILING_HASHES, "")
                .trim()
            out.add(MdBlock.Heading(atx.groupValues[1].length, text))
            i++
            continue
        }

        // 4. 分隔线
        if (THEMATIC_BREAK.matches(trimmed)) {
            out.add(MdBlock.Rule)
            i++
            continue
        }

        // 5. 表格
        if (trimmed.contains('|') && i + 1 < n && isTableDelimiter(lines[i + 1])) {
            val header = splitTableRow(lines[i])
            val alignments = parseTableAlignments(lines[i + 1], header.size)
            val rows = ArrayList<List<String>>()
            var j = i + 2
            while (j < n && lines[j].isNotBlank() && lines[j].contains('|')) {
                rows.add(splitTableRow(lines[j]))
                j++
            }
            out.add(MdBlock.Table(header, rows, alignments))
            i = j
            continue
        }

        // 6. 单独一行的图片
        val image = parseStandaloneImage(line.trim())
        if (image != null) {
            out.add(image)
            i++
            continue
        }

        // 7. 引用
        if (trimmed.startsWith(">")) {
            val inner = ArrayList<String>()
            var j = i
            while (j < n) {
                val t = lines[j].trimStart()
                if (!t.startsWith(">")) break
                var content = t.substring(1)
                if (content.startsWith(" ")) content = content.substring(1)
                inner.add(content)
                j++
            }
            out.addAll(wrapQuote(parseBlocks(inner)))
            i = j
            continue
        }

        // 8. 列表
        if (LIST_ITEM.matches(line)) {
            val result = parseListBlock(lines, i)
            out.addAll(result.first)
            i = result.second
            continue
        }

        // 9. 缩进代码块
        if (leadingWidth(line) >= 4) {
            val body = ArrayList<String>()
            var j = i
            while (j < n) {
                val ln = lines[j]
                if (ln.isBlank()) {
                    body.add("")
                    j++
                    continue
                }
                if (leadingWidth(ln) < 4) break
                body.add(removeIndent(ln, 4))
                j++
            }
            while (body.isNotEmpty() && body.last().isBlank()) body.removeAt(body.size - 1)
            out.add(MdBlock.Code("", body.joinToString("\n")))
            i = j
            continue
        }

        // 10. 段落（顺带处理 setext 标题）
        val para = ArrayList<String>()
        var setextLevel = 0
        while (i < n) {
            val ln = lines[i]
            if (ln.isBlank()) break
            if (para.size == 1) {
                val setext = SETEXT_UNDERLINE.matchEntire(ln.trim())
                if (setext != null) {
                    setextLevel = if (setext.groupValues[1][0] == '=') 1 else 2
                    i++
                    break
                }
            }
            if (para.isNotEmpty() && isBlockStart(ln)) break
            para.add(stripHardBreak(ln.trim()))
            i++
        }
        if (para.isNotEmpty()) {
            val text = para.joinToString("\n")
            if (setextLevel > 0) out.add(MdBlock.Heading(setextLevel, text))
            else out.add(MdBlock.Paragraph(text))
        }
    }
    return out
}

private fun stripHardBreak(line: String): String {
    if (!line.endsWith("\\")) return line
    var backslashes = 0
    var k = line.length - 1
    while (k >= 0 && line[k] == '\\') {
        backslashes++
        k--
    }
    return if (backslashes % 2 == 1) line.dropLast(1) else line
}

private fun wrapQuote(children: List<MdBlock>): List<MdBlock> {
    if (children.isEmpty()) return listOf(MdBlock.Quote(""))
    if (children.size == 1 && children[0] is MdBlock.Paragraph) {
        return listOf(MdBlock.Quote((children[0] as MdBlock.Paragraph).text))
    }
    if (children.all { it is MdBlock.Paragraph }) {
        return listOf(MdBlock.Quote(children.joinToString("\n\n") { (it as MdBlock.Paragraph).text }))
    }
    return listOf(MdBlock.QuoteBlock(children))
}

private fun depthFor(indent: Int): Int = (indent / 2).coerceIn(0, 4)

private fun parseListBlock(lines: List<String>, start: Int): Pair<List<MdBlock>, Int> {
    val out = ArrayList<MdBlock>()
    var i = start
    val n = lines.size

    while (i < n) {
        val line = lines[i]
        if (line.isBlank()) break
        val m = LIST_ITEM.find(line) ?: break
        val indent = leadingWidth(line)
        val markerRaw = m.groupValues[2]
        val ordered = markerRaw[0].isDigit()
        var body = m.groupValues[3]
        var checked: Boolean? = null
        val task = TASK_PREFIX.find(body.trim())
        if (task != null) {
            checked = task.groupValues[1].equals("x", true)
            body = task.groupValues[2]
        }

        val textLines = ArrayList<String>()
        textLines.add(body.trim())
        val cont = ArrayList<String>()
        var fence: Fence? = null
        var mathClose = ""

        var j = i + 1
        while (j < n) {
            val ln = lines[j]
            if (fence != null) {
                cont.add(ln)
                if (isFenceClose(ln, fence)) fence = null
                j++
                continue
            }
            if (mathClose.isNotEmpty()) {
                cont.add(ln)
                if (ln.trim().endsWith(mathClose)) mathClose = ""
                j++
                continue
            }
            if (ln.isBlank()) {
                var k = j + 1
                while (k < n && lines[k].isBlank()) k++
                if (k < n && leadingWidth(lines[k]) > indent) {
                    cont.add("")
                    j++
                    continue
                }
                break
            }
            if (LIST_ITEM.matches(ln)) break
            if (leadingWidth(ln) <= indent) break
            val t = ln.trimStart()
            fence = fenceOpen(t)
            if (fence == null) mathClose = blockMathCloser(t)
            cont.add(ln)
            j++
        }
        i = j

        // 把续行拆成「段落文字」与「子块」
        var k = 0
        var pendingBlank = false
        while (k < cont.size) {
            val raw = cont[k]
            if (raw.isBlank()) {
                pendingBlank = textLines.isNotEmpty()
                k++
                continue
            }
            if (isChildBlockStart(raw)) break
            if (pendingBlank && textLines.isNotEmpty()) textLines.add("")
            pendingBlank = false
            textLines.add(raw.trim())
            k++
        }
        val childRaw = if (k < cont.size) cont.subList(k, cont.size) else emptyList()
        val children = if (childRaw.any { it.isNotBlank() }) {
            parseBlocks(dedentLines(childRaw))
        } else {
            emptyList()
        }

        out.add(
            MdBlock.ListItem(
                text = textLines.joinToString("\n").trim(),
                ordered = ordered,
                marker = if (ordered) markerRaw else "•",
                depth = depthFor(indent),
                checked = checked,
                children = children,
            )
        )
    }
    return out to i
}

private fun parseStandaloneImage(trimmed: String): MdBlock.Image? {
    if (!trimmed.startsWith("![")) return null
    val link = parseBracketLink(trimmed, 1, trimmed.length) ?: return null
    if (link.end != trimmed.length) return null
    return MdBlock.Image(trimmed.substring(link.textStart, link.textEnd).trim(), link.href)
}

private fun isTableDelimiter(line: String): Boolean {
    if (!TABLE_DELIMITER.matches(line)) return false
    return line.count { it == '|' } >= 1 || line.trim().startsWith(":")
}

private fun splitTableRow(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|") && !s.endsWith("\\|")) s = s.substring(0, s.length - 1)
    val cells = ArrayList<String>()
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            sb.append(c).append(s[i + 1])
            i += 2
            continue
        }
        if (c == '|') {
            cells.add(sb.toString().trim())
            sb.clear()
            i++
            continue
        }
        sb.append(c)
        i++
    }
    cells.add(sb.toString().trim())
    return cells
}

private fun parseTableAlignments(sepLine: String, count: Int): List<MdTableAlign> {
    val cells = splitTableRow(sepLine)
    val result = ArrayList<MdTableAlign>(count)
    for (idx in 0 until count) {
        val cell = cells.getOrNull(idx)?.trim().orEmpty()
        result.add(
            when {
                cell.startsWith(":") && cell.endsWith(":") -> MdTableAlign.Center
                cell.startsWith(":") -> MdTableAlign.Left
                cell.endsWith(":") -> MdTableAlign.Right
                else -> MdTableAlign.Default
            }
        )
    }
    return if (result.all { it == MdTableAlign.Default }) emptyList() else result
}

/* ------------------------------------------------------------------ */
/* 四、渲染（无动画 / 无阴影 / 无渐变，沿用墨笺五级灰）                    */
/* ------------------------------------------------------------------ */

@Suppress("DEPRECATION")
@Composable
private fun InlineText(
    raw: String,
    style: TextStyle,
    palette: InkPalette,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(raw, style, palette) {
        buildInlineAnnotated(raw, style, palette)
    }
    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let { onLink(it.item) }
        },
    )
}

private fun buildInlineAnnotated(raw: String, style: TextStyle, palette: InkPalette): AnnotatedString {
    val inlines = parseInline(raw)
    val baseSize: TextUnit = if (style.fontSize == TextUnit.Unspecified) 16.sp else style.fontSize
    return buildAnnotatedString {
        fun emit(list: List<MdInline>) {
            for (item in list) {
                when (item) {
                    is MdInline.Text -> {
                        val span = SpanStyle(
                            fontWeight = if (item.bold) FontWeight.Bold else null,
                            fontStyle = if (item.italic) FontStyle.Italic else null,
                            textDecoration = if (item.strike) TextDecoration.LineThrough else null,
                            fontFamily = if (item.code) FontFamily.Monospace else null,
                            background = if (item.highlight || item.code) palette.mist else Color.Unspecified,
                            fontSize = if (item.code) (baseSize.value * 0.92f).sp else TextUnit.Unspecified,
                        )
                        withStyle(span) { append(item.text) }
                    }

                    is MdInline.Link -> {
                        val start = length
                        withStyle(SpanStyle(color = palette.ink, textDecoration = TextDecoration.Underline)) {
                            emit(item.children)
                        }
                        addStringAnnotation(LINK_TAG, item.target, start, length)
                    }

                    is MdInline.Tag -> {
                        val start = length
                        withStyle(SpanStyle(color = palette.mid, textDecoration = TextDecoration.Underline)) {
                            append('#').append(item.name)
                        }
                        addStringAnnotation(LINK_TAG, "tag:" + item.name, start, length)
                    }

                    is MdInline.InlineMath -> {
                        withStyle(
                            SpanStyle(
                                color = palette.deep,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                            )
                        ) {
                            append(linearizeMath(item.latex))
                        }
                    }

                    is MdInline.ImagePlaceholder -> {
                        withStyle(SpanStyle(color = palette.mid)) {
                            append("[图片]")
                            if (item.alt.isNotEmpty()) append(' ').append(item.alt)
                        }
                    }
                }
            }
        }
        emit(inlines)
    }
}

/** Markdown 阅读视图：LazyColumn + 整页翻动（无动画、无惯性）。 */
@Composable
fun MarkdownView(
    blocks: List<MdBlock>,
    state: LazyListState,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalInkSettings.current
    val palette = LocalInkPalette.current
    val bodyFamily = inkBodyFont(settings)
    val bodySize = settings.fontSize.sp
    val bodyLine = (settings.fontSize * settings.lineFactor).sp
    val bodyStyle = TextStyle(
        fontFamily = bodyFamily,
        fontSize = bodySize,
        lineHeight = bodyLine,
        color = palette.ink,
    )

    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) {
        item { Spacer(Modifier.height(16.dp)) }
        itemsIndexed(blocks) { index, block ->
            MdBlockView(
                block = block,
                bodyStyle = bodyStyle,
                bodyFamily = bodyFamily,
                settings = settings,
                palette = palette,
                onLink = onLink,
                modifier = Modifier.padding(
                    start = settings.margin.dp,
                    end = settings.margin.dp,
                    top = if (index == 0) 0.dp else blockTopPad(block),
                    bottom = blockBottomPad(block),
                ),
            )
        }
        item { Spacer(Modifier.height(64.dp)) }
    }
}

private fun blockTopPad(block: MdBlock): androidx.compose.ui.unit.Dp = when (block) {
    is MdBlock.Heading -> if (block.level <= 2) 20.dp else 14.dp
    is MdBlock.Code -> 10.dp
    is MdBlock.Quote -> 8.dp
    is MdBlock.QuoteBlock -> 8.dp
    is MdBlock.Table -> 10.dp
    is MdBlock.Math -> 12.dp
    is MdBlock.Rule -> 12.dp
    is MdBlock.ListItem -> 2.dp
    is MdBlock.Image -> 8.dp
    else -> 6.dp
}

private fun blockBottomPad(block: MdBlock): androidx.compose.ui.unit.Dp = when (block) {
    is MdBlock.Code -> 10.dp
    is MdBlock.Quote -> 8.dp
    is MdBlock.QuoteBlock -> 8.dp
    is MdBlock.Table -> 10.dp
    is MdBlock.Math -> 12.dp
    is MdBlock.Heading -> 4.dp
    is MdBlock.ListItem -> 2.dp
    is MdBlock.Image -> 8.dp
    else -> 6.dp
}

/**
 * 代码块、图片占位这类「次要文字」随正文字号缩放（约 65%，最小 11sp）。
 * 1.2.0：字号可自定义后，固定 13sp 的代码块在大字号下会显得又小又碎。
 */
private fun secondaryTextSize(settings: InkSettings) =
    (settings.fontSize * 0.65f).coerceAtLeast(11f).sp

@Composable
private fun MdBlockView(
    block: MdBlock,
    bodyStyle: TextStyle,
    bodyFamily: FontFamily,
    settings: InkSettings,
    palette: InkPalette,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is MdBlock.Heading -> {
            val size = when (block.level) {
                1 -> (settings.fontSize + 9).sp
                2 -> (settings.fontSize + 5).sp
                3 -> (settings.fontSize + 3).sp
                4 -> (settings.fontSize + 1).sp
                else -> settings.fontSize.sp
            }
            val style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = size,
                lineHeight = (size.value * 1.32f).sp,
                fontWeight = FontWeight.Bold,
                color = palette.ink,
            )
            InlineText(block.text, style, palette, onLink, modifier.fillMaxWidth())
        }

        is MdBlock.Paragraph -> {
            InlineText(block.text, bodyStyle, palette, onLink, modifier.fillMaxWidth())
        }

        is MdBlock.ListItem -> {
            ListItemView(block, bodyStyle, bodyFamily, settings, palette, onLink, modifier)
        }

        is MdBlock.Quote -> {
            QuoteBar(palette, modifier) {
                InlineText(
                    raw = block.text,
                    style = bodyStyle.copy(color = palette.deep),
                    palette = palette,
                    onLink = onLink,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is MdBlock.QuoteBlock -> {
            QuoteBar(palette, modifier) {
                block.blocks.forEach { child ->
                    MdBlockView(
                        block = child,
                        bodyStyle = bodyStyle,
                        bodyFamily = bodyFamily,
                        settings = settings,
                        palette = palette,
                        onLink = onLink,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        is MdBlock.Code -> {
            Column(
                modifier
                    .fillMaxWidth()
                    .background(palette.mist)
                    .border(1.dp, palette.line)
                    .padding(10.dp),
            ) {
                if (block.lang.isNotBlank()) {
                    Text(
                        text = block.lang,
                        fontSize = 10.sp,
                        color = palette.mid,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    text = block.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = secondaryTextSize(settings),
                    lineHeight = (secondaryTextSize(settings).value * 1.55f).sp,
                    color = palette.ink,
                )
            }
        }

        is MdBlock.Table -> {
            Column(modifier.fillMaxWidth().border(1.dp, palette.line)) {
                TableRowView(block.header, bodyStyle, palette, header = true, alignments = block.alignments, onLink = onLink)
                block.rows.forEach { row ->
                    TableRowView(row, bodyStyle, palette, header = false, alignments = block.alignments, onLink = onLink)
                }
            }
        }

        is MdBlock.Image -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val strokeWidth = 1.dp.toPx()
                        drawRect(
                            color = palette.line,
                            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                            size = Size(size.width - strokeWidth, size.height - strokeWidth),
                            style = Stroke(
                                width = strokeWidth,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                                ),
                            ),
                        )
                    }
                    .padding(vertical = 18.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "[图片] " + block.alt.ifBlank { block.src },
                    fontSize = secondaryTextSize(settings),
                    lineHeight = (secondaryTextSize(settings).value * 1.55f).sp,
                    color = palette.mid,
                    style = TextStyle(fontFamily = bodyFamily),
                )
            }
        }

        is MdBlock.Math -> {
            Box(
                modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                MathBlockView(
                    latex = block.latex,
                    fontSize = settings.fontSize.sp,
                    color = palette.ink,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        MdBlock.Rule -> {
            Box(modifier.fillMaxWidth().height(1.dp).background(palette.line))
        }
    }
}

@Composable
private fun ListItemView(
    item: MdBlock.ListItem,
    bodyStyle: TextStyle,
    bodyFamily: FontFamily,
    settings: InkSettings,
    palette: InkPalette,
    onLink: (String) -> Unit,
    modifier: Modifier,
) {
    // 标记列宽 / 缩进 / 勾选框都跟着正文字号缩放（1.2.0：字号最大 40sp，写死 26dp 会折行）
    val markerWidth = if (item.ordered) (settings.fontSize * 1.3f).dp else (settings.fontSize * 0.8f).dp
    val checkboxSize = (settings.fontSize * 0.85f).dp
    val rowMinHeight = (settings.fontSize * 1.4f).dp
    val indent = (item.depth * settings.fontSize * 0.8f).dp
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = indent),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = rowMinHeight)) {
            if (item.checked != null) {
                Box(
                    Modifier
                        .padding(top = (settings.fontSize * 0.25f).dp)
                        .size(checkboxSize),
                ) {
                    TaskBox(item.checked == true, palette.ink)
                }
                Spacer(Modifier.width((settings.fontSize * 0.4f).dp))
            } else {
                Text(
                    text = item.marker,
                    style = bodyStyle,
                    color = if (item.ordered) palette.deep else palette.ink,
                    modifier = Modifier.width(markerWidth),
                )
            }
            Column(Modifier.weight(1f)) {
                InlineText(
                    raw = item.text,
                    style = if (item.checked == true) bodyStyle.copy(color = palette.mid) else bodyStyle,
                    palette = palette,
                    onLink = onLink,
                    modifier = Modifier.fillMaxWidth(),
                )
                item.children.forEach { child ->
                    MdBlockView(
                        block = child,
                        bodyStyle = bodyStyle,
                        bodyFamily = bodyFamily,
                        settings = settings,
                        palette = palette,
                        onLink = onLink,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuoteBar(
    palette: InkPalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = palette.ink,
                    topLeft = Offset.Zero,
                    size = Size(2.5.dp.toPx(), size.height),
                )
            }
            .padding(start = 14.dp),
    ) {
        Column(Modifier.weight(1f)) { content() }
    }
}

private fun MdTableAlign.toTextAlign(): TextAlign = when (this) {
    MdTableAlign.Center -> TextAlign.Center
    MdTableAlign.Right -> TextAlign.End
    else -> TextAlign.Start
}

@Composable
private fun TableRowView(
    cells: List<String>,
    bodyStyle: TextStyle,
    palette: InkPalette,
    header: Boolean,
    alignments: List<MdTableAlign>,
    onLink: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth().background(if (header) palette.mist else palette.paper)) {
        cells.forEachIndexed { index, cell ->
            val align = alignments.getOrNull(index) ?: MdTableAlign.Default
            Box(
                Modifier
                    .weight(1f)
                    .border(0.5.dp, palette.line)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            ) {
                InlineText(
                    raw = cell,
                    style = bodyStyle.copy(
                        fontSize = (bodyStyle.fontSize.value - 3).sp,
                        lineHeight = (bodyStyle.lineHeight.value * 0.9f).sp,
                        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = align.toTextAlign(),
                    ),
                    palette = palette,
                    onLink = onLink,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TaskBox(checked: Boolean, color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 1.4.dp.toPx()
        drawRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke),
        )
        if (checked) {
            drawLine(
                color = color,
                start = Offset(size.width * 0.2f, size.height * 0.55f),
                end = Offset(size.width * 0.42f, size.height * 0.78f),
                strokeWidth = stroke * 1.6f,
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.42f, size.height * 0.78f),
                end = Offset(size.width * 0.82f, size.height * 0.24f),
                strokeWidth = stroke * 1.6f,
            )
        }
    }
}
