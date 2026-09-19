package com.leo.eats.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.leo.eats.ui.theme.menuColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 相对时间文案（US-05）：没吃过 / 今天 / 昨天 / N 天前 / N 周前 / … */
fun relativeTimeText(at: Long?, now: Long = System.currentTimeMillis()): String {
    if (at == null) return "没吃过"
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(at).atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
    )
    return when {
        days <= 0L -> "今天"
        days == 1L -> "昨天"
        days < 7L -> "$days 天前"
        days < 30L -> "${days / 7} 周前"
        days < 365L -> "${days / 30} 个月前"
        else -> "${days / 365} 年前"
    }
}

private val visitTimeFormat = DateTimeFormatter.ofPattern("M/d · HH:mm")

/** Visit 时间线的「9/17 · 12:30」 */
fun formatVisitTime(at: Long): String =
    visitTimeFormat.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

@Composable
fun RelativeTimeText(at: Long?, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Text(
        text = relativeTimeText(at),
        style = MaterialTheme.typography.labelSmall,
        color = if (highlight) menuColors().accent else menuColors().inkFaint,
        modifier = modifier,
    )
}
