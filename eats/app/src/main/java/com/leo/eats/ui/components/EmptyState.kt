package com.leo.eats.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.leo.eats.ui.theme.menuColors

/**
 * 空态（specs/05 动效 7）：3D 插画（thiings.co，README 署名）或 emoji + 标题 + 提示 + 可选行动按钮。
 * imageRes 优先；未提供时回退 emoji。
 */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    @DrawableRes imageRes: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (imageRes != null) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(120.dp),
            )
        } else {
            Text(emoji, style = MaterialTheme.typography.displayLarge)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = menuColors().ink)
        Spacer(Modifier.height(4.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = menuColors().inkFaint,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
