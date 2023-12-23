/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.collectAsState
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.ModifyLibraryUseCase
import com.cliffracertech.soundaura.model.PlaybackState
import com.cliffracertech.soundaura.model.PlayerServicePlaybackState
import com.cliffracertech.soundaura.model.ReadLibraryUseCase
import com.cliffracertech.soundaura.model.SearchQueryState
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.ui.tweenDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A state holder whose subtypes, [Loading], [Empty], and [Content], represent
 * the possible states for an asynchronously loaded list of [Playlist]s. */
sealed class LibraryViewState {
    /** The list of items is still being loaded */
    data object Loading: LibraryViewState()

    /** The list of items is empty. [message] can be resolved to
     * obtain a [String] describing the empty content. This can be
     * used to, e.g., explain why the list of items might be empty. */
    class Empty(val message: StringResource): LibraryViewState()

    /** The list of items has been loaded, and can be obtained through
     * the property [items]. The value of the property [itemCallback]
     * can be used as an item callback in, e.g., a [PlaylistView]. */
    class Content(
        private val getItems: () -> ImmutableList<Playlist>?,
        val itemCallback: PlaylistViewCallback,
    ): LibraryViewState() {
        val items get() = getItems()
    }
}

/**
 * A [LazyColumn] to display all of the provided [Playlist]s with instances of [PlaylistView].
 *
 * @param modifier The [Modifier] that will be used for the TrackList
 * @param lazyListState The [LazyListState] used for the library's scrolling state
 * @param contentPadding The [PaddingValues] instance that will be used as
 *     the content padding for the list of items
 * @param shownDialog A [PlaylistDialog] instance that describes the [Playlist]
 *     related dialog that should be shown, or null if no dialog needs to be shown
 * @param libraryViewState A [LibraryViewState] instance that describes the UI
 *     state of the LibraryView
 */
@Composable fun LibraryView(
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues,
    shownDialog: PlaylistDialog?,
    libraryViewState: LibraryViewState,
) {
    PlaylistDialogShower(shownDialog)

    Crossfade(
        targetState = libraryViewState,
        modifier = modifier,
        animationSpec = tween(tweenDuration),
        label = "LibraryView loading/empty/content crossfade",
    ) { viewState -> when(viewState) {
        is LibraryViewState.Loading ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(Modifier.size(50.dp))
            }
        is LibraryViewState.Empty -> {
            val context = LocalContext.current
            val text = remember(viewState) { viewState.message.resolve(context) }
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(text = text,
                     modifier = Modifier.width(300.dp),
                     textAlign = TextAlign.Justify)
            }
        } is LibraryViewState.Content -> {
            val items = (viewState.items ?: emptyList()) as ImmutableList<Playlist>
            LazyColumn(
                Modifier, lazyListState, contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = Playlist::name::get) { playlist ->
                    PlaylistView(playlist, viewState.itemCallback,
                        Modifier.animateItemPlacement())
                }
            }
        }
    }}
}

/**
 * A [ViewModel] to provide state and callbacks for an instance of LibraryView.
 *
 * The most recent list of all playlists is provided via the property
 * [playlists]. The [PlaylistViewCallback] that should be used for item
 * interactions is provided via the property [itemCallback]. The state
 * of any dialogs that should be shown are provided via the property
 * [shownDialog].
 */
