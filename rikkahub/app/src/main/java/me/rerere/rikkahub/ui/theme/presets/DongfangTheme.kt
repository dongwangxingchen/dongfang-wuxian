package me.rerere.rikkahub.ui.theme.presets

import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.PresetTheme

/**
 * [DFWX PATCH P17] 东方无限品牌预设主题：色板对齐宿主 ThemeEngine
 * （BG #0B0A12 / SURFACE #16141F / PRIMARY #A78BFA / TEXT #F2F0F7 / MUTED #9A93AB / DIV #262332），
 * 让内嵌的 AI 界面与宿主观感一致。注册于 PresetThemes 首位 = 全新安装默认主题。
 * 同步上游时需重放（新增文件 + PresetThemes 列表首位插入）。
 */
val DongfangThemePreset by lazy {
    PresetTheme(
        id = "dfwx",
        name = {
            Text(stringResource(id = R.string.app_name))
        },
        standardLight = dongfangLightScheme,
        standardDark = dongfangDarkScheme,
    )
}

private val primaryLight = Color(0xFF6B4FD8)
private val onPrimaryLight = Color(0xFFFFFFFF)
private val primaryContainerLight = Color(0xFFE7DEFF)
private val onPrimaryContainerLight = Color(0xFF24005D)
private val secondaryLight = Color(0xFF5F5A72)
private val onSecondaryLight = Color(0xFFFFFFFF)
private val secondaryContainerLight = Color(0xFFE5DFF5)
private val onSecondaryContainerLight = Color(0xFF1B1729)
private val tertiaryLight = Color(0xFF3B6569)
private val onTertiaryLight = Color(0xFFFFFFFF)
private val tertiaryContainerLight = Color(0xFFBFEAEF)
private val onTertiaryContainerLight = Color(0xFF001F22)
private val errorLight = Color(0xFFB03D2E)
private val onErrorLight = Color(0xFFFFFFFF)
private val errorContainerLight = Color(0xFFF8E1DC)
private val onErrorContainerLight = Color(0xFF7E2418)
private val backgroundLight = Color(0xFFF4F2FA)
private val onBackgroundLight = Color(0xFF1B1826)
private val surfaceLight = Color(0xFFF4F2FA)
private val onSurfaceLight = Color(0xFF1B1826)
private val surfaceVariantLight = Color(0xFFE6E1F2)
private val onSurfaceVariantLight = Color(0xFF4A4460)
private val outlineLight = Color(0xFF766F92)
private val outlineVariantLight = Color(0xFFE6E1F2)
private val scrimLight = Color(0xFF000000)
private val inverseSurfaceLight = Color(0xFF2E2A3D)
private val inverseOnSurfaceLight = Color(0xFFF4F2FA)
private val inversePrimaryLight = Color(0xFFA78BFA)
private val surfaceDimLight = Color(0xFFDCD8E8)
private val surfaceBrightLight = Color(0xFFF4F2FA)
private val surfaceContainerLowestLight = Color(0xFFFFFFFF)
private val surfaceContainerLowLight = Color(0xFFF0EDF8)
private val surfaceContainerLight = Color(0xFFEAE6F4)
private val surfaceContainerHighLight = Color(0xFFE4E0EF)
private val surfaceContainerHighestLight = Color(0xFFDED9E9)

private val primaryDark = Color(0xFFA78BFA)
private val onPrimaryDark = Color(0xFF221A3D)
private val primaryContainerDark = Color(0xFF3B2E63)
private val onPrimaryContainerDark = Color(0xFFE9DEFB)
private val secondaryDark = Color(0xFFC9C2DA)
private val onSecondaryDark = Color(0xFF322C44)
private val secondaryContainerDark = Color(0xFF3A3450)
private val onSecondaryContainerDark = Color(0xFFE6DFF5)
private val tertiaryDark = Color(0xFF8FD0C6)
private val onTertiaryDark = Color(0xFF003736)
private val tertiaryContainerDark = Color(0xFF1F4E50)
private val onTertiaryContainerDark = Color(0xFFBFEAEF)
private val errorDark = Color(0xFFFFB4AB)
private val onErrorDark = Color(0xFF690005)
private val errorContainerDark = Color(0xFF93000A)
private val onErrorContainerDark = Color(0xFFFFDAD6)
private val backgroundDark = Color(0xFF0B0A12)
private val onBackgroundDark = Color(0xFFF2F0F7)
private val surfaceDark = Color(0xFF0B0A12)
private val onSurfaceDark = Color(0xFFF2F0F7)
private val surfaceVariantDark = Color(0xFF262332)
private val onSurfaceVariantDark = Color(0xFF9A93AB)
private val outlineDark = Color(0xFF4A4460)
private val outlineVariantDark = Color(0xFF262332)
private val scrimDark = Color(0xFF000000)
private val inverseSurfaceDark = Color(0xFFF2F0F7)
private val inverseOnSurfaceDark = Color(0xFF0B0A12)
private val inversePrimaryDark = Color(0xFF6B4FD8)
private val surfaceDimDark = Color(0xFF0B0A12)
private val surfaceBrightDark = Color(0xFF3A3550)
private val surfaceContainerLowestDark = Color(0xFF08070E)
private val surfaceContainerLowDark = Color(0xFF16141F)
private val surfaceContainerDark = Color(0xFF1C1928)
private val surfaceContainerHighDark = Color(0xFF242032)
private val surfaceContainerHighestDark = Color(0xFF2C2840)

private val dongfangLightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val dongfangDarkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)
