/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
import android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
import android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioAttributesCompat
import androidx.media.AudioAttributesCompat.CONTENT_TYPE_UNKNOWN
import androidx.media.AudioAttributesCompat.USAGE_MEDIA
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.AudioManagerCompat.AUDIOFOCUS_GAIN
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.model.database.Playlist
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.preferenceFlow
import com.cliffracertech.soundaura.repeatWhenStarted
import com.cliffracertech.soundaura.service.PlayerService.Binder
import com.cliffracertech.soundaura.service.PlayerService.Companion.PlaybackChangeListener
import com.cliffracertech.soundaura.service.PlayerService.Companion.addPlaybackChangeListener
import com.cliffracertech.soundaura.settings.PrefKeys
import com.cliffracertech.soundaura.settings.dataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * A service to play the set of playlists marked as active in the app's database.
 *
 * PlayerService can either be started independently of an activity with a
 * [startService] call, or can be started bound to an activity if the activity
 * calls [bindService]. In the latter case, PlayerService will call [startService]
 * on itself so that it outlives the binding activity. In either case,
 * PlayerService presents a foreground notification to the user that displays
 * its current play/pause state in string form, along with actions to toggle
 * the play/pause state and to close the service.
 *
 * Changes in the playback state can be listened to by calling the static
 * function [addPlaybackChangeListener] with a [PlaybackChangeListener].
 * Playback state can be affected by calling startService with an [Intent]
 * with an action value of [PlayerService.setPlaybackAction], an extra key the
 * same as the action, and an extra value of the desired [PlaybackStateCompat]
 * value. A stop timer can be set with an [Intent] with an action value of
 * [PlayerService.setTimerAction], an extra key the same as the action, and an
 * extra value that is the milliseconds since 1/01/1970 when playback should be
 * stopped. A null or zero value can also be passed in for the duration to
 * cancel a current stop timer. Both of the actions can be accomplished more
 * easily with the static methods [PlayerService.setPlaybackIntent] and
 * [PlayerService.setTimerIntent].
 *
 * To ensure that the volume for already playing playlists is changed without
 * perceptible lag, PlayerService will not respond to playlist volume changes
 * made at the database level for already playing playlists. Instead, the method
 * [Binder.setPlaylistVolume] must be called with the [Playlist.name] and its
 * new volume. If a bound activity presents the user with, e.g, a slider to
 * change a playlist's volume, the slider's onSlide callback should call
 * [Binder.setPlaylistVolume].
 *
 * PlayerService reads the values of and changes its behavior depending on the
 * app preferences pointed to by the keys [PrefKeys.playInBackground] and
 * [PrefKeys.stopInsteadOfPause]. Read the documentation for these preferences
 * for more information about how PlayerService responds to each value of these
 * settings.
 */
@AndroidEntryPoint
class PlayerService: LifecycleService() {
    private val unpauseLocks = mutableSetOf<String>()
    private val playbackModules = mutableListOf(
        OnAudioDeviceChangePlaybackModule(
            unpauseLocks, ::autoPauseIf, ::setPlaybackState),
        PhoneStateAwarePlaybackModule(::autoPauseIf))
    @Inject lateinit var playlistDao: PlaylistDao
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: PlayerNotification

    private var autoStopJob: Job? = null
    private var stopTime by mutableStateOf<Instant?>(null)

    private val playerMap = PlayerMap(this) { problemUris ->
        /* onPlayerCreationFailure = */lifecycleScope.launch {
            playlistDao.setTracksHaveError(problemUris)
        }
    }

    private fun updateNotification() = notificationManager.update(playbackState, stopTime)

    private var playInBackground = false
        set(value) {
            field = value
            notificationManager.useMediaSession = !value
            if (value) {
                abandonAudioFocus()
                unpauseLocks.remove(autoPauseAudioFocusLossKey)
                hasAudioFocus = true
            } else if (isPlaying)
                hasAudioFocus = requestAudioFocus()
        }

