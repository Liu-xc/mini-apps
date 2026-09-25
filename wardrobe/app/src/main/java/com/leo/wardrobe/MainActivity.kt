package com.leo.wardrobe

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.LocalNavAnimatedVisibilityScope
import com.leo.wardrobe.ui.components.LocalSharedTransitionScope
import com.leo.wardrobe.ui.detail.ItemDetailScreen
import com.leo.wardrobe.ui.outfit.OutfitScreen
import com.leo.wardrobe.ui.records.OutfitDetailScreen
import com.leo.wardrobe.ui.records.RecordsScreen
import com.leo.wardrobe.ui.recap.WardrobeRecapScreen
import com.leo.wardrobe.ui.theme.WardrobeTheme
import com.leo.wardrobe.ui.wardrobe.ItemEditScreen
import com.leo.wardrobe.ui.wardrobe.WardrobeScreen
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        pendingOpenItemId.value = intent?.getStringExtra(EXTRA_OPEN_ITEM_ID) ?: pendingOpenItemId.value
        handleImportIntent(intent)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WardrobeTheme {
                WardrobeRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingOpenItemId.value = intent.getStringExtra(EXTRA_OPEN_ITEM_ID)
        handleImportIntent(intent)
    }

    /**
     * it-024 系统直达导入（D3④b）：zip 分享/打开方式入口。
     * 外授 Uri 即取即用——立刻整体拷入缓存，不持有临时权限。
     */
    private fun handleImportIntent(intent: Intent?) {
        if (intent == null) return
        val uri = when (intent.action) {
            Intent.ACTION_SEND ->
                androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return
        runCatching {
            val f = File(cacheDir, "import-${System.currentTimeMillis()}.zip")
            contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { input.copyTo(it) }
            } ?: return
            if (f.length() == 0L) return
            val display = contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
            pendingImport.value = PendingImport(f, display ?: f.name)
        }.onFailure { android.util.Log.e("Wardrobe", "handleImportIntent failed", it) }
    }

    companion object {
        /** 「好久没穿」通知深链（it-018）：点击进 W5 衣物详情 */
        const val EXTRA_OPEN_ITEM_ID = "openItemId"
        val pendingOpenItemId = MutableStateFlow<String?>(null)

        /** it-024：系统直达导入的待处理包（文件已拷入缓存） */
        data class PendingImport(val file: java.io.File, val displayName: String)
        val pendingImport = MutableStateFlow<PendingImport?>(null)
    }
}

private object Routes {
    const val HOME = "home"
    const val ITEM_EDIT = "itemEdit?itemId={itemId}"
    fun itemEdit(id: String?) = if (id == null) "itemEdit" else "itemEdit?itemId=$id"
    const val ITEM_DETAIL = "itemDetail/{itemId}"
    fun itemDetail(id: String) = "itemDetail/$id"
    const val OUTFIT_DETAIL = "outfitDetail/{outfitId}"
    fun outfitDetail(id: String) = "outfitDetail/$id"
    const val RECAP = "recap"
    const val WISHLIST = "wishlist"
    const val SETTINGS = "settings" // it-041：W11 设置页
    const val CHAT = "chat" // it-041 阶段 B：W12 对话页
}

private enum class Tab(val label: String) {
    OUTFIT("搭配"), RECORDS("穿搭记录"), WARDROBE("衣橱")
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun WardrobeRoot() {
    val vm: AppViewModel = viewModel()
    val recapVm: com.leo.wardrobe.ui.recap.RecapViewModel = viewModel()
    val settingsVm: com.leo.wardrobe.ui.settings.SettingsViewModel = viewModel()
    val chatVm: com.leo.wardrobe.ui.chat.ChatViewModel = viewModel()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(Tab.OUTFIT) }

    // it-018：启动对齐提醒任务；通知深链 → W5 衣物详情
    LaunchedEffect(Unit) { recapVm.syncReminderSchedule() }
    val pendingOpen by MainActivity.pendingOpenItemId.collectAsState()
    LaunchedEffect(pendingOpen) {
        val id = pendingOpen ?: return@LaunchedEffect
        nav.navigate(Routes.itemDetail(id))
        MainActivity.pendingOpenItemId.value = null
    }

    val toast by vm.toast.collectAsState()
    LaunchedEffect(toast) {
        if (toast != null) {
            snackbar.showSnackbar(toast!!)
            vm.toast(null)
        }
    }

    // it-024：带动作的一次性消息（导出完成 → 分享）
    val actionToast by vm.actionToast.collectAsState()
    LaunchedEffect(actionToast) {
        val t = actionToast ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(t.message, t.actionLabel)
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) t.onAction?.invoke()
        vm.consumeActionToast()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute == Routes.HOME

