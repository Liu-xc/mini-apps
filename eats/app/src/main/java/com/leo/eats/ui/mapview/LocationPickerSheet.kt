package com.leo.eats.ui.mapview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.leo.eats.domain.model.GeoLoc
import com.leo.eats.map.MapController
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * 内嵌选点地图（US-01，ADR-005）：长按放 pin，确认带回经纬度；
 * 自做菜可直接清除位置。不依赖在线地理编码。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerSheet(
    initial: GeoLoc?,
    onConfirm: (GeoLoc?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf(initial) }
    var map by remember { mutableStateOf<MapView?>(null) }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("选择位置", style = MaterialTheme.typography.titleLarge)
            Text(
                "长按地图放置标记",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapController().create(ctx).apply {
                            controller.setZoom(16.0)
                            controller.setCenter(GeoPoint(initial?.lat ?: 31.23, initial?.lng ?: 121.47))
                            overlays.add(
                                MapEventsOverlay(object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                                    override fun longPressHelper(p: GeoPoint?): Boolean {
                                        pin = p?.let { GeoLoc(it.latitude, it.longitude) }
                                        return true
                                    }
                                }),
                            )
                            map = this
                        }
                    },
                    update = { m ->
                        val target = pin
                        m.overlays.removeAll { it is Marker }
                        if (target != null) {
                            m.overlays.add(
                                Marker(m).apply {
                                    position = GeoPoint(target.lat, target.lng)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                },
                            )
                        }
                        m.invalidate()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(bottom = 24.dp)) {
                if (initial != null) {
                    TextButton(onClick = { onConfirm(null) }) { Text("清除位置") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { pin?.let(onConfirm) },
                    enabled = pin != null,
                ) { Text("确认选点") }
            }
        }
    }

    // MapView 生命周期跟随组合（onResume/onPause），离开时释放
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map?.onPause()
            map?.onDetach()
        }
    }
}
