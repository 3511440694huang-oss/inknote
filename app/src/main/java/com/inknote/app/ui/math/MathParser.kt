package com.inknote.app.ui.math

/**
 * LaTeX 子集的容错解析器。
 *
 * 设计原则：
 * - 纯 Kotlin、无异常：任何输入（空、乱写、超长、未闭合）都返回可绘制的语法树；
 * - 未知命令退化成把命令名当普通文本（`\foo` -> "foo"），绝不丢内容、绝不崩；
 * - 输入长度上限 [MAX_MATH_INPUT_LENGTH]、递归深度上限，防止病态输入卡死。
 */

/** 解析的最大字符数，超出部分截断（行内线性化与块级排版共用）。 */
const val MAX_MATH_INPUT_LENGTH = 20000

private const val MAX_RECURSION = 64

private const val STOP_BRACE = 1
private const val STOP_BRACKET = 2
private const val STOP_RIGHT = 4
private const val STOP_AMP = 8
private const val STOP_ROW = 16
private const val STOP_END = 32

/** 大型运算符命令 -> Unicode（含上下限堆叠）。 */
private val BIG_OPERATOR_COMMANDS: Map<String, String> = mapOf(
    "sum" to "∑", "prod" to "∏", "coprod" to "∐", "int" to "∫",
    "iint" to "∬", "iiint" to "∭", "iiiint" to "⨌", "oint" to "∮",
    "bigcup" to "⋃", "bigcap" to "⋂", "bigvee" to "⋁", "bigwedge" to "⋀",
    "bigoplus" to "⨁", "bigotimes" to "⨂", "bigodot" to "⨀",
    "bigsqcup" to "⨆", "biguplus" to "⨄",
)

/** 需要堆叠上下限的函数名（其余函数名用侧边上下标）。 */
private val STACKED_FUNCTION_NAMES: Set<String> = setOf(
    "lim", "max", "min", "sup", "inf", "limsup", "liminf", "argmin", "argmax",
)

/** 只影响排版样式、忽略即可的字体命令（\mathbb 等没有纯 Kotlin 等价物）。 */
private val FONT_COMMANDS: Set<String> = setOf(
    "mathrm", "mathsf", "mathtt", "mathbb", "mathcal", "mathscr", "mathfrak",
    "mathnormal", "symrm", "symup", "symbf", "boldsymbol", "bm",
)

private val BIG_DELIMITER_COMMANDS: Set<String> = setOf(
    "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr",
    "biggl", "biggr", "Biggl", "Biggr", "middle",
)

/** \not 后的符号替换表。 */
private val NEGATED_SYMBOLS: Map<String, String> = mapOf(
    "=" to "≠", "∈" to "∉", "∋" to "∌", "⊂" to "⊄", "⊃" to "⊅",
    "≤" to "≰", "≥" to "≱", "≡" to "≢", "∼" to "≁", "≈" to "≉",
)

/**
 * 把 LaTeX 解析成 [MathNode]。永不抛异常。
 */
fun parseMath(latex: String): MathNode {
    val limited = if (latex.length > MAX_MATH_INPUT_LENGTH) {
        latex.substring(0, MAX_MATH_INPUT_LENGTH)
    } else {
        latex
    }
    if (limited.isBlank()) return MathNode.Row(emptyList())
    return try {
        MathParser(limited).parseRoot()
    } catch (t: Throwable) {
        // 理论上不会走到这里；兜底保证不丢内容也不崩。
        val flat = limited.replace("\\", "").replace("{", "").replace("}", "").trim()
        if (flat.isEmpty()) MathNode.Row(emptyList()) else MathNode.Row(listOf(MathNode.TextRun(flat)))
    }
}

internal class MathParser(private val src: String) {

    private var pos = 0
    private var braceDepth = 0
    private var recursion = 0

    /** 上一次 parseRow 结束的原因（STOP_* 之一，0 表示正常到输入末尾）。 */
    private var stopReason = 0

    fun parseRoot(): MathNode = parseRow(0)

