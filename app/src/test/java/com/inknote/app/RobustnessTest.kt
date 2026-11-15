package com.inknote.app

import com.inknote.app.ui.MdBlock
import com.inknote.app.ui.plainInlineText
import com.inknote.app.ui.headingIndex
import com.inknote.app.ui.math.linearizeMath
import com.inknote.app.ui.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.random.Random

/**
 * 健壮性测试：解析器 / 公式线性化面对「脏输入」必须永不抛异常。
 *
 * 阅读视图每次打开笔记都会解析全文，一旦解析器对某种字符组合抛异常，
 * App 就会在打开笔记时崩溃 —— 所以这里用固定种子的随机输入做穷举式兜底，
 * 保证任何输入最差也能退化成普通文本。
 */
class RobustnessTest {

    private val alphabet = (
        "abcdefgXYZ0123456789 \n\t" +
            "#*-_~`=><|[]()!{}:;,.\\/$%^&+\"'…—·、。，；：！？「」『』【】（）" +
            "数学公式行内块级**粗体**~~删除~~==高亮==#标签"
        ).toCharArray()

    private fun randomText(rnd: Random, len: Int): String {
        val sb = StringBuilder(len)
        repeat(len) { sb.append(alphabet[rnd.nextInt(alphabet.size)]) }
        return sb.toString()
    }

    @Test
    fun `随机脏输入-parseMarkdown 不抛异常`() {
        val rnd = Random(20261115)
        repeat(400) { round ->
            val text = randomText(rnd, rnd.nextInt(1, 600))
            val blocks = parseMarkdown(text)
            assertNotNull(blocks)
            // 块内容不允许是 null 字段；渲染时拿到的文本都应是字符串
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Heading -> assertNotNull(block.text)
                    is MdBlock.Paragraph -> assertNotNull(block.text)
                    is MdBlock.ListItem -> assertNotNull(block.text)
                    is MdBlock.Quote -> assertNotNull(block.text)
                    is MdBlock.Code -> assertNotNull(block.text)
                    is MdBlock.Math -> assertNotNull(block.latex)
                    else -> Unit
                }
            }
            if (round == 0) assertNotNull(blocks)
        }
    }

    @Test
    fun `随机脏输入-超长文档不抛异常且不丢内容`() {
        val rnd = Random(42)
        val big = buildString {
            repeat(3000) {
                append(randomText(rnd, 200))
                append('\n')
            }
        }
        val blocks = parseMarkdown(big)
        assertNotNull(blocks)
        // 文档不是空的，解析结果也不应该为空（最差也会退化成段落）
        org.junit.Assert.assertTrue("超长文档应至少解析出一个块", blocks.isNotEmpty())
    }

    @Test
    fun `随机脏输入-linearizeMath 不抛异常`() {
        val rnd = Random(7)
        repeat(500) {
            val text = randomText(rnd, rnd.nextInt(0, 200))
            val out = linearizeMath(text) // 不抛异常即可
            assertNotNull(out)
        }
        assertEquals("", linearizeMath(""))
        assertEquals("", linearizeMath("   "))
    }

    @Test
    fun `随机脏输入-LaTeX 解析不抛异常`() {
        val rnd = Random(99)
        repeat(300) {
            val text = randomText(rnd, rnd.nextInt(1, 200))
            // 解析 + 线性化两条路径都跑一遍
            linearizeMath("\\frac{$text}{$text}")
            linearizeMath("\\begin{pmatrix}$text&$text\\\\$text\\end{pmatrix}")
            linearizeMath("\\sqrt[$text]{$text}")
            linearizeMath("\\left($text\\right)")
        }
    }

    @Test
    fun `未闭合语法不吞掉正文`() {
        // 未闭合的公式/代码/链接：内容必须还能显示出来
        assertTrueContains(parseMarkdown("前文 \$未闭合公式 后文"), "后文")
        assertTrueContains(parseMarkdown("前文 ```\ncode\n后文"), "后文")
        assertTrueContains(parseMarkdown("前文 [链接](未闭合 后文"), "后文")
        assertTrueContains(parseMarkdown("前文 [[双链 后文"), "后文")
        assertTrueContains(parseMarkdown("前文 **加粗 后文"), "后文")
        assertTrueContains(parseMarkdown("前文 \$\$\n块公式未闭合\n后文"), "后文")
    }

    @Test
    fun `锚点定位-标题匹配`() {
        val blocks = parseMarkdown(
            """
            # 安装步骤
            ## 第二步：配置
            ### 子小节
            """.trimIndent()
        )
        // 精确 / 去格式 / 宽松匹配都要能找到
        assertEquals(0, headingIndex(blocks, "安装步骤"))
        assertEquals(1, headingIndex(blocks, "第二步：配置"))
        assertEquals(2, headingIndex(blocks, "#子小节"))
        assertEquals(-1, headingIndex(blocks, "不存在的标题"))
        assertEquals("安装步骤", plainInlineText("**安装**步骤"))
    }

    private fun assertTrueContains(blocks: List<MdBlock>, needle: String) {
        val text = blocks.joinToString("\n") { block ->
            when (block) {
                is MdBlock.Heading -> block.text
                is MdBlock.Paragraph -> block.text
                is MdBlock.ListItem -> block.text
                is MdBlock.Quote -> block.text
                is MdBlock.Code -> block.text
                is MdBlock.Math -> block.latex
                else -> ""
            }
        }
        org.junit.Assert.assertTrue("解析结果里应包含「$needle」，实际为：${text.take(200)}", text.contains(needle))
    }
}
