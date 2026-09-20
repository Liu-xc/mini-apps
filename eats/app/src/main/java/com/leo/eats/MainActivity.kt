package com.leo.eats

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import com.leo.eats.ui.AppViewModel
import com.leo.eats.ui.components.LocalNavAnimatedVisibilityScope
import com.leo.eats.ui.components.LocalSharedTransitionScope
import com.leo.eats.ui.detail.PlaceDetailScreen
import com.leo.eats.ui.list.ListScreen
import com.leo.eats.ui.list.PlaceEditScreen
import com.leo.eats.ui.mapview.MapScreen
import com.leo.eats.ui.spin.SpinScreen
import com.leo.eats.ui.recap.RecapScreen
import com.leo.eats.ui.theme.EatsTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        pendingOpenPlaceId.value = intent?.getStringExtra(EXTRA_OPEN_PLACE_ID) ?: pendingOpenPlaceId.value
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            EatsTheme {
                EatsRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingOpenPlaceId.value = intent.getStringExtra(EXTRA_OPEN_PLACE_ID)
    }

    companion object {
        /** 「好久没去」通知深链（it-007）：点击进 W5 详情 */
        const val EXTRA_OPEN_PLACE_ID = "openPlaceId"
        val pendingOpenPlaceId = MutableStateFlow<String?>(null)
    }
}

private object Routes {
    const val HOME = "home"
    const val PLACE_EDIT = "placeEdit?placeId={placeId}"
    fun placeEdit(id: String?) = if (id == null) "placeEdit" else "placeEdit?placeId=$id"
    const val PLACE_DETAIL = "placeDetail/{placeId}"
    fun placeDetail(id: String) = "placeDetail/$id"
    const val RECAP = "recap"
}

private enum class Tab(val label: String) {
    SPIN("干啥"), MAP("地图"), LIST("列表"),
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun EatsRoot() {
    val vm: AppViewModel = viewModel()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(Tab.SPIN) }

    // 地图页「N 条未上地图」→ 列表页「未定位」过滤意图
    var noLocationFocus by rememberSaveable { mutableStateOf(false) }

    // it-007：启动对齐提醒任务；通知深链 → W5 详情
    LaunchedEffect(Unit) { vm.syncReminderSchedule() }
    val pendingOpen by MainActivity.pendingOpenPlaceId.collectAsState()
    LaunchedEffect(pendingOpen) {
        val id = pendingOpen ?: return@LaunchedEffect
        nav.navigate(Routes.placeDetail(id))
        MainActivity.pendingOpenPlaceId.value = null
    }

    val toast by vm.toast.collectAsState()
    LaunchedEffect(toast) {
        val t = toast ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(t.message, t.actionLabel)
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            t.onAction?.invoke()
        }
        vm.toast(null)
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute == Routes.HOME

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
                                        Tab.SPIN -> Icons.Rounded.Casino
                                        Tab.MAP -> Icons.Rounded.Map
                                        Tab.LIST -> Icons.Rounded.RestaurantMenu
                                    },
                                    contentDescription = t.label,
                                )
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        SharedTransitionLayout(Modifier.padding(padding)) {
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
                            HomeTabs(
                                vm = vm,
                                nav = nav,
                                tab = tab,
                                onSwitchTab = { tab = it },
                                noLocationFocus = noLocationFocus,
                                setNoLocationFocus = { noLocationFocus = it },
                            )
                        }
                    }
                    composable(Routes.PLACE_EDIT) { entry ->
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            PlaceEditScreen(
                                vm = vm,
                                placeId = entry.arguments?.getString("placeId"),
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                    composable(Routes.RECAP) {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            RecapScreen(
                                vm = vm,
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                    composable(Routes.PLACE_DETAIL) { entry ->
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val id = entry.arguments?.getString("placeId").orEmpty()
                            PlaceDetailScreen(
                                vm = vm,
                                placeId = id,
                                onBack = { nav.popBackStack() },
                                onEdit = { nav.navigate(Routes.placeEdit(it)) },
                                onShowOnMap = { placeId ->
                                    // it-002 R4：详情是独立路由，需先回 HOME 再切地图 Tab 聚焦
                                    nav.popBackStack(Routes.HOME, false)
                                    vm.focusOnMap(placeId)
                                    tab = Tab.MAP
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTabs(
    vm: AppViewModel,
    nav: NavHostController,
    tab: Tab,
    onSwitchTab: (Tab) -> Unit,
    noLocationFocus: Boolean,
    setNoLocationFocus: (Boolean) -> Unit,
) {
    val openDetail: (String) -> Unit = { nav.navigate(Routes.placeDetail(it)) }
    val editPlace: (String?) -> Unit = { nav.navigate(Routes.placeEdit(it)) }
    val openRecap: () -> Unit = { nav.navigate(Routes.RECAP) }

    Crossfade(targetState = tab, animationSpec = tween(220), label = "tabs") { current ->
        when (current) {
            Tab.SPIN -> SpinScreen(
                vm = vm,
                onOpenDetail = openDetail,
                onAddPlace = { editPlace(null) },
            )
            Tab.MAP -> MapScreen(
                vm = vm,
                onOpenDetail = openDetail,
                onShowNoLocation = {
                    setNoLocationFocus(true)
                    onSwitchTab(Tab.LIST)
                },
            )
            Tab.LIST -> ListScreen(
                vm = vm,
                onOpenDetail = openDetail,
                onEditPlace = editPlace,
                onOpenRecap = openRecap,
                focusNoLocation = noLocationFocus,
                onFocusConsumed = { setNoLocationFocus(false) },
            )
        }
    }
}
