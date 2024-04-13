/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.mediacontroller

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.model.database.Preset
import com.cliffracertech.soundaura.rememberDerivedStateOf
import com.cliffracertech.soundaura.rememberMutableStateOf
import com.cliffracertech.soundaura.ui.MarqueeText
import com.cliffracertech.soundaura.ui.Overlay
import com.cliffracertech.soundaura.ui.defaultSpring
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant
import java.time.temporal.ChronoUnit

val Orientation.isHorizontal get() = this == Orientation.Horizontal
val Orientation.isVertical get() = this == Orientation.Vertical

fun Modifier.rotateClockwise() = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(
        minWidth = constraints.minHeight, maxWidth = constraints.maxHeight,
        minHeight = constraints.minWidth, maxHeight = constraints.maxWidth))
    layout(placeable.height, placeable.width) {
        placeable.place(
            x = -(placeable.width / 2 - placeable.height / 2),
            y = -(placeable.height / 2 - placeable.width / 2))
    }
}.rotate(90f)

/** A collection of state related to the display of an active preset. The
 * name of the active preset, or null if there isn't one, can be accessed
 * through the [name] property. Whether or not the active preset has been
 * modified since it was last saved can be accessed through the property
 * [isModified]. The property [onClick] should be used as the onClick
 * callback for the active preset display. */
class ActivePresetViewState(
    private val nameProvider: () -> String?,
    private val isModifiedProvider: () -> Boolean,
    val onClick: () -> Unit,
) {
    val name get() = nameProvider()
    val isModified get() = isModifiedProvider()
}

@Composable private fun ActivePresetView(
    sizes: MediaControllerSizes,
    state: ActivePresetViewState,
    modifier: Modifier = Modifier,
) {
    val onClickLabel = stringResource(R.string.preset_button_click_label)
    val columnModifier = remember(modifier, sizes.orientation) {
        modifier.size(sizes.activePresetSize)
                .clip(sizes.activePresetShape)
                .clickable(true, onClickLabel, Role.Button, state.onClick)
                .then(if (sizes.orientation.isHorizontal)
                          Modifier.padding(start = 12.dp, end = 8.dp)
                    else Modifier.padding(top = 12.dp, bottom = 8.dp)
                                   .rotateClockwise())
    }
    Column(columnModifier, Arrangement.Center, Alignment.CenterHorizontally) {
        val style = MaterialTheme.typography.caption
        Text(text = stringResource(R.string.playing),
             maxLines = 1, style = style, softWrap = false)
        Row {
            MarqueeText(
                text = state.name ?: stringResource(R.string.unsaved_preset_description),
                maxWidth = sizes.activePresetLength,
                modifier = Modifier.weight(1f, false),
                style = style)
            AnimatedVisibility(state.isModified) {
                Text(" *", style = style)
            }
        }
    }
}

/** A wrapper around [StopTimer] that animates its appearance and
 * disappearance as it changes between null and non-null values. */
@Composable private fun MediaControllerStopTimer(
    sizes: MediaControllerSizes,
    stopTimeProvider: () -> Instant?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val stopTime = stopTimeProvider()
    // lastNonNullStopTime is used so that the StopTimerDisplay will
    // fade out with its last non-null value when it changes to null.
    var lastNonNullStopTime by rememberMutableStateOf(Instant.MIN)
    stopTime?.let { lastNonNullStopTime = it }

    val appearanceProgress by animateFloatAsState(
        targetValue = if (stopTime != null) 1f else 0f,
        animationSpec = defaultSpring(),
        label = "Auto stop time appearance transition")
    if (appearanceProgress == 0f) return

    val translationPercent = (1f - appearanceProgress) / -2f
    val size = sizes.stopTimerSize
    val clickLabel = stringResource(R.string.stop_timer_click_label)

    LinearLayout(
        orientation = sizes.orientation,
        modifier = modifier
            .requiredSize(size)
            .graphicsLayer {
                alpha = appearanceProgress
                translationX = if (sizes.orientation.isVertical) 0f else
                                   translationPercent * size.width.toPx()
                translationY = if (sizes.orientation.isHorizontal) 0f else
                                   translationPercent * size.height.toPx()
            }.clip(sizes.stopTimerShape)
            .clickable(true, clickLabel, Role.Button, onClick),
    ) {
        Divider(sizes.orientation, sizeFraction = 0.8f)
        StopTimer(lastNonNullStopTime, Modifier.fillMaxSize())
    }
}

