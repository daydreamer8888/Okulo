package com.example.okulo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal const val PAGE_TRANSITION_MILLIS = 200
private const val FADE_OUT_MILLIS = 80
private const val FADE_IN_MILLIS = 120

@Composable
internal fun <T> PageTransition(page: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = page,
        modifier = modifier,
        transitionSpec = {
            val enter = fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS))
            val exit = fadeOut(tween(FADE_OUT_MILLIS))
            (enter togetherWith exit).using(null)
        },
        label = "page-transition"
    ) { visible -> content(visible) }
}