    private var hasAudioFocus = false
        set(hasFocus) {
            if (field == hasFocus) return
            field = hasFocus
            autoPauseIf(!hasFocus, autoPauseAudioFocusLossKey)
        }

    /** isPlaying is only used so that binding clients have access to a
     * snapshot aware version of [playbackState]. Its value is updated in
     * [setPlaybackState], and should not be changed elsewhere to ensure
     * that mismatched state does not occur.*/
    private var isPlaying by mutableStateOf(false)

    private var stopInsteadOfPause = false
        set(value) {
            field = value
            if (value && !isPlaying)
                playerMap.stop()
        }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        playbackState = STATE_PAUSED

        val playInBackgroundKey = booleanPreferencesKey(PrefKeys.playInBackground)
        val playInBackgroundFlow = dataStore.preferenceFlow(playInBackgroundKey, false)
        // playInBackground needs to be set before playback starts so that
        // PlayerService knows whether it needs to request audio focus or not.
        val playInBackgroundFirstValue = runBlocking { playInBackgroundFlow.first() }

        notificationManager = PlayerNotification(
            service = this,
            playIntent = playIntent(this),
            pauseIntent = pauseIntent(this),
            stopIntent = stopIntent(this),
            cancelTimerIntent = setTimerIntent(this, null),
            playbackState = playbackState,
            stopTime = stopTime,
            useMediaSession = !playInBackgroundFirstValue)
        playInBackground = playInBackgroundFirstValue

        repeatWhenStarted {
            playInBackgroundFlow
                .onEach { playInBackground = it }
                .launchIn(this)

            val stopInsteadOfPauseKey = booleanPreferencesKey(PrefKeys.stopInsteadOfPause)
            dataStore.preferenceFlow(stopInsteadOfPauseKey, false)
                .onEach { stopInsteadOfPause = it }
                .launchIn(this)

            playlistDao.getActivePlaylistsAndTracks()
                .onEach(::updatePlayers)
                .launchIn(this)
        }

