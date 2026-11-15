package com.inknote.app.ui.math

import kotlin.math.max
import kotlin.math.min

/**
 * 排版引擎：把 [MathNode] 摆成带坐标的 [MathLayout]。
 *
 * 纯 Kotlin，文本测量全部走注入的 [MathMeasure]，因此可以在 JVM 单元测试里跑。
 * 盒模型约定：
 * - 每个盒子有 width / ascent / descent，子节点按基线（baseline）对齐；
 * - 每个盒子的绘制指令坐标以盒子左上角为原点；
 * - 内部布局先按“基线 y = 0”摆放，最后统一平移到实际坐标。
 */

private const val SCRIPT_SCALE = 0.7f
private const val FRAC_NORMAL_SCALE = 0.85f
private const val FRAC_DISPLAY_SCALE = 0.9f
private const val FRAC_TEXT_SCALE = 0.65f
private const val MATRIX_SCALE = 0.95f
private const val MIN_LAYOUT_SIZE = 0.5f
private const val MAX_LAYOUT_SIZE = 512f

/** 行内排版盒。 */
internal class Box(
    val width: Float,
    val ascent: Float,
    val descent: Float,
    val elements: List<MathElement>,
) {
    val height: Float get() = ascent + descent
}

private class FontFlags(val italic: Boolean, val bold: Boolean)

private val NO_ELEMENTS: List<MathElement> = emptyList()

private val RELATION_TEXTS: Set<String> = setOf(
    "=", "<", ">", "≤", "≥", "≠", "≈", "≡", "∼", "≃", "≅", "∝", "≪", "≫",
    "∈", "∉", "∋", "⊂", "⊃", "⊆", "⊇", "→", "←", "↔", "⇒", "⇐", "⇔",
    "⟹", "⟸", "⟺", "↦", "⟶", "⟵", "↑", "↓", "⊥", "∥", "∣", "⊢", "⊨",
)

private val BINARY_TEXTS: Set<String> = setOf(
    "+", "-", "×", "÷", "±", "∓", "·", "∗", "⋆", "∘", "∙", "⊕", "⊖",
    "⊗", "⊘", "⊙", "∪", "∩", "∖", "∧", "∨", "⊎", "⊔", "⊓",
)

/**
 * 解析 + 排版一行 LaTeX（纯函数，可注入测量）。
 */
fun layoutMath(latex: String, fontSizePx: Float, measure: MathMeasure): MathLayout =
    layoutMath(parseMath(latex), fontSizePx, measure)

/**
 * 对已有语法树排版。永不抛异常；尺寸保证有限且非负。
 */
fun layoutMath(node: MathNode, fontSizePx: Float, measure: MathMeasure): MathLayout {
    val size = sanitizeRootSize(fontSizePx)
    val box = try {
        layoutNode(node, size, MathFontStyle.Normal, measure)
    } catch (t: Throwable) {
        Box(0f, 0f, 0f, NO_ELEMENTS)
    }
    if (!box.width.isFinite() || !box.ascent.isFinite() || !box.descent.isFinite()) {
        return MathLayout.Empty
    }
    return MathLayout(
        width = max(0f, box.width),
        height = max(0f, box.ascent + box.descent),
        ascent = max(0f, box.ascent),
        descent = max(0f, box.descent),
        elements = box.elements,
    )
}

// ------------------------------------------------------------------ 节点分发

private fun layoutNode(
    node: MathNode,
    sizePx: Float,
    style: MathFontStyle,
    measure: MathMeasure,
): Box {
    val size = sanitizeSize(sizePx)
    return when (node) {
        is MathNode.Row -> layoutRow(node, size, style, measure)
        is MathNode.Symbol -> layoutSymbol(node, size, style, measure)
        is MathNode.TextRun -> layoutTextRun(node, size, style, measure)
        is MathNode.Styled -> layoutNode(node.child, size, mergeStyle(style, node.style), measure)
        is MathNode.Space -> layoutSpace(node, size)
        is MathNode.Script -> layoutScript(node, size, style, measure)
        is MathNode.Frac -> layoutFrac(node, size, style, measure)
        is MathNode.Root -> layoutRoot(node, size, style, measure)
        is MathNode.Delimited -> layoutDelimited(node, size, style, measure)
        is MathNode.Accent -> layoutAccent(node, size, style, measure)
        is MathNode.Matrix -> layoutMatrix(node, size, style, measure)
        is MathNode.BigOperator -> layoutBigOperator(node, size, style, measure)
    }
}

