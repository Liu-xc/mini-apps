package com.leo.darkroom.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leo.darkroom.DarkroomViewModel
import com.leo.darkroom.DarkroomViewModel.UiState
import com.leo.darkroom.develop.DevelopSpeed
import com.leo.darkroom.ui.pageInsets
import com.leo.darkroom.ui.theme.editorialColors

/**
 * W4 设置页：显影速度档、甩一甩、水印默认值、关于。
 */
@Composable
fun SettingsScreen(vm: DarkroomViewModel, state: UiState) {
    val colors = editorialColors()

    Column(
        Modifier
            .fillMaxSize()
            .pageInsets()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::closeSettings, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = colors.ink)
            }
            Spacer(Modifier.width(4.dp))
            Text("设置", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        }

        Spacer(Modifier.height(16.dp))

        // 显影速度
        SectionCard {
            Text(
                "显影速度",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            DevelopSpeed.entries.forEach { speed ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { vm.setSpeed(speed) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.speed == speed,
                        onClick = { vm.setSpeed(speed) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = colors.accent,
                            unselectedColor = colors.inkFaint,
                        ),
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(speed.label, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                    }
                    Text(
                        "${speed.durationMs / 1000}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkFaint,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 互动
        SectionCard {
            Text(
                "互动",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ToggleRow(
                title = "甩一甩加速",
                subtitle = "显影中甩动手机推进进度（真机有效）",
                checked = state.shakeEnabled,
                onCheckedChange = vm::setShakeEnabled,
            )
            HorizontalDivider(color = colors.hairline)
            ToggleRow(
                title = "水印默认开启",
                subtitle = "新会话成片带「显影 DARKROOM」卡脚",
                checked = state.spec.showWatermark,
                onCheckedChange = vm::setWatermarkDefault,
            )
        }

        Spacer(Modifier.height(14.dp))

        // 关于
        SectionCard {
            Text("关于", style = MaterialTheme.typography.titleMedium, color = colors.ink, modifier = Modifier.padding(bottom = 6.dp))
            Text(
                "显影 DARKROOM 0.2.4",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "全程离线，照片与视频只在本机处理；导出内容写入相册「显影」相簿。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkFaint,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = editorialColors()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = editorialColors()
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.accent,
                checkedThumbColor = colors.surface,
                uncheckedTrackColor = colors.hairline,
                uncheckedThumbColor = colors.surface,
            ),
        )
    }
}
