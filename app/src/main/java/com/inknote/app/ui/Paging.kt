package com.inknote.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inknote.app.data.InkSettings
import com.inknote.app.ui.theme.Ink
import kotlinx.coroutines.delay

/* ------------------------------------------------------------------ */
/* 翻页按钮：全局通用（首页 / 搜索 / 设置 / 笔记阅读编辑）                  */
/* ------------------------------------------------------------------ */

private val BUTTON_W = 26.dp
private val BUTTON_H = 44.dp

/**
 * 通用「整页翻动」助手函数集合。
 *
 * 设计目标（墨水屏）：
 * 1. 一次点击 = 瞬时滚动一屏（无动画、无惯性）；
 * 2. 翻页后内容对齐：列表吸附到完整条目开头，阅读视图吸附到完整段落；
 * 3. 长按连续翻页（520ms 后每 360ms 一屏），避免反复点。
 *
 * 按钮本身在 [PageButtonsOverlay] / [PageButtonPair] 里，尺寸 26×44dp，
 * 1dp 细边框 + 细箭头（1.0.0 的实心粗三角在墨水屏上太重）。
 */

/** 一屏翻页的像素步长：一屏减去重叠行，最少半屏。 */
fun pageStepPx(settings: InkSettings, density: Density, viewportPx: Int): Int {
    if (viewportPx <= 0) return 0
    // 用 sp -> px 换算（含系统字体缩放），重叠行按真实行高算
    val linePx = with(density) { (settings.fontSize * settings.lineFactor).sp.toPx() }
    val overlap = (linePx * settings.overlapLines).toInt()
    return (viewportPx - overlap).coerceAtLeast(viewportPx / 2)
}

/** 有翻页按钮时，内容两侧需要留出的空白（避免文字被按钮压住）。 */
@Composable
fun pageButtonSidePad(settings: InkSettings): Dp = if (settings.pageButtons) 34.dp else 0.dp

/**
 * LazyColumn / Markdown 阅读视图的整页翻动。
 * 返回是否真的滚动（false = 已经到头）。
 */
suspend fun pageScrollLazy(
    state: LazyListState,
    settings: InkSettings,
    density: Density,
    forward: Boolean,
    snapToItemStart: Boolean = true,
): Boolean {
    val info = state.layoutInfo
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    if (viewport <= 0) return false
    if (forward && !state.canScrollForward) return false
    if (!forward && !state.canScrollBackward) return false

    val page = pageStepPx(settings, density, viewport)
    if (page <= 0) return false
    state.scrollBy((if (forward) page else -page).toFloat())
    if (!snapToItemStart) return true

    val after = state.layoutInfo
    val top = after.viewportStartOffset
    val visible = after.visibleItemsInfo
    if (visible.isEmpty()) return true
    if (forward) {
        // 吸附到视口内第一个「完整开始」的条目（顶部残留的那一条不算）
        val candidate = visible.firstOrNull { it.offset >= top } ?: return true
        val delta = candidate.offset - top
        if (delta > 0) state.scrollBy(delta.toFloat())
    } else {
        // 向上翻：把最上面那条完全露出的条目对齐到视口顶部
        val candidate = visible.lastOrNull { it.offset <= top } ?: return true
        val delta = candidate.offset - top
        if (delta < 0) state.scrollBy(delta.toFloat())
    }
    return true
}

/** Column + verticalScroll 的整页翻动（设置页、侧栏这类非 Lazy 列表）。 */
suspend fun pageScrollScrollable(
    state: ScrollState,
    viewportPx: Int,
    settings: InkSettings,
    density: Density,
    forward: Boolean,
): Boolean {
    val page = pageStepPx(settings, density, viewportPx)
    if (page <= 0 || state.maxValue <= 0) return false
    val target = (state.value + if (forward) page else -page).coerceIn(0, state.maxValue)
    if (target == state.value) return false
    state.scrollTo(target)
    return true
}

/**
 * 屏幕（Box）边缘的翻页按钮组。放进 Box 里即可：
 * 位置跟随设置（左/右），点按翻一屏，长按连续翻。
 */
@Composable
fun BoxScope.PageButtonsOverlay(
    settings: InkSettings,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    if (!settings.pageButtons) return
    val alignment = if (settings.pageButtonsLeft) Alignment.CenterStart else Alignment.CenterEnd
    PageButtonPair(
        modifier = Modifier.align(alignment).padding(horizontal = 3.dp),
        canUp = canUp,
        canDown = canDown,
        onUp = onUp,
        onDown = onDown,
    )
}

/** 上下两个翻页小按钮（可单独摆放，如侧栏内部）。 */
@Composable
fun PageButtonPair(
    modifier: Modifier = Modifier,
    canUp: Boolean = true,
    canDown: Boolean = true,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PageStepButton(up = true, enabled = canUp, onStep = onUp)
        PageStepButton(up = false, enabled = canDown, onStep = onDown)
    }
}

/** 单个翻页按钮：26×44dp，1dp 细边框，按下立刻翻（无涟漪），长按连续翻。 */
@Composable
private fun PageStepButton(up: Boolean, enabled: Boolean, onStep: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    // 长按里已经翻过页 → 松手时 onTap 不再多翻一页
    var steppedByRepeat by remember { mutableStateOf(false) }
    LaunchedEffect(holding) {
        if (holding) {
            delay(520)
            while (holding) {
                onStep()
                steppedByRepeat = true
                delay(360)
            }
        }
    }
    Box(
        Modifier
            .size(width = BUTTON_W, height = BUTTON_H)
            .background(Ink.Paper)
            .border(1.dp, if (enabled) Ink.Ink else Ink.Line)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = {
                        if (steppedByRepeat) steppedByRepeat = false else onStep()
                    },
                    onPress = {
                        holding = true
                        steppedByRepeat = false
                        try {
                            tryAwaitRelease()
                        } finally {
                            holding = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        PageChevron(up = up, enabled = enabled, size = 13.dp)
    }
}

/** 阅读进度百分比（按首个可见块估算，够用且无动画）。 */
fun previewProgress(state: LazyListState, total: Int): Int {
    if (total <= 0) return 0
    val info = state.layoutInfo
    val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
    val frac = if (first >= total - 1) 1f else first.toFloat() / total
    return (frac * 100).toInt().coerceIn(0, 100)
}

/**
 * 阅读视图/预览翻页（兼容入口，等价于 [pageScrollLazy]）。
 * 1. 先按「一屏 - 重叠行」瞬时滚动；
 * 2. 再吸附到最近的完整段落边界，顶部不会出现半行字。
 */
suspend fun pageScrollPreview(
    state: LazyListState,
    settings: InkSettings,
    density: Density,
    forward: Boolean,
): Boolean = pageScrollLazy(state, settings, density, forward, snapToItemStart = true)