// ------------------------------------------------------------------ 基础原子

private fun layoutSymbol(
    node: MathNode.Symbol,
    size: Float,
    style: MathFontStyle,
    measure: MathMeasure,
): Box {
    if (node.text.isEmpty()) return emptyBox()
    val flags = styleFlags(node.kind, style)
    val metrics = safeMeasure(measure, node.text, size, flags.italic, flags.bold)
    if (metrics.width <= 0f && metrics.height <= 0f) return emptyBox()
    val element = MathElement.Text(node.text, 0f, 0f, size, flags.italic, flags.bold)
    return buildBox(metrics.width, -metrics.ascent, metrics.descent, listOf(element))
}

private fun layoutTextRun(
    node: MathNode.TextRun,
    size: Float,
    style: MathFontStyle,
    measure: MathMeasure,
): Box {
    if (node.text.isEmpty()) return emptyBox()
    val italic = style == MathFontStyle.Italic || style == MathFontStyle.BoldItalic
    val bold = style == MathFontStyle.Bold || style == MathFontStyle.BoldItalic
    val metrics = safeMeasure(measure, node.text, size, italic, bold)
    if (metrics.width <= 0f && metrics.height <= 0f) return emptyBox()
    val element = MathElement.Text(node.text, 0f, 0f, size, italic, bold)
    return buildBox(metrics.width, -metrics.ascent, metrics.descent, listOf(element))
}

private fun layoutSpace(node: MathNode.Space, size: Float): Box {
    val width = node.widthEm * size
    if (!width.isFinite() || width <= 0f) return emptyBox()
    return Box(width, 0f, 0f, NO_ELEMENTS)
}

private fun layoutBigOperator(
    node: MathNode.BigOperator,
    size: Float,
    style: MathFontStyle,
    measure: MathMeasure,
): Box {
    val opSize = if (node.op.length == 1 && BIG_OPERATOR_CHARS.contains(node.op)) size * 1.25f else size
    return layoutSymbol(MathNode.Symbol(node.op, SymbolKind.Operator), opSize, style, measure)
}

// ------------------------------------------------------------------ 横向排列

private fun layoutRow(
    node: MathNode.Row,
    size: Float,
    style: MathFontStyle,
    measure: MathMeasure,
): Box {
    if (node.items.isEmpty()) return emptyBox()
    val boxes = ArrayList<Box>(node.items.size)
    val nodes = ArrayList<MathNode>(node.items.size)
    for (item in node.items) {
        val child = layoutNode(item, size, style, measure)
        if (child.width <= 0f && child.height <= 0f) continue
        boxes.add(child)
        nodes.add(item)
    }
    val count = boxes.size
    if (count == 0) return emptyBox()
    val xs = FloatArray(count)
    var x = 0f
    for (i in 0 until count) {
        if (i > 0) x += spaceBetween(nodes[i - 1], nodes[i], size)
        xs[i] = x
        x += boxes[i].width
    }
    var top = 0f
    var bottom = 0f
    for (child in boxes) {
        top = min(top, -child.ascent)
        bottom = max(bottom, child.descent)
    }
    val elements = ArrayList<MathElement>()
    for (i in 0 until count) {
        elements.addAll(boxes[i].elements.shift(xs[i], -boxes[i].ascent))
    }
    return buildBox(x, top, bottom, elements)
}

/** 行间空白：二元运算符/关系符两侧、逗号后、函数名后。 */
private fun spaceBetween(prev: MathNode, next: MathNode, size: Float): Float {
    var gap = 0f
    val prevSymbol = prev as? MathNode.Symbol
    val nextSymbol = next as? MathNode.Symbol
    val prevText = prevSymbol?.text
    val nextText = nextSymbol?.text
    if (prevText != null && nextText != null) {
        gap += when {
            prevText in RELATION_TEXTS || nextText in RELATION_TEXTS -> 0.278f * size
            prevText in BINARY_TEXTS || nextText in BINARY_TEXTS -> 0.222f * size
            else -> 0f
        }
    }
    if (prevText == "," || prevText == ";") gap += 0.167f * size
    if (prevSymbol?.kind == SymbolKind.Function && nextSymbol?.kind == SymbolKind.Variable) {
        gap += 0.16f * size
    }
    if (prev is MathNode.BigOperator) gap += 0.12f * size
    return if (gap.isFinite()) max(0f, gap) else 0f
}

