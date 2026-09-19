package com.leo.wardrobe.data.repo

import com.leo.wardrobe.domain.repository.ImageStore
import java.io.File

/** 记录删除调用的假图片仓库 */
class FakeImageStore : ImageStore {
    val deleted = mutableListOf<String>()
    val stored = mutableListOf<String>()

    override suspend fun importFromUri(uri: String): String? {
        stored += uri
        return "img_${stored.size}.webp"
    }

    override fun file(file: String): File? = null

    override suspend fun delete(file: String) {
        deleted += file
    }
}
