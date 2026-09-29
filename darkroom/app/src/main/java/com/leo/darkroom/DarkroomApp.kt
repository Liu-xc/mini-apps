package com.leo.darkroom

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leo.darkroom.ui.develop.DevelopScreen
import com.leo.darkroom.ui.pick.AlbumPagerScreen
import com.leo.darkroom.ui.pick.PickScreen
import com.leo.darkroom.ui.result.ResultScreen
import com.leo.darkroom.ui.settings.SettingsScreen
import com.leo.darkroom.ui.theme.editorialColors

/**
 * 导航壳：四屏 crossfade（220ms，DESIGN.md §3 节奏），snackbar 消息与全局错误/加载。
 */
@Composable
fun DarkroomApp(vm: DarkroomViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = editorialColors()

    // W1 冷入场只播一次（it-008）：导航壳跨屏持有，W1→W2→W1 返回不重放
    var pickEntrancePlayed by remember { mutableStateOf(false) }

    // 返回栈：设置→选图；其余非选图页→选图（会话即弃）
    BackHandler(enabled = state.screen != DarkroomViewModel.Screen.PICK) {
        when (state.screen) {
            DarkroomViewModel.Screen.SETTINGS -> vm.closeSettings()
            else -> vm.backToPick()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.paper),
    ) {
        Crossfade(
            targetState = state.screen,
            animationSpec = tween(220),
            modifier = Modifier.fillMaxSize(),
        ) { screen ->
            when (screen) {
                DarkroomViewModel.Screen.PICK -> PickScreen(
                    vm = vm,
                    state = state,
                    playEntrance = !pickEntrancePlayed,
                    onEntrancePlayed = { pickEntrancePlayed = true },
                )
                DarkroomViewModel.Screen.DEVELOP -> DevelopScreen(vm, state)
                DarkroomViewModel.Screen.PAGER -> AlbumPagerScreen(vm, state)
                DarkroomViewModel.Screen.RESULT -> ResultScreen(vm, state)
                DarkroomViewModel.Screen.SETTINGS -> SettingsScreen(vm, state)
            }
        }

        // 保存反馈只属于发起保存的当前场景；离开后立即收起，避免遮挡设置或下一次操作。
        LaunchedEffect(state.screen) {
            snackbarHostState.currentSnackbarData?.dismiss()
            if (state.message != null) vm.clearMessage()
        }

        // 消息 snackbar（保存成功 → 可分享）
        state.message?.let { message ->
            val canShare = state.lastSavedKind != DarkroomViewModel.SavedKind.NONE
            LaunchedEffect(message) {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = if (canShare) "分享" else null,
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    when (state.lastSavedKind) {
                        DarkroomViewModel.SavedKind.IMAGE -> vm.shareSavedImage()
                        DarkroomViewModel.SavedKind.VIDEO -> vm.shareSavedVideo()
                        DarkroomViewModel.SavedKind.NONE -> Unit
                    }
                }
                vm.clearMessage()
            }
        }

        // it-008 M2.7 编辑式 snackbar：surface 底 + hairline 边框，替换 M3 默认深色浮层
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        ) { data ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.hairline),
                shadowElevation = 6.dp,
            ) {
                Snackbar(
                    snackbarData = data,
                    containerColor = Color.Transparent,
                    contentColor = colors.ink,
                    actionColor = colors.ink,
                    dismissActionContentColor = colors.inkFaint,
                )
            }
        }

        // 全局错误：单弹窗（不套弹窗）
        state.error?.let { error ->
            AlertDialog(
                onDismissRequest = vm::clearError,
                title = { Text("出状况了") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = vm::clearError) { Text("知道了") }
                },
            )
        }

        // 选图解码中：全屏轻遮罩
        if (state.loadingPhoto) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.paper.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
