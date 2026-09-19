package com.leo.eats.ui.mapview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceWithStats
import com.leo.eats.domain.model.statsOfAll
import com.leo.eats.map.MapController
import com.leo.eats.map.PlaceMarkerFactory
import com.leo.eats.ui.AppViewModel
import com.leo.eats.ui.components.KindChip
import com.leo.eats.ui.components.RatingStars
import com.leo.eats.ui.components.RelativeTimeText
import com.leo.eats.ui.theme.EatsMotion
import com.leo.eats.ui.theme.kindColor
import com.leo.eats.ui.theme.menuColors
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

/**
 * W2 地图总览（US-04）：有坐标的食堂按类型着色 marker，点 marker 滑出摘要卡，
 * 无坐标条目走「N 条未上地图」入口跳列表。
 */
@Composable
fun MapScreen(
    vm: AppViewModel,
    onOpenDetail: (String) -> Unit,
    onShowNoLocation: () -> Unit,
) {
    val data by vm.data.collectAsState()
    val stats = remember(data) { data.statsOfAll() }
    val located = remember(stats) { stats.filter { it.place.located } }
    val unlocatedCount = stats.size - located.size

    var selected by remember { mutableStateOf<PlaceWithStats?>(null) }
    var map by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }
    var cameraInitialized by remember { mutableStateOf(false) }

    // 类型语义色在组合期取一次（marker 回调里不再调 @Composable）
    val menu = menuColors()
    val kindArgb = PlaceKind.entries.associateWith { kind ->
        when (kind) {
            PlaceKind.RESTAURANT -> menu.restaurant
            PlaceKind.TAKEOUT -> menu.takeout
            PlaceKind.HOME -> menu.homeCook
        }.toArgb()
    }

    // marker 随数据全量同步（个人级规模），选中态放大描环（it-002 R1），首次进入框住所有点
    LaunchedEffect(located, map, selected) {
        val m = map ?: return@LaunchedEffect
        val selectedId = selected?.place?.id
        PlaceMarkerFactory.sync(
            map = m,
            places = located,
            argbOf = { kind -> kindArgb.getValue(kind) },
            selectedId = selectedId,
            onClick = { s -> selected = s },
        )
        if (!cameraInitialized && located.isNotEmpty()) {
            cameraInitialized = true
            fitTo(m, located)
        }
    }

    // W5「在地图上看」聚焦意图
    val pendingFocus by vm.pendingMapFocus.collectAsState()
    LaunchedEffect(pendingFocus, map) {
        val id = pendingFocus ?: return@LaunchedEffect
        val target = stats.firstOrNull { it.place.id == id }?.takeIf { it.place.located } ?: run {
            vm.consumeMapFocus(); return@LaunchedEffect
        }
        val loc = target.place.location ?: return@LaunchedEffect
        map?.let { m ->
            m.controller.animateTo(GeoPoint(loc.lat, loc.lng))
            runCatching { (m.controller as org.osmdroid.views.MapController).zoomTo(17.5) }
            selected = target
        }
        vm.consumeMapFocus()    }

    // MapView 生命周期跟随组合；宿主已 RESUMED 时立即补 onResume（瓦片线程启动）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(map, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map?.onResume()
                Lifecycle.Event.ON_PAUSE -> map?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            map?.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map?.onPause()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapController().create(ctx).also { m -> map = m }
            },
        )

        if (unlocatedCount > 0) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = menuColors().surface,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .clickable { onShowNoLocation() },
            ) {
                Text(
                    "⌖ $unlocatedCount 条未上地图（自做等）→",
                    style = MaterialTheme.typography.labelMedium,
                    color = menuColors().ink,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically(EatsMotion.bouncy()) { it } + fadeIn(),
            exit = slideOutVertically(EatsMotion.smooth()) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            selected?.let { s ->
                PlaceSummaryCard(
                    s = s,
                    onClick = { onOpenDetail(s.place.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}

/** 初始视野：单点直接 17 级，多点框住全部 marker（zoomToSpan + 中心点） */
private fun fitTo(m: org.osmdroid.views.MapView, located: List<PlaceWithStats>) {
    val points = located.mapNotNull { s ->
        s.place.location?.let { GeoPoint(it.lat, it.lng) }
    }
    if (points.isEmpty()) return
    if (points.size == 1) {
        m.controller.setCenter(points[0])
        runCatching { (m.controller as org.osmdroid.views.MapController).zoomTo(17.0) }
        return
    }
    val box = BoundingBox.fromGeoPoints(points)
    m.controller.setCenter(box.center)
    runCatching {
        (m.controller as org.osmdroid.views.MapController)
            .zoomToSpan(box.latitudeSpan * 1.25, box.longitudeSpan * 1.25)
    }
}

@Composable
private fun PlaceSummaryCard(s: PlaceWithStats, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = menuColors().surface,
        shadowElevation = 6.dp,
        modifier = modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.place.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = menuColors().ink,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KindChip(s.place.kind, compact = true)
                    Spacer(Modifier.width(8.dp))
                    RatingStars(rating = s.place.rating, size = 14.dp)
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    RelativeTimeText(at = s.lastVisitAt)
                    Text(
                        " · 共 ${s.visitCount} 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = menuColors().inkFaint,
                    )
                }
            }
            Text("详情 →", style = MaterialTheme.typography.labelLarge, color = menuColors().accent)
        }
    }
}
