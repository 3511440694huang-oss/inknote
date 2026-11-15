package com.inknote.app

import com.inknote.app.data.FONT_SIZE_DEFAULT
import com.inknote.app.data.FONT_SIZE_MAX
import com.inknote.app.data.FONT_SIZE_MIN
import com.inknote.app.data.InkSettings
import com.inknote.app.data.clampFontSize
import com.inknote.app.data.stepFontSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.2.0：字号从 4 个固定档位改为可自定义大小，这里覆盖夹紧与步进的纯逻辑。 */
class SettingsTest {

    @Test
    fun `默认字号在可调范围内`() {
        assertTrue(FONT_SIZE_DEFAULT in FONT_SIZE_MIN..FONT_SIZE_MAX)
        assertEquals(FONT_SIZE_DEFAULT, InkSettings().fontSize)
    }

    @Test
    fun `范围本身合理`() {
        assertTrue(FONT_SIZE_MIN >= 10)
        assertTrue(FONT_SIZE_MAX >= 36)
        assertTrue(FONT_SIZE_MIN < FONT_SIZE_DEFAULT)
        assertTrue(FONT_SIZE_DEFAULT < FONT_SIZE_MAX)
    }

    @Test
    fun `夹紧下界`() {
        assertEquals(FONT_SIZE_MIN, clampFontSize(FONT_SIZE_MIN - 100))
        assertEquals(FONT_SIZE_MIN, clampFontSize(0))
        assertEquals(FONT_SIZE_MIN, clampFontSize(-7))
    }

    @Test
    fun `夹紧上界`() {
        assertEquals(FONT_SIZE_MAX, clampFontSize(FONT_SIZE_MAX + 100))
        assertEquals(FONT_SIZE_MAX, clampFontSize(999))
    }

    @Test
    fun `范围内的值原样保留`() {
        for (v in FONT_SIZE_MIN..FONT_SIZE_MAX) {
            assertEquals(v, clampFontSize(v))
        }
    }

    @Test
    fun `步进加一减一`() {
        assertEquals(FONT_SIZE_DEFAULT + 1, stepFontSize(FONT_SIZE_DEFAULT, 1))
        assertEquals(FONT_SIZE_DEFAULT - 1, stepFontSize(FONT_SIZE_DEFAULT, -1))
        assertEquals(FONT_SIZE_DEFAULT, stepFontSize(FONT_SIZE_DEFAULT, 0))
    }

    @Test
    fun `边界上继续步进不会越界`() {
        assertEquals(FONT_SIZE_MAX, stepFontSize(FONT_SIZE_MAX, 1))
        assertEquals(FONT_SIZE_MIN, stepFontSize(FONT_SIZE_MIN, -1))
        assertEquals(FONT_SIZE_MAX, stepFontSize(FONT_SIZE_MAX, 50))
        assertEquals(FONT_SIZE_MIN, stepFontSize(FONT_SIZE_MIN, -50))
    }

    @Test
    fun `逐步走到上界再走回来`() {
        var size = FONT_SIZE_DEFAULT
        var guard = 0
        while (size < FONT_SIZE_MAX && guard++ < 1000) size = stepFontSize(size, 1)
        assertEquals(FONT_SIZE_MAX, size)
        guard = 0
        while (size > FONT_SIZE_MIN && guard++ < 1000) size = stepFontSize(size, -1)
        assertEquals(FONT_SIZE_MIN, size)
    }

    @Test
    fun `行高像素随字号与行距线性变化`() {
        val s = InkSettings(fontSize = 20, lineFactor = 1.5f)
        assertEquals(60f, s.lineHeightPx(2f), 0.001f)
        assertEquals(120f, s.copy(fontSize = 40).lineHeightPx(2f), 0.001f)
        assertEquals(80f, s.copy(lineFactor = 2f).lineHeightPx(2f), 0.001f)
    }

    @Test
    fun `自定义字号设置能带进数据类`() {
        // 12–40 之间的任意整数都应可用（不是只有 4 个档位）
        for (v in FONT_SIZE_MIN..FONT_SIZE_MAX) {
            assertEquals(v, InkSettings(fontSize = clampFontSize(v)).fontSize)
        }
    }
}
