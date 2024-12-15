/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import androidx.compose.material.SnackbarDuration
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.model.database.Playlist
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.model.database.playlistRenameValidator
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** A container of methods that modify the app's library of playlists. */
class ModifyLibraryUseCase(
    private val permissionHandler: UriPermissionHandler,
    private val messageHandler: MessageHandler,
    private val dao: PlaylistDao,
) {
    @Inject constructor(
        permissionHandler: AndroidUriPermissionHandler,
        messageHandler: MessageHandler,
        dao: PlaylistDao
    ): this(permissionHandler as UriPermissionHandler, messageHandler, dao)

    suspend fun togglePlaylistIsActive(playlistId: Long) {
        dao.toggleIsActive(playlistId)
    }

    suspend fun setPlaylistVolume(playlistId: Long, newVolume: Float) {
        dao.setVolume(playlistId, newVolume)
    }

    /**
     * Return a [ValidatedNamingState] that can be used to rename the
     * playlist whose old name matches [oldName]. [onFinished] will be
     * called when the renaming ends successfully or otherwise, and
     * can be used, e.g., to dismiss a rename dialog.
     */
    fun renameState(
        playlistId: Long,
        oldName: String,
        scope: CoroutineScope,
        onFinished: () -> Unit
    ) = ValidatedNamingState(
        validator = playlistRenameValidator(dao, oldName, scope),
        coroutineScope = scope,
        onNameValidated = { newName ->
            if (newName != oldName)
                dao.rename(playlistId, newName)
            onFinished()
        })

    /**
     * Update the [Playlist] identified by [playlistId] to have a shuffle on/
     * off state matching [shuffle], and to have a track list matching [tracks].
     * While the playlist's shuffle state will always be set, the track list
     * update operation can fail if the [UriPermissionHandler] in use indicates
     * that permissions could not be obtained for all of the new tracks (e.g.
     * if the permitted permission allowance has been used up). In this case,
     * an explanatory message will be displayed using the [MessageHandler]
     * provided in the constructor.
     */
    suspend fun setPlaylistShuffleAndTracks(
        playlistId: Long,
        shuffle: Boolean,
        tracks: List<Track>
    ) {
        val uris = tracks.map(Track::uri)
        val newUris = dao.filterNewUris(uris)
        val removableUris = dao.getUniqueUrisNotIn(uris, playlistId)
        val postOpPermissionAllowance = permissionHandler.remainingAllowance +
                                        removableUris.size - newUris.size
        if (postOpPermissionAllowance < 0) {
            messageHandler.postMessage(
                R.string.cant_modify_playlist_tracks_warning,
                duration = SnackbarDuration.Long)
            dao.setPlaylistShuffle(playlistId, shuffle)
        } else {
            val removedUris = dao.setPlaylistShuffleAndTracks(
                playlistId = playlistId,
                shuffle = shuffle,
                tracks = tracks,
                newUris = newUris,
                removableUris = removableUris)
            permissionHandler.releasePermissionsFor(removedUris)
            permissionHandler.acquirePermissionsFor(newUris)
        }
    }

    /** Set the [Playlist] identified by [playlistId]'s volume boost property
     * to [volumeBoostDb]. Values of [volumeBoostDb] will be coerced into the
     * supported range of [0, 30]. */
    suspend fun setPlaylistVolumeBoostDb(
        playlistId: Long,
        volumeBoostDb: Int
    ) {
        dao.setVolumeBoostDb(playlistId, volumeBoostDb.coerceIn(0, 30))
    }

    /** Remove the [Playlist] identified by [id]. */
    suspend fun removePlaylist(id: Long) {
        val unusedTracks = dao.deletePlaylist(id)
        permissionHandler.releasePermissionsFor(unusedTracks)
    }
}