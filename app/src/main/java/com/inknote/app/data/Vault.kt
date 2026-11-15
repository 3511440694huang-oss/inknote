package com.inknote.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File

/** 仓库里的一项（文件 / 文件夹）。id 对内置仓库是相对路径，对 SAF 仓库是 DocumentId。 */
data class VaultEntry(
    val id: String,
    val name: String,
    val isDir: Boolean,
    val lastModified: Long = 0L,
    val size: Long = 0L,
) {
    val isMarkdown: Boolean
        get() = !isDir && (name.endsWith(".md", true) || name.endsWith(".markdown", true) || name.endsWith(".txt", true))

    /** 去掉 .md 的显示标题 */
    val title: String
        get() = name.removeSuffix(".md").removeSuffix(".MD").removeSuffix(".markdown").removeSuffix(".txt")
}

/**
 * 一个 Markdown 仓库的抽象。两种实现：
 * - [FileVault]：应用内置目录（零权限，任何 Android 6.0+ 都能用）；
 * - [SafVault]：用户通过系统「打开文件夹」选择的任意目录（SAF 持久授权，兼容 Android 6~16）。
 */
interface Vault {
    val label: String

    /** 给人看的路径提示（内置仓库 = 绝对路径；SAF = 树 URI） */
    val hint: String

    fun list(parentId: String?): List<VaultEntry>
    fun read(id: String): String?
    fun write(id: String, text: String): Boolean
    fun createNote(parentId: String?, name: String, content: String): VaultEntry?
    fun createDir(parentId: String?, name: String): VaultEntry?
    fun rename(id: String, newName: String): VaultEntry?
    fun delete(id: String): Boolean
    fun exists(id: String): Boolean

    /** 递归列出全部可编辑的 Markdown / 文本文件（供搜索、快速切换、反向链接） */
    fun walkMarkdown(limit: Int = 3000): List<VaultEntry> {
        val out = ArrayList<VaultEntry>()
        fun walk(parent: String?, depth: Int) {
            if (out.size >= limit || depth > 20) return
            val children = runCatching { list(parent) }.getOrNull() ?: return
            for (c in children) {
                if (out.size >= limit) return
                if (c.isDir) walk(c.id, depth + 1) else if (c.isMarkdown) out.add(c)
            }
        }
        walk(null, 0)
        return out
    }

    /** 按文件（去扩展名）名查找笔记，wiki 链接 [[名称]] 使用 */
    fun findNoteByName(name: String): VaultEntry? {
        val target = name.trim().removeSuffix(".md")
        return walkMarkdown().firstOrNull { it.title.equals(target, ignoreCase = true) }
    }

    /** 给人看的相对路径（搜索/最近列表副标题用） */
    fun pathOf(entry: VaultEntry): String = entry.name

    /** 同名去重：返回一个不会覆盖已有文件的名称 */
    fun uniqueName(parentId: String?, desired: String, isDir: Boolean): String {
        val siblings = runCatching { list(parentId) }.getOrDefault(emptyList())
        val existing = siblings.map { it.name.lowercase() }.toHashSet()
        var base = sanitizeName(desired)
        if (base.isBlank()) base = if (isDir) "新建文件夹" else "未命名笔记"
        if (!isDir && !base.endsWith(".md", true)) base += ".md"
        if (!existing.contains(base.lowercase())) return base
        val stem = if (isDir) base else base.removeSuffix(".md")
        var i = 2
        while (true) {
            val candidate = if (isDir) "$stem $i" else "$stem $i.md"
            if (!existing.contains(candidate.lowercase())) return candidate
            i++
        }
    }

    companion object {
        val ILLEGAL = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

        fun sanitizeName(raw: String): String = raw.trim().map { c ->
            if (c in ILLEGAL || c.code < 0x20) ' ' else c
        }.joinToString("").trim().trim('.').trim()
    }
}

