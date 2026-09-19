package com.leo.eats.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import kotlin.random.Random

/**
 * 国内可达瓦片源（ADR-002 修订）：OSM 官方瓦片在网络层不可达（实测 2026-09-20），
 * 改用高德栅格瓦片（webrd，中文注记，四主机轮询）。
 * 注意：高德瓦片为 GCJ-02 坐标系，本应用坐标全部来自同一底图上的长按选点，
 * 自洽存储/回放；不做跨坐标系换算（MVP 范围外，见 ADR-002 后果）。
 */
object ChinaTileSource {

    private val hosts = listOf(
        "https://webrd01.is.autonavi.com/appmaptile",
        "https://webrd02.is.autonavi.com/appmaptile",
        "https://webrd03.is.autonavi.com/appmaptile",
        "https://webrd04.is.autonavi.com/appmaptile",
    )

    val AMap: OnlineTileSourceBase = object : OnlineTileSourceBase(
        /* aName = */ "AMapRoad",
        /* aZoomMinLevel = */ 3,
        /* aZoomMaxLevel = */ 19,
        /* aTileSizePixels = */ 256,
        /* aImageFileNameEnding = */ ".png",
        /* aBaseUrl = */ hosts.toTypedArray(),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val host = hosts[Random.nextInt(hosts.size)]
            return "${host}?lang=zh_cn&size=1&scale=1&style=8" +
                "&x=${MapTileIndex.getX(pMapTileIndex)}" +
                "&y=${MapTileIndex.getY(pMapTileIndex)}" +
                "&z=${MapTileIndex.getZoom(pMapTileIndex)}"
        }
    }
}
