package com.inknote.app

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.inknote.app.data.InkSettings
import com.inknote.app.data.VaultEntry
import com.inknote.app.ui.HomeScreen
import com.inknote.app.ui.NoteScreen
import com.inknote.app.ui.SearchQuery
import com.inknote.app.ui.SettingsScreen
import com.inknote.app.ui.theme.Ink
import com.inknote.app.ui.theme.InkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(0xFFFFFFFF.toInt()))
        setContent {
            val settings by App.services.settings.state.collectAsState()
            InkTheme(settings) {
                AppRoot(settings)
            }
            LaunchedEffect(settings.inverse) { applySystemBars(settings.inverse) }
        }
    }

    override fun onResume() {
        super.onResume()
        val s = App.services.settings.current
        applySystemBars(s.inverse)
    }

    /** 状态栏 / 导航栏跟随纸面配色，浅色主题用深色图标。 */
    private fun applySystemBars(inverse: Boolean) {
        val color = if (inverse) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        window.statusBarColor = color
        window.navigationBarColor = color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        var flags = 0
        if (!inverse) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
        window.decorView.systemUiVisibility = flags
    }
}

private const val ROUTE_HOME = "home"
private const val ROUTE_NOTE = "note"
private const val ROUTE_SETTINGS = "settings"

@Composable
private fun AppRoot(settings: InkSettings) {
    val settingsStore = App.services.settings
    val vaults = App.services.vaults

    var route by rememberSaveable { mutableStateOf(ROUTE_HOME) }
    var noteId by rememberSaveable { mutableStateOf("") }
    var noteName by rememberSaveable { mutableStateOf("") }
    var refreshToken by remember { mutableIntStateOf(0) }
    var pendingSearch by remember { mutableStateOf<SearchQuery?>(null) }
    // 跨笔记跳转时的锚点（[[笔记#小节]] / [文字](笔记.md#小节)）
    var noteAnchor by rememberSaveable { mutableStateOf<String?>(null) }

    fun openNote(vaultKeyVault: com.inknote.app.data.Vault, entry: VaultEntry) {
        settingsStore.addRecent(entry.id, entry.name, vaults.vaultKey)
        noteId = entry.id
        noteName = entry.name
        noteAnchor = null
        route = ROUTE_NOTE
    }

    Box(Modifier.fillMaxSize().background(Ink.Paper)) {
        // 主页始终保留在组合树里，进出笔记/设置不会丢失文件夹位置
        key(settings.vaultUri) {
            HomeScreen(
                vaults = vaults,
                settingsStore = settingsStore,
                settings = settings,
                refreshToken = refreshToken,
                pendingSearch = pendingSearch,
                onPendingSearchConsumed = { pendingSearch = null },
                onOpenNote = { vault, entry -> openNote(vault, entry) },
                onOpenSettings = { route = ROUTE_SETTINGS },
            )
        }

        if (route == ROUTE_NOTE) {
            val vault = remember(settings.vaultUri) { vaults.current() }
            val entry = remember(noteId, noteName) { VaultEntry(noteId, noteName, false) }
            NoteScreen(
                vault = vault,
                entry = entry,
                settingsStore = settingsStore,
                settings = settings,
                onBack = {
                    route = ROUTE_HOME
                    refreshToken++
                },
                onOpenNote = { e ->
                    settingsStore.addRecent(e.id, e.name, vaults.vaultKey)
                    noteId = e.id
                    noteName = e.name
                    noteAnchor = null
                },
                // 链接里带锚点时切换到目标笔记并滚到对应小节
                onOpenNoteAt = { e, anchor ->
                    settingsStore.addRecent(e.id, e.name, vaults.vaultKey)
                    noteId = e.id
                    noteName = e.name
                    noteAnchor = anchor
                },
                onRenamed = { e ->
                    noteId = e.id
                    noteName = e.name
                    settingsStore.addRecent(e.id, e.name, vaults.vaultKey)
                },
                onDeleted = {
                    route = ROUTE_HOME
                    refreshToken++
                },
                onSearch = { q ->
                    pendingSearch = q
                    route = ROUTE_HOME
                    refreshToken++
                },
                initialAnchor = noteAnchor,
                onAnchorConsumed = { noteAnchor = null },
            )
        }

        if (route == ROUTE_SETTINGS) {
            SettingsScreen(
                vaults = vaults,
                settingsStore = settingsStore,
                settings = settings,
                onBack = {
                    route = ROUTE_HOME
                    refreshToken++
                },
            )
        }
    }
}
