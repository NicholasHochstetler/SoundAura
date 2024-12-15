/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cliffracertech.soundaura.edit
import com.cliffracertech.soundaura.mediacontroller.MediaControllerState
import com.cliffracertech.soundaura.model.database.Preset
import com.cliffracertech.soundaura.model.database.PresetDao
import com.cliffracertech.soundaura.settings.PrefKeys
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

@ActivityRetainedScoped
class NavigationState @Inject constructor() {
    var showingAppSettings by mutableStateOf(false)
        private set
    var mediaControllerState by mutableStateOf(MediaControllerState.Visibility.Collapsed)
        private set

    fun showAppSettings() {
        showingAppSettings = true
        mediaControllerState = MediaControllerState.Visibility.Hidden
    }

    fun hideAppSettings() {
        showingAppSettings = false
        mediaControllerState = MediaControllerState.Visibility.Collapsed
    }

    fun showPresetSelector() {
        if (showingAppSettings)
            return
        mediaControllerState = MediaControllerState.Visibility.Expanded
    }

    fun hidePresetSelector() {
        if (showingAppSettings)
            return
        mediaControllerState = MediaControllerState.Visibility.Collapsed
    }

    fun onBackButtonClick(): Boolean = when {
        showingAppSettings -> {
            hideAppSettings(); true
        } mediaControllerState.isExpanded -> {
            hidePresetSelector(); true
        } else -> false
    }
}

/**
 * A state holder for a search query entry.
 *
 * The query itself can be accessed through the property [value] or as a
 * [Flow] through the [flow] property. The method [set] can be used to
 * update an active search query, or [clear] can be used to reset it to null.
 * The convenience method [toggleIsActive] will set the query to null if it
 * is not null, or to a blank string if it is null.
 */
@ActivityRetainedScoped
class SearchQueryState @Inject constructor() {
    var value by mutableStateOf<String?>(null)
        private set
    val flow = MutableStateFlow<String?>(null)

    val isActive get() = value != null

    fun toggleIsActive() {
        if (isActive) clear()
        else          set("")
    }

    fun set(newQuery: String?) {
        value = newQuery
        flow.value = newQuery
    }

    fun clear() = set(null)
}

/**
 * ActivePresetState holds the state of a currently active [Preset]. The name
 * of the currently active [Preset] can be collected from the [Flow]`<String?>`
 * property [name]. Whether or not the active [Preset] is modified can be
 * collected from the [Flow]`<Boolean>` property [isModified]. The active
 * [Preset] can be changed or cleared with the methods [setName] and [clear].
 */
@ActivityRetainedScoped
class ActivePresetState @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val presetDao: PresetDao,
) {
    private val nameKey = stringPreferencesKey(PrefKeys.activePresetName)

    /** A [Flow]`<Preset>` whose latest value is equal to the [Preset] current
     * marked as the active one. */
    val name = dataStore.data.map { prefs ->
        val value = prefs[nameKey]
        if (value.isNullOrBlank() || !presetDao.exists(value))
            null
        else value
    }

    val isModified = name.transformLatest { activePresetName ->
        if (activePresetName == null) emit(false)
        else emitAll(presetDao.getPresetIsModified(activePresetName))
    }

    /** Set the active preset to the one whose name matches [name]. */
    suspend fun setName(name: String) = dataStore.edit(nameKey, name)

    /** Clear the active preset. */
    suspend fun clear() {
        dataStore.edit { it.remove(nameKey) }
    }
}