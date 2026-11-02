package com.classsoftwarehub.android.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── 品牌色（青绿 → 天青）────────────────────────────────
val TealPrimary = Color(0xFF0F766E)
val TealOnPrimary = Color(0xFFFFFFFF)
val TealPrimaryContainer = Color(0xFFCDFAF4)
val TealOnPrimaryContainer = Color(0xFF04312E)
val TealSecondary = Color(0xFF0E7490)
val TealSecondaryContainer = Color(0xFFD3F3FA)
val TealTertiary = Color(0xFFB45309)

val BluePrimaryDark = Color(0xFF5EEAD4)
val BlueOnPrimaryDark = Color(0xFF00352F)
val BluePrimaryContainerDark = Color(0xFF115E59)
val BlueOnPrimaryContainerDark = Color(0xFFCDFAF4)

// ── 背景 / 表面 ────────────────────────────────────────
val LightBackground = Color(0xFFF3F7F7)
val LightBackgroundAlt = Color(0xFFE9F2F2)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE8F0F0)

val DarkBackground = Color(0xFF0A1213)
val DarkBackgroundAlt = Color(0xFF0E1819)
val DarkSurface = Color(0xFF121C1D)
val DarkSurfaceVariant = Color(0xFF1B2729)

// ── 品牌渐变（页头、图标、主按钮共用）──────────────────
val BrandTeal = Color(0xFF0F766E)
val BrandCyan = Color(0xFF0891B2)
val BrandBlue = Color(0xFF2563EB)

fun brandBrush(dark: Boolean = false): Brush = Brush.linearGradient(
    listOf(
        if (dark) Color(0xFF14B8A6) else BrandTeal,
        if (dark) Color(0xFF0EA5E9) else BrandCyan,
    ),
)

fun brandAccentBrush(dark: Boolean = false): Brush = Brush.linearGradient(
    listOf(
        if (dark) Color(0xFF22D3EE) else Color(0xFF0E7490),
        if (dark) Color(0xFF818CF8) else BrandBlue,
    ),
)

// ── 分类标识色（软件卡片 / 分类标签）────────────────────
val CategorySystemMaintenance = Color(0xFF0F766E)
val CategoryTeaching = Color(0xFF2563EB)
val CategoryOffice = Color(0xFF7C3AED)
val CategoryRemote = Color(0xFFEA580C)
val CategoryOther = Color(0xFF475569)
