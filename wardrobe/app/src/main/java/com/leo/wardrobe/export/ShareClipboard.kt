package com.leo.wardrobe.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 导出双通道门面（ADR-007）：剪贴板优先（文本 + 图片 content URI），
 * 系统分享兜底（安卓部分应用不支持粘贴剪贴板图片）。
 */
class ShareClipboard(private val context: Context) {

    fun copyText(text: String) {
        clipboard().setPrimaryClip(ClipData.newPlainText("wardrobe", text))
    }

    /**
     * 图片 + 文本同时进剪贴板。
     * 图片以 content URI 挂载（FLAG_GRANT_READ_URI_PERMISSION），
     * 文本作为附加 Item——支持文本粘贴的应用拿到文案，支持图片的拿到图。
     * 返回是否成功（个别 ROM 可能拒绝）。
     */
    fun copyImageAndText(image: File, text: String): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "$AUTHORITY", image)
        val clip = ClipData.newUri(context.contentResolver, "outfit", uri).apply {
            addItem(ClipData.Item(text))
        }
        clipboard().setPrimaryClip(clip)
        true
    }.getOrDefault(false)

    /** 分享兜底：ACTION_SEND 图片直发目标应用 */
    fun shareImage(image: File) {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, image)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享穿搭图"))
    }

    private fun clipboard(): ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private companion object {
        const val AUTHORITY = "com.leo.wardrobe.fileprovider"
    }
}