/** 应用内置目录里的仓库（java.io.File）。 */
class FileVault(private val root: File, override val label: String = "内置仓库") : Vault {

    override val hint: String get() = root.absolutePath

    init {
        if (!root.exists()) root.mkdirs()
    }

    private fun fileOf(id: String?): File = if (id.isNullOrBlank()) root else File(root, id)

    private fun rel(file: File): String = file.relativeTo(root).path.replace(File.separatorChar, '/')

    override fun list(parentId: String?): List<VaultEntry> {
        val dir = fileOf(parentId)
        if (!dir.isDirectory) return emptyList()
        return (dir.listFiles() ?: emptyArray())
            .asSequence()
            .filter { !it.name.startsWith(".") }
            .map { f ->
                VaultEntry(
                    id = rel(f),
                    name = f.name,
                    isDir = f.isDirectory,
                    lastModified = f.lastModified(),
                    size = if (f.isFile) f.length() else 0L,
                )
            }
            .toList()
    }

    override fun read(id: String): String? = runCatching {
        val f = fileOf(id)
        if (!f.isFile) return null
        val text = f.readText(Charsets.UTF_8)
        if (text.startsWith("\uFEFF")) text.substring(1) else text
    }.getOrNull()

    override fun write(id: String, text: String): Boolean = runCatching {
        val f = fileOf(id)
        f.parentFile?.mkdirs()
        f.writeText(text, Charsets.UTF_8)
        true
    }.getOrDefault(false)

    override fun createNote(parentId: String?, name: String, content: String): VaultEntry? {
        val fileName = uniqueName(parentId, name, isDir = false)
        val f = File(fileOf(parentId), fileName)
        return runCatching {
            f.parentFile?.mkdirs()
            f.writeText(content, Charsets.UTF_8)
            VaultEntry(rel(f), f.name, false, f.lastModified(), f.length())
        }.getOrNull()
    }

    override fun createDir(parentId: String?, name: String): VaultEntry? {
        val dirName = uniqueName(parentId, name, isDir = true)
        val f = File(fileOf(parentId), dirName)
        return runCatching {
            if (!f.mkdirs()) return null
            VaultEntry(rel(f), f.name, true, f.lastModified(), 0)
        }.getOrNull()
    }

    override fun rename(id: String, newName: String): VaultEntry? {
        val src = fileOf(id)
        if (!src.exists()) return null
        val clean = Vault.sanitizeName(newName).ifBlank { return null }
        val finalName = if (src.isFile && src.name.substringAfterLast('.', "") in setOf("md", "markdown", "txt") &&
            clean.substringAfterLast('.', "").isBlank()
        ) "$clean.md" else clean
        var target = File(src.parentFile, finalName)
        if (target.exists() && target.absolutePath != src.absolutePath) {
            target = File(src.parentFile, uniqueName(parentIdOf(src), finalName, src.isDirectory))
        }
        val ok = src.renameTo(target)
        if (!ok) return null
        return VaultEntry(rel(target), target.name, target.isDirectory, target.lastModified(), target.length())
    }

    private fun parentIdOf(f: File): String? {
        val p = f.parentFile ?: return null
        return if (p == root) null else rel(p)
    }

    override fun delete(id: String): Boolean = runCatching {
        val f = fileOf(id)
        if (f.isDirectory) f.deleteRecursively() else f.delete()
    }.getOrDefault(false)

    override fun exists(id: String): Boolean = fileOf(id).exists()

    override fun pathOf(entry: VaultEntry): String = entry.id
}

/**
 * SAF 目录仓库。通过系统「打开文件夹」授权，兼容 Android 6.0~16，
 * 不需要任何存储权限，32 位老设备同样可用。
 */