    // ---------------------------------------------------------------- 行解析

    private fun parseRow(stops: Int): MathNode.Row {
        if (++recursion > MAX_RECURSION) {
            recursion--
            pos = src.length
            return MathNode.Row(emptyList())
        }
        try {
            val items = ArrayList<MathNode>()
            while (true) {
                skipIgnorable()
                if (pos >= src.length) {
                    stopReason = 0
                    break
                }
                val c = src[pos]
                if (c == '}' && stops and STOP_BRACE != 0) {
                    stopReason = STOP_BRACE
                    break
                }
                if (c == ']' && stops and STOP_BRACKET != 0) {
                    stopReason = STOP_BRACKET
                    break
                }
                if (c == '&' && stops and STOP_AMP != 0) {
                    stopReason = STOP_AMP
                    break
                }
                if (c == '\\') {
                    val cmd = peekCommandName()
                    if (cmd == "\\" && stops and STOP_ROW != 0) {
                        stopReason = STOP_ROW
                        break
                    }
                    if (cmd == "right" && stops and STOP_RIGHT != 0) {
                        stopReason = STOP_RIGHT
                        break
                    }
                    if (cmd == "end" && stops and STOP_END != 0) {
                        stopReason = STOP_END
                        break
                    }
                }
                val before = pos
                val atom = parseAtomWithScripts()
                if (pos == before) pos++ // 防御：保证游标前进
                if (atom !is MathNode.Row || atom.items.isNotEmpty()) items.add(atom)
            }
            return MathNode.Row(items)
        } finally {
            recursion--
        }
    }

    // ---------------------------------------------------------------- 原子解析

    private fun parseAtomWithScripts(): MathNode = attachScripts(parseAtom())

    private fun parseAtom(): MathNode {
        skipIgnorable()
        if (pos >= src.length) return MathNode.Row(emptyList())
        val c = src[pos]
        return when {
            c == '{' -> parseGroup()
            c == '\\' -> parseCommand()
            c.isDigit() -> parseNumber()
            c == '.' && pos + 1 < src.length && src[pos + 1].isDigit() -> parseNumber()
            isAsciiLetter(c) -> parseLetterRun()
            c == '^' || c == '_' -> {
                pos++
                parseArgument()
            }
            c == '~' || c == '\u00A0' -> {
                pos++
                MathNode.Space(0.333f)
            }
            c == '\'' -> {
                pos++
                MathNode.Symbol("′", SymbolKind.Operator)
            }
            c == '$' -> {
                pos++
                MathNode.Row(emptyList())
            }
            c == '}' -> {
                pos++
                MathNode.Row(emptyList())
            }
            else -> {
                pos++
                symbolForChar(c)
            }
        }
    }

    private fun parseGroup(): MathNode {
        if (braceDepth >= MAX_RECURSION) {
            val raw = readRawBraceContent()
            return if (raw.isEmpty()) {
                MathNode.Row(emptyList())
            } else {
                MathNode.Row(listOf(MathNode.TextRun(raw)))
            }
        }
        pos++ // '{'
        braceDepth++
        try {
            val row = parseRow(STOP_BRACE)
            if (pos < src.length && src[pos] == '}') pos++
            return row
        } finally {
            braceDepth--
        }
    }

    private fun attachScripts(baseNode: MathNode): MathNode {
        var base = baseNode
        var sub: MathNode? = null
        var sup: MathNode? = null
        var primes: StringBuilder? = null
        while (true) {
            skipIgnorable()
            if (pos >= src.length) break
            when (src[pos]) {
                '^' -> {
                    pos++
                    sup = mergeScripts(sup, parseArgument())
                }
                '_' -> {
                    pos++
                    sub = mergeScripts(sub, parseArgument())
                }
                '\'' -> {
                    pos++
                    if (primes == null) primes = StringBuilder()
                    primes.append('′')
                }
                else -> break
            }
        }
        if (primes != null) {
            sup = mergeScripts(sup, MathNode.Symbol(primes.toString(), SymbolKind.Operator))
        }
        // 连续多个 ^/_ 合并成一个脚本组，避免内容丢失。
        return if (sub == null && sup == null) base else MathNode.Script(base, sub, sup)
    }