// ------------------------------------------------------------------ 上下标

private fun layoutScript(
    node: MathNode.Script,
    size: Float,
    style: MathFontStyle,
    measure: MathMeasure,
): Box {
    val (baseNode, baseStyle) = unwrapStyled(node.base, style)
    if (baseNode is MathNode.BigOperator && baseNode.limits) {
        return layoutStackedLimits(baseNode, node.sub, node.sup, size, baseStyle, measure)
    }
    val base = layoutNode(node.base, size, style, measure)
    val scriptSize = size * SCRIPT_SCALE
    val subBox = node.sub?.let { layoutNode(it, scriptSize, style, measure) }
    val supBox = node.sup?.let { layoutNode(it, scriptSize, style, measure) }
    val gap = 0.04f * size
    val rise = 0.42f * size
    var subBaseline = 0.30f * size
    if (subBox != null && supBox != null) {
        val supBottom = -rise + supBox.descent
        val subTop = subBaseline - subBox.ascent
        val minGap = 0.05f * size
        if (subTop < supBottom + minGap) subBaseline += supBottom + minGap - subTop
    }
    val scriptWidth = max(subBox?.width ?: 0f, supBox?.width ?: 0f)
    val width = base.width + gap + scriptWidth
    var top = -base.ascent
    var bottom = base.descent
    if (supBox != null) {
        top = min(top, -rise - supBox.ascent)
        bottom = max(bottom, -rise + supBox.descent)
    }
    if (subBox != null) {
        top = min(top, subBaseline - subBox.ascent)
        bottom = max(bottom, subBaseline + subBox.descent)
    }
    val elements = ArrayList<MathElement>()
    elements.addAll(base.elements.shift(0f, -base.ascent))
    val scriptX = base.width + gap
    if (supBox != null) {
        elements.addAll(supBox.elements.shift(scriptX, -rise - supBox.ascent))
    }
    if (subBox != null) {
        elements.addAll(subBox.elements.shift(scriptX, subBaseline - subBox.ascent))
    }
    return buildBox(width, top, bottom, elements)
}

/** 大型运算符（∑∏∫）或 lim/max 的上下限上下堆叠。 */
private fun layoutStackedLimits(
    operator: MathNode.BigOperator,
    subNode: MathNode?,
    supNode: MathNode?,
    size: Float,
    style: MathFontStyle,
    measure: MathMeasure,
): Box {
    val opBox = layoutBigOperator(operator, size, style, measure)
    val limitSize = size * SCRIPT_SCALE
    val subBox = subNode?.let { layoutNode(it, limitSize, style, measure) }
    val supBox = supNode?.let { layoutNode(it, limitSize, style, measure) }
    val gap = 0.10f * size
    val width = max(opBox.width, max(subBox?.width ?: 0f, supBox?.width ?: 0f))
    var top = -opBox.ascent
    var bottom = opBox.descent
    var supBaseline = 0f
    var subBaseline = 0f
    if (supBox != null) {
        supBaseline = -opBox.ascent - gap - supBox.descent
        top = min(top, supBaseline - supBox.ascent)
        bottom = max(bottom, supBaseline + supBox.descent)
    }
    if (subBox != null) {
        subBaseline = opBox.descent + gap + subBox.ascent
        top = min(top, subBaseline - subBox.ascent)
        bottom = max(bottom, subBaseline + subBox.descent)
    }
    val elements = ArrayList<MathElement>()
    elements.addAll(opBox.elements.shift((width - opBox.width) / 2f, -opBox.ascent))
    if (supBox != null) {
        elements.addAll(supBox.elements.shift((width - supBox.width) / 2f, supBaseline - supBox.ascent))
    }
    if (subBox != null) {
        elements.addAll(subBox.elements.shift((width - subBox.width) / 2f, subBaseline - subBox.ascent))
    }
    return buildBox(width, top, bottom, elements)
}

