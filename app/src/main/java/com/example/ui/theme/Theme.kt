package com.example.ui.theme

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = OnIndigoPrimaryDark,
    primaryContainer = IndigoPrimaryContainerDark,
    onPrimaryContainer = OnIndigoPrimaryContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = OnSlateSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = OnSlateSecondaryContainerDark,
    tertiary = CyberTealTertiaryDark,
    onTertiary = OnCyberTealTertiaryDark,
    tertiaryContainer = CyberTealContainerDark,
    onTertiaryContainer = OnCyberTealContainerDark,
    background = ExpressiveSurfaceDark,
    surface = ExpressiveSurfaceDark,
    surfaceContainer = ExpressiveSurfaceContainerDark,
    surfaceContainerHigh = ExpressiveSurfaceContainerHighDark,
    outline = ExpressiveOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = OnIndigoPrimaryLight,
    primaryContainer = IndigoPrimaryContainerLight,
    onPrimaryContainer = OnIndigoPrimaryContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = OnSlateSecondaryLight,
    secondaryContainer = SlateSecondaryContainerLight,
    onSecondaryContainer = OnSlateSecondaryContainerLight,
    tertiary = CyberTealTertiaryLight,
    onTertiary = OnCyberTealTertiaryLight,
    tertiaryContainer = CyberTealContainerLight,
    onTertiaryContainer = OnCyberTealContainerLight,
    background = ExpressiveSurfaceLight,
    surface = ExpressiveSurfaceLight,
    surfaceContainer = ExpressiveSurfaceContainerLight,
    surfaceContainerHigh = ExpressiveSurfaceContainerHighLight,
    outline = ExpressiveOutlineLight
)

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun CodeChainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CodeChainTypography,
        shapes = ExpressiveShapes,
        content = content
    )
}
