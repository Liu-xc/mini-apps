package com.leo.wardrobe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leo.wardrobe.ui.theme.editorialColors
import androidx.compose.foundation.layout.padding

/**
 * it-077 十三次修订：全屏工作台面板（替代 ModalBottomSheet 承载预览/生成长表单）。
 * 动机（Leo 反馈×10~11）：92% 屏高的工作流塞进 BottomSheet 后，嵌套滚动接交/双 sheet
 * 接力动画/自管高度上限反复出现「分层·不跟手·抖动」；全屏化后内容区高度恒等于屏幕、
 * 钉底动作栏由容器结构保证，不存在撑满歧义；关闭走顶栏 ✕ 与系统返回，无拖拽手势语义。
 * dismissGuard=true 时拦截关闭（生成进行中）。
 */
@Composable
fun FullscreenSheet(
    title: String,
    onDismiss: () -> Unit,
    dismissGuard: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!dismissGuard) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = editorialColors().ink,
                    modifier = Modifier.weight(1f).padding(start = 20.dp),
                )
                IconButton(onClick = onDismiss, enabled = !dismissGuard) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = editorialColors().ink,
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                content = content,
            )
        }
    }
}
