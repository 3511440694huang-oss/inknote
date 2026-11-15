package com.inknote.app

import com.inknote.app.data.FileVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VaultTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun vault(): FileVault = FileVault(tmp.newFolder("vault"), "测试仓库")

    @Test
    fun `新建笔记自动补 md 并去重`() {
        val v = vault()
        val a = v.createNote(null, "未命名笔记", "内容")
        val b = v.createNote(null, "未命名笔记", "")
        assertEquals("未命名笔记.md", a?.name)
        assertEquals("未命名笔记 2.md", b?.name)
        assertEquals("内容", v.read(a!!.id))
        // Windows 非法字符会被清洗
        val c = v.createNote(null, "a/b:c", "x")
        assertNotNull(c)
        assertFalse(c!!.name.contains('/'))
        assertFalse(c.name.contains(':'))
    }

    @Test
    fun `读写与列表隐藏点文件`() {
        val v = vault()
        val e = v.createNote(null, "笔记", "第一行\n第二行：中文")!!
        assertEquals("第一行\n第二行：中文", v.read(e.id))
        assertTrue(v.write(e.id, "改写"))
        assertEquals("改写", v.read(e.id))
        File(tmp.root, "vault/.obsidian").mkdirs()
        assertEquals(1, v.list(null).size)
    }

    @Test
    fun `重命名保留 markdown 扩展名`() {
        val v = vault()
        val e = v.createNote(null, "旧名字", "x")!!
        val renamed = v.rename(e.id, "新名字")
        assertNotNull(renamed)
        assertEquals("新名字.md", renamed!!.name)
        assertNull(v.read(e.id))
        assertEquals("x", v.read(renamed.id))
    }

    @Test
    fun `删除文件夹递归删除`() {
        val v = vault()
        val dir = v.createDir(null, "资料")!!
        v.createNote(dir.id, "子笔记", "x")
        assertTrue(v.list(dir.id).isNotEmpty())
        assertTrue(v.delete(dir.id))
        assertFalse(File(tmp.root, "vault/资料").exists())
    }

    @Test
    fun `walkMarkdown 递归找到所有文本文件`() {
        val v = vault()
        v.createNote(null, "根笔记", "x")
        val d = v.createDir(null, "子目录")!!
        v.createNote(d.id, "子笔记", "x")
        v.createNote(d.id, "说明", "x")
        v.write("${d.id}/其它.txt", "纯文本")
        val all = v.walkMarkdown().map { it.name }.toSet()
        assertTrue(all.containsAll(setOf("根笔记.md", "子笔记.md", "说明.md", "其它.txt")))
    }

    @Test
    fun `示例笔记的 Markdown 都能解析`() {
        val blocks1 = com.inknote.app.ui.parseMarkdown(com.inknote.app.data.VaultManager.WELCOME)
        val blocks2 = com.inknote.app.ui.parseMarkdown(com.inknote.app.data.VaultManager.PAGING_SAMPLE)
        assertTrue(blocks1.size > 5)
        assertTrue(blocks2.size > 20)
    }
}
