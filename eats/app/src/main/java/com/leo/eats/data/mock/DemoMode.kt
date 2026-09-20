package com.leo.eats.data.mock

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/**
 * 演示模式开关（it-006）：开启后 AppContainer 装配 Mock 仓库与 mock 图片目录，
 * 真实 eats.json 与 images/ 零接触。演示仓库不落盘，因此退出即还原。
 *
 * 开关在组合根构造时读取，切换必须重建容器——重启进程是唯一安全的重建方式。
 */
object DemoMode {
    private const val PREFS = "demo_mode"
    private const val KEY = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    /** 保存开关并重启进程使其生效（commit 同步落盘——exitProcess 前必须写完） */
    fun setAndRestart(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, enabled).commit()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(launch)
        exitProcess(0)
    }
}
