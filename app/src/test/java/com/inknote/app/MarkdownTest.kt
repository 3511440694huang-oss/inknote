package com.inknote.app

import com.inknote.app.ui.MdBlock
import com.inknote.app.ui.MdInline
import com.inknote.app.ui.MdTableAlign
import com.inknote.app.ui.parseInline
import com.inknote.app.ui.parseMarkdown
import com.inknote.app.ui.plainInline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 解析单元测试（纯 JVM，JUnit4，不依赖 Robolectric / Android 运行时）。
 *
 * 只断言 [MdBlock] / [MdInline] 的结构与字段，不断言渲染像素。
 */
class MarkdownTest {

    private fun inlines(md: String): List<MdInline> = parseInline(md)

    private fun plain(md: String): String = plainInline(parseInline(md))

    private fun links(md: String): List<MdInline.Link> = inlines(md).filterIsInstance<MdInline.Link>()

    private fun singleLink(md: String): MdInline.Link = links(md).single()

    private fun tags(md: String): List<String> = inlines(md).filterIsInstance<MdInline.Tag>().map { it.name }

    /* ----------------------------- 块级 ----------------------------- */

    @Test
    fun `标题与段落`() {
        val blocks = parseMarkdown("# 一级标题\n\n正文第一行\n正文第二行\n\n## 二级标题")
        assertEquals(3, blocks.size)
        assertEquals(MdBlock.Heading(1, "一级标题"), blocks[0])
        assertEquals(MdBlock.Paragraph("正文第一行\n正文第二行"), blocks[1])
        assertEquals(MdBlock.Heading(2, "二级标题"), blocks[2])
    }

    @Test
    fun `setext 标题与分隔线的区分`() {
        val blocks = parseMarkdown("标题一\n===\n\n标题二\n---\n\n正文\n\n---")
        assertEquals(4, blocks.size)
        assertEquals(MdBlock.Heading(1, "标题一"), blocks[0])
        assertEquals(MdBlock.Heading(2, "标题二"), blocks[1])
        assertEquals(MdBlock.Paragraph("正文"), blocks[2])
        assertEquals(MdBlock.Rule, blocks[3])
    }

    @Test
    fun `任务清单与有序列表`() {
        val md = """
            - [ ] 未完成
            - [x] 已完成
            1. 第一步
            2. 第二步
        """.trimIndent()
        val blocks = parseMarkdown(md)
        assertEquals(4, blocks.size)
        val first = blocks[0] as MdBlock.ListItem
        assertEquals(false, first.checked)
        assertEquals("未完成", first.text)
        val second = blocks[1] as MdBlock.ListItem
        assertEquals(true, second.checked)
        val ordered = blocks[2] as MdBlock.ListItem
        assertTrue(ordered.ordered)
        assertEquals("1.", ordered.marker)
    }

    @Test
    fun `有序列表保留起始序号`() {
        val items = parseMarkdown("3. 第三\n4. 第四").map { it as MdBlock.ListItem }
        assertEquals(2, items.size)
        assertTrue(items[0].ordered)
        assertEquals("3.", items[0].marker)
        assertEquals("4.", items[1].marker)
    }

    @Test
    fun `嵌套列表按缩进映射深度`() {
        val md = "- 一级\n  - 二级\n    - 三级\n- 回到一级"
        val items = parseMarkdown(md).map { it as MdBlock.ListItem }
        assertEquals(4, items.size)
        assertEquals(listOf(0, 1, 2, 0), items.map { it.depth })
        assertEquals(listOf("一级", "二级", "三级", "回到一级"), items.map { it.text })
    }

    @Test
    fun `多行列表项续行并入上一项`() {
        val blocks = parseMarkdown("- 第一行\n  第二行续\n- 第二项")
        assertEquals(2, blocks.size)
        assertEquals("第一行\n第二行续", (blocks[0] as MdBlock.ListItem).text)
    }

    @Test
    fun `列表项内的围栏代码成为子块`() {
        val md = "- 说明\n  ```kotlin\n  val a = 1\n  ```\n- 下一项"
        val blocks = parseMarkdown(md)
        assertEquals(2, blocks.size)
        val item = blocks[0] as MdBlock.ListItem
        assertEquals("说明", item.text)
        assertEquals(listOf(MdBlock.Code("kotlin", "val a = 1")), item.children)
    }

