package com.leo.lottery.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.lottery.ui.theme.LocalLotteryColors

/** 分段胶囊（玩法/单式复式切换）：选中 accent 填充，未选 hairline 描边。 */
@Composable
fun SegmentedPill(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalLotteryColors.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .border(1.dp, c.hairline, shape)
            .background(c.surface),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(if (selected) c.accent else Color.Transparent)
                    .clip(shape)
                    .then(
                        if (enabled) Modifier.clickableNoRipple { onSelect(i) } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = when {
                        !enabled -> c.inkFaint.copy(alpha = 0.5f)
                        selected -> c.accentContent
                        else -> c.inkFaint
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 数量步进器（复式主区个数）。 */
@Composable
fun Stepper(
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalLotteryColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Filled.Remove, "减少", enabled = value > min) { onChange(value - 1) }
        Text(
            text = value.toString(),
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            color = c.ink,
        )
        StepButton(Icons.Filled.Add, "增加", enabled = value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalLotteryColors.current
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        Icon(
            icon,
            contentDescription = desc,
            tint = if (enabled) c.ink else c.hairline,
        )
    }
}

/** 主按钮（福彩红渐变 + 金线，全宽）。 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalLotteryColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent,
            contentColor = c.accentContent,
            disabledContainerColor = c.accent.copy(alpha = 0.32f),
            disabledContentColor = c.accentContent.copy(alpha = 0.6f),
        ),
        border = BorderStroke(1.dp, c.gold.copy(alpha = 0.65f)),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

/** 次按钮（描边）。 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalLotteryColors.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, c.gold.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = c.ink,
            disabledContentColor = c.inkFaint.copy(alpha = 0.5f),
        ),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** 小圆角标签（演示数据、期号说明这类印刷小标签）。 */
@Composable
fun MiniChip(
    text: String,
    modifier: Modifier = Modifier,
    borderColor: Color = LocalLotteryColors.current.hairline,
    textColor: Color = LocalLotteryColors.current.inkFaint,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

/** 空态：图形 + 说明 + 行动按钮（DESIGN §5.8）。 */
@Composable
fun EmptyState(
    illustration: @Composable () -> Unit,
    text: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalLotteryColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        illustration()
        Spacer(Modifier.height(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = c.inkFaint,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        SecondaryButton(text = actionText, onClick = onAction)
    }
}
