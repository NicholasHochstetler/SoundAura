/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

/**
 * Show a [PresetList] of [Preset]s (with the currently playing preset, if any,
 * highlighted) for the user to choose from, along with buttons to save changes
 * to the current preset and to create a new preset.
 *
 * @param modifier The [Modifier] to use for the PresetSelector
 * @param onCloseButtonClick The callback that will be invoked when the user
 *     clicks the close button
 * @param backgroundBrush The [Brush] that will be used to paint the
 *     background of the selector
 * @param currentPreset The currently active [Preset], if any
 * @param currentIsModified Whether or not the current preset has been modified
 *     since the last time it was saved
 * @param presetListProvider A lambda that will return the list of presets when invoked
 * @param onPresetClick The callback that will be invoked when the user clicks
 *     on a preset from the list
 * @param onPresetRenameRequest The callback that will be invoked when the user
 *     requests the renaming of the [Preset] parameter to the provided [String] value
 * @param onPresetDeleteRequest The callback that will be invoked when the user
 *     requests the deletion of the [Preset] parameter
 * @param onOverwriteCurrentPresetRequest The callback that will be invoked
 *     when the user requests that the [Preset] parameter has any current
 *     changes saved
 * @param onNewPresetNameChange The callback that will be invoked when the user
 *     has entered a new value in the name field during the creation of a new preset
 * @param newPresetNameValidatorMessage A message describing why the currently
 *     input value for a new [Preset]'s name is not acceptable, or null if the
 *     current name is acceptable.
 * @param onCreateNewPresetRequest The callback that will be invoked when the
 *     user requests the creation of a new [Preset] with the provided name. The
 *     returned value should indicate whether or not the new [Preset] will be
 *     created, in which case the inner [NewPresetDialog] will close, or not
 *     acceptable, in which case the dialog will remain open.
 */
@Composable fun PresetSelector(
    modifier: Modifier = Modifier,
    onCloseButtonClick: () -> Unit,
    backgroundBrush: Brush,
    currentPreset: Preset? = null,
    currentIsModified: Boolean,
    presetListProvider: () -> List<Preset>,
    onPresetClick: (Preset) -> Unit,
    onPresetRenameRequest: (Preset, String) -> Unit,
    onPresetDeleteRequest: (Preset) -> Unit,
    onOverwriteCurrentPresetRequest: () -> Unit,
    onNewPresetNameChange: (String) -> Unit,
    newPresetNameValidatorMessage: String?,
    onCreateNewPresetRequest: (String) -> Boolean,
) = Column(modifier.background(backgroundBrush, MaterialTheme.shapes.large)) {

    Row(modifier = Modifier.padding(start = 16.dp, top = 4.dp,
                                    bottom = 2.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.preset_selector_title),
            style = MaterialTheme.typography.h6)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onCloseButtonClick) {
            Icon(Icons.Default.Close, stringResource(R.string.close_preset_selector_description))
        }
    }
    PresetList(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        shape = MaterialTheme.shapes.medium,
        currentPreset = currentPreset,
        currentIsModified = currentIsModified,
        selectionBrush = backgroundBrush,
        presetListProvider = presetListProvider,
        onPresetClick = onPresetClick,
        onRenameRequest = onPresetRenameRequest,
        onDeleteRequest = onPresetDeleteRequest)

    HorizontalDivider()

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
        var showingOverwritePresetDialog by rememberSaveable { mutableStateOf(false) }
        TextButton(
            onClick = { showingOverwritePresetDialog = true },
            modifier = Modifier.minTouchTargetSize().weight(1f),
            enabled = currentPreset != null,
            shape = MaterialTheme.shapes.large.bottomStartShape(),
        ) {
            Text(stringResource(R.string.save_preset_changes_button_text),
                 color = MaterialTheme.colors.onPrimary)
        }
        VerticalDivider()
        if (showingOverwritePresetDialog && currentPreset != null)
            ConfirmPresetOverwriteDialog(
                currentPresetName = currentPreset.name,
                onDismissRequest = { showingOverwritePresetDialog = false },
                onConfirm = {
                    showingOverwritePresetDialog = false
                    onOverwriteCurrentPresetRequest()
                })

        var showingSaveNewPresetDialog by rememberSaveable { mutableStateOf(false) }
        TextButton(
            onClick = { showingSaveNewPresetDialog = true },
            modifier = Modifier.minTouchTargetSize().weight(1f),
            shape = MaterialTheme.shapes.large.bottomStartShape(),
        ) {
            Text(stringResource(R.string.create_new_preset_button_text),
                 color = MaterialTheme.colors.onPrimary)
        }
        if (showingSaveNewPresetDialog)
            CreateNewPresetDialog(
                onNameChange = onNewPresetNameChange,
                nameValidatorMessage = newPresetNameValidatorMessage,
                onDismissRequest = { showingSaveNewPresetDialog = false },
                onConfirm = {
                    if (onCreateNewPresetRequest(it))
                        showingSaveNewPresetDialog = false
                })
    }
}

/**
 * Show a dialog asking the user to confirm that they would like to overwrite
 * the [Preset] whose name is equal to [currentPresetName] with any current
 * changes. The cancel and ok buttons will invoke [onDismissRequest] and
 * [onConfirm], respectively.
 */
