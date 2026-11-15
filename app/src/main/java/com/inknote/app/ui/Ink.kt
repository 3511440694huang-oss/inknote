package com.inknote.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inknote.app.ui.theme.Ink
import kotlinx.coroutines.delay

/**
 * 无涟漪 / 无按压动画的点击。墨水屏上一切反馈都应当是瞬时的。
 */
@Composable
fun Modifier.inkClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun InkDivider(color: Color = Ink.Line, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** 顶栏：左侧可选返回/菜单，标题 + 小副标题，右侧 actions。高 54dp。 */
@Composable
fun InkTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backIcon: ImageVector = Icons.Filled.ArrowBack,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().background(Ink.Paper)) {
        Row(
            Modifier.fillMaxWidth().height(54.dp).padding(start = 2.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                InkIconButton(backIcon, onBack, "返回")
            }
            Column(
                Modifier.weight(1f).padding(start = if (onBack != null) 2.dp else 14.dp, end = 4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = Ink.Mid,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
        InkDivider()
    }
}

@Composable
fun InkIconButton(icon: ImageVector, onClick: () -> Unit, description: String? = null) {
    Box(
        Modifier.size(Ink.Touch).inkClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Ink.Ink, modifier = Modifier.size(23.dp))
    }
}

@Composable
fun InkTextButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(Ink.Touch).inkClickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, color = if (enabled) Ink.Ink else Ink.Mid, maxLines = 1)
    }
}

@Composable
fun InkChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(42.dp).padding(vertical = 5.dp).inkClickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .border(if (selected) Ink.StrongLine else 1.dp, if (enabled) Ink.Ink else Ink.Line)
                .background(if (selected) Ink.Ink else Ink.Paper)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (!enabled) Ink.Mid else if (selected) Ink.Paper else Ink.Ink,
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
    }
}

/** 一行选项 chip（自动等分宽度；选项不宜超过 4 个） */
@Composable
fun InkChipsRow(labels: List<String>, selectedIndex: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        labels.forEachIndexed { i, label ->
            Box(Modifier.weight(1f)) {
                InkChip(
                    label = label,
                    selected = i == selectedIndex,
                    enabled = enabled,
                    onClick = { onSelect(i) },
                )
            }
        }
    }
}

@Composable
fun InkPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .height(46.dp)
            .inkClickable(enabled = enabled, onClick = onClick)
            .background(if (enabled) Ink.Ink else Ink.Mist)
            .border(1.5.dp, if (enabled) Ink.Ink else Ink.Line)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, color = if (enabled) Ink.Paper else Ink.Mid)
    }
}

@Composable
fun InkOutlineButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .height(46.dp)
            .inkClickable(enabled = enabled, onClick = onClick)
            .background(Ink.Paper)
            .border(1.dp, if (enabled) Ink.Ink else Ink.Line)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, color = if (enabled) Ink.Ink else Ink.Mid)
    }
}

/**
 * 列表行：左侧图标 / 中间标题+副标题 / 右侧尾部插槽。
 * 点按与长按都用无动画手势检测；长按用于重命名/删除。
 */
@Composable
fun InkListItem(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    height: Dp = 58.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(enabled, onLongClick) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick?.invoke() },
                    )
                }
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { leading() }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    color = if (enabled) Ink.Ink else Ink.Mid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Ink.Mid,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (trailing != null) {
            Box(Modifier.padding(end = 2.dp)) { trailing() }
        }
    }
}

/** 设置项：左侧标题 + 右侧自定义控件 */
@Composable
fun InkSettingRow(label: String, hint: String? = null, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .let { if (onClick != null) it.inkClickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, fontSize = 16.sp, color = Ink.Ink)
            if (!hint.isNullOrBlank()) Text(hint, fontSize = 11.sp, color = Ink.Mid)
        }
        content()
    }
}

/** 自绘「勾选框」（无 Material 动画）。 */
@Composable
fun InkCheckBox(checked: Boolean) {
    val inkColor = Ink.Ink
    Canvas(Modifier.size(24.dp)) {
        val stroke = 1.6.dp.toPx()
        drawRect(
            color = inkColor,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke),
        )
        if (checked) {
            val p = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.55f)
                lineTo(size.width * 0.43f, size.height * 0.78f)
                lineTo(size.width * 0.82f, size.height * 0.25f)
            }
            drawPath(p, inkColor, style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun InkToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    InkSettingRow(label = label, onClick = { onChange(!checked) }) {
        Box(Modifier.size(48.dp).inkClickable { onChange(!checked) }, contentAlignment = Alignment.CenterEnd) {
            InkCheckBox(checked)
        }
    }
}

@Composable
fun InkSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = Ink.Mid,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
fun InkChevron() {
    Icon(Icons.Filled.KeyboardArrowRight, null, tint = Ink.Mid, modifier = Modifier.size(20.dp))
}

/* ------------------------------------------------------------------ */
/* 自绘图标：文件夹 / 笔记 / 翻页三角                                    */
/* ------------------------------------------------------------------ */

