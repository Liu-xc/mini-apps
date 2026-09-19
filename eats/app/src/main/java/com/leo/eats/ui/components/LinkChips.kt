package com.leo.eats.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.leo.eats.domain.model.LinkSource
import com.leo.eats.domain.model.PlaceLink
import com.leo.eats.ui.theme.menuColors

/** 来源徽标色：美团黄 / 点评橙 / 中性 */
private val LinkSource.dotColor: Color
    get() = when (this) {
        LinkSource.MEITUAN -> Color(0xFFE8A33D)
        LinkSource.DIANPING -> Color(0xFFE86A33)
        LinkSource.OTHER -> Color(0xFF8C8478)
    }

private val LinkSource.display: String get() = label

/**
 * 链接 chip 列表（US-08）：来源徽标 + 标签，点击 onOpen(url)；
 * 传入 onRemove 显示 ✕（表单编辑用）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkChips(
    links: List<PlaceLink>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRemove: ((PlaceLink) -> Unit)? = null,
) {
    if (links.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        links.forEach { link ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { onOpen(link.url) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp, top = 5.dp, bottom = 5.dp, end = if (onRemove != null) 2.dp else 8.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(link.source.dotColor, CircleShape),
                    )
                    Text(
                        text = if (link.label.isBlank()) link.source.display else "${link.source.display}·${link.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = menuColors().ink,
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 1,
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "打开链接",
                        tint = menuColors().inkFaint,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(13.dp),
                    )
                    if (onRemove != null) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "移除链接",
                            tint = menuColors().inkFaint,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(2.dp)
                                .clickable { onRemove(link) },
                        )
                    }
                }
            }
        }
    }
}