/**
 * The content of a [MediaController] instance when it is collapsed
 * (i.e. not showing the preset selector).
 *
 * @param sizes The [MediaControllerSizes] instance that describes
 *     the sizes of [MediaController]'s internal elements.
 * @param transitionProgressProvider A method that returns the
 *     current progress of the [MediaController]'s show/hide
 *     preset selector transition when invoked
 * @param activePresetState The [ActivePresetViewState] that
 *     will be used for the active preset indicator
 * @param playButtonState The [PlayButtonState] that will be used for the play/pause button
 * @param stopTimeProvider A method that will return the [Instant] at which
 *     media will automatically stop playing, if any, when invoked. This value
 *     is only used for informational display; playback is not affected by
 *     this value.
 * @param onStopTimerClick The callback that will be invoked when
 *     the display of the stop time is clicked
 * @param modifier The [Modifier] to use for the composable
 */
@Composable private fun MediaControllerCollapsedContent(
    sizes: MediaControllerSizes,
    transitionProgressProvider: () -> Float,
    activePresetState: ActivePresetViewState,
    playButtonState: PlayButtonState,
    stopTimeProvider: () -> Instant?,
    onStopTimerClick: () -> Unit,
    modifier: Modifier = Modifier,
) = LinearLayout(
    orientation = sizes.orientation,
    modifier = modifier
        .graphicsLayer { alpha = 1f - transitionProgressProvider() }
) {
    ActivePresetView(sizes, activePresetState)
    Divider(sizes.orientation, sizeFraction = 0.8f)
    PlayButton(
        state = playButtonState,
        modifier = Modifier
            .size(sizes.buttonSize)
            .clip(sizes.playButtonShape(stopTimeProvider() != null)))
    MediaControllerStopTimer(sizes, stopTimeProvider,
                             onClick = onStopTimerClick)
}

/**
 * The content of a [MediaController] instance when it is expanded
 * (i.e. showing the preset selector.
 *
 * @param sizes The [MediaControllerSizes] instance that describes
 *     the sizes of [MediaController]'s internal elements.
 * @param onCloseButtonClick The method that will be invoked when
 *     the close button of the preset selector title is clicked
 * @param transitionProgressProvider A method that returns the
 *     current progress of the [MediaController]'s show/hide
 *     preset selector transition when invoked
 * @param modifier The [Modifier] to use for the composable
 */
@Composable private fun PresetSelectorTitle(
    sizes: MediaControllerSizes,
    onCloseButtonClick: () -> Unit,
    transitionProgressProvider: () -> Float,
    modifier: Modifier = Modifier,
) = Box(modifier
    .height(sizes.minThickness)
    .fillMaxWidth()      // This end padding makes the close button align
    .padding(end = 8.dp) // with the more options buttons for each preset
    .graphicsLayer { alpha = transitionProgressProvider() }
) {
    Text(text = stringResource(R.string.preset_selector_title),
        modifier = Modifier.align(Alignment.Center)
            .requiredWidth(sizes.presetSelectorSize.width),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.h6)
    IconButton(onCloseButtonClick, Modifier.align(Alignment.CenterEnd)) {
        Icon(Icons.Default.Close,
            stringResource(R.string.close_preset_selector_description))
    }
}

/** A wrapper around [PresetList] that scales its size
 * with the [MediaController]'s expansion progress */
