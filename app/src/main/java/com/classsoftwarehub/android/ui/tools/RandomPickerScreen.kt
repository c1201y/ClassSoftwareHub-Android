package com.classsoftwarehub.android.ui.tools

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classsoftwarehub.android.appContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

private enum class PickerMode(val label: String) {
    ROLL_CALL("点名"),
    LOTTERY("抽奖"),
}

private enum class PickerDialog { CREATE, RENAME, DELETE, RANGE }

@Serializable
private data class PickerNameList(
    val name: String = "default",
    val members: List<String> = emptyList(),
)

private data class PickerRecord(val mode: String, val names: List<String>)

private const val DefaultListName = "default"
private const val MaxHistory = 10

private fun parseMembers(text: String): List<String> =
    text.split('\n', '，', ',', '、', ' ', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * 随机抽取：点名（不重复）/ 抽奖（每轮独立）。
 * 上方常驻模式切换，中间大字展示结果，底部为名单与数量控制面板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomPickerScreen() {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val listSerializer = remember { ListSerializer(PickerNameList.serializer()) }

    val lists = remember { mutableStateMapOf<String, List<String>>() }
    val drawn = remember { mutableStateMapOf<String, Set<String>>() }
    val history = remember { mutableStateListOf<PickerRecord>() }

    var mode by rememberSaveable { mutableStateOf(PickerMode.ROLL_CALL) }
    var currentList by rememberSaveable { mutableStateOf(DefaultListName) }
    var count by rememberSaveable { mutableStateOf(1) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var rolling by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<PickerDialog?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var minInput by remember { mutableStateOf("1") }
    var maxInput by remember { mutableStateOf("50") }
    var editingText by remember { mutableStateOf("") }

    fun saveLists() {
        val payload = container.json.encodeToString(
            listSerializer,
            lists.map { PickerNameList(it.key, it.value) },
        )
        scope.launch { container.settingsRepository.setRandomListsJson(payload) }
    }

    LaunchedEffect(Unit) {
        val raw = container.settingsRepository.randomListsJson()
        val stored = raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { container.json.decodeFromString(listSerializer, it) }.getOrNull() }
            .orEmpty()
        if (stored.isEmpty()) {
            // 老版本只有一份纯文本名单，迁移成 default
            lists[DefaultListName] = parseMembers(container.settingsRepository.current().randomNameList)
        } else {
            stored.forEach { lists[it.name] = it.members }
        }
        if (lists.isEmpty()) lists[DefaultListName] = emptyList()
        if (currentList !in lists) currentList = lists.keys.first()
    }

    // 打开「更多」或切换名单时，把编辑框同步成该名单当前内容
    LaunchedEffect(showMore, currentList) {
        if (showMore) editingText = lists[currentList].orEmpty().joinToString("\n")
    }

    val members = lists[currentList].orEmpty()
    val drawnSet = drawn[currentList].orEmpty()
    val remaining = members.count { it !in drawnSet }
    val statusRemaining = if (mode == PickerMode.ROLL_CALL) remaining else members.size
    val maxCount = members.size.coerceAtLeast(1)

    LaunchedEffect(members.size) {
        if (count > maxCount) count = maxCount
    }

    fun draw() {
        if (rolling) return
        val pool = lists[currentList].orEmpty()
        if (pool.isEmpty()) return
        val wanted = count.coerceIn(1, pool.size)
        rolling = true
        scope.launch {
            repeat(16) { index ->
                results = List(wanted) { pool.random() }
                delay(38L + index * 8L)
            }
            val picked = if (mode == PickerMode.ROLL_CALL) {
                var available = pool.filter { it !in drawn[currentList].orEmpty() }
                if (available.size < wanted) {
                    // 一轮抽完，自动开新的一轮
                    drawn[currentList] = emptySet()
                    available = pool
                }
                available.shuffled().take(wanted).also { winners ->
                    drawn[currentList] = (drawn[currentList].orEmpty() + winners).toSet()
                }
            } else {
                pool.shuffled().take(wanted)
            }
            results = picked
            rolling = false
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            history.add(0, PickerRecord(mode.label, picked))
            while (history.size > MaxHistory) history.removeAt(history.lastIndex)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(18.dp))
            ModeSwitcher(
                selected = mode,
                onSelect = {
                    if (it != mode) {
                        mode = it
                        results = emptyList()
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            PickerDisplay(
                results = results,
                rolling = rolling,
                noMembers = members.isEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
            )

            ControlPanel(
                listNames = lists.keys.toList(),
                currentList = currentList,
                count = count,
                maxCount = maxCount,
                memberCount = members.size,
                remainingCount = statusRemaining,
                rolling = rolling,
                onSelectList = { name ->
                    if (name != currentList) {
                        currentList = name
                        results = emptyList()
                    }
                },
                onCountChange = { count = it },
                onMore = { showMore = true },
                onStart = { draw() },
            )
        }

        if (showMore) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showMore = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                ) {
                    Text(
                        "名单管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "当前名单 · $currentList（${members.size} 人）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                drawn[currentList] = emptySet()
                            },
                            enabled = drawnSet.isNotEmpty(),
                        ) {
                            Text("重置本轮")
                        }
                    }

                    OutlinedTextField(
                        value = editingText,
                        onValueChange = { text ->
                            editingText = text
                            lists[currentList] = parseMembers(text)
                            saveLists()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        placeholder = { Text("每行一个名字，也可以用逗号、顿号分隔") },
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                nameInput = ""
                                dialog = PickerDialog.CREATE
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("新建名单")
                        }
                        OutlinedButton(
                            onClick = {
                                nameInput = currentList
                                dialog = PickerDialog.RENAME
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("重命名")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { dialog = PickerDialog.RANGE },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("按学号生成")
                        }
                        OutlinedButton(
                            onClick = { dialog = PickerDialog.DELETE },
                            enabled = lists.size > 1,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("删除")
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "抽取记录",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { history.clear() },
                            enabled = history.isNotEmpty(),
                        ) {
                            Text("清空")
                        }
                    }
                    if (history.isEmpty()) {
                        Text(
                            "还没有抽取记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        history.forEach { record ->
                            Text(
                                "${record.mode} · ${record.names.joinToString("、")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }

        when (dialog) {
            PickerDialog.CREATE, PickerDialog.RENAME -> {
                val isCreate = dialog == PickerDialog.CREATE
                val trimmed = nameInput.trim()
                val confirmEnabled = trimmed.isNotEmpty() &&
                    if (isCreate) trimmed !in lists else (trimmed == currentList || trimmed !in lists)
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(if (isCreate) "新建名单" else "重命名名单") },
                    text = {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            singleLine = true,
                            label = { Text("名单名称") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = confirmEnabled,
                            onClick = {
                                if (isCreate) {
                                    lists[trimmed] = emptyList()
                                    currentList = trimmed
                                    results = emptyList()
                                } else if (trimmed != currentList) {
                                    val old = currentList
                                    val renamed = lists.map {
                                        if (it.key == old) trimmed to it.value else it.key to it.value
                                    }
                                    lists.clear()
                                    lists.putAll(renamed)
                                    drawn[trimmed] = drawn.remove(old).orEmpty()
                                    currentList = trimmed
                                }
                                saveLists()
                                dialog = null
                            },
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialog = null }) { Text("取消") }
                    },
                )
            }

            PickerDialog.DELETE -> {
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text("删除名单") },
                    text = { Text("删除「$currentList」？名单里的名字会一起清空。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                lists.remove(currentList)
                                drawn.remove(currentList)
                                currentList = lists.keys.first()
                                results = emptyList()
                                saveLists()
                                dialog = null
                            },
                        ) {
                            Text("删除")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialog = null }) { Text("取消") }
                    },
                )
            }

            PickerDialog.RANGE -> {
                val min = minInput.toIntOrNull()
                val max = maxInput.toIntOrNull()
                val valid = min != null && max != null && max >= min && max - min < 1000
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text("按学号生成名单") },
                    text = {
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = minInput,
                                    onValueChange = { minInput = it.filter { c -> c.isDigit() } },
                                    singleLine = true,
                                    label = { Text("最小") },
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = maxInput,
                                    onValueChange = { maxInput = it.filter { c -> c.isDigit() } },
                                    singleLine = true,
                                    label = { Text("最大") },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "会新建一份「学号 $minInput-$maxInput」的名单",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = valid,
                            onClick = {
                                if (min != null && max != null) {
                                    val listName = "学号 $min-$max"
                                    lists[listName] = (min..max).map { it.toString() }
                                    currentList = listName
                                    results = emptyList()
                                    saveLists()
                                    dialog = null
                                }
                            },
                        ) {
                            Text("生成")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialog = null }) { Text("取消") }
                    },
                )
            }

            null -> Unit
        }
    }
}

