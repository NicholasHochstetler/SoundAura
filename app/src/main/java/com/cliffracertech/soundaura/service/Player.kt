/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.service

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

data class Playlist(
    val name: String,
    val shuffle: Boolean,
    val volume: Float,
    val tracks: List<Uri>
) {
    constructor(mapEntry: Map.Entry<com.cliffracertech.soundaura.model.database.Playlist, List<Uri>>) :
        this(mapEntry.key.name, mapEntry.key.shuffle, mapEntry.key.volume, mapEntry.value)
}

/**
 * A [MediaPlayer] wrapper that allows for seamless looping of the provided
 * [Playlist]. The [update] method can be used when the [Playlist]'s properties
 * change. The property [volume] describes the current volume for both audio
 * channels, and is initialized to the [Playlist.volume] field.
 *
 * The methods [play], [pause], and [stop] can be used to control playback of
 * the Player. These methods correspond to the [MediaPlayer] methods of the
 * same name, except for [stop]. [Player]'s [stop] method is functionally the
 * same as pausing while seeking to the start of the media.
 *
 * If there is a problem with one or more [Uri]s within the [Playlist.tracks],
 * playback of the next track will be attempted until one is found that can be
 * played. If [MediaPlayer] creation fails for all of the tracks, no playback
 * will occur, and calling [play] will have no effect. When one or more tracks
 * fail to play, the provided callback [onPlaybackFailure] will be invoked.
 *
 * @param context A [Context] instance. Note that the provided context instance
 *     is held onto for the lifetime of the Player instance, and so should not
 *     be a component that the Player might outlive.
 * @param playlist The [Playlist] whose contents will be played
 * @param startPlaying Whether or not the Player should call start upon
 *     successful MediaPlayer creation
 * @param onPlaybackFailure A callback that will be invoked if MediaPlayer
 *     creation fails for one or more [Uri]s. This callback is used instead
 *     of, e.g., a factory method that can return null if creation fails due
 *     to the fact that creation can fail at any point in the future when
 *     the player is looped.
 */
class Player(
    private val context: Context,
    playlist: Playlist,
    startPlaying: Boolean = false,
    private val onPlaybackFailure: (List<Uri>) -> Unit,
) {
    private var shuffle = playlist.shuffle
    private var uris = playlist.tracks
        .toMutableList()
        .apply { if (playlist.shuffle) shuffle() }
    var volume: Float = playlist.volume
        set(value) {
            field = value
            currentPlayer?.setVolume(volume, volume)
            nextPlayer?.setVolume(volume, volume)
        }

    // The next index might not be currentIndex + 1 if MediaPlayer creation
    // fails for uris[currentIndex + 1], so we track it separately
    private var currentIndex = 0
    private var nextIndex = 0
    private var currentPlayer: MediaPlayer? = createPlayerForNextUri()
    private var nextPlayer: MediaPlayer? = null

    init {
        prepareNextPlayer()
        volume = playlist.volume
        if (startPlaying) play()
    }

    fun play() { currentPlayer?.attempt(MediaPlayer::start) }
    fun pause() { currentPlayer?.attempt(MediaPlayer::pause) }
    fun stop() { currentPlayer?.attempt { pause(); seekTo(0) }}

    fun update(newPlaylist: Playlist) {
        volume = newPlaylist.volume
        val playing = currentPlayer?.isPlaying == true &&
                (currentPlayer?.currentPosition ?: 0) > 0
        val playingUri = if (!playing) null
        else uris[currentIndex]

        shuffle = newPlaylist.shuffle
        uris = newPlaylist.tracks.toMutableList().apply {
            if (newPlaylist.shuffle) shuffle()
        }
        currentIndex = newPlaylist.tracks
            .indexOf(playingUri)
            .coerceIn(uris.indices)
        prepareNextPlayer()
    }

    fun release() {
        currentPlayer?.release()
        nextPlayer?.release()
    }

    private fun prepareNextPlayer() {
        val currentPlayer = this.currentPlayer ?: return
        nextPlayer = createPlayerForNextUri()
        nextPlayer?.setVolume(volume, volume)
        currentPlayer.setNextMediaPlayer(nextPlayer)
        currentPlayer.setOnCompletionListener {
            it.release()
            this.currentPlayer = nextPlayer
            currentIndex = nextIndex
            prepareNextPlayer()
        }
    }

    private fun createPlayerForNextUri(): MediaPlayer? {
        // The start index is recorded so that we know
        // we have done one full loop of the playlist
        val startIndex = nextIndex
        var player: MediaPlayer?
        var failedUris: MutableList<Uri>? = null

        do {
            val uri = uris[nextIndex]
            player = MediaPlayer.create(context, uri) ?: run {
                failedUris?.add(uri) ?: run {
                    failedUris = mutableListOf(uri)
                }
                nextIndex = if (nextIndex == uris.lastIndex) 0
                            else nextIndex++
                null
            }
        } while (player == null && nextIndex != startIndex)

        failedUris?.let(onPlaybackFailure)
        return player
    }

    /** Attempt the [action] on the receiver [MediaPlayer]. In the case of a still-
     * initializing [MediaPlayer] a [MediaPlayer.OnPreparedListener] will automatically
     * be added to execute the [action] when the [MediaPlayer] is ready. */
    private fun MediaPlayer.attempt(action: MediaPlayer.() -> Unit) =
        try { action() }
        catch(e: java.lang.IllegalStateException) {
            setOnPreparedListener {
                action()
                it.setOnPreparedListener(null)
            }
        }
}

