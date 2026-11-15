package com.inknote.app

import com.inknote.app.ui.math.MathElement
import com.inknote.app.ui.math.MathLayout
import com.inknote.app.ui.math.MathMeasure
import com.inknote.app.ui.math.TextMetrics
import com.inknote.app.ui.math.layoutMath
import com.inknote.app.ui.math.linearizeMath
import com.inknote.app.ui.math.mathFitScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数学公式模块的纯 JVM 测试（不依赖 Robolectric）。
 *
 * 覆盖两块：
 * 1) linearizeMath：LaTeX -> Unicode 行内文本；
 * 2) layoutMath：注入假测量后的排版不变量（分数上下关系、上下标位置、
 *    根号覆盖、cases 行不重叠、尺寸全部有限非负等）。
 */
class MathTest {

    /** 假测量：宽度与字符数和字号成正比，用于验证排版几何关系。 */
    private object FakeMeasure : MathMeasure {
        override fun measure(
            text: String,
            sizePx: Float,
            italic: Boolean,
            bold: Boolean,
        ): TextMetrics = TextMetrics(
            width = 0.6f * sizePx * text.length,
            height = sizePx,
            ascent = 0.8f * sizePx,
            descent = 0.2f * sizePx,
        )
    }

    private fun layout(latex: String, size: Float = 100f): MathLayout =
        layoutMath(latex, size, FakeMeasure)

    private fun texts(layout: MathLayout): List<MathElement.Text> =
        layout.elements.filterIsInstance<MathElement.Text>()

    private fun rules(layout: MathLayout): List<MathElement.Rule> =
        layout.elements.filterIsInstance<MathElement.Rule>()

    private fun textOf(layout: MathLayout, text: String): MathElement.Text? =
        texts(layout).firstOrNull { it.text == text }

    // ------------------------------------------------------------ 行内线性化

    @Test
    fun `线性化-分式`() {
        assertEquals("a/b", linearizeMath("\\frac{a}{b}"))
        assertEquals("1/2", linearizeMath("\\frac{1}{2}"))
        assertEquals("(a+1)/(b-2)", linearizeMath("\\frac{a+1}{b-2}"))
        assertEquals("a/b", linearizeMath("\\dfrac{a}{b}"))
    }

    @Test
    fun `线性化-根号`() {
        assertEquals("√x", linearizeMath("\\sqrt{x}"))
        assertEquals("∛x", linearizeMath("\\sqrt[3]{x}"))
        assertEquals("√(2x)", linearizeMath("\\sqrt{2x}"))
        assertEquals("(√x)/2", linearizeMath("\\frac{\\sqrt{x}}{2}"))
    }

    @Test
    fun `线性化-上下标`() {
        assertEquals("x²", linearizeMath("x^{2}"))
        assertEquals("xᵢ", linearizeMath("x_{i}"))
        assertEquals("xᵢ²", linearizeMath("x_i^2"))
        assertEquals("xᵃ⁺ᵇ", linearizeMath("x^{a+b}"))
        // 下标有字符无法转 Unicode 时退化成 _(...)
        assertEquals("x_(abc)", linearizeMath("x_{abc}"))
        assertEquals("xʸ⁺¹", linearizeMath("x^{y+1}"))
    }

    @Test
    fun `线性化-希腊字母与符号`() {
        assertEquals("α+β=γ", linearizeMath("\\alpha + \\beta = \\gamma"))
        assertEquals("≤≥→×·∞π", linearizeMath("\\le \\geq \\to \\times \\cdot \\infty \\pi"))
        assertEquals("∑∏∫", linearizeMath("\\sum \\prod \\int"))
    }

    @Test
    fun `线性化-求和与积分上下限`() {
        assertEquals("∑ᵢ₌₁ⁿ", linearizeMath("\\sum_{i=1}^{n}"))
        assertEquals("∫ₐᵇ", linearizeMath("\\int_a^b"))
        assertEquals("lim_(x→0)", linearizeMath("\\lim_{x\\to 0}"))
        assertEquals("∑ᵢ₌₁ⁿxᵢ", linearizeMath("\\sum_{i=1}^{n} x_i"))
    }

