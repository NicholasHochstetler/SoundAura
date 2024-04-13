/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.addbutton

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.Overlay
import com.cliffracertech.soundaura.ui.defaultSpring
import com.cliffracertech.soundaura.ui.tweenDuration

enum class ExpandableButtonState {
    Visible, Expanded, Hidden;

    val isVisible get() = this == Visible
    val isExpanded get() = this == Expanded
    val isHidden get() = this == Hidden

    val toggledExpansion get() = if (isVisible) Expanded
                                 else           Visible
}

@Composable fun ExpandableButton(
    state: ExpandableButtonState,
    onClick: () -> Unit,
    onClickDescriptionProvider: @Composable () -> String?,
    onOverlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Float) -> Unit = { expansionProgressProvider ->
        Icon(imageVector = Icons.Default.Add,
             contentDescription = onClickDescriptionProvider(),
             modifier = Modifier.rotate(
                 if (state.isExpanded) expansionProgressProvider() * 45.0f
                 else 90.0f - expansionProgressProvider() * 45.0f),
             tint = MaterialTheme.colors.onPrimary)
    }, backgroundColor: Color = MaterialTheme.colors.primary,
    expandedContent: List<@Composable (Modifier) -> Unit> = emptyList(),
) {
    val expansionProgress by animateFloatAsState(
        targetValue = if (state.isExpanded) 1f else 0f,
        animationSpec = defaultSpring(),
        label = "add button expand animation progress",)

    Overlay(
        show = state.isExpanded,
        appearanceProgressProvider = { expansionProgress },
        onClick = onOverlayClick,
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End,
        ) {
            if (expansionProgress > 0f) {
                for (i in (0..expandedContent.lastIndex)) {
                    val contentItem = expandedContent[i]
                    Spacer(Modifier.height(6.dp))
                    contentItem(Modifier.graphicsLayer {
                        translationY = 6.dp.toPx() * (expandedContent.size - i) * (1f - expansionProgress)
                        alpha = expansionProgress
                    })
                }
                Spacer(Modifier.height(6.dp))
            }

            val enterSpec = tween<Float>(
                durationMillis = tweenDuration,
                easing = LinearOutSlowInEasing)
            val exitSpec = tween<Float>(
                durationMillis = tweenDuration,
                delayMillis = tweenDuration / 3,
                easing = LinearOutSlowInEasing)
            AnimatedVisibility(
                visible = !state.isHidden,
                enter = fadeIn(enterSpec) + scaleIn(enterSpec, initialScale = 0.8f),
                exit = fadeOut(exitSpec) + scaleOut(exitSpec, targetScale = 0.8f),
            ) {
                FloatingActionButton(
                    onClick = onClick,
                    backgroundColor = backgroundColor,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                    ), content = { icon { expansionProgress } })
            }
        }
    }
}