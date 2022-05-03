/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.*
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

fun Modifier.minTouchTargetSize() =
    sizeIn(minWidth = 48.dp, minHeight = 48.dp)

/** A modifier that sets the background to be a MaterialTheme.shapes.large
 * shape filled in with a MaterialTheme.colors.surface color. */
fun Modifier.largeSurfaceBackground() = composed {
    background(MaterialTheme.colors.surface, MaterialTheme.shapes.large)
}

/** Return a radio button icon with its checked state set according to the value of @param checked. */
@Composable fun RadioButton(checked: Boolean, modifier: Modifier) {
    val vector = if (checked) Icons.Default.RadioButtonChecked
                 else         Icons.Default.RadioButtonUnchecked
    val desc = stringResource(if (checked) R.string.checked_description
                              else         R.string.unchecked_description)
    Icon(vector, desc, modifier)
}

/**
 * A button that alternates between an empty circle with a plus icon, and
 * a filled circle with a minus icon depending on the parameter checked.
 *
 * @param added The added/removed state of the item the button is
 *     representing. If added == true, the button will display a minus
 *     icon. If added == false, a plus icon will be displayed instead.
 * @param contentDescription The content description of the button.
 * @param backgroundColor The color of the background that the button
 *     is being displayed on. This is used for the inner plus icon
 *     when added == true and the background of the button is filled.
 * @param tint The tint that will be used for the button.
 * @param onClick The callback that will be invoked when the button is clicked.
 */
@Composable fun AddRemoveButton(
    added: Boolean,
    contentDescription: String? = null,
    backgroundColor: Color = MaterialTheme.colors.background,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit
) = IconButton(onClick) {
    // Circle background
    // The combination of the larger tinted circle and the smaller
    // background-tinted circle creates the effect of a circle that
    // animates between filled and outlined.
    Box(Modifier.size(24.dp).background(tint, CircleShape))
    AnimatedVisibility(
        visible = !added,
        enter = scaleIn(overshootTweenSpec()),
        exit = scaleOut(anticipateTweenSpec()),
    ) {
        Box(Modifier.size(20.dp).background(backgroundColor, CircleShape))
    }

    // Plus / minus icon
    val angle by animateFloatAsState(
        targetValue = if (added) 0f else 90f,
        animationSpec = tween())
    val iconTint = if (added) backgroundColor else tint
    val minusIcon = painterResource(R.drawable.minus)

    // One minus icon always appears horizontally, while the other can
    // rotate between 0 and 90 degrees so that both minus icons together
    // appear as a plus icon.
    Icon(minusIcon, null, Modifier.rotate(2 * angle), iconTint)
    Icon(minusIcon, contentDescription, Modifier.rotate(angle), iconTint)
}


@Composable fun PlayPauseIcon(
    playing: Boolean,
    contentDescription: String =
        if (playing) stringResource(R.string.pause_description)
        else         stringResource(R.string.play_description),
    tint: Color,
) {
    val playToPause = AnimatedImageVector.animatedVectorResource(R.drawable.play_to_pause)
    val playToPausePainter = rememberAnimatedVectorPainter(playToPause, atEnd = playing)
    val pauseToPlay = AnimatedImageVector.animatedVectorResource(R.drawable.pause_to_play)
    val pauseToPlayPainter = rememberAnimatedVectorPainter(pauseToPlay, atEnd = !playing)
    Icon(painter = if (playing) playToPausePainter
                   else         pauseToPlayPainter,
         contentDescription = contentDescription,
         tint = tint)
}

/** A simple back arrow IconButton for when only the onClick needs changed. */
@Composable fun BackButton(onClick: () -> Unit) = IconButton(onClick) {
    Icon(Icons.Default.ArrowBack, stringResource(R.string.back_description))
}

/** A simple settings IconButton for when only the onClick needs changed. */
@Composable fun SettingsButton(onClick: () -> Unit) = IconButton(onClick) {
    Icon(Icons.Default.Settings, stringResource(R.string.settings_description))
}

@Composable fun <T>overshootTweenSpec(
    duration: Int = DefaultDurationMillis,
    delay: Int = 0,
) = tween<T>(duration, delay) {
    val t = it - 1
    t * t * (3 * t + 2) + 1
}

@Composable fun <T>anticipateTweenSpec(
    duration: Int = DefaultDurationMillis,
    delay: Int = 0,
) = tween<T>(duration, delay) {
    it * it * (3 * it - 2)
}

/**
 * An AnimatedContent with predefined slide left/right transitions.
 * @param targetState The key that will cause a change in the SlideAnimatedContent's
 *     content when its value changes.
 * @param modifier The modifier that will be applied to the content.
 * @param leftToRight Whether the existing content should be slid off screen
 *     to the left with the new content sliding in from the right, or the
 *     other way around.
 * @param content The composable that itself composes the contents depending
 *     on the value of targetState, e.g. if (targetState) A() else B().
 */
@Composable fun<S> SlideAnimatedContent(
    targetState: S,
    modifier: Modifier = Modifier,
    leftToRight: Boolean,
    content: @Composable (AnimatedVisibilityScope.(S) -> Unit)
) {
    val transition = remember(leftToRight) {
        val enterOffset = { size: Int -> size * if (leftToRight) 1 else -1 }
        val exitOffset = { size: Int -> size * if (leftToRight) -1 else 1 }
        slideInHorizontally(tween(), enterOffset) with
        slideOutHorizontally(tween(), exitOffset)
    }
    AnimatedContent(targetState, modifier, { transition }, content = content)
}