@HiltViewModel class LibraryViewModel(
    readLibrary: ReadLibraryUseCase,
    private val modifyLibrary: ModifyLibraryUseCase,
    private val searchQueryState: SearchQueryState,
    private val messageHandler: MessageHandler,
    private val playbackState: PlaybackState,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    @Inject constructor(
        readLibraryUseCase: ReadLibraryUseCase,
        modifyLibraryUseCase: ModifyLibraryUseCase,
        searchQueryState: SearchQueryState,
        messageHandler: MessageHandler,
        playbackState: PlayerServicePlaybackState,
    ) : this(readLibraryUseCase, modifyLibraryUseCase,
             searchQueryState, messageHandler, playbackState, null)

    private val scope = coroutineScope ?: viewModelScope

    var shownDialog by mutableStateOf<PlaylistDialog?>(null)
    private fun dismissDialog() { shownDialog = null }

    private val itemCallback = object : PlaylistViewCallback {
        override fun onAddRemoveButtonClick(playlist: Playlist) {
            scope.launch { modifyLibrary.togglePlaylistIsActive(playlist.id) }
        }
        override fun onVolumeChange(playlist: Playlist, volume: Float) {
            playbackState.setPlaylistVolume(playlist.id, volume)
        }
        override fun onVolumeChangeFinished(playlist: Playlist, volume: Float) {
            scope.launch { modifyLibrary.setPlaylistVolume(playlist.id, volume) }
        }
        override fun onRenameClick(playlist: Playlist) {
            shownDialog = PlaylistDialog.Rename(
                target = playlist,
                namingState = modifyLibrary.renameState(
                    playlist.id, playlist.name, scope, ::dismissDialog),
                onDismissRequest = ::dismissDialog)
        }
        override fun onExtraOptionsClick(playlist: Playlist) {
            scope.launch {
                val existingTracks = readLibrary.getPlaylistTracks(playlist.id)
                val shuffleEnabled = readLibrary.getPlaylistShuffle(playlist.id)
                assert((existingTracks.size == 1) == playlist.isSingleTrack)
                if (playlist.isSingleTrack)
                    showFileChooser(playlist, existingTracks)
                else showPlaylistOptions(playlist, existingTracks, shuffleEnabled)
            }
        }
        override fun onRemoveClick(playlist: Playlist) {
            if (playlist.hasError)
                scope.launch { modifyLibrary.removePlaylist(playlist.id) }
            else shownDialog = PlaylistDialog.Remove(
                target = playlist,
                onDismissRequest = ::dismissDialog,
                onConfirmClick = {
                    dismissDialog()
                    scope.launch { modifyLibrary.removePlaylist(playlist.id) }
                })
        }
    }

    private val playlists by readLibrary.playlistsFlow.collectAsState(null, scope)
    private val noSearchResultsState = LibraryViewState.Empty(StringResource(R.string.no_search_results_message))
    private val emptyLibraryState = LibraryViewState.Empty(StringResource(R.string.empty_library_message))
    private val contentState = LibraryViewState.Content(::playlists, itemCallback)

    val viewState get() = when {
        playlists == null ->
            LibraryViewState.Loading
        playlists?.isEmpty() == true -> {
            if (searchQueryState.isActive)
                noSearchResultsState
            else emptyLibraryState
        } else -> contentState
    }

    private fun showFileChooser(
        target: Playlist,
        existingTracks: List<Track>,
        shuffleEnabled: Boolean = false,
    ) {
        shownDialog = PlaylistDialog.FileChooser(
            target, messageHandler, existingTracks,
            onDismissRequest = {
                // If the file chooser was arrived at by selecting the 'create playlist'
                // option for a single track playlist, we want the back button/gesture to
                // completely dismiss the dialog. If the file chooser was arrived at by
                // selecting the 'add more files' button in the playlist options dialog
                // of an existing multi-track playlist, then we want the back button/
                // gesture to go back to the playlist options dialog for that playlist.
                if (existingTracks.size == 1)
                    dismissDialog()
                else showPlaylistOptions(target, existingTracks, shuffleEnabled)
            }, onChosenFilesValidated = { validatedFiles ->
                val newTrackList = existingTracks + validatedFiles.map(::Track)
                showPlaylistOptions(target, newTrackList, shuffleEnabled)
            })
    }

    private fun showPlaylistOptions(
        target: Playlist,
        existingTracks: List<Track>,
        shuffleEnabled: Boolean,
    ) {
        shownDialog = PlaylistDialog.PlaylistOptions(
            target, existingTracks, shuffleEnabled, ::dismissDialog,
            onAddFilesClick = {
                showFileChooser(target, existingTracks, shuffleEnabled)
            }, onConfirm = { newShuffle, newTrackList ->
                dismissDialog()
                scope.launch {
                    modifyLibrary.setPlaylistShuffleAndTracks(
                        target.id, newShuffle, newTrackList)
                }
            })
    }
}

/**
 * Show a [LibraryView] that uses an instance of [LibraryViewModel] for its state.
 *
 * @param modifier The [Modifier] that will be used for the TrackList.
 * @param padding A [PaddingValues] instance whose values will be
 *     as the contentPadding for the TrackList
*  @param state The [LazyListState] used for the TrackList. state
 *     defaults to an instance of LazyListState returned from a
 *     [rememberLazyListState] call, but can be overridden here in
 *     case, e.g., the scrolling position needs to be remembered
 *     even when the SoundAuraTrackList leaves the composition.
 */
@Composable fun SoundAuraLibraryView(
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    state: LazyListState = rememberLazyListState(),
) = Surface(modifier, color = MaterialTheme.colors.background) {
    val viewModel: LibraryViewModel = viewModel()
    LibraryView(
        modifier = modifier,
        lazyListState = state,
        contentPadding = padding,
        shownDialog = viewModel.shownDialog,
        libraryViewState = viewModel.viewState)
}