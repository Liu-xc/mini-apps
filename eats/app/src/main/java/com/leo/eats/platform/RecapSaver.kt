package com.leo.eats.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/**
 * 年度食光长图出口（it-007）：存相册走 MediaStore Pictures/Eats（API 29+ 免权限，
 * 与 wardrobe it-014 同策略），旧系统返回 FallbackShare 由调用方引导分享；
 * 分享经 FileProvider（cache/share/）。
 */
class RecapSaver(private val context: Context) {

    /** @return 成功时给出提示文案；null 表示当前系统不支持直接存相册（引导分享） */
    suspend fun saveToGallery(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        runCatching {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "eats_recap_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Eats")
                }
            }
            val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri ?: return@runCatching null
            val ok = context.contentResolver.openOutputStream(uri)?.use { out: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            } ?: false
            if (ok) "已存相册 Pictures/Eats ✓" else null
        }.getOrNull()
    }

    /** 组装系统分享 intent（调用方 startActivity）；临时文件放 cache/share/（file_paths.xml） */
    fun shareIntent(bitmap: Bitmap): Intent = withContextUIFree(bitmap)

    private fun withContextUIFree(bitmap: Bitmap): Intent {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "eats_recap_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "分享年度食光")
    }

    private companion object {
        const val JPEG_QUALITY = 90
    }
}
