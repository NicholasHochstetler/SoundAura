/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.mediacontroller

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.rememberDerivedStateOf
import com.cliffracertech.soundaura.ui.tweenDuration

/** A [MediaController] with state provided by an instance of [MediaControllerViewModel]. */
@Composable fun BoxWithConstraintsScope.SoundAuraMediaController(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(),
    alignment: BiasAlignment = Alignment.BottomStart as BiasAlignment,
) = CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onPrimary) {
    val ld = LocalLayoutDirection.current
    val alignToEnd = alignment == Alignment.TopEnd

    val contentAreaSize = remember(padding) {
        val startPadding = padding.calculateStartPadding(ld)
        val endPadding = padding.calculateEndPadding(ld)
        val topPadding = padding.calculateTopPadding()
        val bottomPadding = padding.calculateBottomPadding()
        DpSize(maxWidth - startPadding - endPadding,
               maxHeight - topPadding - bottomPadding)
    }

    val sizes = remember(padding, alignment) {
        // The goal is to have the media controller have such a length that
        // the play/pause icon is centered in the content area's width in
        // portrait mode, or centered in the content area's height in
        // landscape mode. This preferred length is found by adding half of
        // the play/pause button's size and the stop timer display's length
        // (in case it needs to be displayed) to half of the length of the
        // content area. The min value between this preferred length and the
        // full content area length minus 64dp (i.e. the add button's 56dp
        // size plus an 8dp margin) is then used to ensure that for small
        // screen sizes the media controller can't overlap the add button.
        val playButtonLength = MediaControllerSizes.defaultPlayButtonLengthDp.dp
        val dividerThickness = MediaControllerSizes.dividerThicknessDp.dp
        val stopTimerLength =
            if (alignToEnd) MediaControllerSizes.defaultStopTimerHeightDp.dp
            else            MediaControllerSizes.defaultStopTimerWidthDp.dp
        val extraLength = playButtonLength / 2f + stopTimerLength
        val length = if (alignToEnd) contentAreaSize.height / 2f + extraLength
                     else            contentAreaSize.width / 2f + extraLength
        val maxLength = if (alignToEnd) contentAreaSize.height - 64.dp
                        else            contentAreaSize.width - 64.dp
        val activePresetLength = minOf(length, maxLength) - playButtonLength -
                                 dividerThickness - stopTimerLength
        MediaControllerSizes(
            orientation = if (alignToEnd) Orientation.Vertical
                          else            Orientation.Horizontal,
            activePresetLength = activePresetLength,
            presetSelectorSize = DpSize(
                width = contentAreaSize.width * if (alignToEnd) 0.6f
                                                else            1.0f,
                height = if (!alignToEnd) 350.dp
                         else contentAreaSize.height))
    }

    val viewModel: MediaControllerViewModel = viewModel()

    val startColor = MaterialTheme.colors.primaryVariant
    val endColor = MaterialTheme.colors.secondaryVariant
    val backgroundBrush = remember(startColor, endColor) {
        Brush.horizontalGradient(colors = listOf(startColor, endColor))
    }

    val enterSpec = tween<Float>(
        durationMillis = tweenDuration,
        delayMillis = tweenDuration / 3,
        easing = LinearOutSlowInEasing)
    val exitSpec = tween<Float>(
        durationMillis = tweenDuration,
        easing = LinearOutSlowInEasing)
    val hasStopTime by rememberDerivedStateOf { viewModel.state.stopTime != null }
    val transformOrigin = rememberClippedBrushBoxTransformOrigin(
        alignment = alignment,
        padding = padding,
        dpSize = sizes.collapsedSize(hasStopTime))

    AnimatedVisibility(
        visible = !viewModel.state.visibility.isHidden,
        enter = fadeIn(enterSpec) + scaleIn(enterSpec, 0.8f, transformOrigin),
        exit = fadeOut(exitSpec) + scaleOut(exitSpec, 0.8f, transformOrigin),
        modifier = modifier,
    ) {
        MediaController(
            sizes = sizes,
            state = viewModel.state,
            backgroundBrush = backgroundBrush,
            alignment = alignment,
            padding = padding)
    }
    DialogShower(viewModel.shownDialog)
}