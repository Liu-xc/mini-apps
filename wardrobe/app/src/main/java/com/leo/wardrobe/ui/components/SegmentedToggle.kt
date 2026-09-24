package com.leo.wardrobe.ui.components

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * it-036 C11：全站连体分段控件（specs/05「筛选行渐隐与分段控件」）。
 *
 * 原样抽自 W9 衣橱回顾顶栏「今年/累计」的实现：等分 [count] 段、
 * [SegmentedButtonDefaults.itemShape] 连体圆角、M3 默认选中填充（容器填充 + 勾选图标）。
 * W9 与 W10「想买单品 / 心愿穿搭」两页共用，新页面做分段一律复用本组件，
 * 不再另起 FilterChip 双选形制（走查 C11：分段两套规格废止）。
 *
 * @param selectedIndex 当前选中段下标（0 起）
 * @param onSelect 点击第 n 段回调
 * @param count 总段数
 * @param label 各段文案槽（下标入参）
 */
@Composable
fun SegmentedToggleRow(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    count: Int,
    modifier: Modifier = Modifier,
    label: @Composable (Int) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        repeat(count) { i ->
            SegmentedButton(
                selected = i == selectedIndex,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = count),
                label = { label(i) },
            )
        }
    }
}