/** 点名 / 抽奖 分段切换：选中项下方一条主题色短横线 */
@Composable
private fun ModeSwitcher(
    selected: PickerMode,
    onSelect: (PickerMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .width(220.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .selectableGroup(),
    ) {
        PickerMode.entries.forEachIndexed { index, item ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(cs.outlineVariant.copy(alpha = 0.6f)),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = item == selected,
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (item != selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(item)
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.label,
                    fontSize = 15.sp,
                    fontWeight = if (item == selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (item == selected) cs.onSurface else cs.onSurfaceVariant,
                )
                if (item == selected) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                            .width(28.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(cs.primary),
                    )
                }
            }
        }
    }
}

/** 中间结果区：空态「别紧张」，抽取中滚动名字，结束后大字展示 */
@Composable
private fun PickerDisplay(
    results: List<String>,
    rolling: Boolean,
    noMembers: Boolean,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val idle = results.isEmpty()
    val scale by animateFloatAsState(
        targetValue = if (rolling) 0.97f else 1f,
        animationSpec = tween(160),
        label = "pickerResultScale",
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (idle) "别紧张" else results.joinToString("、"),
                fontSize = if (results.size <= 2) 44.sp else 28.sp,
                lineHeight = if (results.size <= 2) 56.sp else 40.sp,
                fontWeight = FontWeight.Bold,
                color = if (idle) cs.onSurfaceVariant.copy(alpha = 0.35f) else cs.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.scale(scale),
            )
            if (idle && noMembers) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "先在「更多」里添加名单成员",
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** 底部控制面板：当前名单 / 抽取数量 / 更多 / 开始 / 人数状态 */
@Composable
private fun ControlPanel(
    listNames: List<String>,
    currentList: String,
    count: Int,
    maxCount: Int,
    memberCount: Int,
    remainingCount: Int,
    rolling: Boolean,
    onSelectList: (String) -> Unit,
    onCountChange: (Int) -> Unit,
    onMore: () -> Unit,
    onStart: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 16.dp),
    ) {
        Text(
            "当前名单",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NameListDropdown(
                names = listNames,
                current = currentList,
                onSelect = onSelectList,
                modifier = Modifier.weight(1f),
            )
            Text(
                count.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(24.dp),
            )
            StepButton(
                icon = Icons.Filled.Remove,
                contentDescription = "减少抽取数量",
                enabled = count > 1 && !rolling,
                onClick = { onCountChange((count - 1).coerceAtLeast(1)) },
            )
            StepButton(
                icon = Icons.Filled.Add,
                contentDescription = "增加抽取数量",
                enabled = count < maxCount && !rolling,
                onClick = { onCountChange((count + 1).coerceAtMost(maxCount)) },
            )
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onMore,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = cs.surfaceContainerHigh,
                contentColor = cs.onSurface,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
        ) {
            Text("更多", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onStart,
            enabled = memberCount > 0 && !rolling,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(if (rolling) "抽取中…" else "开始", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "成员数量: $memberCount | 剩余数量: $remainingCount",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun NameListDropdown(
    names: List<String>,
    current: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(shape)
                .border(1.dp, cs.outlineVariant.copy(alpha = 0.7f), shape)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                current,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "切换名单",
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            names.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(name)
                    },
                    trailingIcon = if (name == current) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun StepButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(cs.surfaceContainerHigh.copy(alpha = if (enabled) 1f else 0.45f))
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.55f), shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp),
        )
    }
}
