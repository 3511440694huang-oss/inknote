package com.inknote.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inknote.app.data.InkSettings
import com.inknote.app.data.SettingsStore
import com.inknote.app.data.Vault
import com.inknote.app.data.VaultEntry
import com.inknote.app.data.VaultManager
import com.inknote.app.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 面包屑一层 */
data class Crumb(val id: String?, val name: String)

/** 搜索请求（来自主页或笔记内双链/标签跳转） */
data class SearchQuery(val text: String, val fullText: Boolean, val autoRun: Boolean = true)

private data class InputSpec(
    val kind: Kind,
    val title: String,
    val label: String,
    val initial: String,
    val target: VaultEntry? = null,
) {
    enum class Kind { NewNote, NewFolder, Rename }
}

@Composable
fun HomeScreen(
    vaults: VaultManager,
    settingsStore: SettingsStore,
    settings: InkSettings,
    refreshToken: Int,
    pendingSearch: SearchQuery?,
    onPendingSearchConsumed: () -> Unit,
    onOpenNote: (Vault, VaultEntry) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vault = remember(settings.vaultUri) { vaults.current() }
    var crumbs by remember(vault) { mutableStateOf(emptyList<Crumb>()) }
    var entries by remember(vault) { mutableStateOf(emptyList<VaultEntry>()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var drawer by remember { mutableStateOf(false) }
    var searchRequest by remember { mutableStateOf<SearchQuery?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var newMenu by remember { mutableStateOf(false) }
    var inputSpec by remember { mutableStateOf<InputSpec?>(null) }
    var actionTarget by remember { mutableStateOf<VaultEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<VaultEntry?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val folderId = crumbs.lastOrNull()?.id

    // 文件列表：翻页按钮 + 内容左右预留（按钮位置跟随设置）
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val listSidePad = pageButtonSidePad(settings)
    val listStartPad = if (settings.pageButtons && settings.pageButtonsLeft) listSidePad else 0.dp
    val listEndPad = if (settings.pageButtons && !settings.pageButtonsLeft) listSidePad else 0.dp

    LaunchedEffect(vault, folderId, refreshToken, refresh, settings.sortByModified) {
        loading = true
        errorText = null
        val result = runCatching { withContext(Dispatchers.IO) { vault.list(folderId) } }
        if (result.isSuccess) {
            entries = sortEntries(result.getOrThrow(), settings.sortByModified)
        } else {
            entries = emptyList()
            errorText = "无法读取仓库。授权可能已失效，可以重新选择文件夹或在设置里恢复内置仓库。"
        }
        loading = false
    }

    // 切换文件夹：无动画回到列表顶部
    LaunchedEffect(folderId) {
        runCatching { listState.scrollToItem(0) }
    }

    // 外部（笔记页里的双链/标签）请求搜索
    LaunchedEffect(pendingSearch) {
        if (pendingSearch != null) {
            searchRequest = pendingSearch
            onPendingSearchConsumed()
        }
    }

    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val v = vaults.openTree(uri)
            if (v == null) toast = "无法打开这个文件夹（可能缺少读写权限）"
        }
    }

    fun reload() {
        refresh++
    }

    Box(Modifier.fillMaxSize().background(Ink.Paper)) {
        Column(Modifier.fillMaxSize()) {
            val atRoot = crumbs.isEmpty()
            InkTopBar(
                title = if (atRoot) vault.label else crumbs.last().name,
                subtitle = if (atRoot) "墨笺 · ${entries.size} 项" else null,
                onBack = {
                    if (atRoot) drawer = true else crumbs = crumbs.dropLast(1)
                },
                backIcon = if (atRoot) Icons.Filled.Menu else Icons.Filled.ArrowBack,
                actions = {
                    InkIconButton(Icons.Filled.Search, { searchRequest = SearchQuery("", false, false) }, "搜索")
                    InkIconButton(Icons.Filled.Add, { newMenu = true }, "新建")
                },
            )

            BreadcrumbBar(
                vaultLabel = vault.label,
                crumbs = crumbs,
                settings = settings,
                onRoot = { crumbs = emptyList() },
                onCrumb = { index -> crumbs = crumbs.take(index + 1) },
                onToggleSort = {
                    settingsStore.update { it.copy(sortByModified = !it.sortByModified) }
                },
            )

            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    loading -> CenterHint("读取中…")
                    errorText != null -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(errorText!!, fontSize = 14.sp, lineHeight = 22.sp, color = Ink.Deep)
                        Spacer(Modifier.height(16.dp))
                        InkOutlineButton("重新选择文件夹", onClick = { openTree.launch(null) })
                        Spacer(Modifier.height(10.dp))
                        InkOutlineButton("恢复内置仓库", onClick = {
                            vaults.useInternal()
                            crumbs = emptyList()
                            reload()
                        })
                    }

                    entries.isEmpty() -> EmptyHint(
                        if (folderId == null) "仓库还是空的" else "这个文件夹是空的",
                    )

                    else -> Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = listStartPad, end = listEndPad),
                        ) {
                            items(entries, key = { it.id }) { e ->
                                InkListItem(
                                    title = if (e.isMarkdown) e.title else e.name,
                                    subtitle = metaOf(e),
                                    leading = {
                                        if (e.isDir) FolderGlyph() else NoteGlyph()
                                    },
                                    trailing = {
                                        InkIconButton(Icons.Filled.MoreVert, { actionTarget = e }, "更多")
                                    },
                                    onClick = {
                                        when {
                                            e.isDir -> crumbs = crumbs + Crumb(e.id, e.name)
                                            e.isMarkdown -> onOpenNote(vault, e)
                                            else -> toast = "暂不支持打开这种文件：${e.name}"
                                        }
                                    },
                                    onLongClick = { actionTarget = e },
                                )
                                InkDivider()
                            }
                            item { Spacer(Modifier.height(40.dp)) }
                        }
                        // 整页翻页按钮：一屏一翻，对齐到整条（无动画）
                        PageButtonsOverlay(
                            settings = settings,
                            canUp = listState.canScrollBackward,
                            canDown = listState.canScrollForward,
                            onUp = { scope.launch { pageScrollLazy(listState, settings, density, false) } },
                            onDown = { scope.launch { pageScrollLazy(listState, settings, density, true) } },
                        )
                    }
                }
            }
        }

        // 侧栏
        if (drawer) {
            HomeDrawer(
                vault = vault,
                vaultKey = vaults.vaultKey,
                settings = settings,
                onDismiss = { drawer = false },
                onQuickSwitch = { drawer = false; searchRequest = SearchQuery("", false, false) },
                onOpenRecent = { recent ->
                    if (vault.exists(recent.id)) {
                        drawer = false
                        onOpenNote(vault, VaultEntry(recent.id, recent.name, false))
                    } else {
                        toast = "找不到这篇笔记（可能已被移动或删除）"
                    }
                },
                onOpenFolder = { drawer = false; openTree.launch(null) },
                onUseInternal = { drawer = false; vaults.useInternal() },
                onOpenSettings = { drawer = false; onOpenSettings() },
                onAbout = { drawer = false; showAbout = true },
            )
        }

        // 新建菜单
        if (newMenu) {
            InkActionsDialog(
                title = if (folderId == null) vault.label else crumbs.last().name,
                actions = listOf(
                    "新建笔记" to {
                        newMenu = false
                        inputSpec = InputSpec(InputSpec.Kind.NewNote, "新建笔记", "文件名（自动补 .md）", "未命名笔记")
                    },
                    "新建文件夹" to {
                        newMenu = false
                        inputSpec = InputSpec(InputSpec.Kind.NewFolder, "新建文件夹", "文件夹名称", "新建文件夹")
                    },
                ),
                onDismiss = { newMenu = false },
            )
        }

        // 输入对话框
        inputSpec?.let { spec ->
            InkInputDialog(
                title = spec.title,
                label = spec.label,
                initial = spec.initial,
                confirmText = if (spec.kind == InputSpec.Kind.Rename) "重命名" else "创建",
                onDismiss = { inputSpec = null },
                onConfirm = { value ->
                    val name = value.trim()
                    inputSpec = null
                    when (spec.kind) {
                        InputSpec.Kind.NewNote -> scope.launch {
                            val created = withContext(Dispatchers.IO) { vault.createNote(folderId, name, "") }
                            if (created != null) {
                                reload()
                                onOpenNote(vault, created)
                            } else toast = "创建失败"
                        }

                        InputSpec.Kind.NewFolder -> scope.launch {
                            val created = withContext(Dispatchers.IO) { vault.createDir(folderId, name) }
                            if (created != null) reload() else toast = "创建失败"
                        }

                        InputSpec.Kind.Rename -> scope.launch {
                            val target = spec.target ?: return@launch
                            val renamed = withContext(Dispatchers.IO) { vault.rename(target.id, name) }
                            if (renamed != null) reload() else toast = "重命名失败"
                        }
                    }
                },
            )
        }

        // 单项操作
        actionTarget?.let { target ->
            InkActionsDialog(
                title = if (target.isMarkdown) target.title else target.name,
                actions = listOf(
                    "重命名" to {
                        actionTarget = null
                        inputSpec = InputSpec(InputSpec.Kind.Rename, "重命名", "新名称", target.name, target)
                    },
                    "删除" to {
                        actionTarget = null
                        deleteTarget = target
                    },
                ),
                onDismiss = { actionTarget = null },
            )
        }
        deleteTarget?.let { target ->
            InkConfirmDialog(
                title = "删除",
                message = "确定删除「${if (target.isMarkdown) target.title else target.name}」吗？删除后不可恢复。",
                confirmText = "删除",
                onDismiss = { deleteTarget = null },
                onConfirm = {
                    deleteTarget = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { vault.delete(target.id) }
                        if (ok) reload() else toast = "删除失败"
                    }
                },
            )
        }

        // 搜索页
        searchRequest?.let { req ->
            SearchScreen(
                vault = vault,
                initial = req.text,
                initialFullText = req.fullText,
                onDismiss = { searchRequest = null },
                onOpen = { entry ->
                    searchRequest = null
                    onOpenNote(vault, entry)
                },
            )
        }

        if (showAbout) {
            AboutDialog(onDismiss = { showAbout = false })
        }

        toast?.let { msg ->
            InkConfirmDialog(
                title = "提示",
                message = msg,
                confirmText = "好",
                onDismiss = { toast = null },
                onConfirm = { toast = null },
            )
        }
    }
}

