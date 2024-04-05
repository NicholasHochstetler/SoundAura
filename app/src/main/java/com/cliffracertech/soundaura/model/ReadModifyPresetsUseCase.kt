/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.PresetDao
import com.cliffracertech.soundaura.model.database.newPresetNameValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** A container of methods that reads, adds, or
 * modifies presets to the app's list of presets. */
class ReadModifyPresetsUseCase @Inject constructor(
    private val messageHandler: MessageHandler,
    private val activePresetState: ActivePresetState,
    private val presetDao: PresetDao,
    private val playlistDao: PlaylistDao,
) {
    /** Return a [ValidatedNamingState] that can be used to choose a valid name
     * for a new preset, or null if creating a new preset is not possible. If
     * the returned ValidatedNamingState's [ValidatedNamingState.finish] method
     * is called and the input name is valid, a new preset with the validated
     * name will be created and [onAddPreset] will be invoked. */
    suspend fun newPresetNamingState(
        scope: CoroutineScope,
        onAddPreset: (() -> Unit)? = null,
    ): ValidatedNamingState? {
        val noPlaylistsAreActive = playlistDao.getNoPlaylistsAreActive().first()
        return if (noPlaylistsAreActive) {
            messageHandler.postMessage(
                R.string.preset_cannot_be_empty_warning_message)
            null
        } else ValidatedNamingState(
            newPresetNameValidator(presetDao, scope),
            coroutineScope = scope,
            onNameValidated = { validatedName ->
                onAddPreset?.invoke()
                presetDao.savePreset(validatedName)
                activePresetState.setName(validatedName)
            })
    }
}