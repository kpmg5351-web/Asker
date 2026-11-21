package com.example.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Immutable
data class HesabatyUiScale(
    val isNarrow: Boolean = false,
    val isCompact: Boolean = false,
    val typeScale: Float = 1f,
    val screenPadding: Dp = 16.dp,
    val cardPadding: Dp = 16.dp,
    val sectionSpacing: Dp = 12.dp
)

@Immutable
data class HesabatyColors(
    val success: Color,
    val successContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val wallet: Color,
    val walletContainer: Color,
    val cardBorder: Color,
    val subtleText: Color
)

private val LightSemanticColors = HesabatyColors(
    success = SuccessGreen,
    successContainer = SuccessGreenLight,
    danger = DangerRed,
    dangerContainer = DangerRedLight,
    warning = WarningAmber,
    warningContainer = WarningAmberLight,
    wallet = EWalletPurple,
    walletContainer = EWalletPurpleLight,
    cardBorder = LightOutline,
    subtleText = LightTextSecondary
)

private val DarkSemanticColors = HesabatyColors(
    success = Color(0xFF4ADE80),
    successContainer = SuccessGreenDark,
    danger = Color(0xFFF87171),
    dangerContainer = DangerRedDark,
    warning = Color(0xFFFBBF24),
    warningContainer = WarningAmberDark,
    wallet = Color(0xFFA78BFA),
    walletContainer = EWalletPurpleDark,
    cardBorder = DarkOutline,
    subtleText = DarkTextSecondary
)

val LocalHesabatyColors = staticCompositionLocalOf { LightSemanticColors }
val LocalHesabatyUiScale = staticCompositionLocalOf { HesabatyUiScale() }

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryLightContainer,
    onPrimaryContainer = PrimaryDarkBlue,
    secondary = PrimaryDarkBlue,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightTextPrimary,
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRedLight,
    onErrorContainer = DangerRed
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimaryBlue,
    onPrimary = Color(0xFF061B3E),
    primaryContainer = PrimaryDarkContainer,
    onPrimaryContainer = Color(0xFFD7E6FF),
    secondary = PrimaryBlue,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = Color(0xFF4ADE80),
    onTertiary = Color(0xFF042611),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = Color(0xFFF87171),
    onError = Color(0xFF3E0B0E),
    errorContainer = DangerRedDark,
    onErrorContainer = Color(0xFFFECACA)
)

private val HesabatyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun HesabatyTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors
    val baseDensity = LocalDensity.current
    val normalizedDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale.coerceIn(0.88f, 1.12f)
    )

    BoxWithConstraints(propagateMinConstraints = true) {
        val uiScale = when {
            maxWidth < 340.dp -> HesabatyUiScale(
                isNarrow = true,
                isCompact = true,
                typeScale = 0.78f,
                screenPadding = 10.dp,
                cardPadding = 10.dp,
                sectionSpacing = 8.dp
            )
            maxWidth < 372.dp -> HesabatyUiScale(
                isNarrow = false,
                isCompact = true,
                typeScale = 0.85f,
                screenPadding = 12.dp,
                cardPadding = 12.dp,
                sectionSpacing = 10.dp
            )
            maxWidth < 396.dp -> HesabatyUiScale(
                isNarrow = false,
                isCompact = true,
                typeScale = 0.92f,
                screenPadding = 14.dp,
                cardPadding = 14.dp,
                sectionSpacing = 11.dp
            )
            else -> HesabatyUiScale()
        }
        val typography = scaledHesabatyTypography(uiScale.typeScale)

        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalHesabatyColors provides semanticColors,
            LocalHesabatyUiScale provides uiScale,
            LocalDensity provides normalizedDensity
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = typography,
                shapes = HesabatyShapes,
                content = content
            )
        }
    }
}

object HesabatyThemeExtras {
    val colors: HesabatyColors
        @Composable get() = LocalHesabatyColors.current

    val uiScale: HesabatyUiScale
        @Composable get() = LocalHesabatyUiScale.current
}