/**
 * A collection of [Player] instances.
 *
 * [PlayerMap] manages a collection of [Player] instances for a list of
 * [Playlist]s. The collection of [Player]s is updated via the method [update].
 *
 * Whether or not the collection of players is empty can be queried with the
 * property [isEmpty]. The property [isInitialized], which will start as false
 * but will be set to true after the first [update] call, is also provided so
 * that [PlayerMap] being empty due to the provided [List]`<Playlist>` being
 * empty can be differentiated from [PlayerMap] being empty because update
 * hasn't been called yet.
 *
 * The playing/paused/stopped state can be set for all [Player]s at once with
 * the methods [play], [pause], and [stop]. The volume for individual playlists
 * can be set with the method [setPlayerVolume]. The method [releaseAll] should
 * be called before the PlayerMap is destroyed so that all [Player] instances
 * can be released first.
 *
 * @param context A [Context] instance. Note that the context instance
 *     will be held onto for the lifetime of the [PlayerSet].
 * @param onPlaybackFailure The callback that will be invoked
 *     when playback for the provided list of [Uri]s has failed
 */
class PlayerMap(
    private val context: Context,
    private val onPlaybackFailure: (uris: List<Uri>) -> Unit,
) {
    var isInitialized = false
        private set
    private var playerMap: MutableMap<String, Player> = hashMapOf()

    val isEmpty get() = playerMap.isEmpty()

    fun play() = playerMap.values.forEach(Player::play)
    fun pause() = playerMap.values.forEach(Player::pause)
    fun stop() = playerMap.values.forEach(Player::stop)

    fun setPlayerVolume(playlistName: String, volume: Float) {
        playerMap[playlistName]?.volume = volume
    }

    fun releaseAll() = playerMap.values.forEach(Player::release)

    /** Update the PlayerSet with new [Player]s to match the provided [playlists].
     * If [startPlaying] is true, playback will start immediately. Otherwise, the
     * [Player]s will begin paused. */
    fun update(playlists: List<Playlist>, startPlaying: Boolean) {
        isInitialized = true
        val oldMap = playerMap
        playerMap = HashMap(playlists.size, 1f)

        for (playlist in playlists) {
            val existingPlayer = oldMap.remove(playlist.name)
                ?.apply { update(playlist) }

            playerMap[playlist.name] = existingPlayer ?:
                Player(context, playlist, startPlaying, onPlaybackFailure)
        }
        oldMap.values.forEach(Player::release)
    }
}