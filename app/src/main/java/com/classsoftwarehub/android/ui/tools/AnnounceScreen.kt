package com.classsoftwarehub.android.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.layout.PaddingValues
import com.classsoftwarehub.android.appContainer
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.data.model.AnnounceDevice

/**
 * 教室喊话（手机 → 教室电脑，只发送）。
 * 电脑端打开「内置工具 → 教室喊话」后即可接收：全屏弹窗 + 语音播报，播完自动消失。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnounceScreen() {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: AnnounceViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AnnounceViewModel(container.announceRepository) }
        },
    )

    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var codeInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val selectedCount = devices.count { it.selected }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        ) {
            // 说明
            GlassCard(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                contentPadding = PaddingValues(0.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "手机给教室电脑喊话",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "电脑端打开「内置工具 → 教室喊话」并保持联网即可接收：全屏弹窗大字展示 + 语音播报，" +
                            "播完自动消失。手机只负责发送 —— 先添加电脑显示的 6 位配对码，再从手机发一句话。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 目标设备
            GlassCard(contentPadding = PaddingValues(0.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "目标设备（已选 $selectedCount / ${devices.size}）",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (devices.size > 1) {
                            TextButton(onClick = { viewModel.setAllSelected(selectedCount < devices.size) }) {
                                Text(if (selectedCount < devices.size) "全选" else "全不选")
                            }
                        }
                        if (viewModel.busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        IconButton(onClick = viewModel::refreshStatuses) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新设备状态")
                        }
                    }

                    if (devices.isEmpty()) {
                        Text(
                            "还没有配对的电脑：在电脑端打开「教室喊话」查看 6 位配对码，输入下方后添加。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        devices.forEachIndexed { index, device ->
                            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp))
                            DeviceRow(
                                device = device,
                                status = viewModel.statusOf(device.code),
                                onToggle = { checked -> viewModel.toggle(device.code, checked) },
                                onRemove = { viewModel.remove(device.code) },
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { input ->
                                codeInput = input.filter { it.isDigit() }.take(6)
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("配对码（6 位数字）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.addDevice(codeInput)
                                codeInput = ""
                            },
                            enabled = codeInput.length == 6 && !viewModel.busy,
                        ) {
                            Text("添加")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 喊话内容
            GlassCard(contentPadding = PaddingValues(0.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "喊话内容",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = viewModel.content,
                        onValueChange = viewModel::onContentChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        placeholder = { Text("例如：张老师请到教务处一趟") },
                        supportingText = { Text("${viewModel.content.length} / 100") },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.from,
                        onValueChange = viewModel::onFromChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("署名（选填）") },
                        placeholder = { Text("如：教务处") },
                        supportingText = { Text("展示为「××× 说」") },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "展示时长",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnnounceViewModel.DURATION_OPTIONS.forEach { seconds ->
                            FilterChip(
                                selected = viewModel.duration == seconds,
                                onClick = { viewModel.onDurationChange(seconds) },
                                label = { Text("$seconds 秒") },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = viewModel::send,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !viewModel.sending &&
                            viewModel.content.isNotBlank() &&
                            selectedCount > 0,
                    ) {
                        if (viewModel.sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (viewModel.sending) "发送中…" else "发送喊话")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "电脑大约每 30 秒轮询一次，发送后最迟一个间隔内弹出。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(navBottom + 24.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBottom + 12.dp),
        )
    }
}

@Composable
private fun DeviceRow(
    device: AnnounceDevice,
    status: String,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = device.selected, onCheckedChange = onToggle)
        Column(Modifier.weight(1f)) {
            Text(
                text = if (device.name.isBlank()) "未命名电脑" else device.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "配对码 ${device.code}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = when (status) {
                "在线" -> Color(0xFF4CAF50)
                "离线" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = "移除设备",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
