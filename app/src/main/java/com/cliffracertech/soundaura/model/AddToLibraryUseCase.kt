/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import android.net.Uri
import com.cliffracertech.soundaura.model.database.Playlist
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.model.database.TrackNamesValidator
import com.cliffracertech.soundaura.model.database.newPlaylistNameValidator
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** A container of methods that adds playlists (single track
 * or multi-track) to the app's library of playlists. */
class AddToLibraryUseCase(
    private val permissionHandler: UriPermissionHandler,
    private val dao: PlaylistDao,
) {
    @Inject constructor(
        permissionHandler: AndroidUriPermissionHandler,
        dao: PlaylistDao
    ): this(permissionHandler as UriPermissionHandler, dao)

    fun trackNamesValidator(
        scope: CoroutineScope,
        initialTrackNames: List<String>
    ) = TrackNamesValidator(dao, scope, initialTrackNames)

    /** The two subtypes, [Success] and [Failure], represent the possible
     * results for calls to [addSingleTrackPlaylists] or [addPlaylist]. */
    sealed class Result {
        /** The operation succeeded. */
        data object Success: Result()

        /** The operation failed. The properties [permissionsUsed] and
         * [permissionAllowance] can help explain the reason for the failure. */
        data class Failure(
            val permissionsUsed: Int,
            val permissionAllowance: Int
        ): Result()
    }

    /**
     * Attempt to add multiple single-track playlists. Each value in [names]
     * will be used as a name for a new [Playlist], while the [Uri] with the
     * same index in [uris] will be used as that [Playlist]'s single track.
     *
     * @return The [Result] of the operation
     */
    suspend fun addSingleTrackPlaylists(
        names: List<String>,
        uris: List<Uri>,
    ): Result {
        assert(names.size == uris.size)
        val newUris = dao.filterNewUris(uris)
        val succeeded = permissionHandler.acquirePermissionsFor(newUris)

        return if (succeeded) {
            dao.insertSingleTrackPlaylists(names, uris, newUris)
            Result.Success
        } else Result.Failure(
            permissionsUsed = permissionHandler.usedAllowance,
            permissionAllowance = permissionHandler.totalAllowance)
    }

    fun newPlaylistNameValidator(
        scope: CoroutineScope,
        initialName: String
    ) = newPlaylistNameValidator(dao, scope, initialName)

    /**
     * Attempt to add a playlist with the given [name] and [shuffle] values and
     * with a track list equal to [tracks]. If non enough file permissions are
     * available to add all of the new playlist's tracks, the operation will
     * fail and the number of extra permissions that would be needed to succeed
     * will be returned.
     *
     * @return The [Result] of the operation
     */
    suspend fun addPlaylist(
        name: String,
        shuffle: Boolean,
        tracks: List<Track>,
        trackUris: List<Uri> = tracks.map(Track::uri)
    ): Result {
        val newUris = dao.filterNewUris(trackUris)
        val succeeded = permissionHandler.acquirePermissionsFor(newUris)

        return if (succeeded) {
            dao.insertPlaylist(name, shuffle, tracks, newUris)
            Result.Success
        } else Result.Failure(
            permissionsUsed = permissionHandler.usedAllowance,
            permissionAllowance = permissionHandler.totalAllowance)
    }
}