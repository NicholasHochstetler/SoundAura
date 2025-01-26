/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cliffracertech.soundaura.mediacontroller.DialogType
import com.cliffracertech.soundaura.mediacontroller.MediaControllerState
import com.cliffracertech.soundaura.mediacontroller.MediaControllerViewModel
import com.cliffracertech.soundaura.model.ActivePresetState
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.PlaybackState
import com.cliffracertech.soundaura.model.TestPlaybackState
import com.cliffracertech.soundaura.model.database.LibraryPlaylist
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.Preset
import com.cliffracertech.soundaura.model.database.PresetDao
import com.cliffracertech.soundaura.model.database.SoundAuraDatabase
import com.cliffracertech.soundaura.service.ActivePlaylistSummary
import com.cliffracertech.soundaura.settings.PrefKeys
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class MediaControllerViewModelTests {
    private val activePresetNameKey = stringPreferencesKey(PrefKeys.activePresetName)

    private lateinit var scope: TestCoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var db: SoundAuraDatabase
    private lateinit var presetDao: PresetDao
    private lateinit var playlistDao: PlaylistDao
    private lateinit var navigationState: NavigationState
    private lateinit var playbackState: PlaybackState
    private lateinit var activePresetState: ActivePresetState
    private lateinit var messageHandler: MessageHandler
    private lateinit var instance: MediaControllerViewModel

    @Before fun init() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        scope = TestCoroutineScope()
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile("testDatastore")
        }
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        presetDao = db.presetDao()
        playlistDao = db.playlistDao()
        navigationState = NavigationState()
        playbackState = TestPlaybackState()
        activePresetState = ActivePresetState(dataStore, presetDao)
        messageHandler = MessageHandler()
        instance = MediaControllerViewModel(
            presetDao, navigationState, playbackState,
            activePresetState, messageHandler,
            dataStore, playlistDao, scope)
    }

    @After fun clean_up() {
        db.close()
        scope.cancel()
    }

    private val testPlaylistNames = List(5) { "playlist $it" }
    private lateinit var testPlaylistIds: List<Long>
    private val testUris = List(5) { "uri $it".toUri() }
    private val testPresetNames = List(3) { "preset $it" }

    private val presetList get() = instance.state.presetList
    private val currentPresets get() = presetList.list
    private val currentPresetNames get() = presetList.list?.map(Preset::name)
    private val activePreset get() = instance.state.activePreset
    private val playButton get() = instance.state.playButton
    private val stopTime get() = instance.state.stopTime
    private suspend fun getActivePlaylistIds() =
        playlistDao.getActivePlaylistsAndTracks().first().keys.map(ActivePlaylistSummary::id)

    private val renameDialog get() = instance.shownDialog as DialogType.RenamePreset
    private val confirmatoryDialog get() = instance.shownDialog as DialogType.Confirmatory
    private val unsavedChangesWarningDialog get() = instance.shownDialog as DialogType.PresetUnsavedChangesWarning
    private val setStopTimeDialog get() = instance.shownDialog as DialogType.SetAutoStopTimer

    private suspend fun insertTestPresets() {
        playlistDao.insertSingleTrackPlaylists(testPlaylistNames, testUris)
        testPlaylistIds = playlistDao.getPlaylistsSortedByNameAsc().first().map(LibraryPlaylist::id)
        playlistDao.toggleIsActive(testPlaylistIds[0])
        presetDao.savePreset(testPresetNames[0])

        playlistDao.toggleIsActive(testPlaylistIds[0])
        playlistDao.toggleIsActive(testPlaylistIds[1])
        playlistDao.toggleIsActive(testPlaylistIds[2])
        presetDao.savePreset(testPresetNames[1])

        playlistDao.toggleIsActive(testPlaylistIds[1])
        playlistDao.toggleIsActive(testPlaylistIds[2])
        playlistDao.toggleIsActive(testPlaylistIds[3])
        playlistDao.toggleIsActive(testPlaylistIds[4])
        presetDao.savePreset(testPresetNames[2])
        waitUntil { currentPresets?.isNotEmpty() == true }
    }

    @Test fun no_dialog_is_initially_shown() {
        assertThat(instance.shownDialog).isNull()
    }

    @Test fun active_preset_name_matches_underlying_state() = runTest {
        insertTestPresets()
        assertThat(activePreset.name).isNull()

        activePresetState.setName(testPresetNames[1])
        waitUntil { activePreset.name == testPresetNames[1] }
        assertThat(activePreset.name).isEqualTo(testPresetNames[1])

        activePresetState.clear()
        waitUntil { activePreset.name == null }
        assertThat(activePreset.name).isNull()
    }

    @Test fun active_preset_is_modified_matches_underlying_state() = runTest {
        insertTestPresets()
        assertThat(activePreset.isModified).isFalse()

        dataStore.edit(activePresetNameKey, testPresetNames[2])
        waitUntil { dataStore.data.first()[activePresetNameKey] != null }
        assertThat(activePreset.isModified).isFalse()

        playlistDao.toggleIsActive(testPlaylistIds[2])
        waitUntil { getActivePlaylistIds().size == 3 }
        waitUntil { activePreset.isModified }
        assertThat(activePreset.isModified).isTrue()

        playlistDao.toggleIsActive(testPlaylistIds[1])
        playlistDao.toggleIsActive(testPlaylistIds[3])
        playlistDao.toggleIsActive(testPlaylistIds[4])
        activePresetState.setName(testPresetNames[1])
        waitUntil { dataStore.data.first()[activePresetNameKey] == testPresetNames[1] }
        waitUntil { !activePreset.isModified }
        assertThat(activePreset.isModified).isFalse()

        playlistDao.setVolume(testPlaylistIds[2], 0.5f)
        waitUntil { activePreset.isModified }
        assertThat(activePreset.isModified).isTrue()
    }

    @Test fun clicking_active_preset_opens_preset_selector() {
        assertThat(instance.state.visibility)
            .isEqualTo(MediaControllerState.Visibility.Collapsed)
        activePreset.onClick()
        assertThat(instance.state.visibility)
            .isEqualTo(MediaControllerState.Visibility.Expanded)
    }

    @Test fun play_button_isPlaying_matches_underlying_state() = runTest {
        insertTestPresets()
        assertThat(playButton.isPlaying).isFalse()

        playbackState.toggleIsPlaying()
        waitUntil { playbackState.isPlaying }
        assertThat(playButton.isPlaying).isTrue()

        playbackState.toggleIsPlaying()
        waitUntil { !playbackState.isPlaying }
        assertThat(playButton.isPlaying).isFalse()
    }

    @Test fun play_button_clicks_affect_underlying_state() = runTest {
        playButton.onClick()
        assertThat(playbackState.isPlaying).isTrue()

        playButton.onClick()
        assertThat(playbackState.isPlaying).isFalse()
    }

    @Test fun play_button_long_click_hint_not_shown_with_no_active_tracks() = runTest {
        insertTestPresets()
        val prefKey = booleanPreferencesKey(PrefKeys.playButtonLongClickHintShown)
        dataStore.edit { it.remove(prefKey) }

        var latestMessage: MessageHandler.Message? = null
        val job = scope.launch {
            messageHandler.messages.collect { latestMessage = it }
        }

        playButton.onClick()
        waitUntil { latestMessage != null } // Should time out
        assertThat(latestMessage).isNull()
        job.cancel()
    }

    @Test fun play_button_long_click_hint_shows_once() = runTest {
        insertTestPresets()
        val prefKey = booleanPreferencesKey(PrefKeys.playButtonLongClickHintShown)
        dataStore.edit { it.remove(prefKey) }

        var latestMessage: MessageHandler.Message? = null
        val job = scope.launch {
            messageHandler.messages.collect { latestMessage = it }
        }

        waitUntil { getActivePlaylistIds().isNotEmpty() }
        playButton.onClick()
        waitUntil { latestMessage != null }
        assertThat(latestMessage?.stringResource?.stringResId)
            .isEqualTo(R.string.play_button_long_click_hint_text)

        latestMessage = null
        waitUntil { dataStore.data.first()[prefKey] == true }
        playButton.onClick()
        waitUntil { latestMessage != null } // Should time out
        assertThat(latestMessage).isNull()
        job.cancel()
    }

    @Test fun stop_time_matches_underlying_state() = runTest {
        insertTestPresets()
        val startTime = Instant.now()
        val duration = Duration.ofMinutes(1)
        assertThat(stopTime).isNull()

        playbackState.setTimer(duration)
        waitUntil { stopTime != null }
        assertThat(stopTime).isIn(Range.closed(
            startTime + duration, startTime + duration + Duration.ofSeconds(1)))

        playbackState.clearTimer()
        waitUntil { stopTime == null }
        assertThat(stopTime).isNull()
    }

    @Test fun play_button_long_click_opens_set_stop_timer_dialog() = runTest {
        insertTestPresets()
        playButton.onLongClick()
        waitUntil { instance.shownDialog != null }
        assertThat(instance.shownDialog).isInstanceOf(DialogType.SetAutoStopTimer::class)
    }

    @Test fun set_stop_timer_dialog_no_ops_for_zero_duration() = runTest {
        insertTestPresets()
        playButton.onLongClick()
        waitUntil { instance.shownDialog != null }
        setStopTimeDialog.onConfirmClick(Duration.ZERO)
        waitUntil { instance.shownDialog == null && stopTime == null }
        assertThat(instance.shownDialog).isNull()
        assertThat(stopTime).isNull()
    }

    @Test fun set_stop_timer_dialog_dismissal() = runTest {
        insertTestPresets()
        playButton.onLongClick()
        waitUntil { instance.shownDialog != null }
        setStopTimeDialog.onDismissRequest()
        waitUntil { instance.shownDialog == null && stopTime == null }
        assertThat(instance.shownDialog).isNull()
        assertThat(stopTime).isNull()
    }

    @Test fun set_stop_timer_dialog_confirm() = runTest {
        insertTestPresets()
        val startTime = Instant.now()
        val duration = Duration.ofMinutes(1)
        val acceptableRange = Range.closed(
            startTime + duration,
            startTime + duration + Duration.ofSeconds(1))

        playButton.onLongClick()
        waitUntil { instance.shownDialog != null }
        setStopTimeDialog.onConfirmClick(duration)
        waitUntil { instance.shownDialog == null && stopTime != null }
        assertThat(instance.shownDialog).isNull()
        assertThat(stopTime).isIn(acceptableRange)
    }

    @Test fun cancel_stop_timer_dialog_appearance() = runTest {
        insertTestPresets()
        playButton.onLongClick()
        waitUntil { instance.shownDialog != null }
        setStopTimeDialog.onConfirmClick(Duration.ofMinutes(1))
        waitUntil { instance.shownDialog == null && stopTime != null }

        instance.state.onStopTimerClick()
        waitUntil { instance.shownDialog != null }
        assertThat(instance.shownDialog).isInstanceOf(DialogType.Confirmatory::class)
        assertThat((instance.shownDialog as DialogType.Confirmatory).text.stringResId)
            .isEqualTo(R.string.cancel_stop_timer_dialog_text)
    }

    @Test fun cancel_stop_timer_dialog_dismissal() = runTest {
        insertTestPresets()
        val startTime = Instant.now()
        val duration = Duration.ofMinutes(1)
        val acceptableRange = Range.closed(
            startTime + duration,
            startTime + duration + Duration.ofSeconds(1))
        playButton.onLongClick()
        waitUntil { instance.shownDialog != null }
        setStopTimeDialog.onConfirmClick(duration)
        waitUntil { instance.shownDialog == null && stopTime != null }

        instance.state.onStopTimerClick()
        waitUntil { instance.shownDialog != null }
        confirmatoryDialog.onDismissRequest()
        waitUntil { stopTime == null } // should time out
        assertThat(instance.shownDialog).isNull()
        assertThat(stopTime).isIn(acceptableRange)
    }

    @Test fun cancel_stop_timer_dialog_confirm() = runTest {
        insertTestPresets()
        playButton.onLongClick()
        waitUntil { instance.shownDialog != null }
        setStopTimeDialog.onConfirmClick(Duration.ofMinutes(1))
        waitUntil { instance.shownDialog == null && stopTime != null }

        instance.state.onStopTimerClick()
        waitUntil { instance.shownDialog != null }
        confirmatoryDialog.onConfirmClick()
        waitUntil { instance.shownDialog == null && stopTime == null }
        assertThat(instance.shownDialog).isNull()
        assertThat(stopTime).isNull()
    }

    @Test fun preset_selector_close_button_and_back_button_closes_selector() {
        activePreset.onClick()
        instance.state.onCloseButtonClick()
        assertThat(instance.state.visibility)
            .isEqualTo(MediaControllerState.Visibility.Collapsed)

        activePreset.onClick()
        navigationState.onBackButtonClick()
        assertThat(instance.state.visibility)
            .isEqualTo(MediaControllerState.Visibility.Collapsed)
    }

    @Test fun preset_list_matches_underlying_state() = runTest {
        insertTestPresets()
        waitUntil { !currentPresets.isNullOrEmpty() } // should time out
        assertThat(currentPresets.isNullOrEmpty()).isTrue()

        presetDao.savePreset(testPresetNames[0])
        waitUntil { currentPresets?.isEmpty() == false }
        assertThat(currentPresetNames).containsExactly(testPresetNames[0])
        presetDao.savePreset(testPresetNames[1])
        presetDao.savePreset(testPresetNames[2])
        waitUntil { currentPresets?.size == 3 }
        assertThat(currentPresetNames).containsExactlyElementsIn(testPresetNames)

        presetDao.deletePreset(testPresetNames[1])
        waitUntil { currentPresets?.size == 2 }
        assertThat(currentPresetNames).containsExactly(testPresetNames[0], testPresetNames[2])
    }

    @Test fun rename_dialog_appearance() = runTest {
        insertTestPresets()
        presetList.onRenameClick(testPresetNames[1])
        assertThat(instance.shownDialog).isInstanceOf(DialogType.RenamePreset::class)
        assertThat(renameDialog.name).isEqualTo(testPresetNames[1])
        assertThat(renameDialog.message).isNull()
    }

    @Test fun rename_dialog_dismissal() = runTest {
        insertTestPresets()
        presetList.onRenameClick(testPresetNames[1])
        val newName = "new name"
        renameDialog.onNameChange(newName)
        renameDialog.onDismissRequest()

        waitUntil { currentPresetNames?.contains(newName) == true } // should time out
        assertThat(instance.shownDialog).isNull()
        assertThat(currentPresetNames?.get(1)).isEqualTo(testPresetNames[1])
    }

    @Test fun rename_dialog_confirm() = runTest {
        insertTestPresets()
        presetList.onRenameClick(testPresetNames[1])
        waitUntil { instance.shownDialog is DialogType.RenamePreset }
        renameDialog.finish()
        waitUntil { currentPresetNames?.get(1) != testPresetNames[1] } // should time out
        assertThat(instance.shownDialog).isNull()
        assertThat(currentPresetNames?.get(1)).isEqualTo(testPresetNames[1])

        presetList.onRenameClick(testPresetNames[1])
        val newName = "new name"
        renameDialog.onNameChange(newName)
        waitUntil { renameDialog.message == null } // should time out
        assertThat(renameDialog.message).isNull()

        renameDialog.finish()
        waitUntil { currentPresetNames?.get(1) == newName }
        assertThat(instance.shownDialog).isNull()
        assertThat(currentPresetNames?.get(1)).isEqualTo(newName)
    }

    @Test fun renaming_active_preset_updates_active_preset_name() = runTest {
        insertTestPresets()
        activePresetState.setName(testPresetNames[1])
        waitUntil { activePreset.name != null }

        presetList.onRenameClick(testPresetNames[1])
        renameDialog.onNameChange("new name")
        renameDialog.finish()
        waitUntil { currentPresetNames?.contains("new name") == true }
        waitUntil { activePresetState.name.first() == "new name" }
        assertThat(activePresetState.name.first()).isEqualTo("new name")
    }

    @Test fun overwrite_dialog_appearance() = runTest {
        insertTestPresets()
        presetList.onOverwriteClick(testPresetNames[0])
        waitUntil { instance.shownDialog != null }
        assertThat(instance.shownDialog).isInstanceOf(DialogType.Confirmatory::class)
        assertThat(confirmatoryDialog.title.stringResId).isEqualTo(R.string.confirm_overwrite_preset_dialog_title)
        assertThat(confirmatoryDialog.text.stringResId).isEqualTo(R.string.confirm_overwrite_preset_dialog_message)
    }

    @Test fun overwrite_dialog_dismissal() = runTest {
        insertTestPresets()
        presetList.onOverwriteClick(testPresetNames[0])
        waitUntil { instance.shownDialog != null }
        confirmatoryDialog.onDismissRequest()

        assertThat(instance.shownDialog).isNull()
        assertThat(presetDao.getPlaylistNamesFor(testPresetNames[0]))
            .containsExactly(testPlaylistNames[0])
    }

    @Test fun overwriting_not_allowed_with_no_active_tracks() = runTest {
        insertTestPresets()
        activePresetState.setName(testPresetNames[2])
        waitUntil { activePreset.name != null }
        playlistDao.toggleIsActive(testPlaylistIds[3])
        playlistDao.toggleIsActive(testPlaylistIds[4])
        waitUntil { getActivePlaylistIds().isEmpty() }

        presetList.onOverwriteClick(testPresetNames[2])
        waitUntil { instance.shownDialog != null }
        confirmatoryDialog.onConfirmClick()
        waitUntil { !activePreset.isModified } // should time out
        assertThat(presetDao.getPlaylistNamesFor(testPresetNames[2]))
            .containsExactly(testPlaylistNames[3], testPlaylistNames[4])
        assertThat(activePreset.isModified).isTrue()
    }

    @Test fun overwrite_dialog_confirm() = runTest {
        insertTestPresets()
        presetList.onOverwriteClick(testPresetNames[1])
        waitUntil { instance.shownDialog != null }
        confirmatoryDialog.onConfirmClick()
        waitUntil { !activePreset.isModified }

        assertThat(instance.shownDialog).isNull()
        assertThat(presetDao.getPlaylistNamesFor(testPresetNames[1]))
            .containsExactly(testPlaylistNames[3], testPlaylistNames[4])
        assertThat(activePreset.isModified).isFalse()
    }

    @Test fun overwriting_preset_makes_it_active() = runTest {
        insertTestPresets()
        activePresetState.setName(testPresetNames[2])
        waitUntil { activePreset.name != null }

        presetList.onOverwriteClick(testPresetNames[1])
        waitUntil { instance.shownDialog != null }
        confirmatoryDialog.onConfirmClick()
        waitUntil { !activePreset.isModified }

        val name = activePresetState.name.first()
        assertThat(name).isEqualTo(testPresetNames[2])
    }

    @Test fun delete_dialog_appearance() = runTest {
        insertTestPresets()
        presetList.onDeleteClick(testPresetNames[1])
        waitUntil { instance.shownDialog != null }
        assertThat(instance.shownDialog).isInstanceOf(DialogType.Confirmatory::class)
        assertThat(confirmatoryDialog.title.stringResId).isEqualTo(R.string.confirm_delete_preset_title)
        assertThat(confirmatoryDialog.text.stringResId).isEqualTo(R.string.confirm_delete_preset_message)
    }

    @Test fun delete_dialog_dismissal() = runTest {
        insertTestPresets()
        presetList.onDeleteClick(testPresetNames[1])
        waitUntil { instance.shownDialog != null }
        instance.shownDialog?.onDismissRequest?.invoke()
        waitUntil { currentPresets?.size == 2 } // should time out
        assertThat(instance.shownDialog).isNull()
        assertThat(currentPresetNames).containsExactlyElementsIn(testPresetNames)
    }

    @Test fun delete_dialog_confirm() = runTest {
        insertTestPresets()
        presetList.onDeleteClick(testPresetNames[1])
        waitUntil { instance.shownDialog != null }
        confirmatoryDialog.onConfirmClick()
        waitUntil { (currentPresets?.size ?: 0) < 3 }
        assertThat(currentPresetNames).containsExactlyElementsIn(
            testPresetNames - testPresetNames[1]).inOrder()
    }

    @Test fun deleting_active_preset_makes_active_preset_null() = runTest {
        insertTestPresets()
        activePresetState.setName(testPresetNames[0])
        waitUntil { activePreset.name == testPresetNames[0] }
        presetList.onDeleteClick(testPresetNames[0])
        waitUntil { instance.shownDialog != null }
        confirmatoryDialog.onConfirmClick()
        waitUntil { activePreset.name == null }
        assertThat(activePreset.name).isNull()
    }

    @Test fun clicking_presets_without_unsaved_changes_skips_dialog() = runTest {
        insertTestPresets()
        activePreset.onClick()
        presetList.onClick(testPresetNames[1])
        waitUntil { activePreset.name != null }
        assertThat(activePreset.name).isEqualTo(testPresetNames[1])
        assertThat(getActivePlaylistIds()).containsExactly(
            testPlaylistIds[1], testPlaylistIds[2])
        assertThat(instance.state.visibility)
            .isEqualTo(MediaControllerState.Visibility.Collapsed)

        activePreset.onClick()
        presetList.onClick(testPresetNames[0])
        waitUntil { activePreset.name != testPresetNames[1] }
        assertThat(activePreset.name).isEqualTo(testPresetNames[0])
        assertThat(getActivePlaylistIds()).containsExactly(testPlaylistIds[0])
        assertThat(instance.state.visibility)
            .isEqualTo(MediaControllerState.Visibility.Collapsed)
    }

    @Test fun unsaved_changes_dialog_appearance() = runTest {
        insertTestPresets()
        presetList.onClick(testPresetNames[1])
        waitUntil { activePreset.name != null }
        playlistDao.toggleIsActive(testPlaylistIds[3])
        waitUntil { activePreset.isModified }
        assertThat(activePreset.isModified).isTrue()

        activePreset.onClick()
        presetList.onClick(testPresetNames[2])
        waitUntil { !activePreset.isModified } // should time out
        assertThat(instance.shownDialog).isInstanceOf(
            DialogType.PresetUnsavedChangesWarning::class)
        assertThat(instance.state.visibility)
            .isEqualTo(MediaControllerState.Visibility.Expanded)
        assertThat(activePreset.isModified).isTrue()
        assertThat(getActivePlaylistIds())
            .containsExactlyElementsIn(testPlaylistIds.subList(1, 4))
    }

    @Test fun unsaved_changes_dialog_dismissal() = runTest {
        insertTestPresets()
        presetList.onClick(testPresetNames[1])
        waitUntil { activePreset.name != null }
        playlistDao.toggleIsActive(testPlaylistIds[3])
        waitUntil { getActivePlaylistIds().size > 2 }

        activePreset.onClick()
        presetList.onClick(testPresetNames[2])
        unsavedChangesWarningDialog.onDismissRequest()
        waitUntil { !activePreset.isModified } // should time out
        assertThat(instance.shownDialog).isNull()
        assertThat(instance.state.visibility)
            .isEqualTo(MediaControllerState.Visibility.Expanded)
        assertThat(activePreset.isModified).isTrue()
        assertThat(getActivePlaylistIds())
            .containsExactlyElementsIn(testPlaylistIds.subList(1, 4))
    }

    @Test fun unsaved_changes_dialog_drop_changes() = runTest {
        insertTestPresets()
        presetList.onClick(testPresetNames[1])
        waitUntil { activePreset.name != null }
        playlistDao.toggleIsActive(testPlaylistIds[3])
        waitUntil { getActivePlaylistIds().size > 2 }

        activePreset.onClick()
        presetList.onClick(testPresetNames[2])
        unsavedChangesWarningDialog.onConfirmClick(false)
        waitUntil { !activePreset.isModified }
        assertThat(activePreset.isModified).isFalse()
        assertThat(instance.shownDialog).isNull()
        assertThat(instance.state.visibility)
            .isEqualTo(MediaControllerState.Visibility.Collapsed)

        // Check that new preset was loaded
        assertThat(activePreset.name).isEqualTo(testPresetNames[2])
        assertThat(getActivePlaylistIds()).containsExactly(testPlaylistIds[3], testPlaylistIds[4])

        // Check that previous preset's changes were dropped
        assertThat(presetDao.getPlaylistNamesFor(testPresetNames[1]))
            .containsExactly(testPlaylistNames[1], testPlaylistNames[2])
    }

    @Test fun unsaved_changes_dialog_save_first() = runTest {
        insertTestPresets()
        activePreset.onClick()
        presetList.onClick(testPresetNames[1])
        waitUntil { activePreset.name != null }
        playlistDao.toggleIsActive(testPlaylistIds[3])
        waitUntil { getActivePlaylistIds().size > 2 }

        presetList.onClick(testPresetNames[2])
        waitUntil { instance.shownDialog != null }
        unsavedChangesWarningDialog.onConfirmClick(true)
        waitUntil { activePreset.name == testPresetNames[2] }
        waitUntil { !activePreset.isModified }
        assertThat(activePreset.name).isEqualTo(testPresetNames[2])
        assertThat(activePreset.isModified).isFalse()
        assertThat(instance.shownDialog).isNull()
        assertThat(instance.state.visibility)
            .isEqualTo(MediaControllerState.Visibility.Collapsed)

        // Check that new preset was loaded
        assertThat(activePreset.name).isEqualTo(testPresetNames[2])
        assertThat(getActivePlaylistIds())
            .containsExactly(testPlaylistIds[3], testPlaylistIds[4])

        // Check that previous preset's changes were saved first
        assertThat(presetDao.getPlaylistNamesFor(testPresetNames[1]))
            .containsExactly(testPlaylistNames[1], testPlaylistNames[2], testPlaylistNames[3])
    }
}