    @Test
    fun `线性化-文本与样式`() {
        assertEquals("中文", linearizeMath("\\text{中文}"))
        assertEquals("X", linearizeMath("\\mathbf{X}"))
        assertEquals("{x}", linearizeMath("\\{x\\}"))
        assertEquals("(a/b)", linearizeMath("\\left( \\frac{a}{b} \\right)"))
        assertEquals("x̄", linearizeMath("\\overline{x}"))
    }

    @Test
    fun `线性化-未知命令与容错`() {
        assertEquals("foo", linearizeMath("\\foo"))
        val custom = linearizeMath("\\foo{bar}")
        assertTrue(custom.contains("foo"))
        assertTrue(custom.contains("bar"))
        assertFalse(custom.contains("\\"))
        assertEquals("", linearizeMath(""))
        assertEquals("", linearizeMath("   \n\t "))
        // 未闭合花括号、孤立上下标等乱写输入不能抛异常
        assertNotNull(linearizeMath("\\frac{a}{b"))
        assertNotNull(linearizeMath("{{{x"))
        assertEquals("x", linearizeMath("x^"))
    }

    @Test
    fun `线性化-超长输入有长度保护`() {
        val huge = "x".repeat(50_000)
        val result = linearizeMath(huge)
        assertTrue(result.isNotEmpty())
        assertTrue(result.length <= 20_000)
        // 命令重复也不能卡死
        val repeated = "\\frac{a}{b}".repeat(3_000)
        assertNotNull(linearizeMath(repeated))
    }

    // ------------------------------------------------------------ 排版不变量

    @Test
    fun `排版-分式的分子分母与分数线`() {
        val layout = layout("\\frac{a}{b}")
        val numerator = textOf(layout, "a")
        val denominator = textOf(layout, "b")
        val rule = rules(layout).firstOrNull()
        assertNotNull(numerator)
        assertNotNull(denominator)
        assertNotNull(rule)
        // 分母在分子下方
        assertTrue(denominator!!.baseline > numerator!!.baseline)
        // 分数线夹在两者之间
        assertTrue("分数线应在分子下方", rule!!.y > numerator.baseline)
        assertTrue("分数线应在分母上方", rule.y + rule.height < denominator.baseline)
        assertTrue(layout.width > 0f)
        assertTrue(layout.ascent > 0f)
        assertTrue(layout.descent > 0f)
    }

    @Test
    fun `排版-上标位置与字号`() {
        val layout = layout("x^{2}")
        val base = textOf(layout, "x")
        val sup = textOf(layout, "2")
        assertNotNull(base)
        assertNotNull(sup)
        // 变量斜体、数字正体
        assertTrue(base!!.italic)
        assertFalse(sup!!.italic)
        // 上标字号 0.7 倍
        assertEquals(70f, sup.sizePx, 0.001f)
        // 上标基线高于主基线，且上标底部高于主基线
        assertTrue(sup.baseline < base.baseline)
        assertTrue(sup.baseline + 0.2f * sup.sizePx <= base.baseline + 0.001f)
    }

    @Test
    fun `排版-嵌套上下标`() {
        val layout = layout("x_{i}^{2}")
        assertTrue(texts(layout).any { it.text == "x" })
        assertTrue(texts(layout).any { it.text == "i" })
        assertTrue(texts(layout).any { it.text == "2" })
        assertTrue(layout.width > 0f)
        assertAllFinite(layout)
    }

    @Test
    fun `排版-根号宽度覆盖内部`() {
        val layout = layout("\\sqrt{x}")
        val inner = textOf(layout, "x")
        assertNotNull(inner)
        assertTrue("根号横线在内容上方", rules(layout).isNotEmpty())
        assertTrue("根号的笔画应在内容左边", inner!!.x > 0f)
        assertTrue("根号总宽度要覆盖内部", layout.width >= inner.x + inner.sizePx * 0.6f)
        assertTrue(layout.ascent > 0f)
    }

