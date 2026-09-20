package com.leo.wardrobe

import android.os.Bundle
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
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewModule
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
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.LocalNavAnimatedVisibilityScope
import com.leo.wardrobe.ui.components.LocalSharedTransitionScope
import com.leo.wardrobe.ui.detail.ItemDetailScreen
import com.leo.wardrobe.ui.outfit.OutfitScreen
import com.leo.wardrobe.ui.records.OutfitDetailScreen
import com.leo.wardrobe.ui.records.RecordsScreen
import com.leo.wardrobe.ui.theme.WardrobeTheme
import com.leo.wardrobe.ui.wardrobe.ItemEditScreen
import com.leo.wardrobe.ui.wardrobe.WardrobeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WardrobeTheme {
                WardrobeRoot()
            }
        }
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
}

private enum class Tab(val label: String) {
    OUTFIT("搭配"), RECORDS("穿搭记录"), WARDROBE("衣橱")
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun WardrobeRoot() {
    val vm: AppViewModel = viewModel()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(Tab.OUTFIT) }

    val toast by vm.toast.collectAsState()
    LaunchedEffect(toast) {
        if (toast != null) {
            snackbar.showSnackbar(toast!!)
            vm.toast(null)
        }
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
                                        Tab.OUTFIT -> Icons.Filled.Style
                                        Tab.RECORDS -> Icons.Filled.ViewModule
                                        Tab.WARDROBE -> Icons.Filled.Checkroom
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
            )
            Tab.RECORDS -> RecordsScreen(vm = vm, onOpenOutfit = { nav.navigate(Routes.outfitDetail(it)) })
            Tab.WARDROBE -> WardrobeScreen(vm = vm, onEditItem = editItem)
        }
    }
}