// ------------------------------------------------------------------ 分式

private fun layoutFrac(
    node: MathNode.Frac,
    size: Float,
    style: MathFontStyle,
    measure: MathMeasure,
): Box {
    val childScale = when (node.style) {
        FracStyle.Display -> FRAC_DISPLAY_SCALE
        FracStyle.Text -> FRAC_TEXT_SCALE
        FracStyle.Normal -> FRAC_NORMAL_SCALE
    }
    val childSize = size * childScale
    val numerator = layoutNode(node.numerator, childSize, style, measure)
    val denominator = layoutNode(node.denominator, childSize, style, measure)
    val thickness = ruleThickness(size)
    val axis = 0.28f * size
    val pad = 0.14f * size
    val innerWidth = max(numerator.width, denominator.width)
    val width = innerWidth + 0.20f * size
    val ruleWidth = innerWidth + 0.12f * size
    if (width <= 0f && numerator.height <= 0f && denominator.height <= 0f) return emptyBox()
    // 以基线为 0 的坐标：分数线在数学轴上方
    val ruleTop = -axis - thickness / 2f
    val numeratorBaseline = ruleTop - pad - numerator.descent
    val denominatorBaseline = ruleTop + thickness + pad + denominator.ascent
    val top = numeratorBaseline - numerator.ascent
    val bottom = denominatorBaseline + denominator.descent
    val elements = ArrayList<MathElement>()
    elements.addAll(numerator.elements.shift((width - numerator.width) / 2f, numeratorBaseline - numerator.ascent))
    elements.addAll(denominator.elements.shift((width - denominator.width) / 2f, denominatorBaseline - denominator.ascent))
    elements.add(MathElement.Rule((width - ruleWidth) / 2f, ruleTop, ruleWidth, thickness))
    return buildBox(width, top, bottom, elements)
}

// ------------------------------------------------------------------ 根号

private fun layoutRoot(
    node: MathNode.Root,
    size: Float,
    style: MathFontStyle,
    measure: MathMeasure,
): Box {
    val body = layoutNode(node.body, size, style, measure)
    val thickness = ruleThickness(size)
    val radicalWidth = 0.72f * size
    val gap = 0.10f * size
    val bodyTop = -body.ascent
    val bodyBottom = body.descent
    val ruleY = bodyTop - gap - thickness
    val width = radicalWidth + body.width + 0.10f * size
    val elements = ArrayList<MathElement>()
    elements.addAll(body.elements.shift(radicalWidth, bodyTop))
    // 根号上的横线
    if (body.width > 0f) {
        elements.add(
            MathElement.Rule(
                x = radicalWidth * 0.72f,
                y = ruleY,
                width = width - radicalWidth * 0.72f,
                height = thickness,
            )
        )
    }
    // 根号的两笔：短地下行 + 长地上行（用直线，锐利）
    val radicalHeight = max(body.height, 0.2f * size)
    val leftY = bodyBottom - radicalHeight * 0.55f
    elements.add(MathElement.Line(0f, leftY, radicalWidth * 0.30f, bodyBottom, thickness))
    elements.add(MathElement.Line(radicalWidth * 0.30f, bodyBottom, radicalWidth * 0.72f, ruleY, thickness))
    var top = ruleY
    if (node.index != null) {
        val index = layoutNode(node.index, size * 0.55f, style, measure)
        val indexBaseline = ruleY - 0.06f * size - index.descent
        elements.addAll(index.elements.shift(0f, indexBaseline - index.ascent))
        top = min(top, indexBaseline - index.ascent)
    }
    return buildBox(width, top, bodyBottom, elements)
}

// ------------------------------------------------------------------ 定界符

