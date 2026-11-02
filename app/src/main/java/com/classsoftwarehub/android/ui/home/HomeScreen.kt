package com.classsoftwarehub.android.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.classsoftwarehub.android.appContainer
import com.classsoftwarehub.android.data.model.DefaultPlatformKey
import com.classsoftwarehub.android.data.model.PlatformTab
import com.classsoftwarehub.android.data.model.SoftwareCategory
import com.classsoftwarehub.android.ui.components.CircleIconButton
import com.classsoftwarehub.android.ui.components.EmptyState
import com.classsoftwarehub.android.ui.components.FilterPill
import com.classsoftwarehub.android.ui.components.ScreenHeader
import com.classsoftwarehub.android.ui.components.SearchField
import com.classsoftwarehub.android.ui.components.SoftwareCard
import com.classsoftwarehub.android.ui.main.bottomBarClearance
import kotlinx.coroutines.delay

/** 首页（软件库）：页头 + 平台大分类 + 搜索 / 业务分类 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(onOpenDetail: (String) -> Unit) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.softwareRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val bottomClearance = bottomBarClearance()

    // 只有首次进列表才有错峰入场；900ms 后滚进来的项走快速淡入，避免快滑白屏
    var initialPhase by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(900)
        initialPhase = false
    }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = bottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    title = "软件库",
                    trailing = {
                        CircleIconButton(
                            icon = Icons.Default.Refresh,
                            contentDescription = "刷新清单",
                            onClick = { viewModel.refresh(force = true) },
                            tint = if (state.refreshing) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    },
                )
            }

            // 平台大分类：软件库的第一层筛选，始终可见
            item(key = "platform-category") {
                PlatformCategoryRow(
                    platforms = state.platforms,
                    selected = state.platform,
                    onSelect = viewModel::onPlatformChange,
                )
            }

            item {
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = "搜索软件、描述或标签",
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterPill(
                            text = "全部",
                            selected = state.category == null,
                            onClick = { viewModel.onCategoryChange(null) },
                        )
                    }
                    items(SoftwareCategory.entries.toList()) { category ->
                        FilterPill(
                            text = category.label,
                            selected = state.category == category,
                            onClick = { viewModel.onCategoryChange(category) },
                        )
                    }
                }
            }

            item {
                CountRow(
                    visible = state.items.size,
                    total = state.totalCount,
                    refreshing = state.refreshing,
                )
            }

            if (state.refreshing) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
            }

            if (state.items.isEmpty() && !state.refreshing) {
                item {
                    Box(Modifier.fillMaxWidth().height(340.dp)) {
                        EmptyState(
                            title = if (state.platform.isNotEmpty()) "该平台暂无收录软件" else "没有找到软件",
                            subtitle = when {
                                state.query.isNotBlank() -> "换个关键词试试吧"
                                state.platform.isNotEmpty() -> "切到「全部」或其他平台看看"
                                else -> "点右上角刷新清单，或稍后再试"
                            },
                        )
                    }
                }
            } else {
                itemsIndexed(state.items, key = { _, item -> item.id }) { index, software ->
                    SoftwareCard(
                        software = software,
                        onClick = { onOpenDetail(software.id) },
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .animateItemPlacement()
                            .entranceItem(software.id, index, stagger = initialPhase),
                    )
                }
            }
        }

        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomClearance - 12.dp),
        )
    }
}

/**
 * 平台大分类条：第一项「全部」，后面是仓库 platforms.json 的平台。
 * 安卓排在最前（移动端默认平台，打开就能看到选中项），其余保持数据顺序。
 */
@Composable
private fun PlatformCategoryRow(
    platforms: List<PlatformTab>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val ordered = remember(platforms) {
        platforms.sortedBy { if (it.key == DefaultPlatformKey) 0 else 1 }
    }
    LazyRow(
        modifier = Modifier.selectableGroup(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "all") {
            PlatformChip(
                text = "全部",
                selected = selected.isEmpty(),
                onClick = { onSelect("") },
            )
        }
        items(ordered, key = { it.key }) { platform ->
            PlatformChip(
                text = platform.label,
                selected = selected == platform.key,
                onClick = { onSelect(platform.key) },
            )
        }
    }
}

/** 平台大分类胶囊：比业务分类更大更重，选中为实心主题色 */
@Composable
private fun PlatformChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.selectable(
            selected = selected,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Tab,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) cs.primary else cs.surfaceContainerHigh.copy(alpha = 0.9f),
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.7f))
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 列表项入场：淡入 + 轻微上移。
 * 看过一次的项不再重播（rememberSaveable 随列表项状态保存，滚回来不闪）；
 * 只有首次进页的头部若干项错峰，滚动加载的项走 200ms 快速淡入，快滑不白屏。
 */
private const val EntranceStaggerMs = 35L
private const val EntranceMaxStaggerSteps = 6

@Composable
private fun Modifier.entranceItem(itemKey: String, index: Int, stagger: Boolean): Modifier {
    var played by rememberSaveable(itemKey) { mutableStateOf(false) }
    if (played) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(itemKey) {
        if (stagger) delay(minOf(index, EntranceMaxStaggerSteps) * EntranceStaggerMs)
        progress.animateTo(1f, tween(if (stagger) 280 else 200, easing = FastOutSlowInEasing))
        played = true
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 14.dp.toPx()
    }
}

@Composable
private fun CountRow(visible: Int, total: Int, refreshing: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "共 $visible 款" + if (total != visible) " · 全部 $total 款" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (refreshing) {
            Text(
                "正在同步镜像清单…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