@Composable
private fun BreadcrumbBar(
    vaultLabel: String,
    crumbs: List<Crumb>,
    settings: InkSettings,
    onRoot: () -> Unit,
    onCrumb: (Int) -> Unit,
    onToggleSort: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(36.dp).padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                vaultLabel,
                fontSize = 12.sp,
                color = Ink.Mid,
                modifier = Modifier.inkClickable(onClick = onRoot).padding(vertical = 6.dp),
                maxLines = 1,
            )
            crumbs.forEachIndexed { index, crumb ->
                Text(" / ", fontSize = 12.sp, color = Ink.Line)
                Text(
                    crumb.name,
                    fontSize = 12.sp,
                    color = Ink.Ink,
                    maxLines = 1,
                    modifier = Modifier.inkClickable { onCrumb(index) }.padding(vertical = 6.dp),
                )
            }
        }
        Text(
            if (settings.sortByModified) "按时间" else "按名称",
            fontSize = 12.sp,
            color = Ink.Mid,
            modifier = Modifier.inkClickable(onClick = onToggleSort).padding(horizontal = 6.dp, vertical = 6.dp),
        )
    }
    InkDivider()
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 14.sp, color = Ink.Mid)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, fontSize = 15.sp, color = Ink.Mid)
        Spacer(Modifier.height(6.dp))
        Text("点右上角「＋」新建笔记", fontSize = 12.sp, color = Ink.Mid)
    }
}

