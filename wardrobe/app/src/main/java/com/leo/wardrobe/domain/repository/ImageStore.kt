package com.leo.wardrobe.domain.repository

import java.io.File

/**
 * 图片文件存取（URI 抽象为字符串，保持 domain 可 JVM 单测）。
 * 实现负责 EXIF 旋转、压缩为 WebP（最长边 1440、质量 82，specs/03-data-model.md）。
 * 位图解码在实现类上（data 层），domain 不引用 Android 类型。
 */
interface ImageStore {
    /** 从 content URI 导入并压缩存储，返回存储文件名；失败返回 null */
    suspend fun importFromUri(uri: String): String?

    fun file(file: String): File?

    suspend fun delete(file: String)
}