    private fun mergeScripts(existing: MathNode?, extra: MathNode): MathNode =
        if (existing == null) extra else MathNode.Row(listOf(existing, extra))

    /** 命令参数：{...} 组，或单个 token（单 token 不再吞掉后面的上下标）。 */
    private fun parseArgument(): MathNode {
        skipIgnorable()
        if (pos >= src.length) return MathNode.Row(emptyList())
        return if (src[pos] == '{') parseGroup() else parseAtom()
    }

    private fun parseNumber(): MathNode {
        val start = pos
        while (pos < src.length && src[pos].isDigit()) pos++
        if (pos < src.length && src[pos] == '.' && pos + 1 < src.length && src[pos + 1].isDigit()) {
            pos++
            while (pos < src.length && src[pos].isDigit()) pos++
        }
        return MathNode.Symbol(src.substring(start, pos), SymbolKind.Number)
    }

    /** 连续字母：已知函数名整体成正体，否则拆成单个变量（数学惯例）。 */
    private fun parseLetterRun(): MathNode {
        val start = pos
        var end = pos
        while (end < src.length && isAsciiLetter(src[end])) end++
        val word = src.substring(start, end)
        if (word.length > 1 && (FUNCTION_NAMES.contains(word) || STACKED_FUNCTION_NAMES.contains(word))) {
            pos = end
            return MathNode.Symbol(word, SymbolKind.Function)
        }
        pos = start + 1
        return MathNode.Symbol(src.substring(start, start + 1), SymbolKind.Variable)
    }

    private fun symbolForChar(c: Char): MathNode {
        val kind = when (c) {
            '+', '-', '=', '<', '>', '*', '/', '!', ',', ';', ':', '±', '×', '÷',
            '·', '≤', '≥', '≠', '≈', '→', '←', '↔', '⇒', '⇔', '∑', '∏', '∫',
            -> SymbolKind.Operator
            in '0'..'9' -> SymbolKind.Number
            in 'a'..'z', in 'A'..'Z' -> SymbolKind.Variable
            else -> SymbolKind.Normal
        }
        return MathNode.Symbol(c.toString(), kind)
    }

    // ---------------------------------------------------------------- 命令解析

    private fun parseCommand(): MathNode {
        pos++ // '\\'
        if (pos >= src.length) return MathNode.Row(emptyList())
        if (isAsciiLetter(src[pos])) {
            val start = pos
            while (pos < src.length && isAsciiLetter(src[pos])) pos++
            return commandNode(src.substring(start, pos))
        }
        val c = src[pos]
        pos++
        SPACE_COMMANDS[c.toString()]?.let { return MathNode.Space(it) }
        ESCAPED_CHARS[c]?.let { return MathNode.Symbol(it, SymbolKind.Operator) }
        return when (c) {
            '\\' -> MathNode.Space(0.35f) // 环境之外的换行，当成一点空白
            '(', ')', '[', ']' -> MathNode.Row(emptyList()) // 数学定界符本身不显示
            else -> MathNode.Symbol(c.toString(), SymbolKind.Operator)
        }
    }