@Composable fun ConfirmPresetOverwriteDialog(
    currentPresetName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) = SoundAuraDialog(
    title = stringResource(R.string.confirm_overwrite_preset_dialog_title),
    text = stringResource(R.string.confirm_overwrite_preset_dialog_message, currentPresetName),
    onDismissRequest = onDismissRequest,
    onConfirm = onConfirm)

/**
 * Show a dialog to create a new preset.
 *
 * @Param onNameChange The callback that will be invoked when the user input
 *     for the new preset's name changes.
 * @param nameValidatorMessage A nullable String that represents a warning
 *     message that should be displayed to the user regarding the new preset's
 *     name. Generally onNameChange should change this value to null if the
 *     entered new name is acceptable, or an error message explaining why the
 *     new name is not acceptable otherwise.
 * @param onDismissRequest The callback that will be invoked when the dialog is dismissed
 * @param onConfirm The callback that will be invoked when the
 */
@Composable fun CreateNewPresetDialog(
    onNameChange: (String) -> Unit,
    nameValidatorMessage: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var currentName by rememberSaveable { mutableStateOf("") }
    SoundAuraDialog(
        title = stringResource(R.string.create_new_preset_dialog_title),
        onDismissRequest = onDismissRequest,
        confirmButtonEnabled = nameValidatorMessage == null,
        onConfirm = { onConfirm(currentName) }
    ) {
        TextField(
            onValueChange = {
                currentName = it
                onNameChange(it)
            }, value = currentName,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            isError = nameValidatorMessage != null,
            singleLine = true,
            textStyle = MaterialTheme.typography.body1)

        var previousNameValidatorMessage by remember { mutableStateOf("") }
        AnimatedVisibility(nameValidatorMessage != null) {
            Row(Modifier.align(Alignment.CenterHorizontally)
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Error,
                     contentDescription = null,
                     tint = MaterialTheme.colors.error)
                Crossfade(nameValidatorMessage ?: previousNameValidatorMessage) {
                    Text(it, Modifier.weight(1f), MaterialTheme.colors.error)
                }
                if (nameValidatorMessage != null)
                    previousNameValidatorMessage = nameValidatorMessage
            }
        }
    }
}

@Preview @Composable fun PresetSelectorPreview() = SoundAuraTheme {
    val list = List(4) { Preset("Preset $it") }
    var currentPreset by remember { mutableStateOf(list.first()) }
    var nameValidatorMessage by remember { mutableStateOf<String?>(null) }

    Box(Modifier.size(400.dp, 400.dp)) {
        PresetSelector(
            modifier = Modifier.align(Alignment.BottomStart),
            onCloseButtonClick = {},
            backgroundBrush = Brush.horizontalGradient(
                listOf(MaterialTheme.colors.primaryVariant,
                       MaterialTheme.colors.secondaryVariant)),
            currentPreset = currentPreset,
            currentIsModified = false,
            presetListProvider = { list },
            onPresetClick = { currentPreset = it },
            onPresetRenameRequest = { _, _ -> },
            onPresetDeleteRequest = {},
            onOverwriteCurrentPresetRequest = {},
            newPresetNameValidatorMessage = nameValidatorMessage,
            onNewPresetNameChange = {
                nameValidatorMessage = if (it.length % 2 == 0) null else
                    "New preset names must have an even number of characters"
            },
            onCreateNewPresetRequest = { true })
    }
}

/**
 * Show a dialog containing a [PresetSelector]. The parameters are same as for
 * a [PresetSelector], except for [visible], which indicates whether or not the
 * dialog should be shown.
 */
@Composable fun PresetSelectorDialog(
    modifier: Modifier = Modifier,
    visible: Boolean,
    onCloseButtonClick: () -> Unit,
    backgroundBrush: Brush,
    currentPreset: Preset? = null,
    currentIsModified: Boolean,
    presetListProvider: () -> List<Preset>,
    onPresetClick: (Preset) -> Unit,
    onPresetRenameRequest: (Preset, String) -> Unit,
    onPresetDeleteRequest: (Preset) -> Unit,
    onOverwriteCurrentPresetRequest: () -> Unit,
    onNewPresetNameChange: (String) -> Unit,
    newPresetNameValidatorMessage: String?,
    onSaveNewPresetRequest: (String) -> Boolean,
) {
    if (visible) Dialog(
        onDismissRequest = onCloseButtonClick,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PresetSelector(
            modifier = modifier.restrictWidthAccordingToSizeClass(),
            onCloseButtonClick = onCloseButtonClick,
            backgroundBrush = backgroundBrush,
            currentPreset = currentPreset,
            currentIsModified = currentIsModified,
            presetListProvider = presetListProvider,
            onPresetClick = onPresetClick,
            onPresetRenameRequest = onPresetRenameRequest,
            onPresetDeleteRequest = onPresetDeleteRequest,
            onOverwriteCurrentPresetRequest = onOverwriteCurrentPresetRequest,
            onNewPresetNameChange = onNewPresetNameChange,
            newPresetNameValidatorMessage = newPresetNameValidatorMessage,
            onCreateNewPresetRequest = onSaveNewPresetRequest)
    }
}