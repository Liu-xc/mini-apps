package com.leo.wardrobe.data.gen

import java.io.File

/** Export composer returns an absolute file; ordinary image refs are names in ImageStore. */
internal fun resolveImageSourceFile(fileName: String, mediaFile: (String) -> File?): File? {
    val source = File(fileName)
    return if (source.isAbsolute) source else mediaFile(fileName)
}