    @Test
    fun `列表项内的块级公式成为子块`() {
        val md = "- 公式\n  \$\$\n  E = mc^2\n  \$\$\n- 下一项"
        val blocks = parseMarkdown(md)
        val item = blocks[0] as MdBlock.ListItem
        assertEquals(listOf(MdBlock.Math("E = mc^2")), item.children)
    }

    @Test
    fun `引用合并嵌套与多段`() {
        assertEquals(MdBlock.Quote("第一行\n第二行"), parseMarkdown("> 第一行\n> 第二行").single())
        assertEquals(MdBlock.Quote("第一段\n\n第二段"), parseMarkdown("> 第一段\n>\n> 第二段").single())

        val nested = parseMarkdown("> 外层\n> > 内层").single()
        assertTrue(nested is MdBlock.QuoteBlock)
        val children = (nested as MdBlock.QuoteBlock).blocks
        assertEquals(2, children.size)
        assertEquals(MdBlock.Paragraph("外层"), children[0])
        assertEquals(MdBlock.Quote("内层"), children[1])
    }

    @Test
    fun `引用里的代码块`() {
        val block = parseMarkdown("> ```kotlin\n> val a = 1\n> ```").single()
        assertTrue(block is MdBlock.QuoteBlock)
        assertEquals(MdBlock.Code("kotlin", "val a = 1"), (block as MdBlock.QuoteBlock).blocks.single())
    }

    @Test
    fun `围栏代码块保留 info 且不解析内部`() {
        val md = "```kotlin title=demo\n# 不是标题\n- 不是列表\n```\n\n正文"
        val blocks = parseMarkdown(md)
        assertEquals(2, blocks.size)
        val code = blocks[0] as MdBlock.Code
        assertEquals("kotlin title=demo", code.lang)
        assertEquals("# 不是标题\n- 不是列表", code.text)
        assertEquals(MdBlock.Paragraph("正文"), blocks[1])
    }

    @Test
    fun `缩进代码块`() {
        val blocks = parseMarkdown("    第一行\n    第二行\n\n正文")
        assertEquals(2, blocks.size)
        assertEquals(MdBlock.Code("", "第一行\n第二行"), blocks[0])
        assertEquals(MdBlock.Paragraph("正文"), blocks[1])
    }

    @Test
    fun `表格对齐与转义竖线`() {
        val md = """
            | 左 | 中 | 右 | 默认 |
            | :--- | :---: | ---: | --- |
            | 甲 | 1 | 2 | 3 |
            | a \| b | c | d | e |
        """.trimIndent()
        val table = parseMarkdown(md).single() as MdBlock.Table
        assertEquals(listOf("左", "中", "右", "默认"), table.header)
        assertEquals(
            listOf(MdTableAlign.Left, MdTableAlign.Center, MdTableAlign.Right, MdTableAlign.Default),
            table.alignments,
        )
        assertEquals(2, table.rows.size)
        assertEquals(listOf("甲", "1", "2", "3"), table.rows[0])
        assertEquals("a \\| b", table.rows[1][0])
        assertEquals("a | b", plain(table.rows[1][0]))
    }

    @Test
    fun `分隔线多种写法`() {
        val blocks = parseMarkdown("***\n\n___\n\n- - -")
        assertEquals(3, blocks.size)
        assertTrue(blocks.all { it is MdBlock.Rule })
    }

    @Test
    fun `单独一行的图片与行内图片占位`() {
        assertEquals(
            MdBlock.Image("示意图", "images/a.png"),
            parseMarkdown("![示意图](images/a.png)").single(),
        )
        val placeholder = inlines("前文 ![图](a.png) 后文").filterIsInstance<MdInline.ImagePlaceholder>().single()
        assertEquals("图", placeholder.alt)
        assertEquals("前文 [图片] 图 后文", plain("前文 ![图](a.png) 后文"))
    }