    @Test
    fun `排版-cases 两行不重叠`() {
        val layout = layout("\\begin{cases} a & b \\\\ c & d \\end{cases}")
        val a = textOf(layout, "a")
        val b = textOf(layout, "b")
        val c = textOf(layout, "c")
        val d = textOf(layout, "d")
        assertNotNull(a)
        assertNotNull(b)
        assertNotNull(c)
        assertNotNull(d)
        // 同一行基线一致
        assertEquals(a!!.baseline, b!!.baseline, 0.001f)
        assertEquals(c!!.baseline, d!!.baseline, 0.001f)
        // 第二行在第一行下方且不重叠
        assertTrue(c.baseline > a.baseline)
        assertTrue("cases 两行不应重叠", c.baseline - 0.8f * c.sizePx >= a.baseline + 0.2f * a.sizePx - 0.001f)
    }

    @Test
    fun `排版-矩阵与自动缩放括号`() {
        val matrix = layout("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}")
        assertTrue(texts(matrix).size >= 4)
        val matrixGlyphs = matrix.elements.filterIsInstance<MathElement.StretchedGlyph>()
        assertTrue("pmatrix 应有左右两个括号", matrixGlyphs.size >= 2)
        assertTrue(matrixGlyphs.all { it.targetHeight > 0f })

        val delimited = layout("\\left(\\frac{a}{b}\\right)")
        val glyphs = delimited.elements.filterIsInstance<MathElement.StretchedGlyph>()
        assertEquals(2, glyphs.size)
        // 括号必须比普通大写字母更高（自动缩放）
        assertTrue(glyphs[0].targetHeight > delimited.ascent * 0.5f)
        assertAllFinite(delimited)
    }

    @Test
    fun `排版-求和上下限上下堆叠`() {
        val layout = layout("\\sum_{i=1}^{n}")
        val op = textOf(layout, "∑")
        val sup = textOf(layout, "n")
        val sub = textOf(layout, "i")
        assertNotNull(op)
        assertNotNull(sup)
        assertNotNull(sub)
        assertTrue("上限定在运算符上方", sup!!.baseline < op!!.baseline)
        assertTrue("下限定在运算符下方", sub!!.baseline > op.baseline)
        assertTrue(layout.ascent > 100f)
        assertAllFinite(layout)
    }

    @Test
    fun `排版-空输入与空白`() {
        assertTrue(layout("").elements.isEmpty())
        assertEquals(0f, layout("").width, 0.001f)
        // 未知命令退化成文本，不崩
        val unknown = layout("\\unknowncmd")
        assertTrue(unknown.width > 0f)
    }

