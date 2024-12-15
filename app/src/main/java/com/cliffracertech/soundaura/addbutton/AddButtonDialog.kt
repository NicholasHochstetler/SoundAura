/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.addbutton

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.dialog.AnimatedValidatorMessage
import com.cliffracertech.soundaura.dialog.DialogWidth
import com.cliffracertech.soundaura.dialog.NamingState
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.library.PlaylistOptionsView
import com.cliffracertech.soundaura.ui.HorizontalDivider
import com.cliffracertech.soundaura.ui.SlideAnimatedContent
import com.cliffracertech.soundaura.ui.TextButton
import com.cliffracertech.soundaura.ui.VerticalDivider
import com.cliffracertech.soundaura.ui.bottomEndShape
import com.cliffracertech.soundaura.ui.bottomShape
import com.cliffracertech.soundaura.ui.bottomStartShape
import com.cliffracertech.soundaura.ui.minTouchTargetSize

@Composable private fun ColumnScope.AddButtonDialogButtons(
    state: AddButtonDialogState
) {
    HorizontalDivider()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
        state.buttons.forEachIndexed { index, button ->
            TextButton(
                modifier = Modifier.minTouchTargetSize().weight(1f),
                enabled = button.isEnabledProvider(),
                shape = when {
                    state.buttons.size == 1 ->
                        MaterialTheme.shapes.medium.bottomShape()
                    index == 0 ->
                        MaterialTheme.shapes.medium.bottomStartShape()
                    index == state.buttons.lastIndex ->
                        MaterialTheme.shapes.medium.bottomEndShape()
                    else -> RectangleShape
                }, textResId = button.textResId,
                onClick = button.onClick)
            if (index != state.buttons.lastIndex)
                VerticalDivider()
        }
    }
}

/**
 * When [SystemFileChooser] enters the composition a system file picker
 * will be shown to allow the user to pick one or more files.
 *
 * @param fileTypeArgs An [Array] of [String]s that describes which
 *     file MIME types are allowed to be chosen
 * @param onFilesSelected The callback that will be invoked when one
 *     or more files are picked, represented in the [List] argument
 */
@Composable fun SystemFileChooser(
    fileTypeArgs: Array<String> = arrayOf("audio/*", "application/ogg"),
    onFilesSelected: (List<Uri>) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = onFilesSelected)
    LaunchedEffect(Unit) { launcher.launch(fileTypeArgs) }
}

@Composable private fun AccessAudioFilesPermissionRequester(onPermissionGranted: (Boolean) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onPermissionGranted)
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                         Manifest.permission.READ_MEDIA_AUDIO
                     else Manifest.permission.READ_EXTERNAL_STORAGE
    LaunchedEffect(Unit) { launcher.launch(permission)}
}

@Composable private fun NamingTextField(
    state: NamingState,
    modifier: Modifier,
) = Column(modifier) {
    TextField(
        value = state.name,
        onValueChange = state::onNameChange,
        textStyle = MaterialTheme.typography.body1,
        singleLine = true,
        isError = state.message?.isError == true,
        modifier = Modifier.fillMaxWidth())
    AnimatedValidatorMessage(state.message)
}

/** Show the add button related dialog to the user inside an instance of
 * [SoundAuraDialog]. Nothing will be shown if the value of [state] is one of
 * [AddButtonDialogState]'s values that are not displayed in an instance of
 * [SoundAuraDialog] (generally ones that are shown as Android system dialogs
 * instead, e.g. a permission dialog).*/
@Composable private fun AddButtonSoundAuraDialogShower(state: AddButtonDialogState) =
    SoundAuraDialog(
        width = DialogWidth.MatchToScreenSize(WindowInsets.ime),
        title = stringResource(state.titleResId),
        onDismissRequest = state.onDismissRequest,
        buttons = { AddButtonDialogButtons(state) }
    ) {
        SlideAnimatedContent(
            targetState = state,
            leftToRight = state.wasNavigatedForwardTo,
        ) { state ->
            // This background modifiers gives a border to the content to
            // improve the appearance of the SlideAnimatedContent animations
            val backgroundModifier = Modifier
                .background(MaterialTheme.colors.surface)
                .padding(horizontal = 16.dp)
            when (state) {
                is AddButtonDialogState.SelectingFiles -> {}
                is AddButtonDialogState.RequestStoragePermission -> {}
                is AddButtonDialogState.AddIndividuallyOrAsPlaylistQuery -> {
                    Box(modifier = backgroundModifier.padding(vertical = 16.dp),
                        // The vertical padding is set to match the TextField decoration box's
                        // vertical padding. This reduces the amount that the dialog box height
                        // has to be animated when switching between steps of the dialog.
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(stringResource(state.textResId))
                    }
                } is AddButtonDialogState.NamePreset -> {
                    NamingTextField(state, backgroundModifier)
                } is AddButtonDialogState.NameTracks -> {
                    Column(backgroundModifier) {
                        // We have to restrict the LazyColumn's height to prevent
                        // a crash due to nested infinite height scrollables
                        val maxHeight = (LocalConfiguration.current.screenHeightDp * 2 / 3).dp
                        LazyColumn(
                            modifier = Modifier.heightIn(max = maxHeight),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.names.size) { index ->
                                TextField(
                                    value = state.names[index],
                                    onValueChange = { state.onNameChange(index, it) },
                                    textStyle = MaterialTheme.typography.body1,
                                    singleLine = true,
                                    isError = state.errorIndices.contains(index),
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                        AnimatedValidatorMessage(state.message)
                    }
                } is AddButtonDialogState.NamePlaylist -> {
                    NamingTextField(state, backgroundModifier)
                } is AddButtonDialogState.PlaylistOptions -> {
                    // PlaylistOptions already has its own horizontal padding, so we avoid
                    // using backgroundModifier here to prevent doubling up on the padding
                    Column(Modifier.background(MaterialTheme.colors.surface)) {
                        PlaylistOptionsView(
                            shuffleEnabled = state.shuffleEnabled,
                            onShuffleClick = state.onShuffleSwitchClick,
                            mutablePlaylist = state.mutablePlaylist,
                            onAddButtonClick = null)
                    }
                } is AddButtonDialogState.RequestStoragePermissionExplanation -> {
                    val context = LocalContext.current
                    Box(modifier = backgroundModifier.padding(bottom = 8.dp)) {
                        Text(state.text.resolve(context),
                             textAlign = TextAlign.Justify)
                    }
                }
            }
        }
    }

/** Show an add button related dialog to the user. */
@Composable fun AddButtonDialogShower(state: AddButtonDialogState) {
    when (state) {
        is AddButtonDialogState.SelectingFiles -> {
            SystemFileChooser { uris ->
                if (uris.isEmpty())
                    state.onDismissRequest()
                else state.onFilesSelected(uris)
            }
        } is AddButtonDialogState.RequestStoragePermission -> {
            AccessAudioFilesPermissionRequester(state.onResult)
        } else -> AddButtonSoundAuraDialogShower(state)
   }
}