package com.classsoftwarehub.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.classsoftwarehub.android.data.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = TealOnPrimaryContainer,
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = TealOnPrimaryContainer,
    tertiary = TealTertiary,
    background = LightBackground,
    onBackground = Color(0xFF15201F),
    surface = LightSurface,
    onSurface = Color(0xFF15201F),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF5C6D6D),
    surfaceTint = TealPrimary,
    surfaceDim = Color(0xFFE4EEEE),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF8FBFB),
    surfaceContainerHigh = Color(0xFFF1F6F6),
    surfaceContainerHighest = Color(0xFFEAF1F1),
    outline = Color(0xFFB6C8C8),
    outlineVariant = Color(0xFFDCE8E8),
    inverseSurface = Color(0xFF23302F),
    inverseOnSurface = Color(0xFFEFF5F4),
    inversePrimary = BluePrimaryDark,
    scrim = Color(0xFF000000),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColorScheme = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = BlueOnPrimaryDark,
    primaryContainer = BluePrimaryContainerDark,
    onPrimaryContainer = BlueOnPrimaryContainerDark,
    secondary = Color(0xFF67E8F9),
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF155E75),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFFFCD34D),
    background = DarkBackground,
    onBackground = Color(0xFFE3ECEC),
    surface = DarkSurface,
    onSurface = Color(0xFFE3ECEC),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF9FB3B3),
    surfaceTint = BluePrimaryDark,
    surfaceDim = Color(0xFF070D0E),
    surfaceBright = Color(0xFF2C393B),
    surfaceContainerLowest = Color(0xFF070D0E),
    surfaceContainerLow = Color(0xFF0F1819),
    surfaceContainer = Color(0xFF141E1F),
    surfaceContainerHigh = Color(0xFF192425),
    surfaceContainerHighest = Color(0xFF202D2F),
    outline = Color(0xFF4A6063),
    outlineVariant = Color(0xFF2A3B3D),
    inverseSurface = Color(0xFFE3ECEC),
    inverseOnSurface = Color(0xFF192425),
    inversePrimary = TealPrimary,
    scrim = Color(0xFF000000),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** 顶部圆角的大卡片、底栏、对话框统一口径 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ClassSoftwareHubTheme(
    themeMode: ThemeMode,
    dynamicColorEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val view = LocalView.current
    val colorScheme = when {
        dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    if (!view.isInEditMode) {
        SideEffect {
            val activity = context as? Activity ?: return@SideEffect
            val window = activity.window
            // HyperOS/MIUI 在透明导航栏下会自作主张画一块白色背板，盖在磨砂底栏上。
            // 必须给不透明、与页面背景一致的颜色；图标深浅与对比度背板同步关闭。
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