class SafVault(
    context: Context,
    private val treeUri: Uri,
    override val label: String,
) : Vault {

    private val resolver = context.contentResolver
    private val rootDocId: String = DocumentsContract.getTreeDocumentId(treeUri)

    override val hint: String get() = treeUri.toString()

    private fun childUri(parentDocId: String?): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId ?: rootDocId)

    private fun docUri(docId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    private fun parentDocUri(parentId: String?): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId ?: rootDocId)

    override fun list(parentId: String?): List<VaultEntry> {
        val out = ArrayList<VaultEntry>()
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
        )
        resolver.query(childUri(parentId), projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                val mime = c.getString(2)
                val isDir = mime == Document.MIME_TYPE_DIR
                val mimeOk = isDir || mime == null || mime.startsWith("text") ||
                    mime == "application/octet-stream" || mime == "application/markdown"
                if (!mimeOk && !name.substringAfterLast('.', "").lowercase().let {
                        it in setOf("md", "markdown", "txt", "json", "csv", "yaml", "yml")
                    }
                ) continue
                out.add(VaultEntry(id, name, isDir, c.getLong(3), c.getLong(4)))
            }
        }
        return out
    }

    override fun read(id: String): String? = runCatching {
        resolver.openInputStream(docUri(id))?.use { input ->
            val text = input.bufferedReader(Charsets.UTF_8).readText()
            if (text.startsWith("\uFEFF")) text.substring(1) else text
        }
    }.getOrNull()

    override fun write(id: String, text: String): Boolean = runCatching {
        val uri = docUri(id)
        val stream = resolver.openOutputStream(uri, "wt") ?: resolver.openOutputStream(uri, "rwt") ?: return false
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        true
    }.getOrDefault(false)

    override fun createNote(parentId: String?, name: String, content: String): VaultEntry? = runCatching {
        val fileName = uniqueName(parentId, name, isDir = false)
        val uri = DocumentsContract.createDocument(resolver, parentDocUri(parentId), "text/markdown", fileName)
            ?: return null
        val docId = DocumentsContract.getDocumentId(uri)
        write(docId, content)
        VaultEntry(docId, fileName, false, System.currentTimeMillis(), content.toByteArray(Charsets.UTF_8).size.toLong())
    }.getOrNull()

    override fun createDir(parentId: String?, name: String): VaultEntry? = runCatching {
        val dirName = uniqueName(parentId, name, isDir = true)
        val uri = DocumentsContract.createDocument(resolver, parentDocUri(parentId), Document.MIME_TYPE_DIR, dirName)
            ?: return null
        val docId = DocumentsContract.getDocumentId(uri)
        VaultEntry(docId, dirName, true, System.currentTimeMillis(), 0)
    }.getOrNull()

    override fun rename(id: String, newName: String): VaultEntry? = runCatching {
        var clean = Vault.sanitizeName(newName).ifBlank { return null }
        if (clean.substringAfterLast('.', "").isBlank() && clean.isNotBlank()) clean += ".md"
        val uri = DocumentsContract.renameDocument(resolver, docUri(id), clean) ?: return null
        val docId = DocumentsContract.getDocumentId(uri)
        var display = clean
        var isDir = false
        resolver.query(
            docUri(docId),
            arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                display = c.getString(0) ?: clean
                isDir = c.getString(1) == Document.MIME_TYPE_DIR
            }
        }
        VaultEntry(docId, display, isDir, System.currentTimeMillis(), 0)
    }.getOrNull()

    override fun exists(id: String): Boolean = runCatching {
        resolver.query(docUri(id), arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true
    }.getOrDefault(false)

    override fun delete(id: String): Boolean = runCatching {
        DocumentsContract.deleteDocument(resolver, docUri(id))
    }.getOrDefault(false)

    override fun pathOf(entry: VaultEntry): String {
        val raw = entry.id.substringAfter(':')
        return raw.ifBlank { entry.name }
    }

    companion object {
        /** 取树根显示名，失败时用 URI 末段 */
        fun treeLabel(context: Context, uri: Uri): String? = runCatching {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
            context.contentResolver.query(docUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

        fun takePermission(context: Context, uri: Uri): Boolean = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)
    }
}
