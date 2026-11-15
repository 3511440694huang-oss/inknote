package com.inknote.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inknote.app.data.FONT_SIZE_MAX
import com.inknote.app.data.FONT_SIZE_MIN
import com.inknote.app.data.InkSettings
import com.inknote.app.data.SettingsStore
import com.inknote.app.data.VaultManager
import com.inknote.app.data.clampFontSize
import com.inknote.app.data.stepFontSize
import com.inknote.app.ui.theme.Ink
import com.inknote.app.ui.theme.LocalInkPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 设置页。所有调整即时生效；调完返回笔记页就能看到新的字号 / 行距 / 翻页方式。
 */
@Composable
fun SettingsScreen(
    vaults: VaultManager,
    settingsStore: SettingsStore,
    settings: InkSettings,
    onBack: () -> Unit,
) {
    val palette = LocalInkPalette.current
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClearRecent by remember { mutableStateOf(false) }
    var showFontInput by remember { mutableStateOf(false) }

    // 设置页列表：整页翻页按钮 + 内容两侧预留（按钮位置跟随设置）
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val sidePad = pageButtonSidePad(settings)
    val startPad = if (settings.pageButtons && settings.pageButtonsLeft) sidePad else 0.dp
    val endPad = if (settings.pageButtons && !settings.pageButtonsLeft) sidePad else 0.dp

    BackHandler { onBack() }

    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val v = vaults.openTree(uri)
            message = if (v != null) "已切换仓库：${v.label}" else "无法打开这个文件夹（可能缺少读写权限）"
        }
    }

    Column(Modifier.fillMaxSize().background(Ink.Paper)) {
        InkTopBar(title = "设置", subtitle = "即时生效 · 零动画", onBack = onBack)

        Box(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = startPad, end = endPad),
            ) {
                item { InkSectionLabel("显示") }
                item {
                    Column(Modifier.fillMaxWidth()) {
                        FontSizeSetting(
                            size = settings.fontSize,
                            lineFactor = settings.lineFactor,
                            serif = settings.serif,
                            onSize = { v -> settingsStore.update { it.copy(fontSize = clampFontSize(v)) } },
                            onInput = { showFontInput = true },
                        )
                        InkDivider()
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth()) {
                        SettingChipsRow(
                            label = "行距",
                            options = listOf("紧凑", "标准", "宽松"),
                            selectedIndex = lineFactorIndex(settings.lineFactor),
                            onSelect = { index -> settingsStore.update { it.copy(lineFactor = lineFactors[index]) } },
                        )
                        InkDivider()
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth()) {
                        SettingChipsRow(
                            label = "正文字体",
                            options = listOf("黑体", "宋体"),
                            selectedIndex = if (settings.serif) 1 else 0,
                            onSelect = { index -> settingsStore.update { it.copy(serif = index == 1) } },
                        )
                        InkDivider()
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth()) {
                        SettingChipsRow(
                            label = "页边距",
                            options = listOf("窄", "标准", "宽"),
                            selectedIndex = marginIndex(settings.margin),
                            onSelect = { index -> settingsStore.update { it.copy(margin = margins[index]) } },
                        )
                        InkDivider()
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth()) {
                        SettingChipsRow(
                            label = "配色",
                            options = listOf("白纸", "反色"),
                            selectedIndex = if (settings.inverse) 1 else 0,
                            onSelect = { index -> settingsStore.update { it.copy(inverse = index == 1) } },
                        )
                        InkDivider()
                    }
                }

                item { InkSectionLabel("翻页") }
                item {
                    Column(Modifier.fillMaxWidth()) {
                        InkToggleRow(
                            label = "显示翻页按钮",
                            checked = settings.pageButtons,
                            onChange = { settingsStore.update { s -> s.copy(pageButtons = it) } },
                        )
                        InkDivider()
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth()) {
                        SettingChipsRow(
                            label = "按钮位置",
                            options = listOf("左侧", "右侧"),
                            selectedIndex = if (settings.pageButtonsLeft) 0 else 1,
                            enabled = settings.pageButtons,
                            onSelect = { index -> settingsStore.update { it.copy(pageButtonsLeft = index == 0) } },
                        )
                        InkDivider()
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth()) {
                        SettingChipsRow(
                            label = "翻页重叠行数",
                            options = listOf("0", "1", "2", "3"),
                            selectedIndex = settings.overlapLines.coerceIn(0, 3),
                            onSelect = { index -> settingsStore.update { it.copy(overlapLines = index) } },
                        )
                        InkDivider()
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth()) {
                        SettingChipsRow(
                            label = "打开笔记默认",
                            options = listOf("编辑", "阅读"),
                            selectedIndex = if (settings.startInReadMode) 1 else 0,
                            onSelect = { index -> settingsStore.update { it.copy(startInReadMode = index == 1) } },
                        )
                    }
                }

                item { InkSectionLabel("仓库") }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("当前仓库", fontSize = 16.sp, color = Ink.Ink)
                        Spacer(Modifier.height(4.dp))
                        Text(settings.vaultLabel, fontSize = 13.sp, color = Ink.Deep)
                        Text(
                            if (settings.vaultUri == null) vaults.internalRoot.absolutePath else settings.vaultUri,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = Ink.Mid,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            InkOutlineButton("打开文件夹…", onClick = { openTree.launch(null) })
                            InkOutlineButton("恢复内置仓库", onClick = {
                                vaults.useInternal()
                                message = "已切换回内置仓库"
                            })
                        }
                    }
                }

                item { InkSectionLabel("其他") }
                item {
                    Column(Modifier.fillMaxWidth()) {
                        SettingChipsRow(
                            label = "文件排序",
                            options = listOf("按名称", "按时间"),
                            selectedIndex = if (settings.sortByModified) 1 else 0,
                            onSelect = { index -> settingsStore.update { it.copy(sortByModified = index == 1) } },
                        )
                        InkDivider()
                    }
                }
                item {
                    Box(
                        Modifier.fillMaxWidth().height(54.dp).inkClickable { confirmClearRecent = true }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text("清空最近打开记录", fontSize = 16.sp, color = Ink.Ink)
                    }
                }

                item { InkSectionLabel("关于") }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text("墨笺 InkNote · 1.2.0", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink.Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "类 Obsidian 的本地 Markdown 笔记软件，专为墨水屏与老设备打磨。\n" +
                                "纯 Kotlin 实现、不含任何原生库：32 位 / 64 位 Android 6.0+ 均可安装。\n" +
                                "无网络权限，笔记只保存在本机。\n" +
                                "支持超链接、锚点与 LaTeX 数学公式。\n" +
                                "正文字号可在 $FONT_SIZE_MIN–$FONT_SIZE_MAX sp 之间自定义。",
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            color = palette.mid,
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }

            // 整页翻页按钮：一屏一翻、对齐到整行（无动画）
            PageButtonsOverlay(
                settings = settings,
                canUp = listState.canScrollBackward,
                canDown = listState.canScrollForward,
                onUp = { scope.launch { pageScrollLazy(listState, settings, density, false) } },
                onDown = { scope.launch { pageScrollLazy(listState, settings, density, true) } },
            )
        }
    }

    if (showFontInput) {
        InkInputDialog(
            title = "自定义字号",
            label = "输入 $FONT_SIZE_MIN–$FONT_SIZE_MAX 之间的整数（单位 sp）",
            // 不预填旧值：数字输入框预填后光标在末尾，补一位就会被截断成旧值
            initial = "",
            confirmText = "确定",
            hint = "当前 ${settings.fontSize}，例如 24",
            numeric = true,
            onDismiss = { showFontInput = false },
            onConfirm = { raw ->
                showFontInput = false
                val typed = raw.trim().toIntOrNull()
                if (typed == null) {
                    message = "请输入数字，例如 24"
                } else {
                    val clamped = clampFontSize(typed)
                    settingsStore.update { it.copy(fontSize = clamped) }
                    if (clamped != typed) message = "字号范围是 $FONT_SIZE_MIN–$FONT_SIZE_MAX，已设为 $clamped"
                }
            },
        )
    }

    if (confirmClearRecent) {
        InkConfirmDialog(
            title = "清空最近打开",
            message = "只会清掉「最近打开」列表，不会删除任何笔记。",
            confirmText = "清空",
            onDismiss = { confirmClearRecent = false },
            onConfirm = {
                confirmClearRecent = false
                settingsStore.clearRecent()
                message = "已清空"
            },
        )
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

@Composable
private fun SettingChipsRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, fontSize = 16.sp, color = if (enabled) Ink.Ink else Ink.Mid)
        Spacer(Modifier.height(6.dp))
        InkChipsRow(labels = options, selectedIndex = selectedIndex, enabled = enabled, onSelect = onSelect)
    }
}

