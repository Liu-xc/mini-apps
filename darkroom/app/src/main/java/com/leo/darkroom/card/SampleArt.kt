package com.leo.darkroom.card

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.leo.darkroom.R

/** Bundled offline photo samples for the no-permission first run. */
object SampleArt {
    val titles = listOf("暮色山脊", "海边余晖", "雨后街灯")

    private val images = intArrayOf(
        R.drawable.sample_mountain,
        R.drawable.sample_coast,
        R.drawable.sample_city,
    )

    fun load(context: Context, index: Int, maxDimension: Int = 1600): Bitmap {
        val resource = images[index.mod(images.size)]
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resource, bounds)
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension) sampleSize *= 2
        return BitmapFactory.decodeResource(
            context.resources,
            resource,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: error("Bundled sample photo could not be decoded: $resource")
    }
}
