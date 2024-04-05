/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cliffracertech.soundaura.addbutton.AddButton
import com.cliffracertech.soundaura.appbar.SoundAuraAppBar
import com.cliffracertech.soundaura.library.SoundAuraLibraryView
import com.cliffracertech.soundaura.mediacontroller.MediaControllerSizes
import com.cliffracertech.soundaura.mediacontroller.SoundAuraMediaController
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.PlayerServicePlaybackState
import com.cliffracertech.soundaura.settings.AppSettings
import com.cliffracertech.soundaura.settings.AppTheme
import com.cliffracertech.soundaura.settings.PrefKeys
import com.cliffracertech.soundaura.ui.SlideAnimatedContent
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import com.cliffracertech.soundaura.ui.tweenDuration
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel class MainActivityViewModel(
    messageHandler: MessageHandler,
    private val dataStore: DataStore<Preferences>,
    private val navigationState: NavigationState,
    private val playbackState: PlayerServicePlaybackState,
    coroutineScope: CoroutineScope?
) : ViewModel() {

    @Inject constructor(
        messageHandler: MessageHandler,
        dataStore: DataStore<Preferences>,
        navigationState: NavigationState,
        playbackState: PlayerServicePlaybackState
    ) : this(messageHandler, dataStore, navigationState, playbackState, null)

    private val scope = coroutineScope ?: viewModelScope

    val messages = messageHandler.messages
    val showingAppSettings get() = navigationState.showingAppSettings
    val showingPresetSelector get() = navigationState.showingPresetSelector

    private val appThemeKey = intPreferencesKey(PrefKeys.appTheme)
    // The thread must be blocked when reading the first value
    // of the app theme from the DataStore or else the screen
    // can flicker between light and dark themes on startup.
    val appTheme by runBlocking {
        dataStore.awaitEnumPreferenceState<AppTheme>(appThemeKey, scope)
    }

    private val lastLaunchedVersionCodeKey = intPreferencesKey(PrefKeys.lastLaunchedVersionCode)
    val lastLaunchedVersionCode by dataStore.preferenceState(
        key = lastLaunchedVersionCodeKey,
        initialValue = 0,
        defaultValue = 9, // version code 9 was the last version code before
        scope = scope)    // the lastLaunchedVersionCode was introduced
    fun onNewVersionDialogShown() {
        dataStore.edit(lastLaunchedVersionCodeKey, BuildConfig.VERSION_CODE, scope)
    }

    fun onBackButtonClick() = with(navigationState) {
        if (willConsumeBackButtonClick) {
            onBackButtonClick(); true
        } else false
    }

    fun onActivityStart(context: Context) = playbackState.onActivityStart(context)

    fun onActivityStop() = playbackState.onActivityStop()

    fun onKeyDown(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
            playbackState.toggleIsPlaying()
            true
        } KeyEvent.KEYCODE_MEDIA_PLAY -> {
            if (playbackState.isPlaying) {
                playbackState.toggleIsPlaying()
                true
            } else false
        } KeyEvent.KEYCODE_MEDIA_PAUSE -> {
            if (playbackState.isPlaying) {
                playbackState.toggleIsPlaying()
                true
            } else false
        } KeyEvent.KEYCODE_MEDIA_STOP -> {
            if (playbackState.isPlaying) {
                playbackState.toggleIsPlaying()
                true
            } else false
        } else -> false
    }
}