@Composable private fun MediaControllerPresetList(
    sizes: MediaControllerSizes,
    hasStopTimer: Boolean,
    backgroundBrush: Brush,
    transitionProgressProvider: () -> Float,
    activePresetState: ActivePresetViewState,
    presetListState: PresetListState,
) {
    val listPadding = 8.dp
    val presetListSize = remember(sizes) {
        val expandedTitleHeight = sizes.minThickness
        val expandedWidth = sizes.presetSelectorSize.width
        val expandedHeight = sizes.presetSelectorSize.height
        DpSize(expandedWidth - listPadding * 2,
               expandedHeight - expandedTitleHeight - listPadding)
    }
    val minScaleX = remember(sizes, hasStopTimer) {
        val collapsedWidth = sizes.collapsedSize(hasStopTimer).width
        (collapsedWidth - listPadding * 2) / presetListSize.width
    }
    if (transitionProgressProvider() > 0f)
        PresetList(
            modifier = Modifier
                .requiredSize(presetListSize)
                .graphicsLayer {
                    alpha = transitionProgressProvider()
                    scaleY = transitionProgressProvider()
                    scaleX = minScaleX + (1f - minScaleX) * transitionProgressProvider()
                }.background(MaterialTheme.colors.surface,
                             MaterialTheme.shapes.large),
            contentPadding = PaddingValues(bottom = 64.dp),
            activePresetState = activePresetState,
            selectionBrush = backgroundBrush,
            state = presetListState)
}

/** A state holder for an expandable media controller. The states for the media
 * controller's sub-components are exposed through the properties [activePreset],
 * [playButton], and [presetList]. The expanded/collapsed/hidden state is
 * accessed through the property [visibility]. The onClick callbacks for the
 * close button and the overlay that should be shown when [visibility] is
 * [Visibility.Expanded] should be set to the properties [onCloseButtonClick]
 * and [onOverlayClick], respectively.
 *
 * If the property [stopTime] returns a non-null [Instant], then the duration
 * remaining until the stop time should be displayed in a clickable display
 * next to the play/pause button, with the onClick action set to the property
 * [onStopTimerClick]. */
class MediaControllerState(
    val activePreset: ActivePresetViewState,
    val playButton: PlayButtonState,
    val presetList: PresetListState,
    private val stopTimeProvider: () -> Instant?,
    val onStopTimerClick: () -> Unit,
    private val visibilityProvider: () -> Visibility,
    val onCloseButtonClick: () -> Unit,
    val onOverlayClick: () -> Unit,
) {
    enum class Visibility { Hidden, Collapsed, Expanded;
        val isHidden get() = this == Hidden
        val isCollapsed get() = this == Collapsed
        val isExpanded get() = this == Expanded
    }

    val stopTime get() = stopTimeProvider()
    val visibility get() = visibilityProvider()
}

/**
 * A floating button that shows information about the currently playing
 * [Preset] and a play/pause button. When the [state]'s [MediaControllerState.visibility]
 * is equal to [MediaControllerState.Visibility.Collapsed], the button will
 * animate its transformation into a preset selector. In this state it
 * will contain a [PresetList] to allow the user to choose a new preset.
 *
 * @param sizes The [MediaControllerSizes] instance that describes the sizes
 *     of [MediaController]'s internal elements.
 * @param state The [MediaControllerState] that will be used for state and callbacks
 * @param backgroundBrush A [Brush] to use as the background. This is passed
 *     as a separate parameter instead of allowing the caller to accomplish
 *     this through a [Modifier] so that the [Brush] can be applied across the
 *     whole parent size, and then clipped down to the size of the contents.
 * @param alignment The [BiasAlignment] to use for placement. This alignment should
 *     not be applied through the [modifier] parameter or it will be applied twice.
 * @param padding The [PaddingValues] to use for placement. This padding should not
 *     be applied through the [modifier] parameter or it will be applied twice.
 * @param modifier The [Modifier] to use for the button / popup
 */
