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
     * 只复制一张图片（it-002 主通道）：Prompt 已绘制在长图底部并写入 EXIF，
     * 生图 Agent 粘贴一张图即可。返回是否成功。
     */
    fun copyImage(image: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "$AUTHORITY", image)
        val clip = ClipData.newUri(context.contentResolver, "outfit", uri)
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
