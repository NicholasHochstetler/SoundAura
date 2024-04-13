/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.addbutton

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.model.AddToLibraryUseCase
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.ReadModifyPresetsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Return a suitable display name for a file [Uri] (i.e. the file name minus
 * the file type extension, and with underscores replaced with spaces). */
fun Uri.getDisplayName(context: Context) =
    DocumentFile.fromSingleUri(context, this)
        ?.name?.substringBeforeLast('.')?.replace('_', ' ')
        ?: pathSegments.last().substringBeforeLast('.').replace('_', ' ')

/**
 * A [ViewModel] that contains state and callbacks for a button to add playlists
 * or presets.
 *
 * The add button's onClick should be set to the view model's provided [onClick]
 * method. The property [dialogState] can then be observed to access the current
 * [AddButtonDialogState] that should be shown to the user. State and callbacks
 * for each dialog step are contained inside the current [AddButtonDialogState]
 * value of the [dialogState] property.
 */
@HiltViewModel @SuppressLint("StaticFieldLeak") // The application context is used
class AddButtonViewModel(
    private val context: Context,
    coroutineScope: CoroutineScope?,
    private val navigationState: NavigationState,
    private val readModifyPresetsUseCase: ReadModifyPresetsUseCase,
    private val addToLibrary: AddToLibraryUseCase,
): ViewModel() {
    @Inject constructor(
        @ApplicationContext context: Context,
        navigationState: NavigationState,
        readModifyPresets: ReadModifyPresetsUseCase,
        addToLibrary: AddToLibraryUseCase
    ): this(context, null, navigationState,
            readModifyPresets, addToLibrary)

    private val scope = coroutineScope ?: viewModelScope

    val state get() = navigationState.addButtonState
    val onOverlayClick = navigationState::collapseAddButton

    var dialogState by mutableStateOf<AddButtonDialogState?>(null)
        private set

    private fun hideDialog() { dialogState = null }

    val onClickContentDescriptionResId get() = when {
        navigationState.showingAppSettings -> null
        navigationState.mediaControllerState.isExpanded ->
            R.string.add_preset_button_description
        else -> R.string.add_local_files_button_description
    }


    val onClick: () -> Unit = { when {
        navigationState.showingAppSettings -> {}
        navigationState.mediaControllerState.isExpanded -> {
            scope.launch {
                val namingState = readModifyPresetsUseCase.newPresetNamingState(
                    scope = scope,
                    onAddPreset = ::hideDialog)
                if (namingState != null)
                    dialogState = AddButtonDialogState.NamePreset(
                        onDismissRequest = ::hideDialog,
                        namingState = namingState)
            }
        } else -> navigationState.toggleAddButtonExpandedState()
    }}

    val onAddFilesClick = {
        onOverlayClick()
        dialogState = AddButtonDialogState.SelectingFiles(
            onDismissRequest = ::hideDialog,
            onFilesSelected = { chosenUris ->
                // If uris.size == 1, we can skip straight to the name
                // track dialog step to skip the user needing to choose
                if (chosenUris.size > 1)
                    showAddIndividuallyOrAsPlaylistQueryStep(chosenUris)
                else showNameTracksStep(chosenUris)
            })
    }

    val onAddDirectoryClick = {
        onOverlayClick()
    }

    private fun showAddIndividuallyOrAsPlaylistQueryStep(chosenUris: List<Uri>) {
        dialogState = AddButtonDialogState.AddIndividuallyOrAsPlaylistQuery(
            onDismissRequest = ::hideDialog,
            onAddIndividuallyClick = { showNameTracksStep(chosenUris) },
            onAddAsPlaylistClick = {
                showNamePlaylistStep(chosenUris, cameFromPlaylistOrTracksQuery = true)
            })
    }

    private fun showNameTracksStep(trackUris: List<Uri>) {
        dialogState = AddButtonDialogState.NameTracks(
            onDismissRequest = ::hideDialog,
            onBackClick = {
                // if uris.size == 1, then the question of whether to add as
                // a track or as a playlist should have been skipped. In this
                // case, the dialog will be dismissed instead of going back.
                if (trackUris.size > 1)
                    showAddIndividuallyOrAsPlaylistQueryStep(trackUris)
                else hideDialog()
            }, validator = addToLibrary.trackNamesValidator(
                scope, trackUris.map { it.getDisplayName(context) }),
            coroutineScope = scope,
            onFinish = { trackNames ->
                hideDialog()
                scope.launch {
                    assert(trackUris.size == trackNames.size)
                    addToLibrary.addSingleTrackPlaylists(trackNames, trackUris)
                }
            })
    }

    private fun showNamePlaylistStep(
        uris: List<Uri>,
        cameFromPlaylistOrTracksQuery: Boolean,
        playlistName: String = "${uris.first().getDisplayName(context)} playlist"
    ) {
        dialogState = AddButtonDialogState.NamePlaylist(
            wasNavigatedForwardTo = cameFromPlaylistOrTracksQuery,
            namingState = ValidatedNamingState(
                validator = addToLibrary.newPlaylistNameValidator(scope, playlistName),
                coroutineScope = scope,
                onNameValidated = { validatedName ->
                    showPlaylistOptionsStep(validatedName, uris)
                }),
            onDismissRequest = ::hideDialog,
            onBackClick = { showAddIndividuallyOrAsPlaylistQueryStep(uris) })
    }

    private fun showPlaylistOptionsStep(
        playlistName: String,
        uris: List<Uri>
    ) {
        dialogState = AddButtonDialogState.PlaylistOptions(
            onDismissRequest = ::hideDialog,
            onBackClick = {
                showNamePlaylistStep(uris,
                    cameFromPlaylistOrTracksQuery = false,
                    playlistName = playlistName)
            }, trackUris = uris,
            onFinish = { shuffle, newTracks ->
                hideDialog()
                scope.launch {
                    addToLibrary.addPlaylist(playlistName, shuffle, newTracks)
                }
            })
    }

}

/**
 * A button to add local files or presets, with state provided by
 * an instance of [AddButtonViewModel].
 *
 * @param backgroundColor The color to use for the button's background
 * @param modifier The [Modifier] to use for the button
 */
@Composable fun AddButton(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val viewModel: AddButtonViewModel = viewModel()

    val filledButtonColors = ButtonDefaults.buttonColors(
        backgroundColor = backgroundColor,
        contentColor = MaterialTheme.colors.onSecondary)
    ExpandableButton(
        state = viewModel.state,
        onClick = viewModel.onClick,
        onClickDescriptionProvider = {
            viewModel.onClickContentDescriptionResId
                ?.let { stringResource(it) }
        }, onOverlayClick = viewModel.onOverlayClick,
        modifier = modifier,
        backgroundColor = backgroundColor,
        expandedContent = listOf(
            { modifier ->
                TextButton(
                    onClick = viewModel.onAddDirectoryClick,
                    modifier = modifier,
                    shape = MaterialTheme.shapes.medium,
                    colors = filledButtonColors
                ) {
                    Text("Directory")
                    Icon(Icons.Default.Folder, null,
                        Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp))
                }
            }, { modifier ->
                TextButton(
                    onClick = viewModel.onAddFilesClick,
                    modifier = modifier,
                    shape = MaterialTheme.shapes.medium,
                    colors = filledButtonColors
                ) {
                    Text("File(s)")
                    Icon(Icons.Default.AudioFile, null,
                        Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp))
                }
            }))

    viewModel.dialogState?.let { AddButtonDialogShower(it) }
}