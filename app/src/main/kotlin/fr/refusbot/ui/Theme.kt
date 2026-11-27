package fr.refusbot.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.refusbot.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF075477), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F3FA), onPrimaryContainer = Color(0xFF062B3C),
    inversePrimary = Color(0xFF9EDFF0),
    secondary = Color(0xFF087F9B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F4F7), onSecondaryContainer = Color(0xFF073B46),
    tertiary = Color(0xFF956316), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBC7), onTertiaryContainer = Color(0xFF493000),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF1F5F8), onBackground = Color(0xFF101E2C),
    surface = Color(0xFFF9FCFE), onSurface = Color(0xFF101E2C),
    surfaceVariant = Color(0xFFE5EDF3), onSurfaceVariant = Color(0xFF435563),
    outline = Color(0xFF718591), outlineVariant = Color(0xFFC6D4DC),
    scrim = Color.Black, surfaceTint = Color(0xFF075477),
    surfaceBright = Color(0xFFF9FCFE), surfaceDim = Color(0xFFDCE5EA),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F7FA),
    surfaceContainer = Color(0xFFEBF2F6), surfaceContainerHigh = Color(0xFFE3EDF2),
    surfaceContainerHighest = Color(0xFFDCE7ED),
    inverseSurface = Color(0xFF243642), inverseOnSurface = Color(0xFFEAF3F7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FE3F5), onPrimary = Color(0xFF032530),
    primaryContainer = Color(0xFF0A465B), onPrimaryContainer = Color(0xFFD5F7FC),
    inversePrimary = Color(0xFF39758B),
    secondary = Color(0xFF8CEBFA), onSecondary = Color(0xFF073447),
    secondaryContainer = Color(0xFF07545D), onSecondaryContainer = Color(0xFFC0F3F5),
    tertiary = Color(0xFFF2BE5C), onTertiary = Color(0xFF3C2600),
    tertiaryContainer = Color(0xFF5A421E), onTertiaryContainer = Color(0xFFFFE9C2),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF040D18), onBackground = Color(0xFFEFF7FB),
    surface = Color(0xFF071624), onSurface = Color(0xFFEFF7FB),
    surfaceVariant = Color(0xFF1A3042), onSurfaceVariant = Color(0xFFB7CBD7),
    outline = Color(0xFF8498A6), outlineVariant = Color(0xFF344B5C),
    scrim = Color.Black, surfaceTint = Color(0xFF59DDF2),
    surfaceBright = Color(0xFF263D50), surfaceDim = Color(0xFF061321),
    surfaceContainerLowest = Color(0xFF030B14), surfaceContainerLow = Color(0xFF0A1C2D),
    surfaceContainer = Color(0xFF0E2336), surfaceContainerHigh = Color(0xFF142D44),
    surfaceContainerHighest = Color(0xFF213D53),
    inverseSurface = Color(0xFFE3EDF2), inverseOnSurface = Color(0xFF263642),
)

/** Typographie CYRAVA : titres plus denses et affirmés, libellés légèrement espacés. */
private val BaseTypography = Typography()

/** Typographie CYRAVA : titres plus denses et affirmés, libellés légèrement espacés. */
private val CyravaTypography = Typography(
    displaySmall = BaseTypography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.25).sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelSmall = BaseTypography.labelSmall.copy(letterSpacing = 0.6.sp),
)

private val CyravaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun RefusBotTheme(mode: ThemeMode = ThemeMode.SYSTEME, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEME -> isSystemInDarkTheme()
        ThemeMode.CLAIR -> false
        ThemeMode.SOMBRE -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = CyravaTypography,
        shapes = CyravaShapes,
        content = content,
    )
}
