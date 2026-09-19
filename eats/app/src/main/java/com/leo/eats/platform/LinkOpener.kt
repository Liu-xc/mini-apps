package com.leo.eats.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 链接跳转门面（ADR-009）：统一 ACTION_VIEW，系统 App Links 自动拉起
 * 美团/点评等目标 App；无处理组件时回调兜底。UI 不直接碰 Intent。
 */
class LinkOpener(private val context: Context) {

    fun open(url: String, onFail: () -> Unit) {
        val normalized = url.trim().let { if (it.startsWith("http", ignoreCase = true)) it else "https://$it" }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: ActivityNotFoundException) {
            onFail()
        } catch (_: SecurityException) {
            onFail()
        }
    }
}
