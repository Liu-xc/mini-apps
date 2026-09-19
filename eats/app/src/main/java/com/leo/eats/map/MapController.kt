package com.leo.eats.map

import android.content.Context
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/**
 * osmdroid MapView 工厂（ADR-002 及其修订）：统一瓦片源（高德栅格，国内可达）、
 * 关闭缩放按钮、瓦片 DPI 缩放。全局配置（UserAgent/内部瓦片缓存）见 AppContainer.init。
 */
class MapController {

    fun create(context: Context): MapView = MapView(context).apply {
        setTileSource(ChinaTileSource.AMap)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        controller.setZoom(DEFAULT_ZOOM)
        controller.setCenter(GeoPoint(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LNG))
    }

    companion object {
        const val DEFAULT_ZOOM = 11.0
        const val DEFAULT_CENTER_LAT = 31.23
        const val DEFAULT_CENTER_LNG = 121.47
    }
}
