package com.inknote.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inknote.app.data.InkSettings

/**
 * 纸面五级灰。墨水屏只用黑白灰，不用任何强调色。
 * 反色主题是数学对偶（255 - c），保证深浅对比关系一致。
 */
data class InkPalette(
    val paper: Color,
    val mist: Color,
    val line: Color,
    val mid: Color,
    val deep: Color,
    val ink: Color,
    val inverse: Boolean,
) {
    companion object {
        val Paper = InkPalette(
            paper = Color(0xFFFFFFFF),
            mist = Color(0xFFF3F3F3),
            line = Color(0xFFB3B3B3),
            mid = Color(0xFF666666),
            deep = Color(0xFF222222),
            ink = Color(0xFF000000),
            inverse = false,
        )

        val Inverse = InkPalette(
            paper = Color(0xFF000000),
            mist = Color(0xFF101010),
            line = Color(0xFF4C4C4C),
            mid = Color(0xFF999999),
            deep = Color(0xFFDDDDDD),
            ink = Color(0xFFFFFFFF),
            inverse = true,
        )
    }
}

val LocalInkPalette = staticCompositionLocalOf { InkPalette.Paper }
val LocalInkSettings = staticCompositionLocalOf { InkSettings() }

/** 全局设计令牌：间距 / 线宽恒定，颜色随主题。 */
object Ink {
    val Paper: Color @Composable @ReadOnlyComposable get() = LocalInkPalette.current.paper
    val Mist: Color @Composable @ReadOnlyComposable get() = LocalInkPalette.current.mist
    val Line: Color @Composable @ReadOnlyComposable get() = LocalInkPalette.current.line
    val Mid: Color @Composable @ReadOnlyComposable get() = LocalInkPalette.current.mid
    val Deep: Color @Composable @ReadOnlyComposable get() = LocalInkPalette.current.deep
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalInkPalette.current.ink

    val PadPage = 18.dp
    val Touch = 48.dp
    val Hairline = 1.dp
    val StrongLine = 1.5.dp
}

/** 正文字体：宋体（衬线）更适合长时间阅读；黑体用于界面。 */
@Composable
fun inkBodyFont(settings: InkSettings = LocalInkSettings.current): FontFamily =
    if (settings.serif) FontFamily.Serif else FontFamily.SansSerif

@Composable
fun InkTheme(settings: InkSettings, content: @Composable () -> Unit) {
    val palette = if (settings.inverse) InkPalette.Inverse else InkPalette.Paper
    val bodyFamily = if (settings.serif) FontFamily.Serif else FontFamily.SansSerif
    val uiFamily = FontFamily.SansSerif
    val bodySize = settings.fontSize.sp
    val bodyLine = (settings.fontSize * settings.lineFactor).sp

    val typography = remember(settings.fontSize, settings.lineFactor, settings.serif) {
        Typography(
            titleLarge = TextStyle(
                fontSize = (settings.fontSize + 3).sp,
                lineHeight = ((settings.fontSize + 3) * 1.3f).sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = uiFamily,
            ),
            bodyLarge = TextStyle(fontSize = bodySize, lineHeight = bodyLine, fontFamily = bodyFamily),
            bodyMedium = TextStyle(fontSize = (settings.fontSize - 2).sp, lineHeight = bodyLine, fontFamily = bodyFamily),
            labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontFamily = uiFamily),
            labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontFamily = uiFamily),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontFamily = uiFamily),
        )
    }

    val scheme = remember(palette) {
        lightColorScheme(
            primary = palette.ink,
            onPrimary = palette.paper,
            background = palette.paper,
            onBackground = palette.ink,
            surface = palette.paper,
            onSurface = palette.ink,
            surfaceVariant = palette.mist,
            onSurfaceVariant = palette.deep,
            outline = palette.line,
            error = palette.ink,
            onError = palette.paper,
        )
    }

    CompositionLocalProvider(
        LocalInkPalette provides palette,
        LocalInkSettings provides settings,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