    @Test
    fun `排版-所有尺寸有限且非负`() {
        val formulas = listOf(
            "\\frac{a}{b}",
            "x^{2}",
            "x_{i}^{2}",
            "\\sqrt{x}",
            "\\sqrt[3]{x+1}",
            "\\sum_{i=1}^{n} i",
            "\\int_0^1 x\\,dx",
            "\\lim_{x\\to 0} \\frac{\\sin x}{x}",
            "\\begin{matrix} a & b \\\\ c & d \\end{matrix}",
            "\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}",
            "\\begin{bmatrix} a & b \\\\ c & d \\end{bmatrix}",
            "\\begin{vmatrix} a & b \\\\ c & d \\end{vmatrix}",
            "\\begin{cases} x & x>0 \\\\ -x & x<0 \\end{cases}",
            "\\begin{array}{cc} a & b \\\\ c & d \\end{array}",
            "\\overline{AB}",
            "\\hat{x}",
            "\\vec{v}",
            "\\tilde{x}",
            "\\dot{x}",
            "\\ddot{x}",
            "\\left(\\frac{a}{b}\\right)",
            "\\left[\\sqrt{x}\\right]",
            "\\left\\{ x \\right\\}",
            "\\text{中文}",
            "\\mathbf{x+1}",
            "\\mathit{ABC}",
            "\\alpha+\\beta=\\gamma",
            "\\foo{bar}",
            "\\displaystyle \\frac{1}{2}",
            "\\frac{\\frac{a}{b}}{\\frac{c}{d}}",
            "{{{{x}}}}",
            "a^",
            "\\left(",
            "\\begin{matrix}",
        )
        for (formula in formulas) {
            val layout = layoutMath(formula, 100f, FakeMeasure)
            assertTrue("width 非有限：$formula", layout.width.isFinite())
            assertTrue("height 非有限：$formula", layout.height.isFinite())
            assertTrue("ascent 非有限：$formula", layout.ascent.isFinite())
            assertTrue("descent 非有限：$formula", layout.descent.isFinite())
            assertTrue("width 为负：$formula", layout.width >= 0f)
            assertTrue("ascent 为负：$formula", layout.ascent >= 0f)
            assertTrue("descent 为负：$formula", layout.descent >= 0f)
            assertTrue("height 与 ascent+descent 不一致：$formula",
                kotlin.math.abs(layout.height - (layout.ascent + layout.descent)) < 0.01f)
            for (element in layout.elements) {
                when (element) {
                    is MathElement.Text -> {
                        assertTrue("$formula 文本 sizePx 非法", element.sizePx.isFinite() && element.sizePx > 0f)
                        assertTrue("$formula 文本坐标非法", element.x.isFinite() && element.baseline.isFinite())
                    }
                    is MathElement.Rule -> {
                        assertTrue("$formula 矩形尺寸非法",
                            element.width.isFinite() && element.height.isFinite() &&
                                element.width >= 0f && element.height >= 0f)
                        assertTrue("$formula 矩形坐标非法", element.x.isFinite() && element.y.isFinite())
                    }
                    is MathElement.Line -> {
                        assertTrue("$formula 线条非法",
                            element.x1.isFinite() && element.y1.isFinite() &&
                                element.x2.isFinite() && element.y2.isFinite() &&
                                element.strokeWidth.isFinite() && element.strokeWidth > 0f)
                    }
                    is MathElement.StretchedGlyph -> {
                        assertTrue("$formula 定界符非法",
                            element.targetHeight.isFinite() && element.targetHeight > 0f &&
                                element.sizePx.isFinite() && element.sizePx > 0f &&
                                element.x.isFinite() && element.top.isFinite())
                    }
                }
            }
        }
    }

    @Test
    fun `排版-超宽公式的均匀缩放`() {
        // 放得下不缩放
        assertEquals(1f, mathFitScale(100f, 400f), 0.001f)
        // 按比例缩小
        assertEquals(0.8f, mathFitScale(1000f, 800f), 0.001f)
        // 最小 0.6，绝不缩到 0
        val tiny = mathFitScale(10_000f, 100f)
        assertEquals(0.6f, tiny, 0.001f)
        assertTrue(tiny > 0f && tiny.isFinite())
        // 非法输入安全退化
        assertEquals(1f, mathFitScale(0f, 100f), 0.001f)
        assertEquals(1f, mathFitScale(100f, Float.POSITIVE_INFINITY), 0.001f)
    }

    private fun assertAllFinite(layout: MathLayout) {
        assertTrue(layout.width.isFinite() && layout.width >= 0f)
        assertTrue(layout.height.isFinite() && layout.height >= 0f)
        assertTrue(layout.ascent.isFinite() && layout.ascent >= 0f)
        assertTrue(layout.descent.isFinite() && layout.descent >= 0f)
        for (element in layout.elements) {
            when (element) {
                is MathElement.Text -> assertTrue(element.x.isFinite() && element.baseline.isFinite())
                is MathElement.Rule -> assertTrue(
                    element.x.isFinite() && element.y.isFinite() &&
                        element.width.isFinite() && element.width >= 0f &&
                        element.height.isFinite() && element.height >= 0f,
                )
                is MathElement.Line -> assertTrue(
                    element.x1.isFinite() && element.y1.isFinite() &&
                        element.x2.isFinite() && element.y2.isFinite(),
                )
                is MathElement.StretchedGlyph -> assertTrue(
                    element.x.isFinite() && element.top.isFinite() &&
                        element.targetHeight.isFinite() && element.targetHeight > 0f,
                )
            }
        }
    }
}