/* ------------------------------------------------------------------ */
/* 字号：可逐 1sp 自定义（1.2.0 起，替代原来的 4 个固定档位）                */
/* ------------------------------------------------------------------ */

/** 快捷档位：只是省事，真正的字号由上面的 − / ＋ 或直接输入决定。 */
private val fontPresets = listOf(16, 20, 26, 32)
private fun presetLabel(size: Int): String = when (size) {
    16 -> "小"
    20 -> "标准"
    26 -> "大"
    32 -> "特大"
    else -> size.toString()
}

private fun lineFactorText(f: Float): String {
    val v = (f * 100).roundToInt() / 100f
    return if (v == v.toInt().toFloat()) "${v.toInt()}.0" else v.toString()
}

/**
 * 字号设置块：
 * 1. − / ＋ 逐 1sp 调整，长按连续调（420ms 后每 140ms 一步，墨水屏刷新跟得上）；
 * 2. 点中间的数字可以直接输入精确值；
 * 3. 下方实时预览当前字号 / 行距 / 字形的实际效果，不用回笔记页试；
 * 4. 四个常用档位做快捷入口。
 */
@Composable
private fun FontSizeSetting(
    size: Int,
    lineFactor: Float,
    serif: Boolean,
    onSize: (Int) -> Unit,
    onInput: () -> Unit,
) {
    val palette = LocalInkPalette.current
    val family = if (serif) FontFamily.Serif else FontFamily.SansSerif
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("字号", fontSize = 16.sp, color = Ink.Ink)
            Spacer(Modifier.weight(1f))
            Text("$size sp", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink.Deep)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FontStepButton(
                label = "−",
                enabled = size > FONT_SIZE_MIN,
                onStep = { onSize(stepFontSize(size, -1)) },
                modifier = Modifier.width(58.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(1.dp, Ink.Ink)
                    .background(Ink.Paper)
                    .inkClickable(onClick = onInput),
                contentAlignment = Alignment.Center,
            ) {
                Text("$size", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink.Ink)
            }
            FontStepButton(
                label = "＋",
                enabled = size < FONT_SIZE_MAX,
                onStep = { onSize(stepFontSize(size, 1)) },
                modifier = Modifier.width(58.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "长按连续调整 · 点数字直接输入 · 范围 $FONT_SIZE_MIN–$FONT_SIZE_MAX",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = Ink.Mid,
        )
        Spacer(Modifier.height(10.dp))

        // 实时预览：与笔记正文使用同一字号 / 行距 / 字体
        Column(Modifier.fillMaxWidth().border(1.dp, Ink.Line).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("预览 · ${size}sp / 行距 ${lineFactorText(lineFactor)}", fontSize = 10.sp, color = Ink.Mid)
            Spacer(Modifier.height(6.dp))
            Text(
                "字大一些，读得更省力。",
                fontSize = size.sp,
                lineHeight = (size * lineFactor).sp,
                fontFamily = family,
                color = palette.ink,
            )
            Text(
                "InkNote 0123",
                fontSize = size.sp,
                lineHeight = (size * lineFactor).sp,
                fontFamily = family,
                color = palette.deep,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("快捷", fontSize = 12.sp, color = Ink.Mid)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                InkChipsRow(
                    labels = fontPresets.map { presetLabel(it) },
                    selectedIndex = fontPresets.indexOf(size),
                    onSelect = { index -> onSize(fontPresets[index]) },
                )
            }
        }
    }
}

/** − / ＋ 步进按钮：48dp 高、1dp 细边框，无涟漪，长按连续步进。 */
@Composable
private fun FontStepButton(
    label: String,
    enabled: Boolean,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 长按期间 composable 会重组（字号一直在变），用最新 lambda，避免每步都从旧值起算
    val step by rememberUpdatedState(onStep)
    var holding by remember { mutableStateOf(false) }
    // 长按期间已经步进过 → 松手时 detectTapGestures 仍会回调 onTap，要吞掉这一次（否则多走一步）
    var steppedByRepeat by remember { mutableStateOf(false) }
    // 到边界（enabled = false）时立刻停掉连发，别空转
    LaunchedEffect(holding, enabled) {
        if (holding && enabled) {
            delay(420)
            while (holding && enabled) {
                step()
                steppedByRepeat = true
                delay(140)
            }
        }
    }
    Box(
        modifier
            .height(48.dp)
            .background(Ink.Paper)
            .border(1.dp, if (enabled) Ink.Ink else Ink.Line)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = {
                        // 长按连发过的那一次，松手不再补一步
                        if (steppedByRepeat) steppedByRepeat = false else step()
                    },
                    onPress = {
                        holding = true
                        steppedByRepeat = false
                        try {
                            tryAwaitRelease()
                        } finally {
                            // 手势协程被取消（如 enabled 变化）时也要复位，否则会一直连发
                            holding = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 22.sp, color = if (enabled) Ink.Ink else Ink.Mid)
    }
}

private val lineFactors = listOf(1.3f, 1.5f, 1.75f)
private fun lineFactorIndex(v: Float): Int = lineFactors.indexOfFirst { kotlin.math.abs(it - v) < 0.01f }.takeIf { it >= 0 } ?: 1

private val margins = listOf(12, 20, 28)
private fun marginIndex(v: Int): Int = margins.indexOf(v).takeIf { it >= 0 } ?: 1