    private fun commandNode(name: String): MathNode {
        // 大型运算符 / 堆叠上下限的函数名
        BIG_OPERATOR_COMMANDS[name]?.let { return MathNode.BigOperator(it, true) }
        if (STACKED_FUNCTION_NAMES.contains(name)) return MathNode.BigOperator(name, true)
        if (FUNCTION_NAMES.contains(name)) return MathNode.Symbol(name, SymbolKind.Function)
        GREEK_SYMBOLS[name]?.let { return MathNode.Symbol(it, SymbolKind.Normal) }
        SYMBOLS[name]?.let { return MathNode.Symbol(it, symbolKindForCommand(name, it)) }

        when (name) {
            // 分式
            "frac", "dfrac", "tfrac" -> {
                val style = when (name) {
                    "dfrac" -> FracStyle.Display
                    "tfrac" -> FracStyle.Text
                    else -> FracStyle.Normal
                }
                val numerator = parseArgument()
                val denominator = parseArgument()
                return MathNode.Frac(numerator, denominator, style)
            }
            // 根号
            "sqrt" -> {
                var index: MathNode? = null
                skipIgnorable()
                if (pos < src.length && src[pos] == '[') {
                    pos++
                    index = parseRow(STOP_BRACKET)
                    if (pos < src.length && src[pos] == ']') pos++
                }
                return MathNode.Root(index, parseArgument())
            }
            // 文本
            "text", "textrm", "textnormal", "mbox", "operatorname", "textup",
            "textmd", "textsf", "texttt", "mathrmtext",
            -> return MathNode.TextRun(readTextArgument())
            "textbf" -> return MathNode.Styled(MathFontStyle.Bold, MathNode.TextRun(readTextArgument()))
            "textit", "textsl", "emph" ->
                return MathNode.Styled(MathFontStyle.Italic, MathNode.TextRun(readTextArgument()))
            // 字体
            "mathbf", "symbf" ->
                return MathNode.Styled(MathFontStyle.Bold, parseArgument())
            "mathit", "symit", "symcal" ->
                return MathNode.Styled(MathFontStyle.Italic, parseArgument())
            // 重音
            "overline", "bar", "hat", "widehat", "vec", "overrightarrow", "dot",
            "ddot", "tilde", "widetilde", "check", "acute", "grave", "breve",
            -> {
                val kind = ACCENT_COMMANDS[name] ?: AccentKind.Bar
                return MathNode.Accent(kind, parseArgument())
            }
            // 自动缩放定界符
            "left" -> return parseDelimited()
            "right" -> {
                parseDelimiterToken()
                return MathNode.Row(emptyList())
            }
            // 环境
            "begin" -> return parseEnvironment()
            "end" -> {
                readTextArgument()
                return MathNode.Row(emptyList())
            }
            // 颜色声明：忽略颜色，保留后面的内容
            "color" -> {
                readTextArgument()
                return MathNode.Row(emptyList())
            }
            "textcolor" -> {
                readTextArgument()
                return parseArgument()
            }
            // 空白
            "hspace", "hskip", "kern", "mkern", "mskip", "vspace" -> {
                val raw = readTextArgument()
                return MathNode.Space(emFromRawLength(raw))
            }
            // 内容保留、装饰丢弃
            "overbrace", "underbrace", "phantom", "hphantom", "vphantom",
            "boxed", "fbox", "cancel", "bcancel", "xcancel",
            -> return parseArgument()
            "stackrel", "overset", "underset" -> {
                val annotation = parseArgument()
                val body = parseArgument()
                return MathNode.Row(listOf(body, annotation))
            }
            // 编号
            "tag" -> {
                val text = readTextArgument()
                return if (text.isEmpty()) {
                    MathNode.Row(emptyList())
                } else {
                    MathNode.TextRun(" ($text)")
                }
            }
            "label", "nonumber", "notag" -> {
                readTextArgument()
                return MathNode.Row(emptyList())
            }
            // 只影响样式/位置，忽略
            "not" -> {
                val next = parseArgument()
                val text = (next as? MathNode.Symbol)?.text
                val negated = text?.let { NEGATED_SYMBOLS[it] }
                return if (negated != null) {
                    MathNode.Symbol(negated, SymbolKind.Operator)
                } else {
                    MathNode.Row(listOf(MathNode.Symbol("not ", SymbolKind.Operator), next))
                }
            }
            "hline" -> return MathNode.Row(emptyList())
            "cline" -> {
                readTextArgument()
                return MathNode.Row(emptyList())
            }
            "pmod" -> {
                val arg = parseArgument()
                return MathNode.Row(listOf(MathNode.TextRun(" (mod "), arg, MathNode.TextRun(")")))
            }
            "bmod" -> return MathNode.TextRun(" mod ")
        }

        if (IGNORED_COMMANDS.contains(name)) return MathNode.Row(emptyList())
        SPACE_COMMANDS[name]?.let { return MathNode.Space(it) }
        if (FONT_COMMANDS.contains(name)) return parseArgument()
        if (BIG_DELIMITER_COMMANDS.contains(name)) {
            val delimiter = parseDelimiterToken()
            return if (delimiter.isEmpty() || delimiter == ".") {
                MathNode.Row(emptyList())
            } else {
                MathNode.Symbol(delimiter, SymbolKind.Normal)
            }
        }
        // 未知命令：去掉反斜杠，把命令名当普通文本，绝不丢内容、绝不崩。
        return MathNode.Symbol(name, SymbolKind.Normal)
    }

