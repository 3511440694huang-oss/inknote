package com.inknote.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * 仓库管理：内置仓库（应用私有外部目录，零权限）+ 用户选择的 SAF 文件夹。
 * 选择过的文件夹会持久化授权，重启后仍可直接打开。
 */
class VaultManager(private val context: Context, private val settings: SettingsStore) {

    private val seedPrefs = context.getSharedPreferences("inknote_seed", Context.MODE_PRIVATE)

    val internalRoot: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "vault").also { if (!it.exists()) it.mkdirs() }
    }

    @Volatile
    private var cached: Vault? = null

    @Volatile
    private var cachedKey: String? = null

    val vaultKey: String get() = settings.current.vaultUri ?: KEY_INTERNAL

    /** 当前仓库。外部仓库不可用（授权失效 / 目录被删）时自动回退内置仓库。 */
    fun current(): Vault {
        val key = vaultKey
        cached?.let { if (cachedKey == key) return it }

        val external = settings.current.vaultUri
        var result: Vault? = null
        if (external != null) {
            result = runCatching {
                val uri = Uri.parse(external)
                if (!hasPermission(uri)) return@runCatching null
                SafVault(context, uri, settings.current.vaultLabel.ifBlank { "外部仓库" })
                    .also { it.list(null) }
            }.getOrNull()
            if (result == null) {
                // 授权失效：清掉外部仓库配置，避免每次启动都失败
                settings.update { it.copy(vaultUri = null, vaultLabel = "内置仓库") }
            }
        }
        if (result == null) {
            val fv = FileVault(internalRoot, "内置仓库")
            seedIfNeeded(fv)
            result = fv
        }
        cached = result
        cachedKey = settings.current.vaultUri ?: KEY_INTERNAL
        return result!!
    }

    /** 用系统「打开文件夹」返回的 tree uri 打开仓库；成功返回仓库，失败返回 null */
    fun openTree(uri: Uri): Vault? {
        SafVault.takePermission(context, uri)
        val label = SafVault.treeLabel(context, uri)?.takeIf { it.isNotBlank() } ?: "外部仓库"
        val vault = SafVault(context, uri, label)
        val children = runCatching { vault.list(null) }.getOrElse { return null }
        @Suppress("UNUSED_VARIABLE") val ok = children
        settings.update { it.copy(vaultUri = uri.toString(), vaultLabel = label) }
        cached = vault
        cachedKey = uri.toString()
        return vault
    }

    fun useInternal() {
        settings.update { it.copy(vaultUri = null, vaultLabel = "内置仓库") }
        cached = null
        cachedKey = null
        seedIfNeeded(FileVault(internalRoot, "内置仓库"))
    }

    private fun hasPermission(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        } || context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    /** 首次启动写入两篇示例笔记（只在内置仓库、且从未写过时） */
    private fun seedIfNeeded(vault: FileVault) {
        if (seedPrefs.getBoolean("seeded", false)) return
        runCatching {
            vault.createNote(null, "欢迎使用墨笺", WELCOME)
            vault.createNote(null, "翻页与对齐示例", PAGING_SAMPLE)
            seedPrefs.edit().putBoolean("seeded", true).apply()
        }
    }

    companion object {
        const val KEY_INTERNAL = "internal"

        val WELCOME = """
            # 欢迎使用墨笺

            墨笺是一个**离线 Markdown 笔记软件**，专为墨水屏与 32 位老设备打磨：
            无动画、无网络依赖、触区大、字距密。

            ## 怎么用

            - 左上角「☰」打开侧栏：快速切换、最近打开、打开文件夹、设置
            - 右上角「＋」新建笔记或文件夹
            - 点开笔记后，右上角切换 **编辑 / 阅读**
            - 阅读时用**右侧两个小按钮**上下翻页，每次翻一页，并自动对齐到段落开头
            - 长按列表项或点行尾「⋯」可以重命名、删除

            ## 支持的写法

            - 标题：`#` 到 `######`
            - **加粗**、*斜体*、~~删除线~~、`行内代码`
            - 列表、任务清单、引用、代码块、表格
            - 双链：[[欢迎使用墨笺]]
            - 标签：#墨笺 #墨水屏

            > 墨水屏上，静止就是美。所有页面切换、翻页都是瞬时的，没有过渡动画。

            ---

            仓库默认在应用私有目录，也可以从侧栏「打开文件夹」选择任意目录，
            比如把整个 WORK 目录当成仓库来读你已有的 Markdown 笔记。
        """.trimIndent()

        val PAGING_SAMPLE = buildString {
            appendLine("# 翻页与对齐示例")
            appendLine()
            appendLine("这一篇用来测试墨水屏翻页：用右侧的「▲ / ▼」小按钮，每次整页移动，")
            appendLine("顶部总是一行/一段的开头，不会出现半行字被切掉的情况。")
            appendLine()
            for (i in 1..18) {
                appendLine("## 第 $i 节 · 静墨之美")
                appendLine()
                appendLine("墨水屏的显示方式决定了它不适合花哨的过渡。墨笺的所有交互都追求「瞬时」：")
                appendLine("翻页不做滚动动画，弹出层不做淡入淡出，按钮不做按压反馈。")
                appendLine("你按下按钮，屏幕就换到下一页，仅此而已。第 $i 节只是占位，用来把文章拉长。")
                appendLine()
                appendLine("- 要点 ${i}.1：每次翻页重叠 $i 行以内的内容，阅读更连贯。")
                appendLine("- 要点 ${i}.2：阅读模式下顶部会自动对齐到完整段落。")
                appendLine("- 要点 ${i}.3：可以在设置里调整字号、行距、页边距与重叠行数。")
                appendLine()
                if (i % 3 == 0) {
                    appendLine("> 静止不是缺少动效，而是把注意力还给文字。")
                    appendLine()
                }
            }
            appendLine("—— 全文完。如果翻到最后这里，恭喜你完成了第 18 节。")
        }
    }
}