        playbackModules.forEach { it.onCreate(this) }
        val intent = Intent(this, PlayerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onDestroy() {
        playbackModules.forEach { it.onDestroy(this) }
        playbackState = STATE_STOPPED
        notificationManager.remove()
        playerMap.releaseAll()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            setPlaybackAction -> {
                val targetState = intent.extras?.getInt(setPlaybackAction)
                targetState?.let(::setPlaybackState)
            } setTimerAction -> {
                setStopTime(intent.extras?.getLong(setTimerAction))
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Set the companion object's [playbackState] and ensure that all state
     * derived from the companion's playbackState (e.g. the notification
     * state and the value of [isPlaying]) is updated. Except for when the
     * service is being created or destroyed, the companion's playback
     * state should not be altered outside of setPlaybackState to ensure
     * that mismatched state does not occur.
     *
     * @param state The desired [PlaybackStateCompat] value. The supported
     *     values are [STATE_PLAYING], [STATE_PAUSED], and [STATE_STOPPED]. Other
     *     values will be ignored.
     * @param clearUnpauseLocks Whether or not to reset all unpause locks.
     *     This should only be false when the playback state is being set
     *     to [STATE_PAUSED] as the result of an [autoPauseIf] call.
     */
    private fun setPlaybackState(state: Int, clearUnpauseLocks: Boolean = true) {
        if (playbackState == state)
            return
        if (state != STATE_PLAYING && state != STATE_PAUSED && state != STATE_STOPPED)
            return

        val newState = when {
            state == STATE_PLAYING && playerMap.isInitialized && playerMap.isEmpty -> {
                // If there are no active tracks, we want to prevent a change to
                // STATE_PLAYING and show an explanation message so that the user
                // understands why their, e.g., play button tap didn't do anything.
                // If the service was moved directly from a stopped to playing
                // state, then the PlayerSet might be empty because the first new
                // value for TrackDao's activeTracks hasn't have been collected
                // yet. The updatePlayers method will handle this edge case.
                showAutoPausePlaybackExplanation()
                STATE_PAUSED
            } state == STATE_PLAYING && !hasAudioFocus -> {
                hasAudioFocus = requestAudioFocus()
                if (hasAudioFocus) STATE_PLAYING
                else {
                    // autoPauseIf is not called directly here because it calls
                    // setPlaybackState itself and we don't want to get stuck in
                    // an infinite loop, but we do want playback to resume if
                    // audio focus is later gained.
                    unpauseLocks.add(autoPauseAudioFocusLossKey)
                    STATE_PAUSED
                }
            } else -> state
        }
        if (newState == playbackState)
            return

        if (clearUnpauseLocks)
            unpauseLocks.clear()
        playbackState = newState
        isPlaying = newState == STATE_PLAYING
        updateNotification()
        if (newState != STATE_STOPPED) when {
            isPlaying ->          playerMap.play()
            stopInsteadOfPause -> playerMap.stop()
            else ->               playerMap.pause()
        } else {
            if (!playInBackground && hasAudioFocus)
                abandonAudioFocus()
            stopSelf()
        }
    }

    private fun setStopTime(epochTimeMillis: Long?) {
        autoStopJob?.cancel()
        if (epochTimeMillis == null || epochTimeMillis == 0L) {
            stopTime = null
            autoStopJob = null
            updateNotification()
        } else {
            stopTime = Instant.ofEpochMilli(epochTimeMillis)
            val delayMillis = Instant.now().until(stopTime, ChronoUnit.MILLIS)
            autoStopJob = lifecycleScope.launch {
                delay(delayMillis)
                stopTime = null
                autoStopJob = null
                // updateNotification() is not needed because setPlaybackState will call it
                setPlaybackState(STATE_STOPPED)
            }
        }
        updateNotification()
    }

    private fun updatePlayers(playlists: Map<ActivePlaylistSummary, List<Uri>>) {
        val firstUpdate = !playerMap.isInitialized
        playerMap.update(playlists, isPlaying)

        // If the new track list is empty when isPlaying is true, we want
        // to pause playback because there are no playlists to play.
        if (isPlaying && playlists.isEmpty()) {
            setPlaybackState(STATE_PAUSED)
            // If this playback auto pause occurred due to the user making the last
            // active track inactive, then no user feedback should be necessary. If
            // this playback auto pause was due to an explicit attempt by the user
            // to start playback when there are no active tracks, then we want to
            // display a message to the user in this case explaining why the attempt
            // to start playback failed. Normally this case would be caught by
            // playbackState's custom setter, but if the service is moved directly
            // from a stopped to playing state, then the first value of playlistDao's
            // getActivePlaylistsAndTracks won't have been collected yet, and
            // playbackState's custom setter therefore won't know if it should
            // prevent the change to STATE_PLAYING. This check will show the
            // explanation in this edge case.
            if (firstUpdate) showAutoPausePlaybackExplanation()
        }
    }

    /** Post a message explaining to the user that playback was
     * automatically paused due to there being no active tracks to play. */
    private fun showAutoPausePlaybackExplanation() {
        val stringResId = R.string.player_no_tracks_warning_message
        // A RuntimeException can be thrown here if the Toast is made outside
        // of the UI thread. This can occur, e.g., during a test. Because the
        // message is non-critical, we can ignore it in this case.
        try {
            Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show()
        } catch(e: RuntimeException) {}
    }

    fun setPlaylistVolume(playlistId: Long, volume: Float) =
        playerMap.setPlayerVolume(playlistId, volume)

    /**
     * Automatically pause playback if the parameter [condition] is true and
     * [isPlaying] is true. If this auto-pause succeeds, an unpause lock will be
     * added to the player with a key equal to the parameter [key]. Calling
     * autoPauseIf with the same key with a false condition will remove the
     * corresponding unpause lock, and, if there are no other unpause locks,
     * resume playback. Different causes of the auto-pause event should
     * therefore utilize unique keys (e.g. one for auto-pausing when a call is
     * started, and another for auto-pausing when other media starts playing).
     */
    private fun autoPauseIf(condition: Boolean, key: String) {
        if (condition) {
            if (unpauseLocks.add(key))
                setPlaybackState(STATE_PAUSED, clearUnpauseLocks = false)
        } else if (unpauseLocks.remove(key) && unpauseLocks.isEmpty())
            setPlaybackState(STATE_PLAYING, clearUnpauseLocks = false)
    }


    private val audioFocusRequest =
        AudioFocusRequestCompat.Builder(AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributesCompat.Builder()
                .setContentType(CONTENT_TYPE_UNKNOWN)
                .setUsage(USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { focusChange ->
                hasAudioFocus = focusChange == AUDIOFOCUS_GAIN
            }.build()

    /** Request audio focus, and return whether the request was granted. */
    private fun requestAudioFocus(): Boolean =
        AudioManagerCompat.requestAudioFocus(
            audioManager, audioFocusRequest
        ) == AUDIOFOCUS_REQUEST_GRANTED

    private fun abandonAudioFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
    }


    inner class Binder: android.os.Binder() {
        val isPlaying get() = this@PlayerService.isPlaying
        fun toggleIsPlaying() {
            setPlaybackState(if (isPlaying) STATE_PAUSED
                             else           STATE_PLAYING)
        }

        val stopTime get() = this@PlayerService.stopTime
        fun setStopTimer(stopTimer: Duration) {
            val stopTimeInstant = Instant.now() + stopTimer
            setStopTime(stopTimeInstant.toEpochMilli())
        }
        fun clearStopTimer() = setStopTime(null)

        fun setPlaylistVolume(playlistId: Long, volume: Float) =
            this@PlayerService.setPlaylistVolume(playlistId, volume)
    }

    override fun onBind(intent: Intent): Binder {
        super.onBind(intent)
        return Binder()
    }

    /** PlaybackModule enables PlayerService to have its functionality extended
     * via composition. Implementors of PlaybackModule define their onCreate
     * and onDestroy methods, which will be called during PlayerService's
     * onCreate and onDestroy, respectively. */
    interface PlaybackModule {
        fun onCreate(service: LifecycleService) {}
        fun onDestroy(service: LifecycleService) {}
    }

    companion object {
        private const val autoPauseAudioFocusLossKey = "auto_pause_audio_focus_loss"
        private const val setPlaybackAction = "com.cliffracertech.soundaura.action.setPlayback"
        private const val setTimerAction = "com.cliffracertech.soundaura.action.setTimer"

        private fun setPlaybackIntent(context: Context, state: Int) =
            Intent(context, PlayerService::class.java)
                .setAction(setPlaybackAction)
                .putExtra(setPlaybackAction, state)

        fun playIntent(context: Context) = setPlaybackIntent(context, STATE_PLAYING)
        fun pauseIntent(context: Context) = setPlaybackIntent(context, STATE_PAUSED)
        fun stopIntent(context: Context) = setPlaybackIntent(context, STATE_STOPPED)

        fun setTimerIntent(context: Context, stopTimer: Duration?) =
            Intent(context, PlayerService::class.java)
                .setAction(setTimerAction)
                .putExtra(setTimerAction, stopTimer?.let {
                    Instant.now().plus(it).toEpochMilli()
                })

        fun interface PlaybackChangeListener {
            fun onPlaybackStateChange(newState: Int)
        }
        private val playbackChangeListeners = mutableListOf<PlaybackChangeListener>()

        fun addPlaybackChangeListener(listener: PlaybackChangeListener) {
            playbackChangeListeners.add(listener)
        }

        fun removePlaybackChangeListener(listener: PlaybackChangeListener) {
            playbackChangeListeners.remove(listener)
        }

        var playbackState = STATE_STOPPED
            private set(value) {
                field = value
                for (listener in playbackChangeListeners)
                    listener.onPlaybackStateChange(value)
            }
    }
}