package cn.yyliucha.steamdeals.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = BrandPurpleDim,
    onPrimaryContainer = Color.White,
    secondary = BrandPurple,
    onSecondary = Color.White,
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDarkHigh,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF2A3450),
    error = DangerRed,
    onError = Color.White,
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    inverseSurface = SurfaceLight,
    inverseOnSurface = TextPrimaryLight,
    inversePrimary = BrandPurpleDim,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceDarkHigh,
)

private val LightColors = lightColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E2FF),
    onPrimaryContainer = Color(0xFF2B2670),
    secondary = BrandPurple,
    onSecondary = Color.White,
    background = BgLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFECEFF7),
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFC9CFDD),
    error = DangerRed,
    onError = Color.White,
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    inverseSurface = SurfaceDark,
    inverseOnSurface = TextPrimaryDark,
    inversePrimary = BrandPurple,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = Color(0xFFECEFF7),
)

/** themeMode: system / light / dark（「我的 → 主题」，登录用户同步到 /api/prefs）。 */
@Composable
fun SteamDealsTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