@Composable
fun FolderGlyph(size: Dp = 22.dp, filled: Boolean = false) {
    val inkColor = Ink.Ink
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val p = Path().apply {
            moveTo(3f * s, 20f * s)
            lineTo(21f * s, 20f * s)
            lineTo(21f * s, 7f * s)
            lineTo(11f * s, 7f * s)
            lineTo(9f * s, 4.5f * s)
            lineTo(3f * s, 4.5f * s)
            close()
        }
        if (filled) {
            drawPath(p, inkColor)
        } else {
            drawPath(p, inkColor, style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

@Composable
fun NoteGlyph(size: Dp = 22.dp) {
    val inkColor = Ink.Ink
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val stroke = 1.6.dp.toPx()
        // 纸张
        drawRect(inkColor, topLeft = Offset(6f * s, 3f * s), size = Size(12f * s, 18f * s), style = Stroke(stroke))
        // 文字线
        for (i in 0..2) {
            val y = (7.5f + i * 3.4f) * s
            drawLine(inkColor, Offset(9f * s, y), Offset(15f * s, y), strokeWidth = stroke * 0.85f)
        }
    }
}

/**
 * 自绘翻页箭头：细折线（1.0.0 用的是实心粗三角，墨水屏上太厚重；
 * 1.1.0 改成 1.8dp 细折线，钉在细边框小按钮里更轻、更清楚）。
 */
@Composable
fun PageChevron(up: Boolean, enabled: Boolean = true, size: Dp = 13.dp) {
    val color = if (enabled) Ink.Ink else Ink.Mid
    Canvas(Modifier.size(size)) {
        // 所有尺寸都按图标自身的 24 格坐标系缩放，线宽才不会在小组件里显得粗
        val s = this.size.width / 24f
        val stroke = 2.2f * s
        val p = Path().apply {
            if (up) {
                moveTo(4.5f * s, 15.5f * s)
                lineTo(12f * s, 7.5f * s)
                lineTo(19.5f * s, 15.5f * s)
            } else {
                moveTo(4.5f * s, 8.5f * s)
                lineTo(12f * s, 16.5f * s)
                lineTo(19.5f * s, 8.5f * s)
            }
        }
        drawPath(
            p,
            color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/* ------------------------------------------------------------------ */
/* 覆盖层对话框：不用系统 Dialog，避免窗口动画，也避免焦点跳转             */
/* ------------------------------------------------------------------ */

@Composable
fun InkOverlay(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (!visible) return
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x40000000))
            .inkClickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.92f)
                .background(Ink.Paper)
                .border(Ink.StrongLine, Ink.Ink)
                .inkClickable { }
                .padding(18.dp),
        ) {
            content()
        }
    }
}

@Composable
fun InkDialogTitle(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink.Ink)
}

@Composable
fun InkDialogText(text: String) {
    Text(text, fontSize = 14.sp, lineHeight = 21.sp, color = Ink.Deep)
}

@Composable
fun InkDialogButtons(vararg buttons: Pair<String, () -> Unit>) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
    ) {
        buttons.forEach { (label, action) ->
            InkOutlineButton(label, onClick = action)
        }
    }
}

/**
 * 单行输入对话框（新建笔记/文件夹、重命名、自定义字号）。
 *
 * @param hint 空输入框里的占位提示
 * @param numeric 只允许数字（用于字号这类数值设置，会弹数字键盘）
 */
@Composable
fun InkInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    hint: String = "输入名称…",
    numeric: Boolean = false,
) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    InkOverlay(visible = true, onDismiss = onDismiss) {
        Column {
            InkDialogTitle(title)
            Spacer(Modifier.height(12.dp))
            Text(label, fontSize = 12.sp, color = Ink.Mid)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.2.dp, Ink.Ink)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                if (value.isEmpty()) {
                    Text(hint, fontSize = 16.sp, color = Ink.Mid)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { raw ->
                        value = if (numeric) raw.filter { it.isDigit() }.take(2) else raw.take(80)
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = Ink.Ink),
                    cursorBrush = SolidColor(Ink.Ink),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); onConfirm(value) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            InkDialogButtons(
                "取消" to onDismiss,
                confirmText to {
                    keyboard?.hide()
                    onConfirm(value)
                },
            )
        }
    }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
}

/** 确认对话框 */
@Composable
fun InkConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    InkOverlay(visible = true, onDismiss = onDismiss) {
        Column {
            InkDialogTitle(title)
            Spacer(Modifier.height(10.dp))
            InkDialogText(message)
            InkDialogButtons(
                "取消" to onDismiss,
                confirmText to onConfirm,
            )
        }
    }
}

/** 操作菜单对话框（更多 / 长按） */
@Composable
fun InkActionsDialog(
    title: String,
    actions: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    InkOverlay(visible = true, onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            InkDialogTitle(title)
            Spacer(Modifier.height(6.dp))
            actions.forEach { (label, action) ->
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
