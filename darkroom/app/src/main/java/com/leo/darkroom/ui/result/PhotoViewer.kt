package com.leo.darkroom.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 全屏大图查看器（it-009 建立，it-011 收尾抽共享）：暗底 Fit 全屏，
 * 点按任意处/返回键关闭；可选底部动作槽（沉浸相册带「存图片/编辑」，成片页不带）。
 */
@Composable
fun PhotoViewer(
    image: ImageBitmap,
    contentDescription: String,
    onClose: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF151515))
                .clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 28.dp),
            )
            if (actions != null) {
                androidx.compose.foundation.layout.Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 46.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    actions()
                }
            }
            Text(
                if (actions != null) "点按空白处关闭" else "点按任意处关闭",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x99FBFBFA),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            )
        }
    }
}