private fun layoutDelimited(
    node: MathNode.Delimited,
    size: Float,
    style: MathFontStyle,
    measure: MathMeasure,
): Box {
    val body = layoutNode(node.body, size, style, measure)
    val pad = 0.05f * size
    val axis = 0.28f * size
    val leftGlyph = visibleDelimiter(node.open)
    val rightGlyph = visibleDelimiter(node.close)
    val leftNatural = if (leftGlyph != null) safeMeasure(measure, leftGlyph, size, false, false) else TextMetrics.Zero
    val rightNatural = if (rightGlyph != null) safeMeasure(measure, rightGlyph, size, false, false) else TextMetrics.Zero
    val naturalHeight = max(leftNatural.height, rightNatural.height)
    val targetHeight = max(body.height + 0.14f * size, max(naturalHeight, size * 1.1f))
    val bodyX = leftNatural.width + (if (leftGlyph != null) pad else 0f)
    val width = bodyX + body.width + (if (rightGlyph != null) pad + rightNatural.width else 0f)
    val delimiterTop = -axis - targetHeight / 2f
    var top = -body.ascent
    var bottom = body.descent
    val elements = ArrayList<MathElement>()
    if (leftGlyph != null) {
        elements.add(MathElement.StretchedGlyph(leftGlyph, 0f, delimiterTop, targetHeight, size))
        top = min(top, delimiterTop)
        bottom = max(bottom, delimiterTop + targetHeight)
    }
    elements.addAll(body.elements.shift(bodyX, -body.ascent))
    if (rightGlyph != null) {
        val rightX = width - rightNatural.width
        elements.add(MathElement.StretchedGlyph(rightGlyph, rightX, delimiterTop, targetHeight, size))
        top = min(top, delimiterTop)
        bottom = max(bottom, delimiterTop + targetHeight)
    }
    return buildBox(width, top, bottom, elements)
}

private fun visibleDelimiter(delimiter: String): String? = when (delimiter) {
    "", "." -> null
    else -> delimiter
}

// ------------------------------------------------------------------ 重音

private fun layoutAccent(
    node: MathNode.Accent,
    size: Float,
    style: MathFontStyle,
    measure: MathMeasure,
): Box {
    val body = layoutNode(node.body, size, style, measure)
    val thickness = ruleThickness(size)
    val gap = 0.10f * size
    val isRule = node.kind == AccentKind.Overline || node.kind == AccentKind.Bar
    val boldAccent = style == MathFontStyle.Bold || style == MathFontStyle.BoldItalic
    val glyph = if (isRule) null else accentGlyph(node.kind)
    val accentMetrics = if (glyph != null) safeMeasure(measure, glyph, size * 0.85f, false, boldAccent) else TextMetrics.Zero
    val accentWidth = if (isRule) max(body.width, 0.35f * size) else max(body.width, accentMetrics.width)
    val width = accentWidth
    var top = -body.ascent
    val elements = ArrayList<MathElement>()
    if (isRule) {
        val y = -body.ascent - gap - thickness
        elements.add(MathElement.Rule((width - accentWidth) / 2f, y, accentWidth, thickness))
        top = min(top, y)
    } else if (glyph != null) {
        val accentSize = size * 0.85f
        val baseline = -body.ascent - gap - accentMetrics.descent
        elements.add(
            MathElement.Text(
                text = glyph,
                x = (width - accentMetrics.width) / 2f,
                baseline = baseline,
                sizePx = accentSize,
                italic = false,
                bold = boldAccent,
            )
        )
        top = min(top, baseline - accentMetrics.ascent)
    }
    elements.addAll(body.elements.shift((width - body.width) / 2f, -body.ascent))
    return buildBox(width, top, body.descent, elements)
}

// ------------------------------------------------------------------ 矩阵 / cases

