package com.example.okulo.camera

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CameraSessionLifetimeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun openingThePhotoPickerPreservesCompositionWhileOrdinaryBackgroundingClearsIt() {
        val session = CameraSession(compose.activity)
        val choosing = mutableStateOf(true)
        session.composition.recommendFrozen({}, 0.2f)
        compose.setContent { CameraSessionLifetime(session, keepComposition = { choosing.value }) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(session.composition.state.value.active)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle { choosing.value = false }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(session.composition.state.value.active)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }
}
