@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.leo.eats.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** 共享元素过渡的作用域桥（MainActivity 提供，it-002 R1） */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** 无缝放大共享元素：任意作用域缺失时安全退化为普通 Modifier */
@Composable
fun Modifier.sharedPhoto(key: String): Modifier {
    val sts = LocalSharedTransitionScope.current ?: return this
    val avs = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sts) {
        this@sharedPhoto.sharedElement(
            rememberSharedContentState(key = key),
            animatedVisibilityScope = avs,
        )
    }
}
