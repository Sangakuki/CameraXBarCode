/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sangakuki.camerax.barcode.extensions

import android.graphics.Rect
import android.view.View
import androidx.camera.core.AspectRatio
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Combination of all flags required to put activity into immersive mode */
const val FLAGS_FULLSCREEN = View.SYSTEM_UI_FLAG_LOW_PROFILE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

const val IMMERSIVE_FLAG_TIMEOUT = 500L
const val RATIO_4_3_VALUE = 4.0 / 3.0
const val RATIO_16_9_VALUE = 16.0 / 9.0

/**
 *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
 *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
 *
 *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
 *  of preview ratio to one of the provided values.
 *
 *  @param width - preview width
 *  @param height - preview height
 *  @return suitable aspect ratio
 */
fun aspectRatio(width: Int, height: Int): Int {
    val previewRatio = max(width, height).toDouble() / min(width, height)
    if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
        return AspectRatio.RATIO_4_3
    }
    return AspectRatio.RATIO_16_9
}

/**
 * 取景框Rect计算方法
 */
fun Rect.cropRect(): Rect {
    val frameWidth = width().coerceAtMost(height()) * 0.7f
    val frameLeft = centerX() - frameWidth * 0.5f
    val frameRight = centerX() + frameWidth * 0.5f
    val frameTop = centerY() - frameWidth * 0.5f
    val frameBottom = centerY() + frameWidth * 0.5f
    return Rect(frameLeft.toInt(), frameTop.toInt(), frameRight.toInt(), frameBottom.toInt())
}