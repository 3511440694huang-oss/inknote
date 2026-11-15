package com.inknote.app

import android.app.Application
import com.inknote.app.data.SettingsStore
import com.inknote.app.data.VaultManager

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        services = AppServices(this)
        // 首次启动准备内置仓库与示例笔记（同步写入，两个小文件，开销可忽略）
        runCatching { services.vaults.current() }
    }

    companion object {
        lateinit var services: AppServices
            private set
    }
}

class AppServices(context: Application) {
    val settings = SettingsStore(context)
    val vaults = VaultManager(context, settings)
}
