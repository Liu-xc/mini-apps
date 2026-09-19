package com.leo.eats.data

import com.leo.eats.domain.repository.ImageStore
import java.io.File

/** 测试用假图片仓：记录删除调用，不落盘 */
class FakeImageStore : ImageStore {
    val deleted = mutableListOf<String>()

    override suspend fun importFromUri(uri: String): String? = uri.substringAfterLast('/')

    override fun file(file: String): File? = null

    override suspend fun delete(file: String) {
        deleted += file
    }
}
