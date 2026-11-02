package com.classsoftwarehub.android.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.classsoftwarehub.android.ui.downloads.DownloadsScreen
import com.classsoftwarehub.android.ui.home.HomeScreen
import com.classsoftwarehub.android.ui.home.SoftwareDetailScreen
import com.classsoftwarehub.android.ui.settings.AboutScreen
import com.classsoftwarehub.android.ui.settings.ChangelogScreen
import com.classsoftwarehub.android.ui.settings.SettingsScreen
import com.classsoftwarehub.android.ui.submit.FeedbackScreen
import com.classsoftwarehub.android.ui.submit.SubmitScreen
import com.classsoftwarehub.android.ui.submit.SubmitSoftwareScreen
import com.classsoftwarehub.android.ui.tools.AnnounceScreen
import com.classsoftwarehub.android.ui.tools.ChemistryScreen
import com.classsoftwarehub.android.ui.tools.ColorPickerToolScreen
import com.classsoftwarehub.android.ui.tools.EncoderToolScreen
import com.classsoftwarehub.android.ui.tools.EquationScreen
import com.classsoftwarehub.android.ui.tools.FunctionPlotScreen
import com.classsoftwarehub.android.ui.tools.MirrorScreen
import com.classsoftwarehub.android.ui.tools.QrCodeToolScreen
import com.classsoftwarehub.android.ui.tools.RandomPickerScreen
import com.classsoftwarehub.android.ui.tools.TimerToolScreen
import com.classsoftwarehub.android.ui.tools.ToolsScreen
import com.classsoftwarehub.android.ui.tools.UnitConvertScreen

// ── 转场动画 ──
// 一级 Tab 之间：轻淡入淡出（快、不抢戏）；
// 二级页面：iOS 那种"右进左出"（进入页从右侧滑入，退出页轻微让位并淡出）。

private val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        initialOffsetX = { it },
    ) + fadeIn(tween(200))
}

private val pushExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        targetOffsetX = { -it / 4 },
    ) + fadeOut(tween(200))
}

private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        initialOffsetX = { -it / 4 },
    ) + fadeIn(tween(200))
}

private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        targetOffsetX = { it },
    ) + fadeOut(tween(200))
}

/** 二级页面统一用"右进左出"转场 */
private fun NavGraphBuilder.slideComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    arguments = arguments,
    enterTransition = pushEnter,
    exitTransition = pushExit,
    popEnterTransition = popEnter,
    popExitTransition = popExit,
    content = content,
)

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(140)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(140)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDetail = { id -> navController.navigate(Routes.softwareDetail(id)) },
            )
        }
        composable(Routes.TOOLS) {
            ToolsScreen(onOpenTool = { route -> navController.navigate(route) })
        }
        composable(Routes.SUBMIT) {
            SubmitScreen(
                onSubmitSoftware = { navController.navigate(Routes.SUBMIT_SOFTWARE) },
                onFeedback = { navController.navigate(Routes.SUBMIT_FEEDBACK) },
            )
        }
        composable(Routes.DOWNLOADS) { DownloadsScreen() }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenChangelog = { navController.navigate(Routes.CHANGELOG) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }

        slideComposable(
            route = Routes.SOFTWARE_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_SOFTWARE_ID) { type = NavType.StringType }),
        ) { entry ->
            SoftwareDetailScreen(
                softwareId = entry.arguments?.getString(Routes.ARG_SOFTWARE_ID).orEmpty(),
                onOpenDownloads = {
                    navController.navigate(Routes.DOWNLOADS) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }

        slideComposable(Routes.TOOL_COLOR) { ColorPickerToolScreen() }
        slideComposable(Routes.TOOL_RANDOM) { RandomPickerScreen() }
        slideComposable(Routes.TOOL_TIMER) { TimerToolScreen() }
        slideComposable(Routes.TOOL_ENCODER) { EncoderToolScreen() }
        slideComposable(Routes.TOOL_QRCODE) { QrCodeToolScreen() }
        slideComposable(Routes.TOOL_UNITS) { UnitConvertScreen() }
        slideComposable(Routes.TOOL_ANNOUNCE) { AnnounceScreen() }
        slideComposable(Routes.TOOL_MIRROR) { MirrorScreen() }
        slideComposable(Routes.TOOL_FUNCTION_PLOT) { FunctionPlotScreen() }
        slideComposable(Routes.TOOL_EQUATION) { EquationScreen() }
        slideComposable(Routes.TOOL_CHEMISTRY) { ChemistryScreen() }

        slideComposable(Routes.SUBMIT_SOFTWARE) { SubmitSoftwareScreen() }
        slideComposable(Routes.SUBMIT_FEEDBACK) { FeedbackScreen() }

        slideComposable(Routes.CHANGELOG) { ChangelogScreen() }
        slideComposable(Routes.ABOUT) { AboutScreen() }
    }
}
