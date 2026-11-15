package com.inknote.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.inknote.app.data.InkSettings
import com.inknote.app.data.SettingsStore
import com.inknote.app.data.Vault
import com.inknote.app.data.VaultEntry
import com.inknote.app.ui.theme.Ink
import com.inknote.app.ui.theme.InkPalette
import com.inknote.app.ui.theme.LocalInkPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MODE_EDIT = 0
private const val MODE_READ = 1

/**
 * 笔记页：编辑 / 阅读两种模式，全程零动画。
 * 翻页按钮：全局通用的细按钮（ui/Paging.kt），上下各一个。
 * - 编辑模式：按行网格对齐（不会被切半行）
 * - 阅读模式：按段落边界对齐（顶部总是完整段落开头）
 *
 * 1.1.0 修复：
 * - 「阅读 / 编辑」改为同一个瞬时状态切换（先切状态、再后台存盘），
 *   不会因为 IO 或滚动协程卡住而看起来「点了没反应」；
 * - 超链接：站内相对链接、锚点 `#小节`、双链 `[[笔记#小节]]` 均可用；
 *   找不到的笔记可以直接新建；
 * - 顶栏切换按钮改成有边框的实体小按钮，墨水屏上「能点」这件事看得见。
 */
@Composable
fun NoteScreen(
    vault: Vault,
    entry: VaultEntry,
    settingsStore: SettingsStore,
    settings: InkSettings,
    onBack: () -> Unit,
    onOpenNote: (VaultEntry) -> Unit,
    onOpenNoteAt: (VaultEntry, String?) -> Unit = { e, _ -> onOpenNote(e) },
    onRenamed: (VaultEntry) -> Unit,
    onDeleted: () -> Unit,
    onSearch: (SearchQuery) -> Unit,
    initialAnchor: String? = null,
    onAnchorConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val palette = LocalInkPalette.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var current by remember(entry.id) { mutableStateOf(entry) }
    var content by remember(entry.id) { mutableStateOf("") }
    var loaded by remember(entry.id) { mutableStateOf(false) }
    var mode by remember(entry.id) {
        mutableIntStateOf(if (settings.startInReadMode) MODE_READ else MODE_EDIT)
    }
    var blocks by remember(entry.id) { mutableStateOf(emptyList<MdBlock>()) }
    var dirty by remember(entry.id) { mutableStateOf(false) }
    var reloadKey by remember(entry.id) { mutableIntStateOf(0) }
    var progress by remember(entry.id) { mutableIntStateOf(0) }

    // 编辑模式的滚动位置（读→编辑来回切换时保持原位）
    var editorScroll by remember(entry.id) { mutableIntStateOf(0) }
    var editorMax by remember(entry.id) { mutableIntStateOf(0) }

    var showMore by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showLinks by remember { mutableStateOf(false) }
    var links by remember { mutableStateOf(emptyList<VaultEntry>()) }
    var linksLoading by remember { mutableStateOf(false) }
    var missingLink by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val preview = androidx.compose.foundation.lazy.rememberLazyListState()
    val editorRef = remember { mutableStateOf<EditText?>(null) }

    fun snapshot(): String = editorRef.value?.text?.toString() ?: content

    suspend fun persist() {
        val text = snapshot()
        content = text
        dirty = false
        withContext(Dispatchers.IO) { runCatching { vault.write(current.id, text) } }
    }

    fun saveAndBack() {
        scope.launch {
            if (dirty) persist()
            onBack()
        }
    }

    BackHandler { saveAndBack() }

    // 退到后台时静默保存（避免系统杀进程丢内容）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && dirty) {
                val text = editorRef.value?.text?.toString()
                if (text != null) {
                    content = text
                    dirty = false
                    runCatching { vault.write(current.id, text) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 载入正文；顺带解析成渲染块（很小，同步做，保证切阅读模式永远有内容）
    LaunchedEffect(current.id, reloadKey) {
        loaded = false
        val text = withContext(Dispatchers.IO) { vault.read(current.id) }.orEmpty()
        content = text
        blocks = parseMarkdown(text)
        dirty = false
        editorScroll = 0
        editorMax = 0
        if (!initialAnchor.isNullOrBlank()) mode = MODE_READ
        loaded = true
    }

    // 阅读进度（按首个可见块估算）
    LaunchedEffect(mode, blocks.size) {
        if (mode == MODE_READ && blocks.isNotEmpty()) {
            progress = 0
            snapshotFlow { preview.firstVisibleItemIndex }.collect {
                progress = previewProgress(preview, blocks.size)
            }
        }
    }

    // 进入阅读模式后回到开头（不阻塞模式切换本身；有锚点时交给下面的锚点跳转）
    LaunchedEffect(mode, loaded) {
        if (mode == MODE_READ && loaded && initialAnchor.isNullOrBlank()) {
            runCatching { preview.scrollToItem(0) }
        }
        if (mode == MODE_EDIT && loaded) {
            val et = editorRef.value
            if (et != null) et.post { et.scrollTo(0, editorScroll) }
        }
    }

    // 来自外部（另一篇笔记里的 [[笔记#小节]]）的锚点跳转
    LaunchedEffect(loaded, mode, blocks.size, initialAnchor) {
        val anchor = initialAnchor
        if (loaded && !anchor.isNullOrBlank()) {
            if (mode != MODE_READ) mode = MODE_READ
            val idx = headingIndex(blocks, anchor)
            if (idx >= 0) {
                runCatching { preview.scrollToItem(idx) }
            } else {
                message = "没有找到小节「$anchor」，已打开笔记开头"
            }
            onAnchorConsumed()
        }
    }

    /* ----------------------- 模式切换 ----------------------- */

    fun captureText(): String = editorRef.value?.text?.toString() ?: content

    /** 编辑 -> 阅读：先就地解析并切状态（瞬时可见），存盘放后台。 */
    fun switchToReadMode() {
        val text = captureText()
        content = text
        blocks = parseMarkdown(text)
        dirty = false
        progress = 0
        mode = MODE_READ
        keyboard?.hide()
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { vault.write(current.id, text) } }
                .onFailure { message = "保存失败：${it.message ?: "写入被拒绝"}" }
        }
    }

    /** 阅读 -> 编辑：直接切状态（编辑框由 AndroidView 重新创建）。 */
    fun switchToEditMode() {
        mode = MODE_EDIT
    }

    fun toggleMode() {
        if (mode == MODE_READ) switchToEditMode() else switchToReadMode()
    }

    /* ----------------------- 链接与锚点 ----------------------- */

    fun jumpToHeading(anchor: String) {
        val idx = headingIndex(blocks, anchor)
        if (idx < 0) {
            message = "没有找到小节「$anchor」"
            return
        }
        scope.launch { runCatching { preview.scrollToItem(idx) } }
    }

    fun openExternal(url: String) {
        val safe = runCatching { Uri.decode(url) }.getOrDefault(url)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safe)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { message = "没有能打开这个链接的应用：$safe" }
    }

    suspend fun openNoteLink(name: String, anchor: String?) {
        if (dirty) persist()
        val target = withContext(Dispatchers.IO) { vault.findNoteByName(name) }
        when {
            target == null -> missingLink = name
            target.id == current.id -> if (!anchor.isNullOrBlank()) jumpToHeading(anchor)
            else -> onOpenNoteAt(target, anchor)
        }
    }

    fun handleLink(raw: String) {
        when {
            raw.startsWith("wiki:") -> {
                val body = raw.removePrefix("wiki:").trim()
                val title = body.substringBefore('#').trim()
                val anchor = body.substringAfter('#', "").trim().ifBlank { null }
                if (title.isEmpty()) {
                    if (anchor != null) jumpToHeading(anchor)
                } else {
                    scope.launch { openNoteLink(title, anchor) }
                }
            }

            raw.startsWith("tag:") -> {
                val tag = raw.removePrefix("tag:").trim()
                if (tag.isNotEmpty()) onSearch(SearchQuery("#$tag", true))
            }

            raw.startsWith("url:") -> {
                val href = raw.removePrefix("url:").trim()
                if (href.isEmpty()) return
                when {
                    // 笔记内锚点：[文字](#小节)
                    href.startsWith("#") -> jumpToHeading(href.removePrefix("#"))
                    href.startsWith("http://") || href.startsWith("https://") ||
                        href.startsWith("mailto:") || href.startsWith("tel:") -> openExternal(href)
                    else -> {
                        // 站内相对链接：[文字](笔记.md) / ../目录/笔记.md
                        val pathPart = href.substringBefore('#')
                        val anchor = href.substringAfter('#', "").trim().ifBlank { null }
                        val name = pathPart.substringAfterLast('/')
                            .removeSuffix(".md")
                            .removeSuffix(".markdown")
                            .removeSuffix(".txt")
                        if (name.isNotBlank()) scope.launch { openNoteLink(name, anchor) }
                    }
                }
            }
        }
    }

    fun computeBacklinks() {
        showLinks = true
        linksLoading = true
        links = emptyList()
        scope.launch {
            val base = current.title
            val found = withContext(Dispatchers.IO) {
                val pattern = Regex("\\[\\[\\s*" + Regex.escape(base) + "\\s*(][|#])")
                vault.walkMarkdown(1500).filter { it.id != current.id }.mapNotNull { e ->
                    val text = runCatching { vault.read(e.id) }.getOrNull() ?: return@mapNotNull null
                    if (pattern.containsMatchIn(text)) e else null
                }
            }
            links = found
            linksLoading = false
        }
    }

    /* ----------------------- 翻页 ----------------------- */

    fun pageStep(forward: Boolean) {
        if (mode == MODE_EDIT) {
            val et = editorRef.value ?: return
            val result = pageScrollEditor(et, settings, forward) ?: return
            editorScroll = result.y
            editorMax = result.max
        } else {
            scope.launch {
                pageScrollLazy(preview, settings, density, forward)
                progress = previewProgress(preview, blocks.size)
            }
        }
    }

    /* ----------------------- 界面 ----------------------- */

    Box(Modifier.fillMaxSize().background(Ink.Paper)) {
        Column(Modifier.fillMaxSize()) {
            InkTopBar(
                title = current.title,
                subtitle = buildString {
                    append(if (mode == MODE_READ) "阅读" else "编辑")
                    if (mode == MODE_READ) append(" · $progress%")
                    if (dirty) append(" · 未保存")
                },
                onBack = { saveAndBack() },
                actions = {
                    ModeButton(reading = mode == MODE_READ, onClick = { toggleMode() })
                    InkIconButton(Icons.Filled.MoreVert, { showMore = true }, "更多")
                },
            )

            if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("读取中…", fontSize = 14.sp, color = Ink.Mid)
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    val sidePad = pageButtonSidePad(settings)
                    val startPad = if (settings.pageButtons && settings.pageButtonsLeft) sidePad else 0.dp
                    val endPad = if (settings.pageButtons && !settings.pageButtonsLeft) sidePad else 0.dp

                    Box(Modifier.fillMaxSize().padding(start = startPad, end = endPad)) {
                        if (mode == MODE_EDIT) {
                            key(reloadKey) {
                                AndroidView(
                                    factory = { ctx ->
                                        EditText(ctx).apply {
                                            setPadding(0, 0, 0, 0)
                                            setText(content)
                                            applyEditorStyle(this, settings, palette, density.density)
                                            addTextChangedListener(object : android.text.TextWatcher {
                                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                                                override fun afterTextChanged(s: android.text.Editable?) {
                                                    if (!dirty) dirty = true
                                                }
                                            })
                                            val restore = editorScroll
                                            post {
                                                editorMax = maxEditorScroll(this)
                                                if (restore > 0) scrollTo(0, restore)
                                            }
                                            editorRef.value = this
                                        }
                                    },
                                    update = { },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else {
                            if (blocks.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("还没有内容", fontSize = 15.sp, color = Ink.Mid)
                                        Spacer(Modifier.height(6.dp))
                                        Text("点右上角「编辑」开始写", fontSize = 12.sp, color = Ink.Mid)
                                    }
                                }
                            } else {
                                MarkdownView(
                                    blocks = blocks,
                                    state = preview,
                                    onLink = { handleLink(it) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }

                    // 全局通用翻页按钮（26×44dp 细边框，长按连续翻页）
                    PageButtonsOverlay(
                        settings = settings,
                        canUp = if (mode == MODE_EDIT) editorScroll > 0 else preview.canScrollBackward,
                        canDown = if (mode == MODE_EDIT) editorScroll < editorMax else preview.canScrollForward,
                        onUp = { pageStep(false) },
                        onDown = { pageStep(true) },
                    )
                }
            }
        }

        if (showMore) {
            NotesActionsDialog(
                isReadMode = mode == MODE_READ,
                onDismiss = { showMore = false },
                onToggleMode = {
                    showMore = false
                    toggleMode()
                },
                onRename = { showMore = false; showRename = true },
                onLinks = { showMore = false; computeBacklinks() },
                onCopy = {
                    showMore = false
                    val text = if (mode == MODE_EDIT) snapshot() else content
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText(current.title, text))
                    message = "全文已复制到剪贴板"
                },
                onReload = {
                    showMore = false
                    reloadKey++
                    dirty = false
                    message = "已从磁盘重新载入（未保存的修改已丢弃）"
                },
                onDelete = { showMore = false; showDelete = true },
            )
        }

        if (showRename) {
            InkInputDialog(
                title = "重命名",
                label = "新文件名（自动补 .md）",
                initial = current.name,
                confirmText = "重命名",
                onDismiss = { showRename = false },
                onConfirm = { newName ->
                    showRename = false
                    scope.launch {
                        if (dirty) persist()
                        val renamed = withContext(Dispatchers.IO) { vault.rename(current.id, newName) }
                        if (renamed != null) {
                            current = renamed
                            onRenamed(renamed)
                        } else {
                            message = "重命名失败（可能重名或没有权限）"
                        }
                    }
                },
            )
        }

        if (showDelete) {
            InkConfirmDialog(
                title = "删除笔记",
                message = "确定删除「${current.title}」吗？删除后不可恢复。",
                confirmText = "删除",
                onDismiss = { showDelete = false },
                onConfirm = {
                    showDelete = false
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { vault.delete(current.id) }
                        if (ok) onDeleted() else message = "删除失败"
                    }
                },
            )
        }

        if (showLinks) {
            InkOverlay(visible = true, onDismiss = { showLinks = false }) {
                Column(Modifier.fillMaxWidth()) {
                    Text("反向链接", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink.Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "提到「${current.title}」的笔记",
                        fontSize = 11.sp,
                        color = Ink.Mid,
                    )
                    Spacer(Modifier.height(10.dp))
                    when {
                        linksLoading -> Text("扫描仓库中…", fontSize = 13.sp, color = Ink.Mid)
                        links.isEmpty() -> Text("暂时没有其他笔记链接到这里", fontSize = 13.sp, color = Ink.Mid)
                        else -> {
                            Column(Modifier.fillMaxWidth().height(360.dp).verticalScroll(rememberScrollState())) {
                                links.take(30).forEach { e ->
                                    InkListItem(
                                        title = e.title,
                                        subtitle = vault.pathOf(e),
                                        leading = { NoteGlyph() },
                                        height = 52.dp,
                                        onClick = {
                                            showLinks = false
                                            onOpenNote(e)
                                        },
                                    )
                                    InkDivider()
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        InkOutlineButton("关闭", onClick = { showLinks = false })
                    }
                }
            }
        }

        // 链接指向不存在的笔记：可以搜索，也可以就地新建
        missingLink?.let { title ->
            InkOverlay(visible = true, onDismiss = { missingLink = null }) {
                Column {
                    Text("没有找到笔记「$title」", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink.Ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "可以新建这篇笔记（创建后自动打开），也可以去仓库里搜索同名内容。",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Ink.Deep,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        InkOutlineButton("取消", onClick = { missingLink = null })
                        InkOutlineButton("去搜索", onClick = {
                            val q = title
                            missingLink = null
                            onSearch(SearchQuery(q, false))
                        })
                        InkOutlineButton("新建", onClick = {
                            missingLink = null
                            scope.launch {
                                val parent = current.id.substringBeforeLast('/', "")
                                    .takeIf { it.isNotBlank() }
                                val created = withContext(Dispatchers.IO) { vault.createNote(parent, title, "") }
                                if (created != null) {
                                    content = ""
                                    onOpenNote(created)
                                } else {
                                    message = "新建笔记失败（可能重名或没有权限）"
                                }
                            }
                        })
                    }
                }
            }
        }

        message?.let { msg ->
            InkConfirmDialog(
                title = "提示",
                message = msg,
                confirmText = "好",
                onDismiss = { message = null },
                onConfirm = { message = null },
            )
        }
    }
}

/** 顶栏模式切换按钮：有边框的实体小按钮（墨水屏上没有涟漪，形状就是唯一反馈）。 */
@Composable
private fun ModeButton(reading: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .height(40.dp)
            .border(1.dp, Ink.Ink)
            .background(Ink.Paper)
            .inkClickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (reading) "编辑" else "阅读",
            fontSize = 14.sp,
            color = Ink.Ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun NotesActionsDialog(
    isReadMode: Boolean,
    onDismiss: () -> Unit,
    onToggleMode: () -> Unit,
    onRename: () -> Unit,
    onLinks: () -> Unit,
    onCopy: () -> Unit,
    onReload: () -> Unit,
    onDelete: () -> Unit,
) {
    InkOverlay(visible = true, onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Text("笔记操作", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink.Ink)
            Spacer(Modifier.height(6.dp))
            listOf(
                (if (isReadMode) "切换到编辑" else "切换到阅读") to onToggleMode,
                "反向链接" to onLinks,
                "重命名" to onRename,
                "复制全文" to onCopy,
                "从磁盘重新载入" to onReload,
                "删除" to onDelete,
            ).forEach { (label, action) ->
                InkDivider()
                Box(
                    Modifier.fillMaxWidth().height(48.dp).inkClickable { action() },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(label, fontSize = 16.sp, color = Ink.Ink)
                }
            }
            InkDivider()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                InkOutlineButton("取消", onClick = onDismiss)
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* 编辑模式翻页与样式                                                    */
/* ------------------------------------------------------------------ */

private data class EditorScroll(val y: Int, val max: Int)

/** 编辑模式翻页：按行网格对齐，屏幕顶部始终是完整的一行。 */
private fun pageScrollEditor(et: EditText, settings: InkSettings, forward: Boolean): EditorScroll? {
    val layout = et.layout ?: return null
    val lineH = et.lineHeight.coerceAtLeast(1)
    val viewport = (et.height - et.paddingTop - et.paddingBottom).coerceAtLeast(1)
    val overlap = lineH * settings.overlapLines
    val page = (viewport - overlap).coerceAtLeast(viewport / 2)
    val maxScroll = maxEditorScroll(et)
    var target = et.scrollY + (if (forward) page else -page)
    target = target.coerceIn(0, maxScroll)
    // 吸附到整行：屏幕顶部不会出现半行字
    if (lineH > 0) target = (target / lineH) * lineH
    et.scrollTo(0, target.coerceIn(0, maxScroll))
    return EditorScroll(et.scrollY, maxScroll)
}

private fun maxEditorScroll(et: EditText): Int {
    val layout = et.layout ?: return 0
    val contentH = layout.height + et.paddingTop + et.paddingBottom
    return (contentH - et.height).coerceAtLeast(0)
}

/** 应用字号 / 行距 / 边距 / 配色（编辑模式的 EditText 就是纸质稿纸）。 */
private fun applyEditorStyle(et: EditText, settings: InkSettings, palette: InkPalette, density: Float) {
    et.setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.fontSize.toFloat())
    et.typeface = Typeface.create(if (settings.serif) "serif" else "sans-serif", Typeface.NORMAL)
    // 用 Android 实际换算出来的 textSize（含系统字体缩放）算行距余量，
    // 和阅读模式“字号 × 行距”的口径保持一致
    val extra = et.textSize * (settings.lineFactor - 1f)
    et.setLineSpacing(extra, 1f)
    et.setTextColor(palette.ink.toArgb())
    et.setHintTextColor(palette.mid.toArgb())
    et.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    val hPad = (settings.margin * density).toInt()
    et.setPadding(hPad, (14 * density).toInt(), hPad, (64 * density).toInt())
    et.gravity = Gravity.TOP or Gravity.START
    et.isVerticalScrollBarEnabled = false
    et.isHorizontalScrollBarEnabled = false
    et.overScrollMode = View.OVER_SCROLL_NEVER
    et.inputType = InputType.TYPE_CLASS_TEXT or
        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    et.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
    et.hint = "开始书写…"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        et.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO)
    }
    et.setSingleLine(false)
}

/* ------------------------------------------------------------------ */
/* 锚点：把「#小节」定位到对应的标题块                                     */
/* ------------------------------------------------------------------ */

/** Markdown 行内标记去掉，只留可比较的纯文字（标题匹配用）。 */
internal fun plainInlineText(raw: String): String {
    var s = raw
    // [文字](链接) -> 文字
    s = Regex("\\[([^\\]]*)]\\([^)]*\\)").replace(s, "$1")
    // [[双链|别名]] -> 别名 / [[双链]] -> 双链
    s = Regex("\\[\\[([^\\]]*)]]").replace(s) { m ->
        val inner = m.groupValues[1]
        if (inner.contains('|')) inner.substringAfter('|') else inner.substringBefore('#')
    }
    // 自动链接 <https://…> -> 去尖括号
    s = Regex("<([^>]*)>").replace(s, "$1")
    // 行内代码
    s = s.replace("`", "")
    // 强调/删除/高亮标记
    s = s.replace("***", "").replace("**", "").replace("__", "")
    s = s.replace("~~", "").replace("==", "")
    s = s.replace("*", "").replace("_", "")
    return s.trim()
}

private fun normalizeAnchor(raw: String): String =
    raw.trim().trimStart('#').trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()

/** 找到与锚点文字对应的标题块下标（-1 = 没找到）。 */
internal fun headingIndex(blocks: List<MdBlock>, rawAnchor: String): Int {
    val anchor = normalizeAnchor(rawAnchor)
    if (anchor.isEmpty()) return -1
    blocks.forEachIndexed { index, block ->
        if (block is MdBlock.Heading) {
            val text = normalizeAnchor(plainInlineText(block.text))
            if (text == anchor) return index
        }
    }
    // 宽松匹配：包含关系（例如「#安装 步骤」能匹配「安装步骤」）
    blocks.forEachIndexed { index, block ->
        if (block is MdBlock.Heading) {
            val text = normalizeAnchor(plainInlineText(block.text))
            if (text.contains(anchor) || anchor.contains(text)) return index
        }
    }
    return -1
}
