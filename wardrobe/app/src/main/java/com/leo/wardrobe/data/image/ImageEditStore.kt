package com.leo.wardrobe.data.image

import android.graphics.Bitmap
import com.leo.libs.cutout.CutoutEngine
import java.io.File

/**
 * 图片加工能力（it-021 自 ImageFileStore 具体类收编为接口）：
 * 抠图/解码/导出目录属 Android 图形能力，不进 domain 的 [com.leo.wardrobe.domain.repository.ImageStore]
 * （domain 不可引用 Bitmap）；导出合成、长图渲染、去背景桥按本接口依赖。
 */
interface ImageEditStore {
    /** 去背景（it-016 US-15）：生成抠图版新文件返回文件名；失败返回 null，原图不动 */
    suspend fun cutoutTo(srcFile: String, engine: CutoutEngine): String?

    /** 读取存储位图（合成图/长图用；失败返回 null） */
    suspend fun decode(file: String): Bitmap?

    /** 合成图等导出临时文件目录（FileProvider export 路径） */
    fun exportDir(): File
}
