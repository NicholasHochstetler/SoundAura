/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cliffracertech.soundaura.model.database.LibraryPlaylist
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.SoundAuraDatabase
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.service.ActivePlaylistSummary
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTests {
    private lateinit var db: SoundAuraDatabase
    private lateinit var dao: PlaylistDao

    private val testUris = List(5) { "uri $it".toUri() }
    private val testTracks = testUris.map(::Track)
    private val testPlaylistNames = listOf(
        "playlist b", "playlist d", "playlist a", "playlist c", "track e")

    @Before fun init() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, SoundAuraDatabase::class.java).build()
        dao = db.playlistDao()
    }

    @After fun closeDb() { db.close() }

    @Test fun initial_list_is_empty() = runTest {
        assertThat(dao.getPlaylistNames()).isEmpty()
    }

    @Test fun inserting_multi_track_playlists() = runTest {
        val name1 = testPlaylistNames[0]
        dao.insertPlaylist(name1, false, testTracks)
        var playlists = dao.getPlaylistsSortedByOrderAdded().first()
        assertThat(playlists.map(LibraryPlaylist::name)).containsExactly(name1)
        assertThat(dao.getPlaylistShuffle(playlists.first().id)).isFalse()
        assertThat(dao.getPlaylistTracks(playlists.first().id)).containsExactlyElementsIn(testTracks)

        val name2 = testPlaylistNames[2]
        dao.insertPlaylist(name2, true, testTracks.subList(0, 3))
        playlists = dao.getPlaylistsSortedByOrderAdded().first()
        assertThat(playlists.map(LibraryPlaylist::name)).containsExactly(name1, name2)
        assertThat(dao.getPlaylistShuffle(playlists[1].id)).isTrue()
        assertThat(dao.getPlaylistTracks(playlists[1].id)).containsExactlyElementsIn(testTracks.subList(0, 3))
    }

    @Test fun inserting_single_track_playlists() = runTest {
        dao.insertSingleTrackPlaylists(
            names = testPlaylistNames.subList(0, 2),
            uris = testUris.subList(0, 2))
        var playlists = dao.getPlaylistsSortedByOrderAdded().first()
        assertThat(playlists.map(LibraryPlaylist::name))
            .containsExactly(testPlaylistNames[0], testPlaylistNames[1])
        playlists.forEachIndexed { index, playlist ->
            assertThat(dao.getPlaylistTracks(playlist.id)).containsExactly(testTracks[index])
        }

        dao.insertSingleTrackPlaylists(
            names = testPlaylistNames.subList(2, 5),
            uris = testUris.subList(2, 5))
        playlists = dao.getPlaylistsSortedByOrderAdded().first()
        assertThat(playlists.map(LibraryPlaylist::name))
            .containsExactlyElementsIn(testPlaylistNames)
        playlists.forEachIndexed { index, playlist ->
            assertThat(dao.getPlaylistTracks(playlist.id)).containsExactly(testTracks[index])
        }
    }

    @Test fun deleting_playlists() = runTest {
        dao.insertSingleTrackPlaylists(testPlaylistNames, testUris)
        val ids = dao.getPlaylistsSortedByOrderAdded().first().map(LibraryPlaylist::id)
        dao.deletePlaylist(ids[2])
        assertThat(dao.getPlaylistNames())
            .containsExactlyElementsIn(testPlaylistNames - testPlaylistNames[2])

        dao.deletePlaylist(ids[1])
        dao.deletePlaylist(ids[4])
        assertThat(dao.getPlaylistNames())
            .containsExactly(testPlaylistNames[0], testPlaylistNames[3])
    }

    @Test fun deleting_playlists_returns_unused_uris() = runTest {
        dao.insertPlaylist(testPlaylistNames[0], false, testTracks.subList(0, 2))
        dao.insertPlaylist(testPlaylistNames[1], false, listOf(testTracks[1]))
        dao.insertPlaylist(testPlaylistNames[2], false, listOf(testTracks[2]))
        dao.insertPlaylist(testPlaylistNames[3], false, testTracks.subList(3, 5))
        dao.insertPlaylist(testPlaylistNames[4], false, testTracks.subList(3, 5))
        val ids = dao.getPlaylistsSortedByOrderAdded().first().map(LibraryPlaylist::id)

        var unusedUris = dao.deletePlaylist(ids[0])
        assertThat(unusedUris).containsExactly(testUris[0])
        
        unusedUris = dao.deletePlaylist(ids[1])
        assertThat(unusedUris).containsExactly(testUris[1])

        unusedUris = dao.deletePlaylist(ids[2])
        assertThat(unusedUris).containsExactly(testUris[2])

        unusedUris = dao.deletePlaylist(ids[3])
        assertThat(unusedUris).isEmpty()
        
        unusedUris = dao.deletePlaylist(ids[4])
        assertThat(unusedUris).containsExactly(testUris[3], testUris[4])
    }

    @Test fun set_playlist_shuffle() = runTest {
        dao.insertPlaylist(testPlaylistNames[0], false, listOf(testTracks[0]))
        dao.insertPlaylist(testPlaylistNames[1], true, listOf(testTracks[1]))
        dao.insertPlaylist(testPlaylistNames[2], false, listOf(testTracks[2]))
        dao.insertPlaylist(testPlaylistNames[3], true, listOf(testTracks[3]))

        val ids = dao.getPlaylistsSortedByOrderAdded().first().map(LibraryPlaylist::id)
        dao.setPlaylistShuffle(ids[0], true)
        dao.setPlaylistShuffle(ids[1], false)
        dao.setPlaylistShuffle(ids[2], false)
        dao.setPlaylistShuffle(ids[3], true)

        assertThat(dao.getPlaylistShuffle(ids[0])).isTrue()
        assertThat(dao.getPlaylistShuffle(ids[1])).isFalse()
        assertThat(dao.getPlaylistShuffle(ids[2])).isFalse()
        assertThat(dao.getPlaylistShuffle(ids[3])).isTrue()
    }
    
    @Test fun set_playlist_shuffle_and_contents() = runTest {
        dao.insertPlaylist(
            playlistName = testPlaylistNames[0],
            shuffle = false,
            tracks = listOf(testTracks[0]))
        val id = dao.getPlaylistsSortedByNameAsc().first().first().id
        
        dao.setPlaylistShuffleAndTracks(
            playlistId = id,
            shuffle = true,
            tracks = testTracks.subList(0, 2))
        assertThat(dao.getPlaylistShuffle(id)).isTrue()
        assertThat(dao.getPlaylistTracks(id))
            .containsExactlyElementsIn(testTracks.subList(0, 2))
        
        dao.setPlaylistShuffleAndTracks(id, true, testTracks.subList(2, 5))
        assertThat(dao.getPlaylistShuffle(id)).isTrue()
        assertThat(dao.getPlaylistTracks(id))
            .containsExactlyElementsIn(testTracks.subList(2, 5))
    }

    @Test fun set_playlist_shuffle_and_contents_returns_unused_uris() = runTest {
        dao.insertPlaylist(
            playlistName = testPlaylistNames[0],
            shuffle = false,
            tracks = listOf(testTracks[0]))
        val id = dao.getPlaylistsSortedByNameAsc().first().first().id

        var unusedUris = dao.setPlaylistShuffleAndTracks(id, true, testTracks.subList(0, 2))
        assertThat(unusedUris).isEmpty()

        unusedUris = dao.setPlaylistShuffleAndTracks(id, true, testTracks.subList(2, 3))
        assertThat(unusedUris).containsExactlyElementsIn(testUris.subList(0, 2))

        unusedUris = dao.setPlaylistShuffleAndTracks(id, true, testTracks)
        assertThat(unusedUris).isEmpty()

        unusedUris = dao.setPlaylistShuffleAndTracks(id, true, testTracks.subList(3, 5))
        assertThat(unusedUris).containsExactlyElementsIn(testUris.subList(0, 3))
    }
    
    @Test fun filter_new_tracks() = runTest {
        var newTracks = dao.filterNewUris(testUris)
        assertThat(newTracks).containsExactlyElementsIn(testUris)
        
        dao.insertPlaylist(testPlaylistNames[0], false, testTracks.subList(0, 2))
        newTracks = dao.filterNewUris(testUris)
        assertThat(newTracks).containsExactlyElementsIn(testUris.subList(2, 5))
    }

    @Test fun playlist_name_already_used() = runTest {
        val name = testPlaylistNames[0]
        assertThat(dao.exists(name)).isFalse()

        dao.insertPlaylist(name, false, testTracks)
        assertThat(dao.exists(name)).isTrue()

        val id = dao.getPlaylistsSortedByNameAsc().first().first().id
        dao.deletePlaylist(id)
        assertThat(dao.exists(name)).isFalse()
    }

    private fun runTestWithPlaylists(testBody: suspend () -> Unit) = runTest {
        dao.insertSingleTrackPlaylists(
            names = testPlaylistNames,
            uris = testUris)
        val playlists = dao.getPlaylistsSortedByOrderAdded().first()
        dao.toggleIsActive(playlists[1].id)
        dao.toggleIsActive(playlists[3].id)
        testBody()
    }

    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun get_playlists_sorted_by_name_asc() = runTestWithPlaylists {
        var playlistNames = dao.getPlaylistsSortedByNameAsc().first().map(LibraryPlaylist::name)
        assertThat(playlistNames).containsExactlyElementsIn(testPlaylistNames.sorted()).inOrder()

        playlistNames = dao.getPlaylistsSortedByNameAsc("%playlist %").first().map { it.name }
        assertThat(playlistNames).containsExactlyElementsIn(
            testPlaylistNames.subList(0, 4).sorted()).inOrder()
    }

    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun get_playlists_sorted_by_name_desc() = runTestWithPlaylists {
        var playlistNames = dao.getPlaylistsSortedByNameDesc().first().map { it.name }
        assertThat(playlistNames).containsExactlyElementsIn(
            testPlaylistNames.sortedDescending()).inOrder()

        playlistNames = dao.getPlaylistsSortedByNameDesc("%playlist %").first().map { it.name }
        assertThat(playlistNames).containsExactlyElementsIn(
            testPlaylistNames.subList(0, 4).sortedDescending()).inOrder()
    }

    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun get_playlists_sorted_by_order_added() = runTestWithPlaylists {
        var playlistNames = dao.getPlaylistsSortedByOrderAdded().first().map { it.name }
        assertThat(playlistNames).containsExactlyElementsIn(testPlaylistNames).inOrder()

        playlistNames = dao.getPlaylistsSortedByOrderAdded("%playlist %").first().map { it.name }
        assertThat(playlistNames).containsExactlyElementsIn(testPlaylistNames.subList(0, 4)).inOrder()
    }

    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun get_playlists_sorted_by_name_asc_with_active_first() = runTestWithPlaylists {
        var playlistNames = dao.getPlaylistsSortedByActiveThenNameAsc().first().map { it.name }
        assertThat(playlistNames).containsExactly(
            testPlaylistNames[3], testPlaylistNames[1],
            testPlaylistNames[2], testPlaylistNames[0],
            testPlaylistNames[4]
        ).inOrder()

        playlistNames = dao.getPlaylistsSortedByActiveThenNameAsc("%playlist %").first().map { it.name }
        assertThat(playlistNames).containsExactly(
            testPlaylistNames[3], testPlaylistNames[1],
            testPlaylistNames[2], testPlaylistNames[0]
        ).inOrder()
    }

    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun get_playlists_sorted_by_name_desc_with_active_first() = runTestWithPlaylists {
        var playlistNames = dao.getPlaylistsSortedByActiveThenNameDesc().first().map { it.name }
        assertThat(playlistNames).containsExactly(
            testPlaylistNames[1], testPlaylistNames[3],
            testPlaylistNames[4], testPlaylistNames[0],
            testPlaylistNames[2]
        ).inOrder()

        playlistNames = dao.getPlaylistsSortedByActiveThenNameDesc("%playlist %").first().map { it.name }
        assertThat(playlistNames).containsExactly(
            testPlaylistNames[1], testPlaylistNames[3],
            testPlaylistNames[0], testPlaylistNames[2]
        ).inOrder()
    }

    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun get_playlists_sorted_by_order_added_with_active_first() = runTestWithPlaylists {
        var playlistNames = dao.getPlaylistsSortedByActiveThenOrderAdded().first().map { it.name }
        assertThat(playlistNames).containsExactly(
            testPlaylistNames[1], testPlaylistNames[3],
            testPlaylistNames[0], testPlaylistNames[2],
            testPlaylistNames[4]
        ).inOrder()

        playlistNames = dao.getPlaylistsSortedByActiveThenOrderAdded("%playlist %").first().map { it.name }
        assertThat(playlistNames).containsExactly(
            testPlaylistNames[1], testPlaylistNames[3],
            testPlaylistNames[0], testPlaylistNames[2]
        ).inOrder()
    }

    @Test fun get_at_least_one_playlist_is_active() = runTest {
        dao.insertSingleTrackPlaylists(testPlaylistNames, testUris)
        val ids = dao.getPlaylistsSortedByOrderAdded().first().map(LibraryPlaylist::id)

        var noPlaylistsAreActive = dao.getNoPlaylistsAreActive().first()
        assertThat(noPlaylistsAreActive).isTrue()

        dao.toggleIsActive(ids[1])
        noPlaylistsAreActive = dao.getNoPlaylistsAreActive().first()
        assertThat(noPlaylistsAreActive).isFalse()

        dao.toggleIsActive(ids[3])
        noPlaylistsAreActive = dao.getNoPlaylistsAreActive().first()
        assertThat(noPlaylistsAreActive).isFalse()

        dao.toggleIsActive(ids[1])
        noPlaylistsAreActive = dao.getNoPlaylistsAreActive().first()
        assertThat(noPlaylistsAreActive).isFalse()

        dao.toggleIsActive(ids[3])
        noPlaylistsAreActive = dao.getNoPlaylistsAreActive().first()
        assertThat(noPlaylistsAreActive).isTrue()
    }

    @Test fun get_active_playlists_and_tracks() = runTest {
        val ids = listOf(
            dao.insertPlaylist(testPlaylistNames[0], false, listOf(testTracks[0])),
            dao.insertPlaylist(testPlaylistNames[1], false, testTracks),
            dao.insertPlaylist(testPlaylistNames[2], false, testTracks.subList(2, 5)))
        var activePlaylists = dao.getActivePlaylistsAndTracks().first()
        assertThat(activePlaylists).isEmpty()

        dao.toggleIsActive(ids[1])
        activePlaylists = dao.getActivePlaylistsAndTracks().first()
        assertThat(activePlaylists.size).isEqualTo(1)
        assertThat(activePlaylists.keys.first().id).isEqualTo(ids[1])
        assertThat(activePlaylists.values.first())
            .containsExactlyElementsIn(testUris).inOrder()

        dao.toggleIsActive(ids[0])
        activePlaylists = dao.getActivePlaylistsAndTracks().first()
        assertThat(activePlaylists.size).isEqualTo(2)
        assertThat(activePlaylists.keys.map(ActivePlaylistSummary::id))
            .containsExactly(ids[0], ids[1]).inOrder()
       assertThat(activePlaylists.values)
           .containsExactly(listOf(testUris[0]), testUris)

        dao.toggleIsActive(ids[0])
        dao.toggleIsActive(ids[1])
        dao.toggleIsActive(ids[2])
        activePlaylists = dao.getActivePlaylistsAndTracks().first()
        assertThat(activePlaylists.size).isEqualTo(1)
        assertThat(activePlaylists.keys.first().id).isEqualTo(ids[2])
        assertThat(activePlaylists.values.first())
            .containsExactlyElementsIn(testUris.subList(2, 5)).inOrder()
    }

    @Test fun get_playlist_names() = runTest {
        assertThat(dao.getPlaylistNames()).isEmpty()

        val ids = mutableListOf(
            dao.insertPlaylist(testPlaylistNames[0], false, testTracks))
        assertThat(dao.getPlaylistNames()).containsExactly(testPlaylistNames[0])

        ids.add(dao.insertPlaylist(testPlaylistNames[1], false, testTracks))
        ids.add(dao.insertPlaylist(testPlaylistNames[2], false, testTracks))
        dao.deletePlaylist(ids[0])
        assertThat(dao.getPlaylistNames())
            .containsExactlyElementsIn(testPlaylistNames.subList(1, 3))
    }

    @Test fun get_playlist_tracks() = runTest {
        var uris = dao.getPlaylistTracks(-1L)
        assertThat(uris).isEmpty()

        var id = dao.insertPlaylist(testPlaylistNames[0], false, testTracks)
        uris = dao.getPlaylistTracks(id)
        assertThat(uris).containsExactlyElementsIn(testTracks).inOrder()

        id = dao.insertPlaylist(testPlaylistNames[1], false, testTracks.subList(3, 5))
        uris = dao.getPlaylistTracks(id)
        assertThat(uris).containsExactlyElementsIn(testTracks.subList(3, 5)).inOrder()
    }

    @Test fun rename_playlist() = runTest {
        val id = dao.insertPlaylist(testPlaylistNames[0], false, testTracks)
        val newName = "new playlist name"
        dao.rename(id, newName)
        assertThat(dao.getPlaylistNames()).containsExactly(newName)
    }

    @Test fun toggle_playlist_is_active() = runTest {
        val ids = mutableListOf(
            dao.insertPlaylist(testPlaylistNames[0], false, testTracks))
        dao.toggleIsActive(ids[0])
        var activePlaylistIds = dao.getActivePlaylistsAndTracks()
            .first().keys.map(ActivePlaylistSummary::id)
        assertThat(activePlaylistIds.size).isEqualTo(1)
        assertThat(activePlaylistIds.first()).isEqualTo(ids[0])

        ids.add(dao.insertPlaylist(testPlaylistNames[1], false, testTracks))
        ids.add(dao.insertPlaylist(testPlaylistNames[2], false, testTracks))
        dao.toggleIsActive(ids[0])
        dao.toggleIsActive(ids[1])
        dao.toggleIsActive(ids[2])
        activePlaylistIds = dao.getActivePlaylistsAndTracks()
            .first().keys.map(ActivePlaylistSummary::id)
        assertThat(activePlaylistIds.size).isEqualTo(2)
        assertThat(activePlaylistIds).containsExactly(ids[1], ids[2])
    }

    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun set_volume() = runTest {
        val ids = listOf(
            dao.insertPlaylist(testPlaylistNames[0], false, testTracks),
            dao.insertPlaylist(testPlaylistNames[1], false, testTracks),
            dao.insertPlaylist(testPlaylistNames[2], false, testTracks))
        var volumes = dao.getPlaylistsSortedByNameAsc()
            .first().map(LibraryPlaylist::volume)
        assertThat(volumes).containsExactly(1f, 1f, 1f)

        dao.setVolume(ids[0], 0.5f)
        dao.setVolume(ids[2], 0.25f)
        volumes = dao.getPlaylistsSortedByNameAsc()
            .first().map(LibraryPlaylist::volume)
        assertThat(volumes).containsExactly(0.25f, 0.5f, 1.0f).inOrder()

        dao.setVolume(ids[2], 1f)
        volumes = dao.getPlaylistsSortedByNameAsc()
            .first().map(LibraryPlaylist::volume)
        assertThat(volumes).containsExactly(1.0f, 0.5f, 1f).inOrder()
    }

    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun set_track_hasError() = runTest {
        val ids = listOf(
            dao.insertPlaylist(testPlaylistNames[0], false, listOf(testTracks[0])),
            dao.insertPlaylist(testPlaylistNames[1], false, testTracks.subList(2, 5)))
        dao.setTracksHaveError(listOf(testUris[0]))
        var tracks = dao.getPlaylistTracks(ids[0])
        assertThat(tracks.map(Track::hasError)).containsExactly(true)

        dao.setTracksHaveError(listOf(testUris[2]))
        tracks = dao.getPlaylistTracks(ids[1])
        assertThat(tracks.map(Track::hasError))
            .containsExactly(true, false, false).inOrder()

        dao.setTracksHaveError(listOf(testUris[3], testUris[4]))
        tracks = dao.getPlaylistTracks(ids[1])
        assertThat(tracks.map(Track::hasError)).doesNotContain(false)
    }

    @Test fun playlist_hasError_updates() = runTest {
        dao.insertPlaylist(testPlaylistNames[0], false, listOf(testTracks[0]))
        dao.insertPlaylist(testPlaylistNames[1], false, testTracks.subList(2, 5))
        dao.insertPlaylist(testPlaylistNames[2], false, testTracks)
        var playlists = dao.getPlaylistsSortedByOrderAdded().first()
        assertThat(playlists.map(LibraryPlaylist::hasError)).doesNotContain(true)

        dao.setTracksHaveError(listOf(testUris[0]))
        playlists = dao.getPlaylistsSortedByOrderAdded().first()
        assertThat(playlists.map(LibraryPlaylist::hasError))
            .containsExactly(true, false, false).inOrder()

        dao.setTracksHaveError(listOf(testUris[2]))
        playlists = dao.getPlaylistsSortedByOrderAdded().first()
        assertThat(playlists.map(LibraryPlaylist::hasError))
            .containsExactly(true, false, false).inOrder()

        dao.setTracksHaveError(listOf(testUris[3], testUris[4]))
        playlists = dao.getPlaylistsSortedByOrderAdded().first()
        assertThat(playlists.map(LibraryPlaylist::hasError))
            .containsExactly(true, true, false).inOrder()

        dao.setTracksHaveError(listOf(testUris[1]))
        playlists = dao.getPlaylistsSortedByOrderAdded().first()
        assertThat(playlists.map(LibraryPlaylist::hasError)).doesNotContain(false)
    }
}