@Composable fun MediaController(
    sizes: MediaControllerSizes,
    state: MediaControllerState,
    backgroundBrush: Brush,
    alignment: BiasAlignment,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val expandTransition = updateTransition(
        targetState = state.visibility.isExpanded,
        label = "MediaController expand transition")
    val expandTransitionProgress by expandTransition.animateFloat(
        transitionSpec = { defaultSpring() },
        label = "MediaController expand transition progress",
        targetValueByState = { if (it) 1f else 0f })
    val transitionProgressProvider = { expandTransitionProgress }
    val hasStopTime by rememberDerivedStateOf { state.stopTime != null }

    Overlay(
        show = state.visibility.isExpanded,
        appearanceProgressProvider = transitionProgressProvider,
        onClick = state.onOverlayClick,
        contentAlignment = alignment,
    ) {
        ClippedBrushBox(
            modifier = modifier,
            brush = backgroundBrush,
            size = sizes.rememberCurrentSize(
                showingPresetSelector = state.visibility.isExpanded,
                hasStopTime = hasStopTime),
            cornerRadius = 28.dp,
            alignment = alignment,
            padding = padding,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val titleHeight by expandTransition.animateDp(
                    transitionSpec = { defaultSpring() },
                    label = "MediaController/preset selector title height transition",
                ) { expanded ->
                    if (!expanded && sizes.orientation.isVertical)
                        sizes.collapsedSize(hasStopTime).height
                    else sizes.minThickness
                }
                Box(Modifier.height(titleHeight)) {
                    if (expandTransitionProgress > 0f)
                        PresetSelectorTitle(
                            sizes, state.onCloseButtonClick,
                            transitionProgressProvider)

                    if (expandTransitionProgress < 1f)
                        MediaControllerCollapsedContent(
                            sizes, transitionProgressProvider,
                            state.activePreset, state.playButton,
                            state::stopTime, state.onStopTimerClick)
                }
                MediaControllerPresetList(
                    sizes, hasStopTime, backgroundBrush,
                    transitionProgressProvider,
                    state.activePreset, state.presetList)
            }
        }
    }
}

@Preview @Composable
fun MediaControllerPreview() = SoundAuraTheme {
    var visibility by rememberMutableStateOf(
        MediaControllerState.Visibility.Collapsed)
    var playing by rememberMutableStateOf(false)
    val list = remember { listOf(
        Preset("Super duper extra really long preset name 0"),
        Preset("Super duper extra really long preset name 1"),
        Preset("Super duper extra really long preset name 2"),
        Preset("Super duper extra really long preset name 3")
    ).toImmutableList() }
    val activePresetName = rememberMutableStateOf<String?>(list.first().name)
    var stopTime by rememberMutableStateOf<Instant?>(null)

    BoxWithConstraints {
        MediaController(
            sizes = MediaControllerSizes(
                activePresetLength = 200.dp - 56.dp,
                orientation = Orientation.Horizontal,
                presetSelectorSize = DpSize(300.dp, 300.dp)),
            state = remember { MediaControllerState(
                ActivePresetViewState(
                    nameProvider = activePresetName::value,
                    isModifiedProvider = { true },
                    onClick = { visibility = MediaControllerState.Visibility.Expanded }),
                PlayButtonState(
                    isPlayingProvider = { playing },
                    onClick = { playing = !playing },
                    clickLabelResIdProvider = { 0 },
                    onLongClick = {
                        stopTime = Instant.now().plus(1, ChronoUnit.HOURS)
                    }, longClickLabelResId = 0),
                PresetListState(
                    listProvider = { list },
                    onRenameClick = {},
                    onOverwriteClick = {},
                    onDeleteClick = {},
                    onClick = { activePresetName.value = it }),
                stopTimeProvider = { stopTime },
                onStopTimerClick = { stopTime = null },
                visibilityProvider = { visibility },
                onCloseButtonClick = { visibility = MediaControllerState.Visibility.Collapsed },
                onOverlayClick = { visibility = MediaControllerState.Visibility.Collapsed }
            )}, backgroundBrush = Brush.horizontalGradient(
                listOf(MaterialTheme.colors.primaryVariant,
                    MaterialTheme.colors.secondaryVariant)),
            alignment = Alignment.BottomStart as BiasAlignment,
            padding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp))
    }
}