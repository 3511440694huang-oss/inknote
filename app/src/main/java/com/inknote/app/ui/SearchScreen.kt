package com.inknote.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inknote.app.data.Vault
import com.inknote.app.data.VaultEntry
import com.inknote.app.ui.theme.Ink
import com.inknote.app.ui.theme.LocalInkSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SearchHit(val entry: VaultEntry, val snippet: String)

/**
 * 搜索 / 快速切换（全屏覆盖，无动画）。
 * 「按名称」= Obsidian 的快速切换；「全文」= 扫描仓库内所有 Markdown。
 */
@Composable
fun SearchScreen(
    vault: Vault,
    initial: String,
    initialFullText: Boolean,
    onDismiss: () -> Unit,
    onOpen: (VaultEntry) -> Unit,
) {
    var query by remember { mutableStateOf(initial) }
    var fullText by remember { mutableStateOf(initialFullText) }
    var results by remember { mutableStateOf(emptyList<SearchHit>()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val settings = LocalInkSettings.current

    // 结果列表：翻页按钮 + 内容两侧预留（按钮位置跟随设置）
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val sidePad = pageButtonSidePad(settings)
    val startPad = if (settings.pageButtons && settings.pageButtonsLeft) sidePad else 0.dp
    val endPad = if (settings.pageButtons && !settings.pageButtonsLeft) sidePad else 0.dp
    var searchSeq by remember { mutableIntStateOf(0) }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList()
            searched = false
            return
        }
        scope.launch {
            searching = true
            val hitList = withContext(Dispatchers.IO) {
                val all = runCatching { vault.walkMarkdown(1200) }.getOrDefault(emptyList())
                val hits = ArrayList<SearchHit>()
                if (!fullText) {
                    all.forEach { e ->
                        if (hits.size < 100 && (e.title.contains(q, ignoreCase = true) || e.name.contains(q, ignoreCase = true))) {
                            hits.add(SearchHit(e, vault.pathOf(e)))
                        }
                    }
                } else {
                    for (e in all) {
                        if (hits.size >= 80) break
                        val text = runCatching { vault.read(e.id) }.getOrNull() ?: continue
                        val idx = text.indexOf(q, ignoreCase = true)
                        if (idx >= 0) {
                            val start = (idx - 24).coerceAtLeast(0)
                            val end = (idx + q.length + 56).coerceAtMost(text.length)
                            val snippet = text.substring(start, end).replace('\n', ' ').replace("  ", " ").trim()
                            hits.add(SearchHit(e, "${vault.pathOf(e)} ｜ $snippet"))
                        }
                    }
                }
                hits
            }
            results = hitList
            searching = false
            searched = true
            searchSeq++
        }
    }

    LaunchedEffect(Unit) {
        if (initial.isNotBlank()) runSearch()
        else {
            kotlinx.coroutines.delay(200)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    BackHandler { onDismiss() }

    // 新结果出来后无动画回到列表顶部
    LaunchedEffect(searchSeq) {
        if (searchSeq > 0) runCatching { listState.scrollToItem(0) }
    }

    Column(Modifier.fillMaxSize().background(Ink.Paper)) {
        InkTopBar(
            title = "搜索",
            subtitle = "名称查找或全文扫描",
            onBack = onDismiss,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .border(1.2.dp, Ink.Ink)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                if (query.isEmpty()) {
                    Text("输入笔记名或正文关键词…", fontSize = 15.sp, color = Ink.Mid)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = Ink.Ink),
                    cursorBrush = SolidColor(Ink.Ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); runSearch() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Spacer(Modifier.width(10.dp))
            InkPrimaryButton("搜索", onClick = {
                keyboard?.hide()
                runSearch()
            })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            InkChipsRow(
                labels = listOf("按名称", "全文"),
                selectedIndex = if (fullText) 1 else 0,
                onSelect = {
                    fullText = it == 1
                    if (query.isNotBlank() && searched) runSearch()
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        InkDivider()

        when {
            searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("搜索中…", fontSize = 14.sp, color = Ink.Mid)
            }

            results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searched) "没有找到匹配的笔记" else "输入关键词后按「搜索」",
                    fontSize = 14.sp,
                    color = Ink.Mid,
                )
            }

            else -> Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = startPad, end = endPad),
                ) {
                    item {
                        Text(
                            "找到 ${results.size} 条${if (fullText) "（全文）" else "（名称）"}",
                            fontSize = 12.sp,
                            color = Ink.Mid,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    items(results, key = { it.entry.id }) { hit ->
                        InkListItem(
                            title = hit.entry.title,
                            subtitle = hit.snippet,
                            leading = { NoteGlyph() },
                            height = 60.dp,
                            onClick = { onOpen(hit.entry) },
                        )
                        InkDivider()
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
                // 整页翻页按钮：一屏一翻、对齐到整条（无动画）
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
