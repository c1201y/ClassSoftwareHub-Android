package com.classsoftwarehub.android.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.classsoftwarehub.android.ui.components.FilterPill
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.util.units.UnitCategory
import com.classsoftwarehub.android.util.units.UnitConverter

/** 单位换算：长度 / 质量 / 面积 / 容积 / 温度 / 数据 / 时间 / 速度 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnitConvertScreen() {
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current

    var categoryName by rememberSaveable { mutableStateOf(UnitCategory.LENGTH.name) }
    val category = UnitCategory.valueOf(categoryName)
    var fromId by rememberSaveable(categoryName) {
        mutableStateOf(UnitConverter.defaultFrom(category).id)
    }
    var toId by rememberSaveable(categoryName) {
        mutableStateOf(UnitConverter.defaultTo(category).id)
    }
    var input by rememberSaveable { mutableStateOf("1") }

    val units = UnitConverter.unitsOf(category)
    val fromUnit = UnitConverter.find(category, fromId) ?: UnitConverter.defaultFrom(category)
    val toUnit = UnitConverter.find(category, toId) ?: UnitConverter.defaultTo(category)
    val value = input.trim().toDoubleOrNull()
    val result = value?.let { UnitConverter.convert(category, fromUnit.id, toUnit.id, it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(UnitCategory.entries.toList()) { item ->
                FilterPill(
                    text = item.label,
                    selected = item == category,
                    onClick = { categoryName = item.name },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            OutlinedTextField(
                value = input,
                onValueChange = { raw ->
                    input = raw.filterIndexed { index, ch ->
                        ch.isDigit() || ch == '.' || (ch == '-' && index == 0)
                    }
                },
                label = { Text("数值") },
                suffix = { Text(fromUnit.label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            UnitPicker(
                title = "从",
                units = units.map { it.id to it.label },
                selectedId = fromUnit.id,
                onSelect = { fromId = it },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val previousFrom = fromId
                        fromId = toId
                        toId = previousFrom
                    },
                ) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = "交换单位",
                        tint = cs.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                HorizontalDivider(Modifier.weight(1f))
            }

            UnitPicker(
                title = "到",
                units = units.map { it.id to it.label },
                selectedId = toUnit.id,
                onSelect = { toId = it },
            )
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "换算结果",
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.primary,
                    modifier = Modifier.weight(1f),
                )
                if (result != null) {
                    IconButton(onClick = {
                        clipboard.setText(
                            AnnotatedString(
                                "${UnitConverter.formatNumber(value!!)} ${fromUnit.label}" +
                                    " = ${UnitConverter.formatNumber(result)} ${toUnit.label}",
                            ),
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

            Spacer(Modifier.height(6.dp))

            if (result == null) {
                Text(
                    "输入数字后自动换算",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            } else {
                Text(
                    text = UnitConverter.formatNumber(result),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onBackground,
                )
                Text(
                    text = toUnit.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${UnitConverter.formatNumber(value!!)} ${fromUnit.label}" +
                        " = ${UnitConverter.formatNumber(result)} ${toUnit.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            Text(
                "说明",
                style = MaterialTheme.typography.titleSmall,
                color = cs.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "数据换算按 1 KB = 1024 B 的二进制口径；温度在摄氏度、华氏度与开尔文之间换算；" +
                    "亩按 1 亩 ≈ 666.67 平方米计算，市斤按 500 克计算。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitPicker(
    title: String,
    units: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            units.forEach { (id, label) ->
                FilterPill(
                    text = label,
                    selected = id == selectedId,
                    onClick = { onSelect(id) },
                )
            }
        }
    }
}