    // ---------------------------------------------------------------- 定界符与环境

    private fun parseDelimited(): MathNode {
        val open = parseDelimiterToken()
        val body = parseRow(STOP_RIGHT)
        var close = ""
        if (stopReason == STOP_RIGHT) {
            pos += "\\right".length
            close = parseDelimiterToken()
        }
        return MathNode.Delimited(open, body, close)
    }

    private fun parseDelimiterToken(): String {
        skipIgnorable()
        if (pos >= src.length) return ""
        if (src[pos] == '\\') {
            val name = consumeCommandName()
            return when (name) {
                "{", "}" -> name
                "|" -> "‖"
                "langle" -> "⟨"
                "rangle" -> "⟩"
                "lfloor" -> "⌊"
                "rfloor" -> "⌋"
                "lceil" -> "⌈"
                "rceil" -> "⌉"
                "vert", "lvert", "rvert" -> "|"
                "Vert", "lVert", "rVert" -> "‖"
                "lbrace" -> "{"
                "rbrace" -> "}"
                "lbrack" -> "["
                "rbrack" -> "]"
                "backslash" -> "\\"
                else -> SYMBOLS[name] ?: GREEK_SYMBOLS[name] ?: ""
            }
        }
        val c = src[pos]
        pos++
        return c.toString()
    }

    private fun parseEnvironment(): MathNode {
        val envName = readTextArgument().trim()
        // array 系列的可选位置参数、列格式参数
        skipIgnorable()
        if (pos < src.length && src[pos] == '[') {
            pos++
            var guard = 0
            while (pos < src.length && src[pos] != ']' && guard < 128) {
                pos++
                guard++
            }
            if (pos < src.length && src[pos] == ']') pos++
        }
        skipIgnorable()
        if (pos < src.length && src[pos] == '{' &&
            (envName == "array" || envName == "subarray" || envName == "darray")
        ) {
            readRawBraceContent()
        }
        val kind = when (envName) {
            "pmatrix" -> MatrixKind.Paren
            "bmatrix", "Bmatrix" -> MatrixKind.Bracket
            "vmatrix" -> MatrixKind.VBar
            "Vmatrix" -> MatrixKind.VBar2
            "cases" -> MatrixKind.Cases
            else -> MatrixKind.Plain
        }
        val rows = ArrayList<List<MathNode>>()
        var cells = ArrayList<MathNode>()
        while (true) {
            val cell = parseRow(STOP_AMP or STOP_ROW or STOP_END)
            cells.add(cell)
            when (stopReason) {
                STOP_AMP -> pos++ // 吃掉 '&'
                STOP_ROW -> {
                    rows.add(cells)
                    cells = ArrayList()
                    pos += 2 // 吃掉 '\\\\'
                    skipIgnorable()
                    if (pos < src.length && src[pos] == '[') {
                        pos++
                        var guard = 0
                        while (pos < src.length && src[pos] != ']' && guard < 128) {
                            pos++
                            guard++
                        }
                        if (pos < src.length && src[pos] == ']') pos++
                    }
                }
                STOP_END -> {
                    consumeEndCommand()
                    rows.add(cells)
                    break
                }
                else -> {
                    rows.add(cells)
                    break
                }
            }
        }
        return MathNode.Matrix(kind, rows)
    }

