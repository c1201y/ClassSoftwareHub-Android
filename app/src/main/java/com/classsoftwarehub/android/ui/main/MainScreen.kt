package com.classsoftwarehub.android.ui.main

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.classsoftwarehub.android.appContainer
import com.classsoftwarehub.android.ui.components.AppBackground
import com.classsoftwarehub.android.ui.components.CircleIconButton
import com.classsoftwarehub.android.ui.icons.FluentIcons
import com.classsoftwarehub.android.ui.navigation.AppNavHost
import com.classsoftwarehub.android.ui.navigation.Routes
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/** 底部导航目的地（固定 5 个 Tab，下载也是常驻入口） */
private enum class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(Routes.HOME, "首页", Icons.Outlined.Home, Icons.Filled.Home),
    TOOLS(Routes.TOOLS, "工具", Icons.Outlined.Widgets, Icons.Filled.Widgets),
    DOWNLOADS(Routes.DOWNLOADS, "下载", FluentIcons.ArrowDownload, FluentIcons.ArrowDownload),
    SUBMIT(Routes.SUBMIT, "提交", Icons.Outlined.CloudUpload, Icons.Filled.CloudUpload),
    SETTINGS(Routes.SETTINGS, "设置", Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** 贴底导航条尺寸口径（一级页面自留底部空白共用） */
private val BarHeight = 56.dp

/** 两次 tab 切换之间的最小间隔，挡住连点导致的导航排队 */
private const val TabNavDebounceMs = 300L

/**
 * 贴底导航给列表类一级页面预留的底部空白：
 * 系统手势区 + 导航条本体 + 呼吸位。
 */
@Composable
fun bottomBarClearance(extra: Dp = 24.dp): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        BarHeight + extra

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    initialRoute: String?,
    onRouteConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val summary by container.downloadRepository.summary.collectAsStateWithLifecycle()

    // 从通知等入口跳转到指定页面
    LaunchedEffect(initialRoute) {
        if (!initialRoute.isNullOrBlank()) {
            navController.navigate(initialRoute) { launchSingleTop = true }
            onRouteConsumed()
        }
    }

    val isTopLevel = currentRoute in Routes.topLevel
    val imeVisible = WindowInsets.isImeVisible
    val showBottomBar = isTopLevel && !imeVisible
    var lastTabNavAt by remember { mutableLongStateOf(0L) }
    val hazeState = remember { HazeState() }

    val onTabNavigate: (BottomDestination) -> Unit = { destination ->
        val sameTab = destination.route == currentRoute
        val now = SystemClock.elapsedRealtime()
        if (!sameTab && now - lastTabNavAt >= TabNavDebounceMs) {
            lastTabNavAt = now
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        AppBackground()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!isTopLevel) {
                    SecondaryTopBar(
                        title = Routes.titleOf(currentRoute),
                        onBack = { navController.navigateUp() },
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            val topPadding = if (isTopLevel) statusTop else innerPadding.calculateTopPadding()
            val bottomPadding = if (isTopLevel) 0.dp else navBottom

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding, bottom = bottomPadding)
                    .haze(
                        state = hazeState,
                        style = HazeStyle(
                            tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.50f),
                            blurRadius = 22.dp,
                            noiseFactor = 0.045f,
                        ),
                    ),
            ) {
                AppNavHost(navController = navController)
            }
        }

        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(140)),
        ) {
            QQStyleBottomBar(
                hazeState = hazeState,
                currentRoute = currentRoute,
                activeDownloads = summary.activeCount,
                onNavigate = onTabNavigate,
            )
        }
    }
}

/** 二级页面顶栏：圆形返回 + 标题，透明背景，跟页面渐变融为一体 */
@Composable
private fun SecondaryTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(58.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * QQ 风贴底导航条：
 * - 真正贴底：铺满宽度、直角、无悬浮卡片与阴影；
 * - 顶部一条发丝分割线，和内容区分开；
 * - 背景用 Haze 轻磨砂（小半径模糊），只透出一点点底层颜色；
 * - 选中项描边图标换成实心并染主题色，不做滑动胶囊。
 */
@Composable
private fun QQStyleBottomBar(
    hazeState: HazeState,
    currentRoute: String?,
    activeDownloads: Int,
    onNavigate: (BottomDestination) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    tint = cs.surface.copy(alpha = if (dark) 0.64f else 0.74f),
                    blurRadius = 12.dp,
                    noiseFactor = 0f,
                ),
            ),
    ) {
        HorizontalDivider(
            thickness = 0.8.dp,
            color = cs.outlineVariant.copy(alpha = if (dark) 0.55f else 0.85f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(BarHeight)
                .selectableGroup(),
        ) {
            BottomDestination.entries.forEach { destination ->
                BottomTab(
                    destination = destination,
                    selected = destination.route == currentRoute,
                    activeDownloads = activeDownloads,
                    onClick = { onNavigate(destination) },
                )
            }
        }
    }
}

/** 单个贴底 Tab：图标 + 文字，按下轻微缩小，选中实心图标 + 主题色 */
@Composable
private fun RowScope.BottomTab(
    destination: BottomDestination,
    selected: Boolean,
    activeDownloads: Int,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessHigh),
        label = "tabPressScale",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.9f),
        animationSpec = tween(160),
        label = "tabTint",
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = {
                    if (!selected) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                },
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = selected,
                animationSpec = tween(140),
                label = "tabIcon",
            ) { isSelected ->
                Icon(
                    imageVector = if (isSelected) destination.selectedIcon else destination.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .size(22.dp)
                        .scale(pressScale),
                )
            }
            if (destination == BottomDestination.DOWNLOADS && activeDownloads > 0) {
                Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-3).dp),
                    containerColor = cs.error,
                    contentColor = cs.onError,
                ) {
                    Text(
                        if (activeDownloads > 99) "99+" else activeDownloads.toString(),
                        fontSize = 9.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp,
            ),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = tint,
            maxLines = 1,
        )
    }
}
