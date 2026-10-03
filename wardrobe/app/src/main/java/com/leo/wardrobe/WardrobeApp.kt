package com.leo.wardrobe

import android.app.Application
import com.leo.wardrobe.data.prefs.BuiltinKeysSeeder
import com.leo.wardrobe.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WardrobeApp : Application() {

    /** 应用级 scope：超 UI 生命周期的启动期杂务（it-080 内置 Key 补缺） */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // it-080：体验包内置 Key 首启补缺当前 namespace；演示↔真实切换经进程重启，
        // 新 namespace 会在下一次 onCreate 再补一遍
        appScope.launch { BuiltinKeysSeeder.seed(container.apiKeyStore) }
    }
}
