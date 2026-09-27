package com.leo.darkroom.export

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * 成片落库与分享（it-001 US-5/US-6）：
 * 图/视频写入 MediaStore（Pictures/显影、Movies/显影，API 29+ 分区存储零权限），
 * 分享直出 content:// URI（FLAG_GRANT_READ）。
 */
object ShareHelper {

    private const val ALBUM_DIR = "显影"

    fun saveImage(context: Context, bitmap: Bitmap, name: String): Uri {
        val resolver: ContentResolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$ALBUM_DIR",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        } ?: error("openOutputStream failed")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    fun saveVideo(context: Context, file: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/$ALBUM_DIR",
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: error("openOutputStream failed")
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    fun share(context: Context, uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "分享到").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    /** 分享位图：先落库拿到长期 URI，再分享 */
    fun saveAndShareImage(context: Context, bitmap: Bitmap, name: String) {
        share(context, saveImage(context, bitmap, name), "image/jpeg")
    }

    /** FileProvider 暂存 URI（拍照回传用） */
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
