package com.leo.lottery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leo.lottery.ui.draw.DrawScreen
import com.leo.lottery.ui.draw.ReplayOverlay
import com.leo.lottery.ui.generate.GenerateScreen
import com.leo.lottery.ui.theme.LocalLotteryColors
import com.leo.lottery.ui.tickets.TicketDetailScreen
import com.leo.lottery.ui.tickets.TicketsScreen

/**
 * 应用外壳：三 Tab（选号/开奖/票夹）+ 详情栈 + 全局 snackbar 事件 + 剧场 overlay。
 */
@Composable
fun LotteryApp(vm: LotteryViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val c = LocalLotteryColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is LotteryViewModel.Event.Message -> snackbarHostState.showSnackbar(e.text)
                is LotteryViewModel.Event.Deleted -> {
                    val r = snackbarHostState.showSnackbar(
                        message = "已删除",
                        actionLabel = "撤销",
                        duration = SnackbarDuration.Short,
                    )
                    if (r == SnackbarResult.ActionPerformed) vm.undoDelete(e.ticket)
                }
            }
        }
    }

    BackHandler(
        enabled = state.draw.replaying ||
            state.detail != null ||
            state.tab != LotteryViewModel.Tab.GENERATE,
    ) {
        when {
            state.draw.replaying -> vm.finishReplay()
            state.detail != null -> vm.closeDetail()
            else -> vm.selectTab(LotteryViewModel.Tab.GENERATE)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = c.paper,
            bottomBar = {
                if (state.detail == null) {
                    NavigationBar(containerColor = c.surface) {
                        NavItem(
                            vm, state,
                            LotteryViewModel.Tab.GENERATE, "选号", Icons.Outlined.AutoAwesome,
                        )
                        NavItem(
                            vm, state,
                            LotteryViewModel.Tab.DRAW, "开奖", Icons.Outlined.Theaters,
                        )
                        NavItem(
                            vm, state,
                            LotteryViewModel.Tab.TICKETS, "票夹", Icons.Outlined.ConfirmationNumber,
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                if (state.detail != null) {
                    TicketDetailScreen(vm, state, snackbarHostState)
                } else {
                    Crossfade(
                        targetState = state.tab,
                        animationSpec = tween(220),
                        label = "tab",
                    ) { tab ->
                        when (tab) {
                            LotteryViewModel.Tab.GENERATE ->
                                GenerateScreen(vm, state, snackbarHostState)

                            LotteryViewModel.Tab.DRAW ->
                                DrawScreen(vm, state, snackbarHostState)

                            LotteryViewModel.Tab.TICKETS ->
                                TicketsScreen(vm, state, snackbarHostState)
                        }
                    }
                }
            }
        }

        if (state.draw.replaying) {
            ReplayOverlay(vm, state)
        }
    }
}

@Composable
private fun RowScope.NavItem(
    vm: LotteryViewModel,
    state: LotteryViewModel.UiState,
    tab: LotteryViewModel.Tab,
    label: String,
    icon: ImageVector,
) {
    NavigationBarItem(
        selected = state.tab == tab,
        onClick = { vm.selectTab(tab) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = LocalLotteryColors.current.ink,
            unselectedIconColor = LocalLotteryColors.current.inkFaint,
            selectedTextColor = LocalLotteryColors.current.accent,
            unselectedTextColor = LocalLotteryColors.current.inkFaint,
            indicatorColor = LocalLotteryColors.current.ink.copy(alpha = 0.055f),
        ),
    )
}
