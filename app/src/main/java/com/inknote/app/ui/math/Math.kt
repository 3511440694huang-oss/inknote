package com.inknote.app.ui.math

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LaTeX 公式渲染（1.1.0 新增）。
 *
 * 两种用法：
 * - 行内公式 `$...$` / `\(...\)`：由 Markdown 解析器交给 [linearizeMath]，
 *   转成 Unicode 行内文本，跟正文一起排版（不打断换行）。
 * - 块级公式 `$$...$$` / `\[...\]`：用 [MathBlockView] 真正排版
 *   （分式上下堆叠、根号线、上下标、矩阵等），自己测量 + Canvas 绘制，
 *   不依赖任何第三方库，也不引入原生库，保证 32 位设备可用。
 *
 * 模块划分：
 * - MathModel.kt：纯 Kotlin 语法树 / 盒模型 / 测量接口；
 * - MathParser.kt：容错 LaTeX 解析器；
 * - MathLayout.kt：测量 + 基线对齐 + 横向居中缩放所需的排版计算（纯 Kotlin，可单测）；
 * - MathLinearizer.kt：行内公式 Unicode 线性化；
 * - Math.kt（本文件）：Compose 侧测量实现与 Canvas 绘制。
 */

/** 块级公式排版视图：在可用宽度内居中，超宽时均匀缩放到最小 0.6 倍。 */
@Composable
fun MathBlockView(
    latex: String,
    fontSize: TextUnit = 18.sp,
    color: Color = Color.Black,
    modifier: Modifier = Modifier,
) {
    if (latex.isBlank()) return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val node = remember(latex) { parseMath(latex) }
    val mathMeasure = remember(measurer, density) { ComposeMathMeasure(measurer, density) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidthPx =
            if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY
        val fontSizePx = remember(fontSize, density) { resolveFontSizePx(fontSize, density) }
        val layout = remember(node, fontSizePx) { layoutMath(node, fontSizePx, mathMeasure) }
        if (layout.width <= 0f && layout.height <= 0f) return@BoxWithConstraints
        val fitScale = remember(layout.width, availableWidthPx) {
            mathFitScale(layout.width, availableWidthPx)
        }
        val drawItems = remember(layout, mathMeasure) { buildDrawItems(layout, measurer, density) }
        val verticalPadding = 3.dp
        val heightDp = with(density) {
            (layout.height * fitScale + verticalPadding.toPx() * 2f).toDp()
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(heightDp)) {
            val offsetX = (size.width - layout.width * fitScale) / 2f
            val offsetY = verticalPadding.toPx()
            withTransform({
                translate(offsetX, offsetY)
                scale(fitScale, fitScale, pivot = Offset.Zero)
            }) {
                for (item in drawItems) {
                    when (item) {
                        is MathDrawItem.TextItem -> drawText(
                            textLayoutResult = item.layout,
                            color = color,
                            topLeft = Offset(item.x, item.top),
                        )
                        is MathDrawItem.RuleItem -> drawRect(
                            color = color,
                            topLeft = Offset(item.x, item.y),
                            size = Size(item.width, item.height),
                        )
                        is MathDrawItem.LineItem -> drawLine(
                            color = color,
                            start = Offset(item.x1, item.y1),
                            end = Offset(item.x2, item.y2),
                            strokeWidth = item.strokeWidth,
                            cap = StrokeCap.Butt,
                        )
                        is MathDrawItem.GlyphItem -> withTransform({
                            translate(item.x, item.top)
                            scale(item.scaleX, item.scaleY, pivot = Offset.Zero)
                        }) {
                            drawText(
                                textLayoutResult = item.layout,
                                color = color,
                                topLeft = Offset.Zero,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 绘制指令

/** 把与 Compose 无关的 [MathElement] 翻译成带 TextLayoutResult 的绘制指令。 */
private sealed interface MathDrawItem {
    class TextItem(val layout: TextLayoutResult, val x: Float, val top: Float) : MathDrawItem
    class RuleItem(val x: Float, val y: Float, val width: Float, val height: Float) : MathDrawItem
    class LineItem(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val strokeWidth: Float,
    ) : MathDrawItem
    class GlyphItem(
        val layout: TextLayoutResult,
        val x: Float,
        val top: Float,
        val scaleX: Float,
        val scaleY: Float,
    ) : MathDrawItem
}

private fun buildDrawItems(
    layout: MathLayout,
    measurer: TextMeasurer,
    density: Density,
): List<MathDrawItem> {
    val items = ArrayList<MathDrawItem>(layout.elements.size)
    for (element in layout.elements) {
        when (element) {
            is MathElement.Text -> {
                val result = measureText(measurer, element.text, element.sizePx, element.italic, element.bold, density)
                    ?: continue
                items.add(
                    MathDrawItem.TextItem(
                        layout = result,
                        x = element.x,
                        top = element.baseline - result.firstBaseline,
                    )
                )
            }
            is MathElement.Rule -> {
                if (element.width <= 0f || element.height <= 0f) continue
                items.add(MathDrawItem.RuleItem(element.x, element.y, element.width, element.height))
            }
            is MathElement.Line -> {
                if (element.strokeWidth <= 0f) continue
                items.add(
                    MathDrawItem.LineItem(
                        element.x1, element.y1, element.x2, element.y2, element.strokeWidth,
                    )
                )
            }
            is MathElement.StretchedGlyph -> {
                val result = measureText(
                    measurer, element.glyph, element.sizePx, element.italic, element.bold, density,
                ) ?: continue
                val naturalWidth = result.size.width.toFloat()
                val naturalHeight = result.size.height.toFloat()
                if (naturalWidth <= 0f || naturalHeight <= 0f) continue
                val scaleY = (element.targetHeight / naturalHeight).let {
                    if (it.isFinite() && it > 0f) it else 1f
                }
                // 纵向拉长时稍微加粗横向比例，避免括号看起来太细。
                val scaleX = if (scaleY > 1.05f) 1f + (scaleY - 1f) * 0.22f else 1f
                val x = element.x + naturalWidth * (1f - scaleX) / 2f
                items.add(MathDrawItem.GlyphItem(result, x, element.top, scaleX, scaleY))
            }
        }
    }
    return items
}

// ------------------------------------------------------------------ 测量实现

/** Compose 端 [MathMeasure]：用 TextMeasurer 量文本，Serif 字体，变量斜体。 */
private class ComposeMathMeasure(
    private val measurer: TextMeasurer,
    private val density: Density,
) : MathMeasure {
    override fun measure(text: String, sizePx: Float, italic: Boolean, bold: Boolean): TextMetrics {
        if (text.isEmpty() || sizePx <= 0f) return TextMetrics.Zero
        val result = measureText(measurer, text, sizePx, italic, bold, density)
            ?: return TextMetrics.Zero
        val ascent = result.firstBaseline
        val height = result.size.height.toFloat()
        return TextMetrics(
            width = result.size.width.toFloat(),
            height = height,
            ascent = if (ascent.isFinite() && ascent >= 0f) ascent else height,
            descent = (height - ascent).let { if (it.isFinite() && it > 0f) it else 0f },
        )
    }
}

private fun measureText(
    measurer: TextMeasurer,
    text: String,
    sizePx: Float,
    italic: Boolean,
    bold: Boolean,
    density: Density,
): TextLayoutResult? {
    if (text.isEmpty() || sizePx <= 0f) return null
    return try {
        measurer.measure(
            text = text,
            style = textStyleFor(sizePx, italic, bold, density),
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            layoutDirection = LayoutDirection.Ltr,
        )
    } catch (t: Throwable) {
        null
    }
}

private fun textStyleFor(sizePx: Float, italic: Boolean, bold: Boolean, density: Density): TextStyle =
    TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = with(density) { sizePx.toSp() },
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )

private fun resolveFontSizePx(fontSize: TextUnit, density: Density): Float {
    val px = try {
        with(density) { fontSize.toPx() }
    } catch (t: Throwable) {
        Float.NaN
    }
    return if (px.isFinite() && px > 0f) px else 18f * density.density
}
