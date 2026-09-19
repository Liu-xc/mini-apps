package com.leo.eats.ui.spin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceWithStats
import com.leo.eats.ui.AppViewModel
import com.leo.eats.ui.components.EmptyState
import com.leo.eats.ui.components.KindChip
import com.leo.eats.ui.components.LinkChips
import com.leo.eats.ui.components.RatingStars
import com.leo.eats.ui.components.RelativeTimeText
import com.leo.eats.ui.components.label
import com.leo.eats.ui.theme.EatsMotion
import com.leo.eats.ui.theme.kindColor
import com.leo.eats.ui.theme.menuColors
import com.leo.eats.ui.visit.LogVisitSheet
import kotlinx.coroutines.launch

private sealed interface SpinPhase {
    data object Idle : SpinPhase
    data object Spinning : SpinPhase
    data class Result(val winner: PlaceWithStats) : SpinPhase
}

private val RECENT_DAY_OPTIONS = listOf(7, 14, 30)

/**
 * W1 今天吃啥（US-07）：过滤器（类型/忌口/最近排除）+ 加权转盘 + 结果卡
 * （就吃这个 → W6 落账；重转排除刚中的项）。权重见 ADR-006。
 */
@Composable
fun SpinScreen(
    vm: AppViewModel,
    onOpenDetail: (String) -> Unit,
    onAddPlace: () -> Unit,
) {
    val data by vm.data.collectAsState()
    val config by vm.spinConfig.collectAsState()
    val rerollExcluded = remember { mutableStateListOf<String>() }
    var phase by remember { mutableStateOf<SpinPhase>(SpinPhase.Idle) }
    var logTarget by remember { mutableStateOf<PlaceWithStats?>(null) }
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }

    val candidates = remember(data, config, rerollExcluded.size) {
        vm.candidatesOf(config).filterNot { it.place.id in rerollExcluded }
    }

    // 类型语义色在组合期取一次（remember 块内不再调 @Composable）
    val menu = menuColors()
    val sectors = remember(candidates, menu) {
        var prevKind: PlaceKind? = null
        candidates.map { s ->
            val sameAsPrev = s.place.kind == prevKind
            prevKind = s.place.kind
            val base = when (s.place.kind) {
                PlaceKind.RESTAURANT -> menu.restaurant
                PlaceKind.TAKEOUT -> menu.takeout
                PlaceKind.HOME -> menu.homeCook
            }
            WheelSector(
                color = if (sameAsPrev) base.copy(alpha = 0.7f) else base,
                label = s.place.name,
            )
        }
    }

    // 中心实时显示指针掠过的名称
    val currentLabel by remember(sectors) {
        derivedStateOf {
            val idx = sectorIndexAt(rotation.value, sectors.size)
            sectors.getOrNull(idx)?.label ?: ""
        }
    }

    fun spin() {
        if (candidates.size < 2) return
        val plan = vm.spinWheel.plan(candidates, System.currentTimeMillis())
        val idx = candidates.indexOf(plan.winner)
        val sweep = 360f / candidates.size
        val mid = idx * sweep + sweep / 2 + plan.offsetDegrees
        val current = rotation.value
        val delta = (((-90f - mid - current) % 360f) + 360f) % 360f
        val target = current + plan.turns * 360f + delta
        phase = SpinPhase.Spinning
        scope.launch {
            rotation.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = plan.durationMillis,
                    easing = CubicBezierEasing(0.2f, 0f, 0.12f, 1f),
                ),
            )
            phase = SpinPhase.Result(plan.winner)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text(
            "今天吃啥",
            style = MaterialTheme.typography.displayMedium,
            color = menuColors().ink,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )

        if (data.places.isEmpty()) {
            EmptyState(
                emoji = "🍜",
                title = "还没有食堂",
                hint = "先去列表添加几家，回来转一把",
                imageRes = com.leo.eats.R.drawable.eats_empty,
                actionLabel = "去添加",
                onAction = onAddPlace,
            )
            return@Column
        }

        // ---- 过滤器（it-002 R2：单行收纳，把视觉主角还给转盘） ----
        val allTags = remember(data) { data.places.flatMap { it.tags }.distinct().sorted() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Text("类型", style = MaterialTheme.typography.labelMedium, color = menuColors().inkFaint)
            PlaceKind.entries.forEach { k ->
                val selected = k in config.kinds
                FilterChip(
                    selected = selected,
                    onClick = {
                        val next = if (selected && config.kinds.size > 1) config.kinds - k else config.kinds + k
                        vm.setSpinKinds(next)
                    },
                    label = { Text(k.label) },
                )
            }
            Text(
                "忌口",
                style = MaterialTheme.typography.labelMedium,
                color = menuColors().inkFaint,
                modifier = Modifier.padding(start = 8.dp),
            )
            allTags.forEach { tag ->
                val selected = tag in config.excludedTags
                FilterChip(
                    selected = selected,
                    onClick = {
                        val next = if (selected) config.excludedTags - tag else config.excludedTags + tag
                        vm.setSpinExcludedTags(next)
                    },
                    label = { Text("#$tag") },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Switch(
                checked = config.excludeRecentDays != null,
                onCheckedChange = { on ->
                    vm.setSpinExcludeRecent(on, config.excludeRecentDays ?: 14)
                },
            )
            Spacer(Modifier.width(8.dp))
            val days = config.excludeRecentDays
            if (days != null) {
                TextButton(onClick = {
                    val next = RECENT_DAY_OPTIONS[(RECENT_DAY_OPTIONS.indexOf(days) + 1) % RECENT_DAY_OPTIONS.size]
                    vm.setSpinExcludeRecent(true, next)
                }) { Text("排除最近 $days 天吃过的", style = MaterialTheme.typography.labelMedium) }
            } else {
                Text(
                    "排除最近吃过的",
                    style = MaterialTheme.typography.labelMedium,
                    color = menuColors().inkFaint,
                )
            }
        }

        // ---- 转盘 ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            WheelCanvas(
                rotationDegrees = rotation.value,
                sectors = sectors,
                modifier = Modifier.size(300.dp),
                spinning = phase is SpinPhase.Spinning,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = menuColors().surface,
                    border = BorderStroke(1.5.dp, menuColors().hairline),
                    modifier = Modifier.size(132.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                        val label = when (val p = phase) {
                            is SpinPhase.Result -> p.winner.place.name
                            else -> currentLabel.ifBlank { if (sectors.isEmpty()) "空" else "开转" }
                        }
                        // 中心文案扫掠（it-002 R1）：掠过扇区时上滑切换
                        AnimatedContent(
                            targetState = label,
                            transitionSpec = {
                                (slideInVertically { it / 2 } + fadeIn(tween(90)))
                                    .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(60)))
                            },
                            label = "wheelCenter",
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.headlineSmall,
                                color = menuColors().ink,
                                maxLines = 3,
                            )
                        }
                    }
                }
            }
        }

        // 重转排除提示（候选数已并入按钮，it-002 R3）
        if (rerollExcluded.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = { rerollExcluded.clear() }) { Text("已排除重转 ${rerollExcluded.size} 家 · 清除") }
            }
        }

        // ---- 开始按钮（it-002 R3：候选数并入按钮，减少一行） ----
        Button(
            onClick = { spin() },
            enabled = candidates.size >= 2 && phase !is SpinPhase.Spinning,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(54.dp),
        ) {
            Icon(Icons.Rounded.Casino, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                when (phase) {
                    is SpinPhase.Spinning -> "转动中…"
                    else -> if (candidates.isEmpty()) "开始转" else "开始转 · ${candidates.size} 家"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (candidates.size < 2) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    candidates.isEmpty() -> "过滤后没有可选项：放宽类型 / 忌口 / 最近排除试试"
                    else -> "过滤后只剩 1 项：${candidates[0].place.name}，今天就它吧"
                },
                style = MaterialTheme.typography.bodySmall,
                color = menuColors().inkFaint,
            )
        }

        // ---- 结果卡 ----
        val result = phase as? SpinPhase.Result
        AnimatedVisibility(
            visible = result != null,
            enter = scaleIn(EatsMotion.pop(), initialScale = 0.9f) + fadeIn(),
            modifier = Modifier.padding(top = 14.dp),
        ) {
            result?.let { r ->
                ResultCard(
                    winner = r.winner,
                    onLog = { logTarget = r.winner },
                    onReroll = {
                        rerollExcluded += r.winner.place.id
                        phase = SpinPhase.Idle
                    },
                    onOpenDetail = { onOpenDetail(r.winner.place.id) },
                    onOpenLink = { url ->
                        vm.linkOpener.open(url) { vm.toast("没有可打开该链接的应用") }
                    },
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    logTarget?.let { target ->
        LogVisitSheet(
            vm = vm,
            placeName = target.place.name,
            onLog = { at, rating, cost, text, uris ->
                vm.logVisit(target.place.id, at, rating, cost, text, uris) {}
                logTarget = null
                phase = SpinPhase.Idle
                rerollExcluded.clear()
            },
            onDismiss = { logTarget = null },
        )
    }

    // 数据变化后若结果卡里的食堂已被删除，回到 Idle
    LaunchedEffect(data, phase) {
        val r = phase as? SpinPhase.Result ?: return@LaunchedEffect
        if (data.places.none { it.id == r.winner.place.id }) phase = SpinPhase.Idle
    }
}

@Composable
private fun ResultCard(
    winner: PlaceWithStats,
    onLog: () -> Unit,
    onReroll: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = menuColors().surface,
        border = BorderStroke(2.dp, menuColors().accent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    winner.place.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = menuColors().ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                KindChip(winner.place.kind)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingStars(rating = winner.place.rating, size = 14.dp)
                RelativeTimeText(at = winner.lastVisitAt)
                Text(
                    "${winner.visitCount} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = menuColors().inkFaint,
                )
                TextButton(onClick = onOpenDetail) { Text("详情 →") }
            }
            if (winner.place.links.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "🔗 直达下单",
                    style = MaterialTheme.typography.labelMedium,
                    color = menuColors().inkFaint,
                )
                LinkChips(
                    links = winner.place.links,
                    onOpen = onOpenLink,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onLog, modifier = Modifier.weight(1f)) { Text("✓ 就吃这个") }
                OutlinedButton(onClick = onReroll, modifier = Modifier.weight(1f)) { Text("重转") }
            }
        }
    }
}