    @Test
    fun `块级公式多行与单行`() {
        assertEquals(MdBlock.Math("E = mc^2"), parseMarkdown("\$\$\nE = mc^2\n\$\$").single())
        assertEquals(MdBlock.Math("a + b"), parseMarkdown("\$\$a + b\$\$").single())
        assertEquals(MdBlock.Math("x^2"), parseMarkdown("\\[\nx^2\n\\]").single())
        assertEquals(MdBlock.Math("x^2"), parseMarkdown("\\[ x^2 \\]").single())
    }

    @Test
    fun `未闭合的块级公式不吞正文`() {
        val blocks = parseMarkdown("\$\$\n第一行\n第二行")
        val para = blocks.single()
        assertTrue(para is MdBlock.Paragraph)
        val text = (para as MdBlock.Paragraph).text
        assertTrue(text.contains("第一行"))
        assertTrue(text.contains("第二行"))
    }

    @Test
    fun `frontmatter 被跳过`() {
        val blocks = parseMarkdown("---\ntags: [a, b]\n---\n\n正文")
        assertEquals(1, blocks.size)
        assertEquals(MdBlock.Paragraph("正文"), blocks[0])
    }

    @Test
    fun `空文档与空白文档`() {
        assertTrue(parseMarkdown("").isEmpty())
        assertTrue(parseMarkdown("   \n\n\t\n").isEmpty())
    }

    @Test
    fun `硬换行`() {
        val blocks = parseMarkdown("第一行  \n第二行\\\n第三行")
        assertEquals(MdBlock.Paragraph("第一行\n第二行\n第三行"), blocks.single())
    }

    @Test
    fun `CRLF 与连续空行`() {
        val blocks = parseMarkdown("# 标题\r\n\r\n\r\n正文\r\n")
        assertEquals(2, blocks.size)
        assertEquals(MdBlock.Heading(1, "标题"), blocks[0])
        assertEquals(MdBlock.Paragraph("正文"), blocks[1])
    }

    /* ----------------------------- 行内 ----------------------------- */

    @Test
    fun `行内加粗斜体删除高亮与代码`() {
        val runs = inlines("**粗** *斜* ***粗斜*** ~~删~~ ==亮== `码`")
        assertTrue(runs.any { it is MdInline.Text && it.bold && it.text == "粗" })
        assertTrue(runs.any { it is MdInline.Text && it.italic && it.text == "斜" })
        assertTrue(runs.any { it is MdInline.Text && it.bold && it.italic && it.text == "粗斜" })
        assertTrue(runs.any { it is MdInline.Text && it.strike && it.text == "删" })
        assertTrue(runs.any { it is MdInline.Text && it.highlight && it.text == "亮" })
        assertTrue(runs.any { it is MdInline.Text && it.code && it.text == "码" })
    }

    @Test
    fun `链接文字可以有行内样式`() {
        val link = singleLink("[**粗链接**](https://a.com)")
        assertEquals("url:https://a.com", link.target)
        assertEquals("粗链接", plainInline(link.children))
        assertTrue(link.children.any { it is MdInline.Text && it.bold })
    }

    @Test
    fun `转义与下划线单词`() {
        assertEquals(
            "*不是斜体* 与 _也不是_ 以及 \\ 反斜杠",
            plain("\\*不是斜体\\* 与 \\_也不是\\_ 以及 \\\\ 反斜杠"),
        )
        assertFalse(inlines("\\*不是斜体\\*").any { it is MdInline.Text && it.italic })
        assertEquals("snake_case_name", plain("snake_case_name"))
        assertFalse(inlines("snake_case_name").any { it is MdInline.Text && it.italic })
    }

    @Test
    fun `未闭合的星号按普通文本`() {
        assertEquals("**未闭合", plain("**未闭合"))
        assertFalse(inlines("**未闭合").any { it is MdInline.Text && it.bold })
        assertEquals("*单项", plain("*单项"))
    }

    @Test
    fun `外链相对链接与锚点`() {
        assertEquals("url:https://example.com/path", singleLink("[官网](https://example.com/path)").target)
        assertEquals("url:note.md", singleLink("[笔记](note.md)").target)
        assertEquals("url:../目录/笔记.md", singleLink("[上级](../目录/笔记.md)").target)
        assertEquals("url:#某个标题", singleLink("[跳到小节](#某个标题)").target)
        assertEquals("文档", plainInline(singleLink("[文档](https://example.com)").children))
    }

