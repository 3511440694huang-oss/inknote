package com.inknote.app.ui.math

/**
 * 纯 Kotlin 的 LaTeX 公式模型与盒模型。
 *
 * 这一层刻意不依赖 Android / Compose 运行时：解析、测量与摆放都是普通 Kotlin 代码，
 * 真正的文本测量通过 [MathMeasure] 注入。这样 JVM 单元测试可以用假测量实现，
 * 而 Compose 端用 [androidx.compose.ui.text.TextMeasurer] 实现，二者共享同一套排版逻辑。
 */

/** 单个文本原子的测量结果，单位全部是像素。 */
data class TextMetrics(
    val width: Float,
    val height: Float,
    val ascent: Float,
    val descent: Float,
) {
    companion object {
        val Zero = TextMetrics(0f, 0f, 0f, 0f)
    }
}

/**
 * 文本测量注入点。
 *
 * @param text 要测量的文本（单个公式原子，如 "x"、"sin"、"∑"）
 * @param sizePx 字号，像素
 * @param italic 是否斜体（数学变量为斜体，数字/函数名为正体）
 * @param bold 是否加粗（\mathbf）
 */
interface MathMeasure {
    fun measure(text: String, sizePx: Float, italic: Boolean, bold: Boolean): TextMetrics
}

/** 数学字体样式，\mathbf / \mathit 等命令作用到子树。 */
enum class MathFontStyle { Normal, Bold, Italic, BoldItalic }

/** 符号种类：决定默认正体/斜体以及行间空白。 */
enum class SymbolKind { Number, Variable, Function, Operator, Normal }

/** 分式样式：\frac（普通）、\dfrac（展示）、\tfrac（文本）。 */
enum class FracStyle { Normal, Display, Text }

/** 矩阵/环境种类。 */
enum class MatrixKind { Plain, Paren, Bracket, VBar, VBar2, Cases }

/** 重音（上划线、帽子、向量箭头等）。 */
enum class AccentKind {
    Overline, Bar, Hat, WideHat, Vec, Dot, Ddot, Tilde, WideTilde, Check, Acute, Grave, Breve,
}

/**
 * 公式语法树。所有节点都不可变；解析器保证不抛异常，未知命令退化成普通文本。
 */
sealed interface MathNode {
    /** 顺序排列的一行（分组）。 */
    data class Row(val items: List<MathNode>) : MathNode

    /** 一个文本符号。 */
    data class Symbol(val text: String, val kind: SymbolKind = SymbolKind.Normal) : MathNode

    /** 原样文本（\text{...}、\operatorname{...}）。 */
    data class TextRun(val text: String) : MathNode

    /** 样式包裹（\mathbf、\mathit 等）。 */
    data class Styled(val style: MathFontStyle, val child: MathNode) : MathNode

    /** 空白，单位 em。 */
    data class Space(val widthEm: Float) : MathNode

    /** 上下标。 */
    data class Script(val base: MathNode, val sub: MathNode?, val sup: MathNode?) : MathNode

    data class Frac(
        val numerator: MathNode,
        val denominator: MathNode,
        val style: FracStyle = FracStyle.Normal,
    ) : MathNode

    /** 根号，index 为可选的根指数（\sqrt[3]{x}）。 */
    data class Root(val index: MathNode?, val body: MathNode) : MathNode

    /** 自动缩放的定界符 \left( ... \right)，open/close 为 Unicode 字符，"." 表示不可见。 */
    data class Delimited(val open: String, val body: MathNode, val close: String) : MathNode

    data class Accent(val kind: AccentKind, val body: MathNode) : MathNode

    /** 矩阵 / cases，rows 为行，每行为若干单元格。 */
    data class Matrix(val kind: MatrixKind, val rows: List<List<MathNode>>) : MathNode

    /** 大型运算符（∑∏∫ 等）或带上下限的函数名（lim、max…）。 */
    data class BigOperator(val op: String, val limits: Boolean = true) : MathNode
}

/**
 * 排版盒模型里的一次绘制指令，坐标以排版盒左上角为原点（y 向下，单位 px）。
 * Compose 端把它翻译成 drawText / drawRect / drawLine。
 */
sealed interface MathElement {
    /** 平移一条绘制指令，用于父节点摆放子节点。 */
    fun translate(dx: Float, dy: Float): MathElement

    /** 一段文字；[baseline] 是基线在盒子里的 y 坐标。 */
    data class Text(
        val text: String,
        val x: Float,
        val baseline: Float,
        val sizePx: Float,
        val italic: Boolean,
        val bold: Boolean,
    ) : MathElement {
        override fun translate(dx: Float, dy: Float): MathElement =
            copy(x = x + dx, baseline = baseline + dy)
    }

    /** 实心矩形：分数线、根号横线、矩阵方括号、上划线都用它画，边缘最锐利。 */
    data class Rule(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    ) : MathElement {
        override fun translate(dx: Float, dy: Float): MathElement = copy(x = x + dx, y = y + dy)
    }

    /** 直线：根号的两笔斜线。 */
    data class Line(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val strokeWidth: Float,
    ) : MathElement {
        override fun translate(dx: Float, dy: Float): MathElement =
            copy(x1 = x1 + dx, y1 = y1 + dy, x2 = x2 + dx, y2 = y2 + dy)
    }

    /**
     * 纵向拉伸的定界符字形：\left( \right)、矩阵圆括号/花括号。
     * [targetHeight] 是拉伸后的目标高度，Compose 端按比例纵向缩放字形。
     */
    data class StretchedGlyph(
        val glyph: String,
        val x: Float,
        val top: Float,
        val targetHeight: Float,
        val sizePx: Float,
        val italic: Boolean = false,
        val bold: Boolean = false,
    ) : MathElement {
        override fun translate(dx: Float, dy: Float): MathElement =
            copy(x = x + dx, top = top + dy)
    }
}

/** 一次完整排版的结果：尺寸（px）与绘制指令。 */
class MathLayout(
    val width: Float,
    val height: Float,
    val ascent: Float,
    val descent: Float,
    val elements: List<MathElement>,
) {
    val baseline: Float get() = ascent

    companion object {
        val Empty = MathLayout(0f, 0f, 0f, 0f, emptyList())
    }
}

/**
 * 计算把公式塞进可用宽度所需的均匀缩放比例。
 *
 * - 放得下就返回 1f；
 * - 放不下按 available / natural 等比缩小；
 * - 最小 [minScale]（默认 0.6），保证不会缩成 0 或不可见的细线。
 */
fun mathFitScale(
    naturalWidth: Float,
    availableWidthPx: Float,
    minScale: Float = 0.6f,
): Float {
    if (!naturalWidth.isFinite() || naturalWidth <= 0f) return 1f
    if (!availableWidthPx.isFinite() || availableWidthPx <= 0f) return 1f
    if (naturalWidth <= availableWidthPx) return 1f
    val raw = availableWidthPx / naturalWidth
    if (!raw.isFinite() || raw <= 0f) return 1f
    return if (raw < minScale) minScale else raw
}