    private fun consumeEndCommand() {
        pos += "\\end".length
        readTextArgument()
    }

    // ---------------------------------------------------------------- 文本参数

    /** 读取 \text{...} 的花括号原始内容（保留中文、空格、嵌套花括号）。 */
    private fun readTextArgument(): String {
        skipIgnorable()
        if (pos >= src.length) return ""
        if (src[pos] == '{') return readRawBraceContent()
        if (src[pos] == '\\') {
            val name = consumeCommandName()
            if (name.length == 1) {
                val c = name[0]
                return ESCAPED_CHARS[c] ?: if (c == ' ' || c == ',') " " else c.toString()
            }
            return SYMBOLS[name] ?: GREEK_SYMBOLS[name] ?: name
        }
        if (src[pos] == '}') {
            pos++
            return ""
        }
        val c = src[pos]
        pos++
        return symbolForChar(c).let {
            (it as? MathNode.Symbol)?.text ?: c.toString()
        }
    }

    /** 从当前 '{' 开始读取平衡的花括号内容，返回内部文本（不含最外层花括号）。 */
    private fun readRawBraceContent(): String {
        if (pos >= src.length || src[pos] != '{') return ""
        pos++
        val sb = StringBuilder()
        var level = 1
        while (pos < src.length) {
            val ch = src[pos]
            if (ch == '\\' && pos + 1 < src.length) {
                val next = src[pos + 1]
                if (next == '{' || next == '}' || next == '%' || next == '$' || next == '&' ||
                    next == '#' || next == '_' || next == '^' || next == '~' || next == '|' ||
                    next == '\\'
                ) {
                    sb.append(if (next == '\\') ' ' else next)
                    pos += 2
                } else {
                    sb.append(ch)
                    pos++
                }
                continue
            }
            when (ch) {
                '{' -> {
                    level++
                    sb.append(ch)
                    pos++
                }
                '}' -> {
                    level--
                    pos++
                    if (level == 0) break
                    sb.append(ch)
                }
                else -> {
                    sb.append(ch)
                    pos++
                }
            }
        }
        return sb.toString()
    }

    private fun consumeCommandName(): String {
        pos++ // '\\'
        if (pos >= src.length) return ""
        if (isAsciiLetter(src[pos])) {
            val start = pos
            while (pos < src.length && isAsciiLetter(src[pos])) pos++
            return src.substring(start, pos)
        }
        val c = src[pos]
        pos++
        return c.toString()
    }

    // ---------------------------------------------------------------- 工具

    private fun peekCommandName(): String? {
        if (pos >= src.length || src[pos] != '\\') return null
        var i = pos + 1
        if (i >= src.length) return null
        if (isAsciiLetter(src[i])) {
            val start = i
            while (i < src.length && isAsciiLetter(src[i])) i++
            return src.substring(start, i)
        }
        return src[i].toString()
    }

    private fun skipIgnorable() {
        while (pos < src.length) {
            val c = src[pos]
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\u00A0') {
                pos++
                continue
            }
            if (c == '%') { // LaTeX 注释
                while (pos < src.length && src[pos] != '\n') pos++
                continue
            }
            break
        }
    }

    private fun isAsciiLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

    private fun symbolKindForCommand(name: String, symbol: String): SymbolKind = when {
        name == "colon" || name == "cdot" || name == "cdotp" -> SymbolKind.Operator
        symbol.length == 1 && symbol[0] in "+-=<>" -> SymbolKind.Operator
        else -> SymbolKind.Normal
    }

    private fun emFromRawLength(raw: String): Float {
        if (raw.isBlank()) return 0.5f
        val numeric = raw.takeWhile { it.isDigit() || it == '.' || it == '-' }
        val value = numeric.toFloatOrNull() ?: 0.5f
        if (value <= 0f) return 0f
        return when {
            raw.contains("em") -> value
            raw.contains("pt") -> value / 10f
            raw.contains("ex") -> value * 0.5f
            else -> value
        }
    }
}