    // it-024：系统直达导入 → 回顾页承接（D3④b 汇入 ⑤）
    val pendingImport by MainActivity.pendingImport.collectAsState()
    LaunchedEffect(pendingImport) {
        val p = pendingImport ?: return@LaunchedEffect
        if (currentRoute != Routes.RECAP) nav.navigate(Routes.RECAP)
        MainActivity.pendingImport.value = null
        recapVm.startImport(p.file, p.displayName)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // it-034 A：白底二级页（W4/W5/W7/W9/W11）状态栏沉浸同色——这些路由去掉根 Scaffold
        // 顶部 inset，内容顶到窗口顶，由各页 TopAppBar 自行吸收状态栏（白顶栏铺进状态栏），
        // 顶栏标题上方只剩常规状态栏高度；搭配/记录/衣橱/W10 维持原浅绿状态栏表现
        contentWindowInsets = if (currentRoute in setOf(
                Routes.ITEM_EDIT, Routes.ITEM_DETAIL, Routes.OUTFIT_DETAIL, Routes.RECAP,
                Routes.SETTINGS, Routes.CHAT,
            )
        ) {
            ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                Icon(
                                    when (t) {
                                        Tab.OUTFIT -> Icons.Filled.Style
                                        Tab.RECORDS -> Icons.Filled.ViewModule
                                        Tab.WARDROBE -> Icons.Filled.Checkroom
                                    },
                                    contentDescription = t.label,
                                )
                            },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        SharedTransitionLayout(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    enterTransition = {
                        fadeIn(tween(220)) + slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(280),
                        )
                    },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = {
                        fadeOut(tween(180)) + slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(280),
                        )
                    },
                ) {
                    composable(Routes.HOME) {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            HomeTabs(vm, nav, tab)
                        }
                    }
                    composable(Routes.ITEM_EDIT) { entry ->
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            ItemEditScreen(
                                vm = vm,
                                itemId = entry.arguments?.getString("itemId"),
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                    composable(Routes.ITEM_DETAIL) { entry ->
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val id = entry.arguments?.getString("itemId").orEmpty()
                            ItemDetailScreen(
                                vm = vm,
                                itemId = id,
                                onBack = { nav.popBackStack() },
                                onEdit = { nav.navigate(Routes.itemEdit(it)) },
                                onOpenOutfit = { nav.navigate(Routes.outfitDetail(it)) },
                            )
                        }
                    }
                    composable(Routes.RECAP) {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            WardrobeRecapScreen(
                                appVm = vm,
                                vm = recapVm,
                                onBack = { nav.popBackStack() },
                                onOpenItem = { nav.navigate(Routes.itemDetail(it)) },
                                onGoRecords = {
                                    nav.popBackStack()
                                    tab = Tab.RECORDS  // it-031 C10：空态「去打卡」直达
                                },
                            )
                        }
                    }
                    // it-019：W9 心愿页（想买单品 + 心愿穿搭）
                    composable(Routes.WISHLIST) {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            com.leo.wardrobe.ui.wishlist.WishlistScreen(
                                vm = vm,
                                onBack = { nav.popBackStack() },
                                onOpenItem = { nav.navigate(Routes.itemDetail(it)) },
                            )
                        }
                    }
                    // it-041 阶段 A：W11 设置页（模型连接 + 模式状态）
                    composable(Routes.SETTINGS) {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            com.leo.wardrobe.ui.settings.SettingsScreen(
                                vm = settingsVm,
                                onBack = { nav.popBackStack() },
                                onOpenChat = { nav.navigate(Routes.CHAT) },
                            )
                        }
                    }
                    // it-041 阶段 B：W12 对话页（穿搭顾问）
                    composable(Routes.CHAT) {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            com.leo.wardrobe.ui.chat.ChatScreen(
                                vm = chatVm,
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                    composable(Routes.OUTFIT_DETAIL) { entry ->
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val id = entry.arguments?.getString("outfitId").orEmpty()
                            OutfitDetailScreen(
                                vm = vm,
                                outfitId = id,
                                onBack = { nav.popBackStack() },
                                onOpenItem = { nav.navigate(Routes.itemDetail(it)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTabs(vm: AppViewModel, nav: NavHostController, tab: Tab) {
    val openItem: (String) -> Unit = { nav.navigate(Routes.itemDetail(it)) }
    val editItem: (String?) -> Unit = { nav.navigate(Routes.itemEdit(it)) }

    Crossfade(targetState = tab, animationSpec = tween(220), label = "tabs") { current ->
        when (current) {
            Tab.OUTFIT -> OutfitScreen(
                vm = vm,
                onOpenItem = openItem,
                onAddItem = { editItem(null) },
                onOpenOutfit = { nav.navigate(Routes.outfitDetail(it)) },
                onOpenWishlist = { nav.navigate(Routes.WISHLIST) },
            )
            Tab.RECORDS -> RecordsScreen(vm = vm, onOpenOutfit = { nav.navigate(Routes.outfitDetail(it)) })
            Tab.WARDROBE -> WardrobeScreen(
                vm = vm,
                onEditItem = editItem,
                onOpenItem = openItem,
                onOpenRecap = { nav.navigate(Routes.RECAP) },
                onOpenWishlist = { nav.navigate(Routes.WISHLIST) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
    }
}
