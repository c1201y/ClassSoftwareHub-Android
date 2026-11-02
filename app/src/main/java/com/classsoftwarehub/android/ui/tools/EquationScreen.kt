package com.classsoftwarehub.android.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classsoftwarehub.android.ui.components.FilterPill
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.util.math.AngleUnit
import com.classsoftwarehub.android.util.math.EquationResult
import com.classsoftwarehub.android.util.math.EquationSolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val equationPresets = listOf(
    "x^2 - 4 = 0",
    "x^2 - 5x + 6 = 0",
    "sin(x) = 0.5",
    "2^x = 8",
    "e^x = 2",
    "ln(x) = 1",
    "x^3 - 3x + 1 = 0",
    "2x + 1 = 5",
)

/** 方程求解：一元方程数值求根，支持三角函数、指数与对数 */
@Composable
fun EquationScreen() {
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current

    var input by rememberSaveable { mutableStateOf("x^2 - 4 = 0") }
    var angleName by rememberSaveable { mutableStateOf(AngleUnit.RADIANS.name) }
    val angleUnit = AngleUnit.valueOf(angleName)

    var result by remember { mutableStateOf<EquationResult?>(null) }
    var solving by remember { mutableStateOf(false) }

    LaunchedEffect(input, angleName) {
        if (input.isBlank()) {
            result = null
            solving = false
            return@LaunchedEffect
        }
        delay(250)
        solving = true
        result = withContext(Dispatchers.Default) {
            EquationSolver.solve(input, angleUnit)
        }
        solving = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("方程（左边 = 右边）") },
            minLines = 2,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("三角函数角度", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            FilterPill(
                text = "弧度",
                selected = angleUnit == AngleUnit.RADIANS,
                onClick = { angleName = AngleUnit.RADIANS.name },
            )
            FilterPill(
                text = "角度",
                selected = angleUnit == AngleUnit.DEGREES,
                onClick = { angleName = AngleUnit.DEGREES.name },
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(equationPresets) { preset ->
                FilterPill(
                    text = preset,
                    selected = input == preset,
                    onClick = { input = preset },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val current = result
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "求解结果",
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.primary,
                    modifier = Modifier.weight(1f),
                )
                if (solving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (current?.ok == true && current.solutions.isNotEmpty()) {
                    IconButton(onClick = {
                        clipboard.setText(
                            AnnotatedString(current.solutions.joinToString("\n")),
                        )
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "复制结果",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                current == null -> Text(
                    "输入方程后会自动求解",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )

                current.error != null -> Text(
                    current.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.error,
                )

                current.solutions.isEmpty() -> Text(
                    current.note ?: "没有找到解",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )

                else -> {
                    current.solutions.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    if (!current.note.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            current.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            Text(
                "使用说明",
                style = MaterialTheme.typography.titleSmall,
                color = cs.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "未知数请用 x；一次解一个一元方程。支持 + - * / ^、pi / e、sin cos tan、ln lg、sqrt 等。" +
                    "结果中的特殊角会以 π 的倍数显示，其余为六位小数的数值近似。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
