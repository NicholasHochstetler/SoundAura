/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.addbutton

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.ReadModifyPresetsUseCase
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.ui.tweenDuration
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
    private val messageHandler: MessageHandler,
    private val navigationState: NavigationState,
    private val readModifyPresetsUseCase: ReadModifyPresetsUseCase,
    private val addToLibrary: AddToLibraryUseCase,
): ViewModel() {
    @Inject constructor(
        @ApplicationContext context: Context,
        messageHandler: MessageHandler,
        navigationState: NavigationState,
        readModifyPresets: ReadModifyPresetsUseCase,
        addToLibrary: AddToLibraryUseCase
    ): this(context, null, messageHandler, navigationState,
            readModifyPresets, addToLibrary)

    private val scope = coroutineScope ?: viewModelScope

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
                    scope = scope, onAddPreset = ::hideDialog)
                if (namingState != null)
                    dialogState = AddButtonDialogState.NamePreset(
                        onDismissRequest = ::hideDialog,
                        namingState = namingState)
            }
        } else -> {
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
    }}

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
                addTracks(trackUris, trackNames)
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
            onFinish = { shuffle, tracks ->
                addPlaylist(playlistName, uris, shuffle, tracks)
            })
    }

    private fun showStoragePermissionExplanation(
        addingPlaylist: Boolean,
        permissionsUsed: Int,
        permissionsAllowed: Int,
        onResult: (permissionGranted: Boolean) -> Unit
    ) {
        dialogState = AddButtonDialogState.RequestStoragePermissionExplanation(
            onDismissRequest = ::hideDialog,
            permissionsUsed = permissionsUsed,
            permissionsAllowed = permissionsAllowed,
            addingPlaylist = addingPlaylist,
            onOkClick = { showStoragePermissionRequest(onResult) })
    }

    private fun showStoragePermissionRequest(
        onResult: (permissionGranted: Boolean) -> Unit
    ) {
        dialogState = AddButtonDialogState.RequestStoragePermission(
            onDismissRequest = ::hideDialog,
            onResult = onResult)
    }

    private fun addTracks(
        trackUris: List<Uri>,
        trackNames: List<String>
    ) {
        scope.launch {
            assert(trackUris.size == trackNames.size)
            when (val result = addToLibrary.addSingleTrackPlaylists(trackNames, trackUris)) {
                is AddToLibraryUseCase.Result.Success ->
                    hideDialog()
                is AddToLibraryUseCase.Result.Failure ->
                    showStoragePermissionExplanation(
                        addingPlaylist = false,
                        permissionsUsed = result.permissionsUsed,
                        permissionsAllowed = result.permissionAllowance,
                    ) { permissionGranted: Boolean ->
                        if (permissionGranted) scope.launch {
                            addToLibrary.addSingleTrackPlaylists(trackNames, trackUris)
                        } else messageHandler.postMessage(
                            stringResource = StringResource(R.string.cant_add_tracks_warning),
                            duration = SnackbarDuration.Long)
                        hideDialog()
                    }
            }
        }
    }

    private fun addPlaylist(
        playlistName: String,
        uris: List<Uri>,
        shuffle: Boolean,
        tracks: List<Track>,
    ) {
        scope.launch {
            when (val result = addToLibrary.addPlaylist(playlistName, shuffle, tracks, uris)) {
                is AddToLibraryUseCase.Result.Success ->
                    hideDialog()
                is AddToLibraryUseCase.Result.Failure ->
                    showStoragePermissionExplanation(
                        addingPlaylist = true,
                        permissionsUsed = result.permissionsUsed,
                        permissionsAllowed = result.permissionAllowance,
                    ) { permissionGranted ->
                        if (permissionGranted) scope.launch {
                            addToLibrary.addPlaylist(playlistName, shuffle, tracks)
                        } else messageHandler.postMessage(
                            R.string.cant_add_playlist_warning,
                            SnackbarDuration.Long)
                        hideDialog()
                    }
            }
        }
    }
}

/**
 * A button to add local files or presets, with state provided by
 * an instance of [AddButtonViewModel].
 *
 * @param backgroundColor The color to use for the button's background
 * @param visible Whether the button is visible
 * @param modifier The [Modifier] to use for the button
 */
@Composable fun AddButton(
    backgroundColor: Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val viewModel: AddButtonViewModel = viewModel()

    val enterSpec = tween<Float>(
        durationMillis = tweenDuration,
        easing = LinearOutSlowInEasing)
    val exitSpec = tween<Float>(
        durationMillis = tweenDuration,
        delayMillis = tweenDuration / 3,
        easing = LinearOutSlowInEasing)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(enterSpec) + scaleIn(enterSpec, initialScale = 0.8f),
        exit = fadeOut(exitSpec) + scaleOut(exitSpec, targetScale = 0.8f),
    ) {
        FloatingActionButton(
            onClick = viewModel.onClick,
            backgroundColor = backgroundColor,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
        ) {
            Icon(imageVector = Icons.Default.Add,
                contentDescription = viewModel.onClickContentDescriptionResId?.let {
                    stringResource(it)
                }, tint = MaterialTheme.colors.onPrimary)
        }
    }

    viewModel.dialogState?.let { AddButtonDialogShower(it) }
}