private fun layoutMatrix(
    node: MathNode.Matrix,
    size: Float,
    style: MathFontStyle,
    measure: MathMeasure,
): Box {
    val rows = node.rows.filter { it.isNotEmpty() }
    if (rows.isEmpty()) return emptyBox()
    val cellSize = size * MATRIX_SCALE
    val laidRows = ArrayList<List<Box>>(rows.size)
    for (row in rows) {
        val cells = ArrayList<Box>(row.size)
        for (cell in row) cells.add(layoutNode(cell, cellSize, style, measure))
        laidRows.add(cells)
    }
    var columnCount = 1
    for (row in laidRows) columnCount = max(columnCount, row.size)
    val isCases = node.kind == MatrixKind.Cases
    val columnGap = if (isCases) 0.45f * size else 0.6f * size
    val rowGap = 0.32f * size
    val columnWidths = FloatArray(columnCount)
    for (row in laidRows) {
        for (c in row.indices) columnWidths[c] = max(columnWidths[c], row[c].width)
    }
    val rowAscents = FloatArray(laidRows.size)
    val rowDescents = FloatArray(laidRows.size)
    for (r in laidRows.indices) {
        var ascent = 0f
        var descent = 0f
        for (cell in laidRows[r]) {
            ascent = max(ascent, cell.ascent)
            descent = max(descent, cell.descent)
        }
        rowAscents[r] = ascent
        rowDescents[r] = descent
    }
    var contentWidth = 0f
    for (c in 0 until columnCount) {
        contentWidth += columnWidths[c]
        if (c < columnCount - 1) contentWidth += columnGap
    }
    var contentHeight = 0f
    for (r in laidRows.indices) {
        contentHeight += rowAscents[r] + rowDescents[r]
        if (r < laidRows.size - 1) contentHeight += rowGap
    }
    val bracketWidth = bracketWidthFor(node.kind, size, measure)
    val hasBracket = bracketWidth > 0f
    val innerPad = if (hasBracket) 0.10f * size else 0f
    val totalWidth = contentWidth + 2f * (bracketWidth + innerPad)
    val overshoot = if (hasBracket) 0.08f * size else 0f
    val bracketHeight = contentHeight + 2f * overshoot
    val contentTop = -contentHeight / 2f
    val contentX = bracketWidth + innerPad
    val thickness = ruleThickness(size)
    val elements = ArrayList<MathElement>()
    if (hasBracket) {
        val bracketTop = -bracketHeight / 2f
        when (node.kind) {
            MatrixKind.Paren -> {
                elements.add(MathElement.StretchedGlyph("(", 0f, bracketTop, bracketHeight, size))
                elements.add(
                    MathElement.StretchedGlyph(
                        ")", totalWidth - bracketWidth, bracketTop, bracketHeight, size,
                    )
                )
            }
            MatrixKind.Cases -> {
                elements.add(MathElement.StretchedGlyph("{", 0f, bracketTop, bracketHeight, size))
            }
            MatrixKind.Bracket -> {
                addSquareBracket(elements, 0f, bracketTop, bracketWidth, bracketHeight, thickness, false)
                addSquareBracket(elements, totalWidth - bracketWidth, bracketTop, bracketWidth, bracketHeight, thickness, true)
            }
            MatrixKind.VBar -> {
                elements.add(MathElement.Rule(0f, bracketTop, thickness, bracketHeight))
                elements.add(MathElement.Rule(totalWidth - thickness, bracketTop, thickness, bracketHeight))
            }
            MatrixKind.VBar2 -> {
                val gap = 0.14f * size
                elements.add(MathElement.Rule(0f, bracketTop, thickness, bracketHeight))
                elements.add(MathElement.Rule(gap, bracketTop, thickness, bracketHeight))
                elements.add(MathElement.Rule(totalWidth - thickness, bracketTop, thickness, bracketHeight))
                elements.add(MathElement.Rule(totalWidth - thickness - gap, bracketTop, thickness, bracketHeight))
            }
            MatrixKind.Plain -> Unit
        }
    }
    var y = contentTop
    for (r in laidRows.indices) {
        val rowAscent = rowAscents[r]
        var x = contentX
        for (c in 0 until columnCount) {
            val cell = laidRows[r].getOrNull(c)
            if (cell != null && (cell.width > 0f || cell.height > 0f)) {
                val cellX = if (isCases) x else x + (columnWidths[c] - cell.width) / 2f
                elements.addAll(cell.elements.shift(cellX, y + rowAscent - cell.ascent))
            }
            x += columnWidths[c]
            if (c < columnCount - 1) x += columnGap
        }
        y += rowAscent + rowDescents[r] + rowGap
    }
    val verticalExtent = if (hasBracket) bracketHeight / 2f else contentHeight / 2f
    return buildBox(totalWidth, -verticalExtent, verticalExtent, elements)
}

private fun addSquareBracket(
    elements: MutableList<MathElement>,
    x: Float,
    top: Float,
    width: Float,
    height: Float,
    thickness: Float,
    rightSide: Boolean,
) {
    val verticalX = if (rightSide) x + width - thickness else x
    elements.add(MathElement.Rule(verticalX, top, thickness, height))
    elements.add(MathElement.Rule(x, top, width, thickness))
    elements.add(MathElement.Rule(x, top + height - thickness, width, thickness))
}