val LocalWindowSizeClass = compositionLocalOf {
    WindowSizeClass.calculateFromSize(DpSize(0.dp, 0.dp))
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        viewModel.onActivityStart(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.onActivityStop()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        if (!viewModel.onBackButtonClick())
            super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentWithTheme {
            val windowWidthSizeClass = LocalWindowSizeClass.current.widthSizeClass
            val widthIsConstrained = windowWidthSizeClass == WindowWidthSizeClass.Compact
            val insets = LocalWindowInsets.current

            NewVersionDialogShower(
                lastLaunchedVersionCode = viewModel.lastLaunchedVersionCode,
                onDialogDismissed = viewModel::onNewVersionDialogShown)

            val scaffoldState = rememberScaffoldState()
            MessageDisplayer(scaffoldState.snackbarHostState)

            val padding = rememberInsetsPaddingValues(
                insets = insets.systemBars,
                additionalStart = 8.dp,
                // The 56dp is added here for the action bar's height.
                additionalTop = 8.dp + 56.dp,
                additionalEnd = 8.dp,
                additionalBottom = 8.dp)

            com.google.accompanist.insets.ui.Scaffold(
                scaffoldState = scaffoldState,
                topBar = { SoundAuraAppBar() },
                floatingActionButton = {
                    // The floating action buttons are added in the content
                    // section instead to have more control over their placement.
                    // A spacer is added here so that snack bars still appear
                    // above the floating action buttons. 48dp was arrived at
                    // by starting from the FAB size of 56dp and adjusting
                    // downward by 8dp due to the inherent snack bar padding.
                    if (widthIsConstrained)
                        Spacer(Modifier.height(48.dp).fillMaxWidth())
                }, contentPadding = padding,
                content = {
                    MainContent(widthIsConstrained, it)
                })
        }
    }

    /** Read the app's theme from a SettingsViewModel instance
     * and compose the provided content using the theme. */
    private fun setContentWithTheme(
        parent: CompositionContext? = null,
        content: @Composable () -> Unit
    ) = setContent(parent) {
        val themePreference = viewModel.appTheme
        val systemInDarkTheme = isSystemInDarkTheme()
        val useDarkTheme by remember(themePreference, systemInDarkTheme) {
            derivedStateOf {
                themePreference == AppTheme.Dark ||
                (themePreference == AppTheme.UseSystem && systemInDarkTheme)
            }
        }

        val uiController = rememberSystemUiController()
        LaunchedEffect(useDarkTheme) {
            // For some reason the status bar icons get reset
            // to a light color when the theme is changed, so
            // this effect needs to run after every theme change.
            uiController.setStatusBarColor(Color.Transparent, true)
            uiController.setNavigationBarColor(Color.Transparent, !useDarkTheme)
        }
        SoundAuraTheme(useDarkTheme) {
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                ProvideWindowInsets { content() }
            }
        }
    }

    /** Compose a message handler that will read messages emitted from a
     * MainActivityViewModel's messages member and display them using snack bars.*/
    @Composable private fun MessageDisplayer(
        snackbarHostState: SnackbarHostState
    ) = LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            message.showAsSnackbar(this@MainActivity, snackbarHostState)
        }
    }

    private fun mainContentAdditionalEndMargin(widthIsConstrained: Boolean) =
        if (widthIsConstrained) 0.dp
        else MediaControllerSizes.defaultStopTimerWidthDp.dp + 8.dp

    @SuppressLint("UnusedBoxWithConstraintsScope") // the scope is used in MediaController
    @Composable private fun MainContent(
        widthIsConstrained: Boolean,
        padding: PaddingValues,
    ) = BoxWithConstraints(Modifier.fillMaxSize()) {
        val showingAppSettings = viewModel.showingAppSettings
        val ld = LocalLayoutDirection.current
        // The track list state is remembered here so that the
        // scrolling position will not be lost if the user
        // navigates to the app settings screen and back.
        val trackListState = rememberLazyListState()

        SlideAnimatedContent(
            targetState = showingAppSettings,
            leftToRight = !showingAppSettings,
            modifier = Modifier.fillMaxSize()
        ) { showingAppSettingsScreen ->
            if (showingAppSettingsScreen)
                AppSettings(padding)
            else SoundAuraLibraryView(
                // The track list's padding must be adjusted
                // depending on the placement of the FABs.
                padding = remember(padding, widthIsConstrained) {
                    PaddingValues(padding, ld,
                        additionalEnd = mainContentAdditionalEndMargin(widthIsConstrained),
                        additionalBottom = if (widthIsConstrained) 64.dp else 0.dp)
                }, state = trackListState)
        }

        MediaController(padding, alignToEnd = !widthIsConstrained)

        AddTrackButton(
            visible = !showingAppSettings,
            widthIsConstrained = widthIsConstrained,
            modifier = Modifier.padding(padding))
    }

    @Composable private fun BoxWithConstraintsScope.MediaController(
        padding: PaddingValues,
        alignToEnd: Boolean
    ) {
        val ld = LocalLayoutDirection.current
        val contentAreaSize = remember(padding) {
            val startPadding = padding.calculateStartPadding(ld)
            val endPadding = padding.calculateEndPadding(ld)
            val topPadding = padding.calculateTopPadding()
            val bottomPadding = padding.calculateBottomPadding()
            DpSize(maxWidth - startPadding - endPadding,
                   maxHeight - topPadding - bottomPadding)
        }

        val mediaControllerSizes = remember(padding, alignToEnd) {
            // The goal is to have the media controller have such a length that
            // the play/pause icon is centered in the content area's width in
            // portrait mode, or centered in the content area's height in
            // landscape mode. This preferred length is found by adding half of
            // the play/pause button's size and the stop timer display's length
            // (in case it needs to be displayed) to half of the length of the
            // content area. The min value between this preferred length and the
            // full content area length minus 64dp (i.e. the add button's 56dp
            // size plus an 8dp margin) is then used to ensure that for small
            // screen sizes the media controller can't overlap the add button.
            val playButtonLength = MediaControllerSizes.defaultPlayButtonLengthDp.dp
            val dividerThickness = MediaControllerSizes.dividerThicknessDp.dp
            val stopTimerLength =
                if (alignToEnd) MediaControllerSizes.defaultStopTimerHeightDp.dp
                else            MediaControllerSizes.defaultStopTimerWidthDp.dp
            val extraLength = playButtonLength / 2f + stopTimerLength
            val length = if (alignToEnd) contentAreaSize.height / 2f + extraLength
                         else            contentAreaSize.width / 2f + extraLength
            val maxLength = if (alignToEnd) contentAreaSize.height - 64.dp
                             else            contentAreaSize.width - 64.dp
            val activePresetLength = minOf(length, maxLength) - playButtonLength -
                                     dividerThickness - stopTimerLength
            MediaControllerSizes(
                orientation = if (alignToEnd) Orientation.Vertical
                              else            Orientation.Horizontal,
                activePresetLength = activePresetLength,
                presetSelectorSize = DpSize(
                    width = contentAreaSize.width * if (alignToEnd) 0.6f
                                                    else            1.0f,
                    height = if (!alignToEnd) 350.dp
                             else contentAreaSize.height))
        }

        val alignment = if (alignToEnd) Alignment.TopEnd as BiasAlignment
                        else            Alignment.BottomStart as BiasAlignment
        SoundAuraMediaController(
            Modifier, mediaControllerSizes, alignment, padding)
    }

    /** Compose an add button at the bottom end edge of the screen that is
     * conditionally visible depending on the value of [visible]. */
    @Composable private fun BoxScope.AddTrackButton(
        visible: Boolean,
        widthIsConstrained: Boolean,
        modifier: Modifier = Modifier,
    ) {
        val showingPresetSelector = viewModel.showingPresetSelector
        // Different stiffnesses are used for the x and y offsets so that the
        // add button moves in a swooping movement instead of a linear one
        val addButtonXDpOffset by animateDpAsState(
            targetValue = when {
                showingPresetSelector -> (-16).dp
                widthIsConstrained -> 0.dp
                else -> {
                    // We want the x offset to be half of the difference between the
                    // total end margin and the button size, so that the button appears
                    // centered within the end margin
                    val margin = mainContentAdditionalEndMargin(widthIsConstrained)
                    val buttonSize = 56.dp
                    (margin - 8.dp - buttonSize) / -2f
                }
            }, label = "Add button x offset animation",
            animationSpec = tween(tweenDuration * 5 / 4, 0, LinearOutSlowInEasing))

        val addButtonYDpOffset by animateDpAsState(
            targetValue = if (!showingPresetSelector) 0.dp
                          else (-16).dp,
            label = "Add button y offset animation",
            animationSpec = tween(tweenDuration, 0, LinearOutSlowInEasing))

        val enterSpec = tween<Float>(
            durationMillis = tweenDuration,
            easing = LinearOutSlowInEasing)
        val exitSpec = tween<Float>(
            durationMillis = tweenDuration,
            delayMillis = tweenDuration / 3,
            easing = LinearOutSlowInEasing)
        AnimatedVisibility( // add track button
            visible = visible,
            modifier = modifier
                .align(Alignment.BottomEnd)
                .offset { IntOffset(
                    addButtonXDpOffset.roundToPx(),
                    addButtonYDpOffset.roundToPx()
                )},
            enter = fadeIn(enterSpec) + scaleIn(enterSpec, initialScale = 0.8f),
            exit = fadeOut(exitSpec) + scaleOut(exitSpec, targetScale = 0.8f),
        ) {
            AddButton(MaterialTheme.colors.secondaryVariant)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?) =
        if (viewModel.onKeyDown(keyCode)) true
        else super.onKeyDown(keyCode, event)
}