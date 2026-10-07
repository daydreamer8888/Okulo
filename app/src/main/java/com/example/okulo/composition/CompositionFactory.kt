package com.example.okulo.composition

import android.content.Context
import com.example.okulo.composition.s2c.S2cCropScorer

fun createCompositionAnalyzer(context: Context): CompositionAnalyzer = CompositionEngine(S2cCropScorer(context))
