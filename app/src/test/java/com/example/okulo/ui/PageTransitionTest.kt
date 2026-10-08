package com.example.okulo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pagesFadeInPlaceAndInterruptedNavigationSettlesOnTheLatestPage() {
        val color = mutableStateOf(Color.Blue)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(100.dp).background(Color.White).testTag("transition")) {
                PageTransition(color.value) { visible ->
                    Box(Modifier.fillMaxSize().background(visible).testTag(visible.toArgb().toString()))
                }
            }
        }
        val bounds = compose.onNodeWithTag(Color.Blue.toArgb().toString()).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { color.value = Color.Red }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(40)
        assertEquals(bounds, compose.onNodeWithTag(Color.Blue.toArgb().toString()).fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(64)
        assertEquals(bounds, compose.onNodeWithTag(Color.Red.toArgb().toString()).fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { color.value = Color.Green }
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag(Color.Green.toArgb().toString()).assertExists()
        compose.onNodeWithTag(Color.Blue.toArgb().toString()).assertDoesNotExist()
        compose.onNodeWithTag(Color.Red.toArgb().toString()).assertDoesNotExist()
    }
}
