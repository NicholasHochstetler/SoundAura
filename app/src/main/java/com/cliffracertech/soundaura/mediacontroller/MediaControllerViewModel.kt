/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.mediacontroller

import androidx.compose.material.SnackbarDuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.collectAsState
import com.cliffracertech.soundaura.edit
import com.cliffracertech.soundaura.model.ActivePresetState
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.PlaybackState
import com.cliffracertech.soundaura.model.PlayerServicePlaybackState
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.Preset
import com.cliffracertech.soundaura.model.database.PresetDao
import com.cliffracertech.soundaura.model.database.presetRenameValidator
import com.cliffracertech.soundaura.preferenceState
import com.cliffracertech.soundaura.settings.PrefKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

/**
 * A [ViewModel] that contains state and callbacks for a [MediaController].
 *
 * The value of the [state] property should be used as the MediaController's
 * same-named parameter.
 *
 * When the property [shownDialog] is not null, a dialog should be shown that
 * reflects [shownDialog]'s type (i.e. one of the subclasses of [DialogType]).
 */
@HiltViewModel
class MediaControllerViewModel(
    private val presetDao: PresetDao,
    private val navigationState: NavigationState,
    private val playbackState: PlaybackState,
    private val activePresetState: ActivePresetState,
    private val messageHandler: MessageHandler,
    private val dataStore: DataStore<Preferences>,
    playlistDao: PlaylistDao,
    coroutineScope: CoroutineScope?
) : ViewModel() {

    @Inject
    constructor(
        dao: PresetDao,
        navigationState: NavigationState,
        playbackState: PlayerServicePlaybackState,
        activePresetState: ActivePresetState,
        messageHandler: MessageHandler,
        dataStore: DataStore<Preferences>,
        playlistDao: PlaylistDao,
    ) : this(dao, navigationState, playbackState, activePresetState,
        messageHandler, dataStore, playlistDao, null)

    private val scope = coroutineScope ?: viewModelScope

    var shownDialog by mutableStateOf<DialogType?>(null)
    private fun dismissDialog() { shownDialog = null }

    private val activePresetName by activePresetState.name.collectAsState(null, scope)
    private val activePresetIsModified by activePresetState.isModified.collectAsState(false, scope)
    private val activePresetViewState = ActivePresetViewState(
        nameProvider = ::activePresetName,
        isModifiedProvider = ::activePresetIsModified,
        onClick = navigationState::showPresetSelector)

    private val playButtonLongClickHintShownKey =
        booleanPreferencesKey(PrefKeys.playButtonLongClickHintShown)
    private val playButtonLongClickHintShown by
        dataStore.preferenceState(playButtonLongClickHintShownKey, false, scope)
    private val noPlaylistsAreActive by playlistDao
        .getNoPlaylistsAreActive()
        .collectAsState(true, scope)
    private val playButtonState = PlayButtonState(
        isPlayingProvider = playbackState::isPlaying,
        onClick = {
            playbackState.toggleIsPlaying()
            // We don't want to show the hint if there are no
            // active playlists because the PlayerService should
            // show a message about there being no active playlists
            if (!playButtonLongClickHintShown && !noPlaylistsAreActive) {
                val stringRes = StringResource(R.string.play_button_long_click_hint_text)
                messageHandler.postMessage(stringRes, SnackbarDuration.Long)
                dataStore.edit(playButtonLongClickHintShownKey, true, scope)
            }
        }, onLongClick = {
            shownDialog = DialogType.SetAutoStopTimer(
                onDismissRequest = ::dismissDialog,
                onConfirmClick = { duration ->
                    if (duration > Duration.ZERO)
                        playbackState.setTimer(duration)
                    dismissDialog()
                })
        }, clickLabelResIdProvider = { isPlaying: Boolean ->
            if (isPlaying) R.string.pause_button_description
            else           R.string.play_button_description
        }, longClickLabelResId = R.string.play_pause_button_long_click_description)

    private val presetList by presetDao
        .getPresetList()
        .map(List<Preset>::toImmutableList)
        .collectAsState(null, scope)
    private val presetListState = PresetListState(
        listProvider = ::presetList,
        onRenameClick = { presetName: String ->
            shownDialog = DialogType.RenamePreset(
                coroutineScope = scope,
                validator = presetRenameValidator(presetDao, scope, presetName),
                onDismissRequest = ::dismissDialog,
                onNameValidated = { validatedName ->
                    dismissDialog()
                    if (validatedName != presetName) {
                        presetDao.renamePreset(presetName, validatedName)
                        if (activePresetName == presetName)
                            activePresetState.setName(validatedName)
                    }
                })
        }, onOverwriteClick = { presetName: String ->
            shownDialog = DialogType.Confirmatory(
                title = StringResource(R.string.confirm_overwrite_preset_dialog_title),
                text = StringResource(R.string.confirm_overwrite_preset_dialog_message, presetName),
                onDismissRequest = ::dismissDialog,
                onConfirmClick = {
                    overwritePreset(presetName)
                    dismissDialog()
                })
        }, onDeleteClick = {presetName: String ->
            shownDialog = DialogType.Confirmatory(
                title = StringResource(R.string.confirm_delete_preset_title, presetName),
                text = StringResource(R.string.confirm_delete_preset_message),
                onDismissRequest = ::dismissDialog,
                onConfirmClick = {
                    scope.launch {
                        if (presetName == activePresetName)
                            activePresetState.clear()
                        presetDao.deletePreset(presetName)
                    }
                    dismissDialog()
                })
        }, onClick = { presetName: String -> when {
            activePresetIsModified -> {
                shownDialog = DialogType.PresetUnsavedChangesWarning(
                    targetName = presetName,
                    onDismissRequest = ::dismissDialog,
                    onConfirmClick = { saveFirst ->
                        if (saveFirst)
                            activePresetName?.let(::overwritePreset)
                        loadPreset(presetName)
                        dismissDialog()
                    })
            } presetName == activePresetName ->
                // This skips a pointless loading of the unmodified active preset
                navigationState.hidePresetSelector()
            else -> loadPreset(presetName)
        }})

    val state = MediaControllerState(
        visibilityProvider = navigationState::mediaControllerState,
        activePreset = activePresetViewState,
        playButton = playButtonState,
        presetList = presetListState,
        stopTimeProvider = playbackState::stopTime,
        onStopTimerClick = {
            shownDialog = DialogType.Confirmatory(
                onDismissRequest = ::dismissDialog,
                title = StringResource(R.string.cancel_stop_timer_dialog_title),
                text = StringResource(R.string.cancel_stop_timer_dialog_text),
                onConfirmClick = {
                    playbackState.clearTimer()
                    dismissDialog()
                })
        }, onCloseButtonClick = navigationState::hidePresetSelector,
        onOverlayClick = navigationState::hidePresetSelector)

    private fun overwritePreset(presetName: String) { when {
        noPlaylistsAreActive ->
            messageHandler.postMessage(R.string.overwrite_no_active_playlists_error_message)
        presetName == activePresetName && !activePresetIsModified -> {
            // This prevents a pointless saving of the unmodified active preset
        } else -> scope.launch {
            presetDao.savePreset(presetName)
            // Since the current sound mix is being saved to the preset
            // whose name == presetName, we want to make it the active
            // preset if it isn't currently.
            if (presetName != activePresetName)
                activePresetState.setName(presetName)
            navigationState.hidePresetSelector()
        }
    }}

    // TODO: Figure out why UI stutters when selecting new preset
    private fun loadPreset(presetName: String) {
        scope.launch {
            activePresetState.setName(presetName)
            presetDao.loadPreset(presetName)
            navigationState.hidePresetSelector()
        }
    }
}