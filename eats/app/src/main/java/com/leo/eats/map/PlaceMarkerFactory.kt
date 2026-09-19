package com.leo.eats.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.toArgb
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceWithStats
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.concurrent.ConcurrentHashMap

/**
 * 地图 marker 工厂：类型 → 颜色圆点（白描边 + 中心白点），按类型缓存位图。
 */
object PlaceMarkerFactory {

    private val iconCache = ConcurrentHashMap<Int, Drawable>()

    fun icon(context: Context, argb: Int, selected: Boolean = false): Drawable {
        val key = argb.toInt() shl 1 or (if (selected) 1 else 0)
        return iconCache.getOrPut(key) {
            val density = context.resources.displayMetrics.density
            val base = if (selected) 38 else 28
            val size = (base * density).toInt().coerceAtLeast(12)
            val border = (if (selected) 3.5f else 2.5f) * density
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val r = size / 2f

            if (selected) {
                // 选中态：外圈描环 + 放大圆点（it-002 R1）
                canvas.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb; style = Paint.Style.STROKE; strokeWidth = border })
                canvas.drawCircle(r, r, r - border * 2.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
                canvas.drawCircle(r, r, r - border * 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb })
            } else {
                canvas.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
                canvas.drawCircle(r, r, r - border, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb })
                canvas.drawCircle(r, r, 3f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
            }
            BitmapDrawable(context.resources, bmp)
        }
    }

    /** 全量同步 marker（个人级规模，重建即可）；selectedId 对应 marker 放大描环 */
    fun sync(
        map: MapView,
        places: List<PlaceWithStats>,
        argbOf: (PlaceKind) -> Int,
        selectedId: String? = null,
        onClick: (PlaceWithStats) -> Unit,
    ) {
        map.overlays.removeAll { it is Marker }
        places.forEach { s ->
            val loc = s.place.location ?: return@forEach
            val selected = s.place.id == selectedId
            val marker = Marker(map).apply {
                position = GeoPoint(loc.lat, loc.lng)
                icon = icon(map.context, argbOf(s.place.kind), selected)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> onClick(s); true }
            }
            map.overlays.add(marker)
        }
        map.invalidate()
    }
}