private fun bracketWidthFor(kind: MatrixKind, size: Float, measure: MathMeasure): Float = when (kind) {
    MatrixKind.Plain -> 0f
    MatrixKind.Paren -> safeMeasure(measure, "(", size, false, false).width
    MatrixKind.Cases -> safeMeasure(measure, "{", size, false, false).width
    MatrixKind.Bracket -> 0.42f * size
    MatrixKind.VBar -> 0.12f * size
    MatrixKind.VBar2 -> 0.22f * size
}

// ------------------------------------------------------------------ 工具

private fun emptyBox(): Box = Box(0f, 0f, 0f, NO_ELEMENTS)

/**
 * 收集一组基线坐标（基线 y = 0）的绘制指令，算出 ascent/descent 后整体下移。
 */
private fun buildBox(
    width: Float,
    top: Float,
    bottom: Float,
    baselineElements: List<MathElement>,
): Box {
    if (!width.isFinite() || !top.isFinite() || !bottom.isFinite()) return emptyBox()
    val safeWidth = max(0f, width)
    val ascent = max(0f, -top)
    val descent = max(0f, bottom)
    val elements = if (ascent == 0f) baselineElements else baselineElements.shift(0f, ascent)
    return Box(safeWidth, ascent, descent, elements)
}

private fun List<MathElement>.shift(dx: Float, dy: Float): List<MathElement> {
    if (isEmpty()) return NO_ELEMENTS
    if (dx == 0f && dy == 0f) return this
    val out = ArrayList<MathElement>(size)
    for (element in this) out.add(element.translate(dx, dy))
    return out
}

private fun safeMeasure(
    measure: MathMeasure,
    text: String,
    sizePx: Float,
    italic: Boolean,
    bold: Boolean,
): TextMetrics {
    if (text.isEmpty() || sizePx <= 0f) return TextMetrics.Zero
    val result = try {
        measure.measure(text, sizePx, italic, bold)
    } catch (t: Throwable) {
        return TextMetrics.Zero
    }
    return if (
        result.width.isFinite() && result.height.isFinite() &&
        result.ascent.isFinite() && result.descent.isFinite()
    ) {
        result
    } else {
        TextMetrics.Zero
    }
}

private fun styleFlags(kind: SymbolKind, style: MathFontStyle): FontFlags {
    val bold = style == MathFontStyle.Bold || style == MathFontStyle.BoldItalic
    val italic = when (style) {
        MathFontStyle.Italic, MathFontStyle.BoldItalic -> true
        MathFontStyle.Bold -> false
        MathFontStyle.Normal -> kind == SymbolKind.Variable
    }
    return FontFlags(italic, bold)
}

private fun mergeStyle(base: MathFontStyle, overlay: MathFontStyle): MathFontStyle = when (overlay) {
    MathFontStyle.Normal -> base
    MathFontStyle.Bold -> when (base) {
        MathFontStyle.Italic, MathFontStyle.BoldItalic -> MathFontStyle.BoldItalic
        else -> MathFontStyle.Bold
    }
    MathFontStyle.Italic -> when (base) {
        MathFontStyle.Bold, MathFontStyle.BoldItalic -> MathFontStyle.BoldItalic
        else -> MathFontStyle.Italic
    }
    MathFontStyle.BoldItalic -> MathFontStyle.BoldItalic
}

private fun unwrapStyled(node: MathNode, style: MathFontStyle): Pair<MathNode, MathFontStyle> =
    when (node) {
        is MathNode.Styled -> unwrapStyled(node.child, mergeStyle(style, node.style))
        else -> node to style
    }

private fun ruleThickness(sizePx: Float): Float = max(1f, 0.055f * sizePx)

private fun sanitizeRootSize(sizePx: Float): Float =
    if (!sizePx.isFinite() || sizePx <= 0f) 16f else sizePx.coerceIn(MIN_LAYOUT_SIZE, MAX_LAYOUT_SIZE)

private fun sanitizeSize(sizePx: Float): Float =
    if (!sizePx.isFinite()) MIN_LAYOUT_SIZE else sizePx.coerceIn(MIN_LAYOUT_SIZE, MAX_LAYOUT_SIZE)
