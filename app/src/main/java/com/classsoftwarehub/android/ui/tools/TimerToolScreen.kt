@file:OptIn(ExperimentalMaterial3Api::class)

package com.classsoftwarehub.android.ui.tools

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import com.classsoftwarehub.android.appContainer
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.util.Formatters
import kotlinx.coroutines.delay

/** 课堂计时：倒计时 / 秒表（计次） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerToolScreen() {
    var savedTab by rememberSaveable { mutableStateOf(0) }
    // 旧版本存过 tab=2（时钟已移除），夹回有效范围
    val tab = savedTab.coerceIn(0, 1)

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("倒计时", "秒表").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { savedTab = index },
                    text = { Text(label) },
                )
            }
        }
        when (tab) {
            0 -> CountdownPanel()
            else -> StopwatchPanel()
        }
    }
}

@Composable
private fun CountdownPanel() {
    val context = LocalContext.current
    val container = remember { context.appContainer }

    var minutesText by rememberSaveable { mutableStateOf("5") }
    var totalMillis by remember { mutableStateOf(5 * 60 * 1000L) }
    var remainingMillis by remember { mutableStateOf(5 * 60 * 1000L) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var endAt by remember { mutableStateOf(0L) }

    LaunchedEffect(running) {
        if (running) {
            endAt = SystemClock.elapsedRealtime() + remainingMillis
            while (running && remainingMillis > 0) {
                delay(100)
                remainingMillis = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            }
            if (remainingMillis <= 0L) {
                running = false
                finished = true
                vibrate(context)
                container.downloadNotifications.notifyTimerFinished("倒计时已结束")
            }
        }
    }

    fun applyMinutes(minutes: Int) {
        minutesText = minutes.toString()
        val millis = minutes * 60_000L
        totalMillis = millis
        remainingMillis = millis
        running = false
        finished = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = if (totalMillis > 0) {
                    (remainingMillis.toFloat() / totalMillis).coerceIn(0f, 1f)
                } else 0f,
                modifier = Modifier.size(220.dp),
                strokeWidth = 10.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Formatters.formatDuration(remainingMillis),
                    style = MaterialTheme.typography.displayMedium,
                )
                if (finished) {
                    Text(
                        "时间到！",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 5, 10).forEach { minutes ->
                FilterChip(
                    selected = minutesText == minutes.toString(),
                    onClick = { applyMinutes(minutes) },
                    label = { Text("$minutes 分钟") },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = minutesText,
                onValueChange = { value ->
                    minutesText = value.filter { it.isDigit() }
                    val minutes = minutesText.toIntOrNull() ?: 0
                    totalMillis = minutes * 60_000L
                    remainingMillis = totalMillis
                    running = false
                    finished = false
                },
                label = { Text("分钟") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp),
            )
            Button(
                onClick = {
                    if (remainingMillis <= 0) return@Button
                    running = !running
                    finished = false
                },
            ) {
                Text(if (running) "暂停" else "开始")
            }
            OutlinedButton(onClick = { applyMinutes(minutesText.toIntOrNull() ?: 5) }) {
                Text("重置")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 一条计次记录：第 N 次按下时的总时长，以及距上一次的间隔 */
private data class CountRecord(val index: Int, val totalMillis: Long, val gapMillis: Long)

@Composable
private fun StopwatchPanel() {
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    var startAt by remember { mutableStateOf(0L) }
    val counts = remember { mutableStateListOf<CountRecord>() }

    LaunchedEffect(running) {
        if (running) {
            startAt = SystemClock.elapsedRealtime() - elapsed
            while (running) {
                delay(50)
                elapsed = SystemClock.elapsedRealtime() - startAt
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            Formatters.formatDurationWithMillis(elapsed),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "已计 ${counts.size} 次",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { running = !running }) {
                Text(if (running) "暂停" else if (elapsed == 0L) "开始" else "继续")
            }
            OutlinedButton(
                onClick = {
                    counts.add(
                        CountRecord(
                            index = counts.size + 1,
                            totalMillis = elapsed,
                            gapMillis = elapsed - (counts.lastOrNull()?.totalMillis ?: 0L),
                        ),
                    )
                },
                enabled = running,
            ) {
                Text("计次")
            }
            OutlinedButton(onClick = {
                running = false
                elapsed = 0L
                counts.clear()
            }) {
                Text("重置")
            }
        }

        if (counts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            GlassCard(contentPadding = PaddingValues(0.dp)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    // 最新一次在最上面
                    counts.asReversed().forEach { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "第 ${record.index} 次",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    Formatters.formatDurationWithMillis(record.totalMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (record.index > 1) {
                                    Text(
                                        "间隔 ${Formatters.formatDurationWithMillis(record.gapMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun vibrate(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300, 200, 500), -1)
        )
    }
}
