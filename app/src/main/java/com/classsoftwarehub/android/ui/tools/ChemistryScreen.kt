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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.classsoftwarehub.android.util.math.BalanceResult
import com.classsoftwarehub.android.util.math.ChemistryBalancer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val chemistryPresets = listOf(
    "Fe + O2 = Fe2O3",
    "H2 + O2 = H2O",
    "C3H8 + O2 = CO2 + H2O",
    "KMnO4 + HCl = KCl + MnCl2 + Cl2 + H2O",
    "Ca(OH)2 + H3PO4 = Ca3(PO4)2 + H2O",
    "CuSO4·5H2O = CuSO4 + H2O",
)

/** 化学方程式配平：按元素守恒求最小整数系数，并列出左右核对表 */
@Composable
fun ChemistryScreen() {
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current

    var input by rememberSaveable { mutableStateOf("Fe + O2 = Fe2O3") }
    var result by remember { mutableStateOf<BalanceResult?>(null) }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(input) {
        if (input.isBlank()) {
            result = null
            working = false
            return@LaunchedEffect
        }
        delay(250)
        working = true
        result = withContext(Dispatchers.Default) { ChemistryBalancer.balance(input) }
        working = false
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
            label = { Text("化学方程式（反应物 = 生成物）") },
            placeholder = { Text("例如：Fe + O2 = Fe2O3") },
            minLines = 2,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chemistryPresets) { preset ->
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
                    "配平结果",
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.primary,
                    modifier = Modifier.weight(1f),
                )
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (current?.ok == true) {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(current.equation))
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "复制方程式",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                current == null -> Text(
                    "输入方程式后会自动配平",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )

                !current.ok -> Text(
                    current.error ?: "无法配平这个方程式",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.error,
                )

                else -> {
                    Text(
                        current.equation,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = cs.primary,
                    )
                    if (!current.note.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
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

        if (current?.ok == true && current.checks.isNotEmpty()) {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "元素守恒核对",
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "元素",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "反应物",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "生成物",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                current.checks.forEachIndexed { index, check ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 5.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            check.element,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            check.left.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            check.right.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (check.left == check.right) cs.primary else cs.error,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        GlassCard {
            Text(
                "使用说明",
                style = MaterialTheme.typography.titleSmall,
                color = cs.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "支持括号 Ca(OH)2、结晶水 CuSO4·5H2O、方括号 [Fe(CN)6]，以及 = / → / ⇌ 三种箭头。" +
                    "左侧写反应物、右侧写生成物；输入里自带的数字系数会被忽略，由程序重新计算。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