@Composable
private fun HomeDrawer(
    vault: Vault,
    vaultKey: String,
    settings: InkSettings,
    onDismiss: () -> Unit,
    onQuickSwitch: () -> Unit,
    onOpenRecent: (com.inknote.app.data.RecentNote) -> Unit,
    onOpenFolder: () -> Unit,
    onUseInternal: () -> Unit,
    onOpenSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val recent = remember(settings.recent, vaultKey) { settings.recent.filter { it.vaultKey == vaultKey } }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    Row(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .width(296.dp)
                .fillMaxHeight()
                .background(Ink.Paper),
        ) {
            // 侧栏视口高度（px），整页翻动时扣掉重叠行
            val viewportPx = with(density) { maxHeight.roundToPx() }
            Column(
                Modifier
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(end = 32.dp),
            ) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 14.dp)) {
                    Text("墨笺", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink.Ink)
                    Text("离线 Markdown · 墨水屏零动画", fontSize = 11.sp, color = Ink.Mid)
                    Spacer(Modifier.height(10.dp))
                    Text(vault.label, fontSize = 13.sp, color = Ink.Deep, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(vault.hint, fontSize = 10.sp, color = Ink.Mid, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                InkDivider()
                DrawerRow("快速切换", "在所有笔记之间跳转") { onQuickSwitch() }
                InkDivider()
                InkSectionLabel("最近打开")
                if (recent.isEmpty()) {
                    Text("还没有打开的笔记", fontSize = 12.sp, color = Ink.Mid, modifier = Modifier.padding(start = 18.dp, bottom = 10.dp))
                } else {
                    recent.take(8).forEach { r ->
                        Box(
                            Modifier.fillMaxWidth().height(42.dp).inkClickable { onOpenRecent(r) }.padding(horizontal = 18.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(r.name, fontSize = 15.sp, color = Ink.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                InkDivider()
                DrawerRow("打开文件夹…", "把任意目录当作仓库（SAF）") { onOpenFolder() }
                DrawerRow("恢复内置仓库", "应用私有目录，零权限") { onUseInternal() }
                InkDivider()
                DrawerRow("设置", "字号 · 行距 · 翻页 · 主题") { onOpenSettings() }
                DrawerRow("关于墨笺", "版本与说明") { onAbout() }
                Spacer(Modifier.height(20.dp))
            }
            // 侧栏整页翻页按钮（贴右边缘；内容已让出 32dp，不会压字）
            // 内容不满一屏时（maxValue == 0）不显示，避免两个灰按钮白占位置
            if (settings.pageButtons && scrollState.maxValue > 0) {
                PageButtonPair(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                    canUp = scrollState.value > 0,
                    canDown = scrollState.value < scrollState.maxValue,
                    onUp = { scope.launch { pageScrollScrollable(scrollState, viewportPx, settings, density, false) } },
                    onDown = { scope.launch { pageScrollScrollable(scrollState, viewportPx, settings, density, true) } },
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0x40000000))
                .inkClickable { onDismiss() },
        )
    }
}

@Composable
private fun DrawerRow(title: String, subtitle: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).inkClickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(title, fontSize = 16.sp, color = Ink.Ink)
            Text(subtitle, fontSize = 11.sp, color = Ink.Mid)
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    InkOverlay(visible = true, onDismiss = onDismiss) {
        Column {
            Text("墨笺 InkNote", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink.Ink)
            Spacer(Modifier.height(4.dp))
            Text("版本 1.2.0 · 离线 · 零动画", fontSize = 12.sp, color = Ink.Mid)
            Spacer(Modifier.height(14.dp))
            InkDialogText(
                "一个类 Obsidian 的本地 Markdown 笔记软件。\n\n" +
                    "· 纯 Kotlin 实现，无任何原生库：32 位 / 64 位 Android 6.0+ 都能装\n" +
                    "· 墨水屏版：无转场、无涟漪、无滚动惯性，翻页瞬时完成\n" +
                    "· 全界面翻页按钮（首页/搜索/设置/笔记），自动对齐到整行/整段\n" +
                    "· 支持 Markdown 超链接、双链、目录锚点与 LaTeX 公式\n" +
                    "· 双链、标签、表格、任务清单、代码块、反向链接\n" +
                    "· 仓库可用应用内置目录，也可用 SAF 打开任意文件夹\n" +
                    "· 数据完全在本地，不联网、不上传"
            )
            InkDialogButtons("好" to onDismiss)
        }
    }
}

private fun sortEntries(list: List<VaultEntry>, byModified: Boolean): List<VaultEntry> {
    val dirs = list.filter { it.isDir }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    val files = if (byModified) {
        list.filter { !it.isDir }.sortedByDescending { it.lastModified }
    } else {
        list.filter { !it.isDir }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }
    return dirs + files
}

private fun metaOf(e: VaultEntry): String {
    if (e.isDir) return "文件夹"
    val parts = ArrayList<String>(3)
    if (e.lastModified > 0) {
        parts.add(SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(e.lastModified)))
    }
    if (e.size > 0) {
        parts.add(
            when {
                e.size < 1024 -> "${e.size} B"
                e.size < 1024 * 1024 -> "${e.size / 1024} KB"
                else -> String.format(Locale.US, "%.1f MB", e.size / 1024.0 / 1024.0)
            }
        )
    }
    if (e.isMarkdown) parts.add("Markdown")
    return parts.joinToString(" · ")
}
