/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.mediacontroller

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cliffracertech.soundaura.R

/** A collection of state and callbacks related to a play/pause button.
 * The icon for the button should be chosen depending on the value of the
 * property [isPlaying]. The onClick action, onClick label resource id, the
 * onLongClick action, and the onLongClick label resource id can be accessed
 * through the properties [onClick], [clickLabelResId], [onLongClick], and
 * [longClickLabelResId], respectively. */
class PlayButtonState(
    private val isPlayingProvider: () -> Boolean,
    val onClick: () -> Unit,
    private val clickLabelResIdProvider: (isPlaying: Boolean) -> Int,
    val onLongClick: () -> Unit,
    val longClickLabelResId: Int
) {
    val isPlaying get() = isPlayingProvider()
    val clickLabelResId get() = clickLabelResIdProvider(isPlaying)
}

/**
 * A combination play/pause button with both click and long click actions.
 *
 * @param state The [PlayButtonState] used for the state of the button
 * @param modifier The [Modifier] to use for the button
 */
@Composable fun PlayButton(
    state: PlayButtonState,
    modifier: Modifier = Modifier,
) = Box(contentAlignment = Alignment.Center,
    modifier = modifier.combinedClickable(
        onClick = state.onClick,
        onClickLabel = stringResource(state.clickLabelResId),
        onLongClick = state.onLongClick,
        onLongClickLabel = stringResource(state.longClickLabelResId)),
) {
    val playToPause = AnimatedImageVector.animatedVectorResource(R.drawable.play_to_pause)
    val playToPausePainter = rememberAnimatedVectorPainter(playToPause, atEnd = state.isPlaying)
    val pauseToPlay = AnimatedImageVector.animatedVectorResource(R.drawable.pause_to_play)
    val pauseToPlayPainter = rememberAnimatedVectorPainter(pauseToPlay, atEnd = !state.isPlaying)
    val painter = if (state.isPlaying) playToPausePainter
                  else                 pauseToPlayPainter
    Icon(painter, null)
}