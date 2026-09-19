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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.leo.eats.domain.model.GeoLoc
import com.leo.eats.map.MapController
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * 内嵌选点地图（US-01，ADR-005）：长按放 pin，确认带回经纬度；
 * 自做菜可直接清除位置。不依赖在线地理编码。
 *
 * 实现 notes：BottomSheet 内 AndroidView 收不到手势（it-001 实测），
 * 长按检测放在包装 FrameLayout 的 dispatchTouchEvent 层（纯 View 体系），
 * 命中后经 MapView.projection 换算经纬度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerSheet(
    initial: GeoLoc?,
    onConfirm: (GeoLoc?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf(initial) }
    val context = LocalContext.current

    // MapView 实例只在组合期创建一次，pin 变化经 LaunchedEffect 刷新标记
    val mapView = remember {
        MapController().create(context).apply {
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(initial?.lat ?: 31.23, initial?.lng ?: 121.47))
        }
    }

    LaunchedEffect(pin) {
        mapView.overlays.removeAll { it is Marker }
        pin?.let { p ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(p.lat, p.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                },
            )
        }
        mapView.invalidate()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(600.dp),
        ) {
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
                    .weight(1f),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
                        val handler = android.os.Handler(android.os.Looper.getMainLooper())
                        var downX = 0f
                        var downY = 0f
                        var pending = false
                        val longPress = Runnable {
                            pending = false
                            val gp = mapView.projection.fromPixels(downX.toInt(), downY.toInt())
                            pin = GeoLoc(gp.latitude, gp.longitude)
                        }
                        object : android.widget.FrameLayout(ctx) {
                            override fun dispatchTouchEvent(e: android.view.MotionEvent): Boolean {
                                when (e.actionMasked) {
                                    android.view.MotionEvent.ACTION_DOWN -> {
                                        downX = e.x; downY = e.y; pending = true
                                        handler.postDelayed(
                                            longPress,
                                            android.view.ViewConfiguration.getLongPressTimeout().toLong(),
                                        )
                                    }
                                    android.view.MotionEvent.ACTION_MOVE ->
                                        if (pending && (kotlin.math.abs(e.x - downX) > slop || kotlin.math.abs(e.y - downY) > slop)) {
                                            handler.removeCallbacks(longPress); pending = false
                                        }
                                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                        handler.removeCallbacks(longPress); pending = false
                                    }
                                }
                                return super.dispatchTouchEvent(e)
                            }
                        }.apply {
                            addView(
                                mapView,
                                android.widget.FrameLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                ),
                            )
                        }
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
                    modifier = Modifier.height(48.dp),
                ) { Text("确认选点") }
            }
        }
    }

    // MapView 生命周期跟随组合；宿主已 RESUMED 时立即补 onResume（瓦片线程启动）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }
}
