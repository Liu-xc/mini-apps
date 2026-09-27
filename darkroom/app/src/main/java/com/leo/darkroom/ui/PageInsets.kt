package com.leo.darkroom.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 页面根布局统一让出系统栏（it-002 O2 / US-9）：
 * 顶部 = 状态栏（页头不再嵌进状态栏窗口，顶行触摸死区随之消除），
 * 底部 = 手势导航条（贴底 CTA 不被遮挡）。
 */
@Composable
fun Modifier.pageInsets(): Modifier =
    this.windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
