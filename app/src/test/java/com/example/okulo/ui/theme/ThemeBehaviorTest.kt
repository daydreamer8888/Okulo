package com.example.okulo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeBehaviorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun defaultThemesKeepAllNeutralRolesAchromaticAndTextReadable() {
        val dark = mutableStateOf(false)
        lateinit var colors: ColorScheme
        compose.setContent { OkuloTheme(darkTheme = dark.value) { colors = MaterialTheme.colorScheme } }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark }
            compose.runOnIdle {
                neutralRoles(colors).forEach { (role, color) ->
                    assertEquals(role, color.red, color.green, 0f)
                    assertEquals(role, color.green, color.blue, 0f)
                }
                textPairs(colors).forEach { (background, foreground) ->
                    val luminances = listOf(background.luminance(), foreground.luminance())
                    val contrast = (luminances.max() + 0.05f) / (luminances.min() + 0.05f)
                    assertTrue("Text contrast $contrast in dark=$isDark", contrast >= 4.5f)
                }
                assertTrue(colors.error.red > colors.error.green)
            }
        }
    }

    private fun neutralRoles(c: ColorScheme): Map<String, Color> = mapOf(
        "primary" to c.primary,
        "onPrimary" to c.onPrimary,
        "primaryContainer" to c.primaryContainer,
        "onPrimaryContainer" to c.onPrimaryContainer,
        "inversePrimary" to c.inversePrimary,
        "secondary" to c.secondary,
        "onSecondary" to c.onSecondary,
        "secondaryContainer" to c.secondaryContainer,
        "onSecondaryContainer" to c.onSecondaryContainer,
        "tertiary" to c.tertiary,
        "onTertiary" to c.onTertiary,
        "tertiaryContainer" to c.tertiaryContainer,
        "onTertiaryContainer" to c.onTertiaryContainer,
        "background" to c.background,
        "onBackground" to c.onBackground,
        "surface" to c.surface,
        "onSurface" to c.onSurface,
        "surfaceVariant" to c.surfaceVariant,
        "onSurfaceVariant" to c.onSurfaceVariant,
        "surfaceTint" to c.surfaceTint,
        "inverseSurface" to c.inverseSurface,
        "inverseOnSurface" to c.inverseOnSurface,
        "outline" to c.outline,
        "outlineVariant" to c.outlineVariant,
        "scrim" to c.scrim,
        "surfaceBright" to c.surfaceBright,
        "surfaceDim" to c.surfaceDim,
        "surfaceContainerLowest" to c.surfaceContainerLowest,
        "surfaceContainerLow" to c.surfaceContainerLow,
        "surfaceContainer" to c.surfaceContainer,
        "surfaceContainerHigh" to c.surfaceContainerHigh,
        "surfaceContainerHighest" to c.surfaceContainerHighest
    )

    private fun textPairs(c: ColorScheme): List<Pair<Color, Color>> = listOf(
        c.primary to c.onPrimary,
        c.primaryContainer to c.onPrimaryContainer,
        c.secondary to c.onSecondary,
        c.secondaryContainer to c.onSecondaryContainer,
        c.tertiary to c.onTertiary,
        c.tertiaryContainer to c.onTertiaryContainer,
        c.surface to c.onSurface,
        c.surfaceVariant to c.onSurfaceVariant,
        c.inverseSurface to c.inverseOnSurface,
        c.error to c.onError,
        c.errorContainer to c.onErrorContainer
    )
}
