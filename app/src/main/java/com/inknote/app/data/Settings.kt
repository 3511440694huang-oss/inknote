package com.inknote.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/* 正文字号的可调范围（sp）。1.2.0 起字号可以逐 1sp 自定义，不再是 4 个固定档位。 */
const val FONT_SIZE_MIN = 12
const val FONT_SIZE_MAX = 40
const val FONT_SIZE_DEFAULT = 20

/** 把字号夹到可用范围内（读设置、输入对话框、外部恢复都用它）。 */
fun clampFontSize(value: Int): Int = value.coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX)

/** 字号加减一步，越界时停在边界上（± 按钮的纯逻辑，便于单测）。 */
fun stepFontSize(current: Int, delta: Int): Int = clampFontSize(current + delta)

/** 最近打开的一条笔记（跨仓库用 vaultKey 区分） */
data class RecentNote(
    val id: String,
    val name: String,
    val vaultKey: String,
)

/**
 * 墨笺的全部可调设置。默认值即「墨水屏推荐值」：
 * 宋体、20sp（可在 12–40sp 之间逐 1sp 自定义）、1.5 行距、
 * 右侧翻页按钮、每次翻页重叠 1 行。
 */
data class InkSettings(
    val fontSize: Int = FONT_SIZE_DEFAULT,
    val lineFactor: Float = 1.5f,
    val serif: Boolean = true,
    val margin: Int = 20,
    val overlapLines: Int = 1,
    val pageButtons: Boolean = true,
    val pageButtonsLeft: Boolean = false,
    val startInReadMode: Boolean = false,
    val inverse: Boolean = false,
    val sortByModified: Boolean = false,
    val vaultUri: String? = null,
    val vaultLabel: String = "内置仓库",
    val recent: List<RecentNote> = emptyList(),
) {
    /** 行高等大小工具 */
    fun lineHeightPx(density: Float): Float = fontSize * lineFactor * density
}

/** SharedPreferences + StateFlow 的极简设置仓库。 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("inknote", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<InkSettings> = _state.asStateFlow()

    val current: InkSettings get() = _state.value

    fun update(transform: (InkSettings) -> InkSettings) {
        val next = transform(_state.value)
        persist(next)
        _state.value = next
    }

    fun addRecent(id: String, name: String, vaultKey: String) {
        update { s ->
            val item = RecentNote(id, name, vaultKey)
            s.copy(recent = (listOf(item) + s.recent.filterNot { it.id == id && it.vaultKey == vaultKey }).take(15))
        }
    }

    fun clearRecent() = update { it.copy(recent = emptyList()) }

    fun resetDisplay() = update {
        it.copy(fontSize = FONT_SIZE_DEFAULT, lineFactor = 1.5f, serif = true, margin = 20, overlapLines = 1,
            pageButtons = true, pageButtonsLeft = false, inverse = false)
    }

    private fun load(): InkSettings {
        val recent = runCatching {
            val raw = prefs.getString(KEY_RECENT, null) ?: return@runCatching emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val name = o.optString("name")
                if (id.isBlank() || name.isBlank()) null
                else RecentNote(id, name, o.optString("vault"))
            }
        }.getOrDefault(emptyList())
        return InkSettings(
            fontSize = clampFontSize(prefs.getInt("fontSize", FONT_SIZE_DEFAULT)),
            lineFactor = prefs.getFloat("lineFactor", 1.5f),
            serif = prefs.getBoolean("serif", true),
            margin = prefs.getInt("margin", 20),
            overlapLines = prefs.getInt("overlapLines", 1),
            pageButtons = prefs.getBoolean("pageButtons", true),
            pageButtonsLeft = prefs.getBoolean("pageButtonsLeft", false),
            startInReadMode = prefs.getBoolean("startInReadMode", false),
            inverse = prefs.getBoolean("inverse", false),
            sortByModified = prefs.getBoolean("sortByModified", false),
            vaultUri = prefs.getString("vaultUri", null),
            vaultLabel = prefs.getString("vaultLabel", "内置仓库") ?: "内置仓库",
            recent = recent,
        )
    }

    private fun persist(s: InkSettings) {
        val recentJson = JSONArray().apply {
            s.recent.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("name", r.name)
                    put("vault", r.vaultKey)
                })
            }
        }.toString()
        prefs.edit()
            .putInt("fontSize", s.fontSize)
            .putFloat("lineFactor", s.lineFactor)
            .putBoolean("serif", s.serif)
            .putInt("margin", s.margin)
            .putInt("overlapLines", s.overlapLines)
            .putBoolean("pageButtons", s.pageButtons)
            .putBoolean("pageButtonsLeft", s.pageButtonsLeft)
            .putBoolean("startInReadMode", s.startInReadMode)
            .putBoolean("inverse", s.inverse)
            .putBoolean("sortByModified", s.sortByModified)
            .putString("vaultUri", s.vaultUri)
            .putString("vaultLabel", s.vaultLabel)
            .putString(KEY_RECENT, recentJson)
            .apply()
    }

    companion object {
        private const val KEY_RECENT = "recent"
    }
}