    @Test
    fun `双链与别名`() {
        assertEquals("wiki:笔记", singleLink("[[笔记]]").target)
        assertEquals("wiki:笔记", singleLink("[[笔记|别名]]").target)
        assertEquals("别名", plainInline(singleLink("[[笔记|别名]]").children))
        assertEquals("wiki:笔记#小节", singleLink("[[笔记#小节]]").target)
        assertEquals("wiki:笔记#小节", singleLink("[[笔记#小节|别名]]").target)
        assertEquals("笔记", plainInline(singleLink("[[笔记#小节]]").children))
    }

    @Test
    fun `裸 URL 与自动链接去尾标点`() {
        assertEquals("url:https://example.com/path", singleLink("访问 https://example.com/path。").target)
        assertEquals("url:http://a.cn/b", singleLink("见 http://a.cn/b，然后").target)
        assertEquals("url:https://www.example.org", singleLink("www.example.org 是网址").target)
        assertEquals("url:https://example.com/x", singleLink("<https://example.com/x>").target)
        assertEquals("url:https://example.com/a", singleLink("(见 https://example.com/a).").target)
    }

    @Test
    fun `标签规则`() {
        assertEquals(listOf("墨笺", "ink-note", "a/b"), tags("#墨笺 #ink-note #a/b"))
        assertTrue(tags("今天花了 #100 元").isEmpty())
        assertTrue(tags("文本#标签").isEmpty())
        assertEquals(listOf("标签"), tags("（#标签）"))
        assertTrue(tags("# 这不是标签").isEmpty())
        assertTrue(tags("background: #FFFFFF").isEmpty())
    }

    @Test
    fun `链接里嵌套括号`() {
        val link = singleLink("[维基](https://en.wikipedia.org/wiki/Foo_(bar))")
        assertEquals("url:https://en.wikipedia.org/wiki/Foo_(bar)", link.target)
        assertEquals("维基", plainInline(link.children))
    }

    @Test
    fun `行内公式`() {
        val runs = inlines("质能方程 \$E = mc^2\$ 与 \\(a^2\\) 结束")
        val math = runs.filterIsInstance<MdInline.InlineMath>()
        assertEquals(listOf("E = mc^2", "a^2"), math.map { it.latex })
        assertFalse(runs.any { it is MdInline.Text && it.italic })

        // $...$ 里的 * 不被 Markdown 解析
        val protected = inlines("公式 \$a * b * c\$ 结束")
        assertEquals(listOf("a * b * c"), protected.filterIsInstance<MdInline.InlineMath>().map { it.latex })
        assertFalse(protected.any { it is MdInline.Text && it.italic })
    }

    @Test
    fun `中文标点不被误判为标签或列表`() {
        val blocks = parseMarkdown("今天花了 #100 元，买了 2 个苹果。")
        assertEquals(1, blocks.size)
        assertEquals(MdBlock.Paragraph("今天花了 #100 元，买了 2 个苹果。"), blocks[0])
    }

    @Test
    fun `代码围栏与表格`() {
        val md = """
            ```kotlin
            val a = 1
            ```
            | 名称 | 值 |
            | --- | --- |
            | 甲 | 1 |
            | 乙 | 2 |
        """.trimIndent()
        val blocks = parseMarkdown(md)
        assertEquals(2, blocks.size)
        val code = blocks[0] as MdBlock.Code
        assertEquals("kotlin", code.lang)
        assertEquals("val a = 1", code.text)
        val table = blocks[1] as MdBlock.Table
        assertEquals(listOf("名称", "值"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("甲", "1"), table.rows[0])
    }

    @Test
    fun `引用与分隔线`() {
        val blocks = parseMarkdown("> 引用第一行\n> 引用第二行\n\n---")
        assertEquals(2, blocks.size)
        assertEquals(MdBlock.Quote("引用第一行\n引用第二行"), blocks[0])
        assertEquals(MdBlock.Rule, blocks[1])
    }
}
