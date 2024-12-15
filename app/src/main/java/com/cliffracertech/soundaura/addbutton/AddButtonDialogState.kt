/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.addbutton

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.dialog.NamingState
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.library.MutablePlaylist
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.Validator
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.model.database.TrackNamesValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A type whose subtypes represent the possible dialogs that can be shown due
 * to add button clicks. The properties [wasNavigatedForwardTo], [titleResId],
 * and [buttons] should be overridden when appropriate.
 *
 * @param onDismissRequest The callback that should be invoked when the dialog
 *     has been requested to be dismissed via a tap outside the dialog's bounds,
 *     a system back button press, or a system back gesture has been performed.
 */
sealed class AddButtonDialogState(
    val onDismissRequest: () -> Unit,
) {
    /** Whether the step is ahead of a previous step. Final steps (i.e. ones
     * that end with a finish instead of a next button) should override this
     * value to return true, while intermediate steps should override it to
     * return true or false depending on whether it was reached by proceeding
     * from a previous step or going back from a successive step. This value
     * is not crucial to functionality, but will allow enter/exit animations
     * between steps to be more precise.  */
    open val wasNavigatedForwardTo = false

    /**
     * A container of state for a visible dialog button.
     *
     * @param textResId A string resource id that points to the string
     *     to use for the button's text.
     * @param isEnabledProvider A callback that returns whether or not
     *     the button should be enabled when invoked.
     * @param onClick The callback to use for when the button is clicked
     */
    class ButtonInfo(
        @StringRes
        val textResId: Int,
        val isEnabledProvider: () -> Boolean = { true },
        val onClick: () -> Unit)

    /** The string resource that points to the string to use for the dialog's title. */
    open val titleResId: Int = 0

    /** A [List] of [ButtonInfo]s that describes the dialog step's buttons. */
    open val buttons: List<ButtonInfo> = emptyList()

    /**
     * A text field to name a new preset is being presented.
     *
     * @param namingState A [ValidatedNamingState] to validate the inputted name
     */
    class NamePreset(
        onDismissRequest: () -> Unit,
        namingState: ValidatedNamingState,
    ): AddButtonDialogState(onDismissRequest),
       NamingState by namingState
    {
        override val titleResId = R.string.create_new_preset_dialog_title
        override val buttons = listOf(
            ButtonInfo(R.string.cancel, onClick = onDismissRequest),
            ButtonInfo(
                textResId = R.string.ok,
                isEnabledProvider = { message?.isError != true },
                onClick = ::finish))
    }

    /**
     * Files are being chosen via the system file picker.
     *
     * @param onFilesSelected The callback that will be invoked when files have been chosen
     */
    class SelectingFiles(
        onDismissRequest: () -> Unit,
        val onFilesSelected: (List<Uri>) -> Unit,
    ): AddButtonDialogState(onDismissRequest)

    /**
     * A question about whether to add multiple files as separate tracks
     * or as files within a single playlist is being presented.
     *
     * @param onAddIndividuallyClick The callback that is invoked when the
     *     dialog's option to add the files as individual tracks is chosen
     * @param onAddAsPlaylistClick The callback that is invoked when dialog's
     *     option to add the files as the contents of a single playlist is chosen
     */
    class AddIndividuallyOrAsPlaylistQuery(
        onDismissRequest: () -> Unit,
        private val onAddIndividuallyClick: () -> Unit,
        private val onAddAsPlaylistClick: () -> Unit,
    ): AddButtonDialogState(onDismissRequest) {
        override val titleResId = R.string.add_local_files_as_playlist_or_tracks_title
        val textResId = R.string.add_local_files_as_playlist_or_tracks_question
        override val buttons = listOf(
            ButtonInfo(R.string.cancel, onClick = onDismissRequest),
            ButtonInfo(R.string.add_local_files_individually_option, onClick = onAddIndividuallyClick),
            ButtonInfo(R.string.add_local_files_as_playlist_option, onClick = onAddAsPlaylistClick))
    }

    /**
     * Text fields for each track are being presented to the user to allow them
     * to name each added track. The property [names] should be used as the
     * list of current names to display in each text field. The property
     * [errorIndices] is a [Set]`<Int>` that contains the indices of invalid names.
     * [message] updates with the most recent [Validator.Message] concerning the
     * input names. Changes within any of the text fields should be connected to
     * [onNameChange].
     *
     * @param onBackClick The callback that is invoked when the dialog's back button is clicked
     * @param validator The [TrackNamesValidator] instance that will be used
     *     to validate the track names
     * @param coroutineScope The [CoroutineScope] that will be used for background work
     * @param onFinish The callback that will be invoked when the dialog's
     *     finish button is clicked and none of the track names are invalid
     */
    class NameTracks(
        onDismissRequest: () -> Unit,
        onBackClick: () -> Unit,
        private val validator: TrackNamesValidator,
        private val coroutineScope: CoroutineScope,
        private val onFinish: (List<String>) -> Unit,
    ): AddButtonDialogState(onDismissRequest) {
        override val wasNavigatedForwardTo = true
        private var confirmJob: Job? = null

        override val titleResId = R.string.add_local_files_as_tracks_dialog_title
        override val buttons = listOf(
            if (validator.values.size == 1)
                ButtonInfo(R.string.cancel, onClick = onDismissRequest)
            else ButtonInfo(R.string.back, onClick = onBackClick),
            ButtonInfo(
                textResId = R.string.finish,
                isEnabledProvider = { message?.isError != true},
                onClick = {
                    confirmJob = confirmJob ?: coroutineScope.launch {
                        val newTrackNames = validator.validate()
                        if (newTrackNames != null)
                            onFinish(newTrackNames)
                        confirmJob = null
                    }
                }))

        val names by validator::values
        val errorIndices by validator::errorIndices
        val message by validator::message
        val onNameChange = validator::setValue
    }

    /**
     * A text field to name a new playlist is being presented.
     *
     * @param onBackClick The callback that will be invoked when the dialog's back button is clicked
     * @param namingState A [ValidatedNamingState] to validate the inputted name
     * @param wasNavigatedForwardTo Whether the step was reached by proceeding
     *     forward from a previous step (as opposed to going backwards from a
     *     following step)
     */
    class NamePlaylist(
        onDismissRequest: () -> Unit,
        onBackClick: () -> Unit,
        namingState: NamingState,
        override val wasNavigatedForwardTo: Boolean,
    ): AddButtonDialogState(onDismissRequest),
       NamingState by namingState
   {
       override val titleResId = R.string.add_local_files_as_playlist_dialog_title
       override val buttons = listOf(
           ButtonInfo(R.string.back, onClick = onBackClick),
           ButtonInfo(
               textResId = R.string.next,
               isEnabledProvider = { message?.isError != true },
               onClick = ::finish))
   }

    /**
     * A shuffle toggle switch and a reorder track widget are being presented
     * to allow these playlist settings to be changed. The on/off state of the
     * playlist's shuffle is provided via [shuffleEnabled]. Clicks on the shuffle
     * switch should be connected to [onShuffleSwitchClick]. The provided [mutablePlaylist]
     * can be used in a [com.cliffracertech.soundaura.library.PlaylistOptionsView].
     *
     * @param onBackClick The callback that will be invoked when the dialog's back button is clicked
     * @param trackUris The [List] of [Uri]s that represent the new playlist's tracks
     * @param onFinish The callback that will be invoked when the dialog's
     *     finish button is clicked. The current shuffle and track ordering
     *     as passed as arguments.
     */
    class PlaylistOptions(
        onDismissRequest: () -> Unit,
        onBackClick: () -> Unit,
        trackUris: List<Uri>,
        private val onFinish: (shuffleEnabled: Boolean, newTrackList: List<Track>) -> Unit,
    ): AddButtonDialogState(onDismissRequest) {
        var shuffleEnabled by mutableStateOf(false)
            private set
        val onShuffleSwitchClick = { shuffleEnabled = !shuffleEnabled }
        val mutablePlaylist = MutablePlaylist(trackUris.map(::Track))

        override val wasNavigatedForwardTo = true
        override val titleResId = R.string.configure_playlist_dialog_title
        override val buttons = listOf(
            ButtonInfo(R.string.back, onClick = onBackClick),
            ButtonInfo(R.string.finish, onClick = {
                onFinish(shuffleEnabled, mutablePlaylist.applyChanges())
            }))
    }

    /** An explanation for why the storage permission is needed is being presented.
     * The [text] [StringResource] property should be resolved and display to the user. */
    class RequestStoragePermissionExplanation(
        onDismissRequest: () -> Unit,
        permissionsUsed: Int,
        permissionsAllowed: Int,
        addingPlaylist: Boolean,
        onOkClick: () -> Unit
    ): AddButtonDialogState(onDismissRequest) {
        override val wasNavigatedForwardTo = true
        override val titleResId = R.string.add_local_files_request_storage_permission_title
        override val buttons = listOf(ButtonInfo(R.string.ok, onClick = onOkClick))

        val text = StringResource(
            string = null,
            stringResId = if (addingPlaylist)
                R.string.add_local_files_request_storage_permission_explanation_for_playlist
            else R.string.add_local_files_request_storage_permission_explanation_for_tracks,
            /*arg1 =*/permissionsUsed,
            /*arg2 =*/permissionsAllowed)
    }

    /** The storage permission request is being presented. */
    class RequestStoragePermission(
        onDismissRequest: () -> Unit,
        val onResult: (Boolean) -> Unit,
    ): AddButtonDialogState(onDismissRequest)
}