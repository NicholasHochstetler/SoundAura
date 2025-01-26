/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cliffracertech.soundaura.addbutton.AddButtonDialogState
import com.cliffracertech.soundaura.addbutton.AddButtonViewModel
import com.cliffracertech.soundaura.addbutton.getDisplayName
import com.cliffracertech.soundaura.library.Playlist
import com.cliffracertech.soundaura.model.ActivePresetState
import com.cliffracertech.soundaura.model.AddToLibraryUseCase
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.ReadModifyPresetsUseCase
import com.cliffracertech.soundaura.model.TestPermissionHandler
import com.cliffracertech.soundaura.model.UriPermissionHandler
import com.cliffracertech.soundaura.model.Validator
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.SoundAuraDatabase
import com.cliffracertech.soundaura.model.database.Track
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddButtonViewModelTests {
    private lateinit var context: Context
    private lateinit var permissionHandler: UriPermissionHandler
    private lateinit var coroutineScope: CoroutineScope
    private lateinit var navigationState: NavigationState
    private lateinit var db: SoundAuraDatabase
    private lateinit var playlistDao: PlaylistDao
    private lateinit var instance: AddButtonViewModel

    @Before fun init() {
        context = ApplicationProvider.getApplicationContext()
        permissionHandler = TestPermissionHandler()
        coroutineScope = TestCoroutineScope()
        navigationState = NavigationState()
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        playlistDao = db.playlistDao()

        val dataStore = PreferenceDataStoreFactory.create(scope = coroutineScope) {
            context.preferencesDataStoreFile("testDatastore")
        }
        val addToLibraryUseCase = AddToLibraryUseCase(permissionHandler, playlistDao)
        
        val activePresetState = ActivePresetState(dataStore, db.presetDao())
        val messageHandler = MessageHandler()
        val readModifyPresetsUseCase = ReadModifyPresetsUseCase(
            messageHandler, activePresetState, db.presetDao(), playlistDao)
        
        instance = AddButtonViewModel(
            context, coroutineScope, messageHandler, navigationState,
            readModifyPresetsUseCase, addToLibraryUseCase)
    }

    @After fun clean_up() {
        db.close()
        coroutineScope.cancel()
    }

    private val testUris = List(3) { "uri $it".toUri() }
    private suspend fun PlaylistDao.getPlaylistUris(id: Long) = getPlaylistTracks(id).map(Track::uri)
    private val testTracks = testUris.map(::Track)

    private val selectingFilesStep get() = instance.dialogState as AddButtonDialogState.SelectingFiles
    private val addIndividuallyOrAsPlaylistStep get() =
        instance.dialogState as AddButtonDialogState.AddIndividuallyOrAsPlaylistQuery
    private val nameTracksStep get() = instance.dialogState as AddButtonDialogState.NameTracks
    private val namePlaylistStep get() = instance.dialogState as AddButtonDialogState.NamePlaylist
    private val playlistOptionsStep get() = instance.dialogState as AddButtonDialogState.PlaylistOptions

    private val AddButtonDialogState.buttonTexts get() =
        buttons.map(AddButtonDialogState.ButtonInfo::textResId)
    private val AddButtonDialogState.cancelButton get() =
        buttons.find { it.textResId == R.string.cancel }!!
    private val AddButtonDialogState.backButton get() =
        buttons.find { it.textResId == R.string.back }!!
    private val AddButtonDialogState.nextButton get() =
        buttons.find { it.textResId == R.string.next }!!
    private val AddButtonDialogState.finishButton get() =
        buttons.find { it.textResId == R.string.finish }!!
    private val AddButtonDialogState.AddIndividuallyOrAsPlaylistQuery.addIndividuallyButton get() =
        buttons.find { it.textResId == R.string.add_local_files_individually_option }!!
    private val AddButtonDialogState.AddIndividuallyOrAsPlaylistQuery.addAsPlaylistButton get() =
        buttons.find { it.textResId == R.string.add_local_files_as_playlist_option }!!

    private fun goto_add_individually_or_as_playlist_step() {
        instance.onClick()
        selectingFilesStep.onFilesSelected(testUris)
    }
    private fun goto_name_tracks_step_with_one_file() {
        instance.onClick()
        selectingFilesStep.onFilesSelected(testUris.subList(0, 1))
    }
    private fun goto_name_tracks_step_with_multiple_files() {
        goto_add_individually_or_as_playlist_step()
        addIndividuallyOrAsPlaylistStep.addIndividuallyButton.onClick()
    }
    private fun goto_name_playlist_step() {
        goto_add_individually_or_as_playlist_step()
        addIndividuallyOrAsPlaylistStep.addAsPlaylistButton.onClick()
    }
    private suspend fun goto_playlist_options_step() {
        goto_name_playlist_step()
        namePlaylistStep.nextButton.onClick()
        waitUntil { instance.dialogState is AddButtonDialogState.PlaylistOptions }
    }

    @Test fun onClick_opens_file_selector() {
        assertThat(instance.dialogState).isNull()
        instance.onClick()
        assertThat(instance.dialogState).isInstanceOf(AddButtonDialogState.SelectingFiles::class)
        assertThat(selectingFilesStep.buttonTexts).isEmpty()
    }

    @Test fun file_selector_back_navigation() {
        instance.onClick()
        selectingFilesStep.onDismissRequest()
        assertThat(instance.dialogState).isNull()
    }

    @Test fun selecting_single_file_skips_query() {
        goto_name_tracks_step_with_one_file()
        assertThat(instance.dialogState).isInstanceOf(AddButtonDialogState.NameTracks::class)
        assertThat(instance.dialogState!!.wasNavigatedForwardTo).isTrue()
        assertThat(nameTracksStep.buttonTexts)
            .containsExactly(R.string.cancel, R.string.finish).inOrder()
    }

    @Test fun naming_single_track_back_navigation() {
        goto_name_tracks_step_with_one_file()
        nameTracksStep.cancelButton.onClick()
        assertThat(instance.dialogState).isNull()

        goto_name_tracks_step_with_one_file()
        nameTracksStep.onDismissRequest()
        assertThat(instance.dialogState).isNull()
    }

    @Test fun selecting_multiple_files_goes_to_query() {
        goto_add_individually_or_as_playlist_step()
        assertThat(instance.dialogState).isInstanceOf(
            AddButtonDialogState.AddIndividuallyOrAsPlaylistQuery::class)
        assertThat(instance.dialogState!!.wasNavigatedForwardTo).isFalse()
        assertThat(addIndividuallyOrAsPlaylistStep.buttonTexts)
            .containsExactly(
                R.string.cancel,
                R.string.add_local_files_individually_option,
                R.string.add_local_files_as_playlist_option
            ).inOrder()
    }

    @Test fun query_back_navigation() {
        goto_add_individually_or_as_playlist_step()
        addIndividuallyOrAsPlaylistStep.onDismissRequest()
        assertThat(instance.dialogState).isNull()

        goto_add_individually_or_as_playlist_step()
        addIndividuallyOrAsPlaylistStep.cancelButton.onClick()
        assertThat(instance.dialogState).isNull()
    }

    @Test fun clicking_add_individually_goes_to_name_tracks_step() {
        goto_name_tracks_step_with_multiple_files()
        assertThat(instance.dialogState).isInstanceOf(AddButtonDialogState.NameTracks::class)
        assertThat(instance.dialogState!!.wasNavigatedForwardTo).isTrue()
        assertThat(nameTracksStep.buttonTexts)
            .containsExactly(R.string.back, R.string.finish).inOrder()
    }

    @Test fun name_tracks_back_navigation() {
        goto_name_tracks_step_with_multiple_files()
        nameTracksStep.onDismissRequest()
        assertThat(instance.dialogState).isNull()

        goto_name_tracks_step_with_multiple_files()
        nameTracksStep.backButton.onClick()
        assertThat(instance.dialogState).isInstanceOf(
            AddButtonDialogState.AddIndividuallyOrAsPlaylistQuery::class)
        assertThat(instance.dialogState!!.wasNavigatedForwardTo).isFalse()
    }

    @Test fun clicking_add_as_playlist_goes_to_name_playlist_step() {
        goto_name_playlist_step()
        assertThat(instance.dialogState).isInstanceOf(AddButtonDialogState.NamePlaylist::class)
        assertThat(instance.dialogState!!.wasNavigatedForwardTo).isTrue()
        assertThat(namePlaylistStep.buttonTexts)
            .containsExactly(R.string.back, R.string.next).inOrder()
    }

    @Test fun name_playlist_back_navigation() {
        goto_name_playlist_step()
        namePlaylistStep.onDismissRequest()
        assertThat(instance.dialogState).isNull()

        goto_name_playlist_step()
        namePlaylistStep.backButton.onClick()
        assertThat(instance.dialogState).isInstanceOf(
            AddButtonDialogState.AddIndividuallyOrAsPlaylistQuery::class)
        assertThat(instance.dialogState!!.wasNavigatedForwardTo).isFalse()
    }

    @Test fun confirming_playlist_name_goes_to_playlist_options_step() = runTest {
        goto_playlist_options_step()
        assertThat(instance.dialogState).isInstanceOf(AddButtonDialogState.PlaylistOptions::class)
        assertThat(instance.dialogState!!.wasNavigatedForwardTo).isTrue()
        assertThat(playlistOptionsStep.buttonTexts)
            .containsExactly(R.string.back, R.string.finish).inOrder()
    }

    @Test fun playlist_options_back_navigation() = runTest {
        goto_playlist_options_step()
        playlistOptionsStep.onDismissRequest()
        assertThat(instance.dialogState).isNull()

        goto_playlist_options_step()
        playlistOptionsStep.backButton.onClick()
        assertThat(instance.dialogState).isInstanceOf(
            AddButtonDialogState.NamePlaylist::class)
        assertThat(instance.dialogState!!.wasNavigatedForwardTo).isFalse()
    }

    @Test fun validating_track_names() = runTest {
        val existingTrackName = "existing track name"
        playlistDao.insertPlaylist(existingTrackName, false, testTracks)
        advanceUntilIdle()

        goto_name_tracks_step_with_multiple_files()
        assertThat(nameTracksStep.errorIndices).isEmpty()
        assertThat(nameTracksStep.message).isNull()

        // Check for name already used error
        nameTracksStep.onNameChange(0, existingTrackName)
        advanceUntilIdle()
        assertThat(nameTracksStep.errorIndices).containsExactly(0)
        assertThat(nameTracksStep.message).isInstanceOf(Validator.Message.Error::class)

        // Check that error changes back to false when name is changed
        nameTracksStep.onNameChange(0, "new name")
        advanceUntilIdle()
        assertThat(nameTracksStep.message).isNull()
        assertThat(nameTracksStep.errorIndices).isEmpty()

        // Check that both track names are invalid when they match
        nameTracksStep.onNameChange(1, "new name")
        advanceUntilIdle()
        assertThat(nameTracksStep.errorIndices).containsExactly(0, 1)
        assertThat(nameTracksStep.message).isInstanceOf(Validator.Message.Error::class)

        // Check that error changes back to false when names no longer match
        nameTracksStep.onNameChange(1, "new name 2")
        advanceUntilIdle()
        assertThat(nameTracksStep.errorIndices).isEmpty()
        assertThat(nameTracksStep.message).isNull()

        // Check for blank name error
        nameTracksStep.onNameChange(2, "")
        advanceUntilIdle()
        assertThat(nameTracksStep.errorIndices).containsExactly(2)
        assertThat(nameTracksStep.message).isInstanceOf(Validator.Message.Error::class)

        // Check that error changes back to false when name is no longer blank
        nameTracksStep.onNameChange(2, "non-blank name")
        advanceUntilIdle()
        assertThat(nameTracksStep.errorIndices).isEmpty()
        assertThat(nameTracksStep.message).isNull()
    }

    @Test fun validating_playlist_names() = runTest {
        val playlistName = testUris.first().getDisplayName(context) + " playlist"
        playlistDao.insertPlaylist(playlistName, false, testTracks)
        waitUntil { playlistDao.getPlaylistNames().isNotEmpty() }

        goto_name_playlist_step()
        waitUntil { namePlaylistStep.message != null }
        assertThat(namePlaylistStep.message).isInstanceOf(Validator.Message.Error::class)

        namePlaylistStep.onNameChange("new playlist 2")
        waitUntil { namePlaylistStep.message == null }
        assertThat(namePlaylistStep.message).isNull()

        namePlaylistStep.onNameChange("")
        waitUntil { namePlaylistStep.message != null }
        assertThat(namePlaylistStep.message).isInstanceOf(Validator.Message.Error::class)
    }

    @Test fun name_tracks_step_finish_closes_dialog_and_adds_tracks() = runTest {
        goto_name_tracks_step_with_multiple_files()
        assertThat(playlistDao.getPlaylistNames()).isEmpty()
        val newTrack2Name = "new name"
        nameTracksStep.onNameChange(1, newTrack2Name)
        nameTracksStep.finishButton.onClick()
        waitUntil { instance.dialogState == null } // advanceUntilIdle doesn't work here for some reason
        assertThat(instance.dialogState).isNull()

        advanceUntilIdle()
        val names = playlistDao.getPlaylistNames()
        val expectedNames = listOf(
            testUris[0].getDisplayName(context),
            newTrack2Name,
            testUris[2].getDisplayName(context))
        assertThat(names).containsExactlyElementsIn(expectedNames)

        val playlists = playlistDao.getPlaylistsSortedByOrderAdded().first()
        playlists.forEachIndexed { index, playlist ->
            val uris = playlistDao.getPlaylistUris(playlist.id)
            assertThat(uris).containsExactly(testUris[index])
        }
    }

    @Test fun playlist_options_finish_closes_dialog_and_adds_playlist() = runTest {
        // default playlist name, shuffle, and track order
        goto_playlist_options_step()
        playlistOptionsStep.finishButton.onClick()
        waitUntil { playlistDao.getPlaylistsSortedByOrderAdded().first().isNotEmpty() }
        assertThat(instance.dialogState).isNull()

        val name1 = testUris.first().getDisplayName(context) + " playlist"
        var playlists = playlistDao.getPlaylistsSortedByOrderAdded().first()
        assertThat(playlists.size).isEqualTo(1)
        val playlist = playlists.first()
        assertThat(playlist.name).isEqualTo(name1)
        assertThat(playlistDao.getPlaylistShuffle(playlist.id)).isFalse()
        assertThat(playlistDao.getPlaylistUris(playlist.id))
            .containsExactlyElementsIn(testUris).inOrder()

        // non-default playlist name, shuffle, and track order
        val name2 = "new playlist name"
        goto_name_playlist_step()
        namePlaylistStep.onNameChange(name2)
        namePlaylistStep.nextButton.onClick()
        waitUntil { instance.dialogState is AddButtonDialogState.PlaylistOptions }
        playlistOptionsStep.onShuffleSwitchClick()
        playlistOptionsStep.mutablePlaylist.moveTrack(1, 2)
        playlistOptionsStep.mutablePlaylist.moveTrack(0, 1)
        playlistOptionsStep.finishButton.onClick()
        waitUntil { instance.dialogState == null }
        assertThat(instance.dialogState).isNull()

        waitUntil { playlistDao.getPlaylistNames().size == 2 }
        playlists = playlistDao.getPlaylistsSortedByOrderAdded().first()
        assertThat(playlists.map(Playlist::name)).containsExactly(name1, name2)
        assertThat(playlistDao.getPlaylistShuffle(playlists[1].id)).isTrue()
        assertThat(playlistDao.getPlaylistUris(playlists[1].id))
            .containsExactly(testUris[2], testUris[0], testUris[1]).inOrder()
    }

    @Test fun onClick_does_not_open_add_preset_with_preset_selector_hidden() {
        assertThat(instance.dialogState).isNull()
        instance.onClick()
        assertThat(instance.dialogState).isInstanceOf(AddButtonDialogState.SelectingFiles::class)
    }

    @Test fun onClick_does_not_open_dialog_with_no_active_playlists() {
        navigationState.showPresetSelector()
        instance.onClick()
        assertThat(instance.dialogState).isNull()
    }
}