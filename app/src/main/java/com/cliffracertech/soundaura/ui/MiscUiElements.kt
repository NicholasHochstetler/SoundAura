/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ButtonElevation
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.rememberMutableIntStateOf
import com.cliffracertech.soundaura.rememberMutableStateOf
import kotlinx.coroutines.delay

internal const val tweenDuration = 250// * 4
internal const val springStiffness = 700f // 30f

fun <T> defaultSpring() = spring<T>(stiffness = springStiffness)

fun Modifier.minTouchTargetSize() =
    this.sizeIn(minWidth = 48.dp, minHeight = 48.dp)

/**
 * An [AnimatedContent] with predefined slide left/right transitions.
 * @param targetState The key that will cause a change in the SlideAnimatedContent's
 *     content when its value changes.
 * @param modifier The [Modifier] that will be applied to the content.
 * @param leftToRight Whether the existing content should slide off screen
 *     to the left with the new content sliding in from the right, or the
 *     other way around.
 * @param content The composable that itself composes the contents depending
 *     on the value of [targetState], e.g. if (targetState) A() else B().
 */
@Composable fun<S> SlideAnimatedContent(
    targetState: S,
    modifier: Modifier = Modifier,
    leftToRight: Boolean,
    content: @Composable (AnimatedVisibilityScope.(S) -> Unit)
) = AnimatedContent(
    targetState, modifier,
    transitionSpec = {
        val enterOffset = { size: Int -> size / if (leftToRight) 1 else -1 }
        val exitOffset = { size: Int -> size / if (leftToRight) -4 else 4 }
        slideInHorizontally(defaultSpring(), enterOffset) togetherWith
        slideOutHorizontally(defaultSpring(), exitOffset)
    }, label = "SlideAnimatedContent left/right slide transition",
    content = content)

/** Add a vertical divider to the [Row]. The divider will take
 * up a fraction of the [Row]'s height equal to [heightFraction]. */
@Composable fun RowScope.VerticalDivider(
    modifier: Modifier = Modifier,
    heightFraction: Float = 1f,
) = Box(modifier
    .width((1.5).dp).fillMaxHeight(heightFraction)
    .align(Alignment.CenterVertically)
    .background(LocalContentColor.current.copy(alpha = 0.2f)))

/** Add a horizontal divider to the [Column]. The divider will take
 * up a fraction of the [Column]'s width equal to [widthFraction]. */
@Composable fun ColumnScope.HorizontalDivider(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
) = Box(modifier
    .fillMaxWidth(widthFraction).height((1.5).dp)
    .align(Alignment.CenterHorizontally)
    .background(LocalContentColor.current.copy(alpha = 0.2f)))

/**
 * Display a single line [Text] that, when width restrictions prevent the
 * whole line from being visible, automatically scrolls to its end, springs
 * back to its beginning, and repeats this cycle indefinitely. The parameters
 * mirror those of [Text], except that the maxLines and the softWrap parameters
 * are unable to be changed, and the additional [maxWidth] parameter. If the
 * available horizontal space is known at the composition site, this can be
 * passed in as the value of [maxWidth] to prevent [MarqueeText] from
 * needing to calculate this itself.
 */
@Composable fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxWidth: Dp? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    val content = @Composable { maxWidth: Dp ->
        val scrollState = rememberScrollState()
        var shouldAnimate by rememberMutableStateOf(true)
        var animationDuration by rememberMutableIntStateOf(0)
        if (animationDuration > 0)
            LaunchedEffect(shouldAnimate) {
                scrollState.animateScrollTo(scrollState.maxValue,
                    tween(animationDuration, 2000, LinearEasing))
                delay(2000)
                scrollState.animateScrollTo(0)
                shouldAnimate = !shouldAnimate
            }
        val density = LocalDensity.current
        Text(text, Modifier.horizontalScroll(scrollState, false),
            color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing,
            textDecoration, textAlign, lineHeight, overflow, maxLines = 1,
            onTextLayout = {
                onTextLayout(it)
                val overflowAmount = it.size.width -
                    with(density) { maxWidth.roundToPx() }
                animationDuration = overflowAmount.coerceAtLeast(0) * 10
            }, style = style)
    }
    if (maxWidth == null)
        BoxWithConstraints(modifier) { content(this.maxWidth) }
    else Box(modifier) { content(maxWidth) }
}

/** The same as an [androidx.compose.material.TextButton], except that the
 * inner contents are set to be a [Text] composable that displays [text]. */
@Composable fun TextButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    elevation: ButtonElevation? = null,
    shape: Shape = MaterialTheme.shapes.small,
    border: BorderStroke? = null,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    text: String,
    onClick: () -> Unit,
) = androidx.compose.material.TextButton(
    onClick, modifier, enabled, interactionSource,
    elevation, shape, border, colors, contentPadding
) { Text(text) }

/** The same as an [androidx.compose.material.TextButton], except that
 * the inner contents are set to be a [Text] composable that displays
 * the string pointed to by [textResId]. */
@Composable fun TextButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    elevation: ButtonElevation? = null,
    shape: Shape = MaterialTheme.shapes.small,
    border: BorderStroke? = null,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    @StringRes textResId: Int,
    onClick: () -> Unit,
) = TextButton(
    modifier, enabled, interactionSource, elevation,
    shape, border, colors, contentPadding,
    stringResource(textResId), onClick)

/** The same as an [androidx.compose.material.IconButton], except
 * that the inner contents are set to be an [Icon] composable that
 * uses [icon], [contentDescription], and [tint]. */
@Composable fun SimpleIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    tint: Color = LocalContentColor.current,
    iconPadding: Dp = 10.dp,
    onClick: () -> Unit,
) = IconButton(
    onClick, modifier, enabled, interactionSource,
) {
    Icon(icon, contentDescription,
         Modifier.padding(iconPadding), tint)
}

/**
 * Show a clickable overlay over the maximum allowed size if [show] is true.
 * [appearanceProgressProvider] should be a method that returns the current
 * progress of the overlay's show/hide animation, e.g. using [animateFloatAsState].
 * When [show] is true, clicks on the overlay will invoke [onClick]. The
 * provided [content] will be aligned inside the overlay according to the
 * value of [contentAlignment].
 *
 */
@Composable fun Overlay(
    show: Boolean,
    appearanceProgressProvider: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) = Box(modifier = modifier
    .fillMaxSize()
    .drawBehind {
        drawRect(Color.Black, alpha = appearanceProgressProvider() / 2f)
    }.then(                 // Disabled clickable modifiers still consume taps, so we
        if (!show) Modifier // have to add or remove the clickable modifier as necessary.
        else Modifier.clickable(
            remember{ MutableInteractionSource() },
            indication = null, onClick = onClick)),
    contentAlignment = contentAlignment,
    content = content
)
