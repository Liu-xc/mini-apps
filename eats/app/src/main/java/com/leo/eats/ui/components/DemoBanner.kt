package com.leo.eats.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 演示模式常驻横幅（it-006）：演示期间全局可见，明确当前数据非真实；
 * 点按退出（重启进程回到真实数据源）。
 */
@Composable
fun DemoBanner(onExit: () -> Unit) {
    Surface(
        onClick = onExit,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        // Scaffold topBar 不自动避开状态栏（edge-to-edge），缺这层会被盖住无法点击
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Rounded.Science, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "演示数据中 · 增删不保存，点按退出",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
