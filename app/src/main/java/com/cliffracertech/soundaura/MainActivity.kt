/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpSize
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
    val showingPresetSelector get() = navigationState.mediaControllerState.isExpanded

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
    fun onNewVersionDialogDismiss() {
        dataStore.edit(lastLaunchedVersionCodeKey, BuildConfig.VERSION_CODE, scope)
    }

    fun onBackButtonClick() = navigationState.onBackButtonClick()

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
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val windowWidthSizeClass = LocalWindowSizeClass.current.widthSizeClass
                val widthIsConstrained = windowWidthSizeClass == WindowWidthSizeClass.Compact
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.messages.collect { message ->
                        message.showAsSnackbar(this@MainActivity, snackbarHostState)
                    }
                }

                NewVersionDialogShower(
                    lastLaunchedVersionCode = viewModel.lastLaunchedVersionCode,
                    onDialogDismissed = viewModel::onNewVersionDialogDismiss)

                Column {
                    SoundAuraAppBar()
                    val mainContentPadding = rememberWindowInsetsPaddingValues(
                        insets = WindowInsets.navigationBars,
                        additionalTop = 8.dp,
                        additionalStart = 8.dp,
                        additionalBottom = if (widthIsConstrained) 72.dp else 8.dp,
                        additionalEnd = mainContentAdditionalEndMargin(widthIsConstrained))
                    MainContent(mainContentPadding)
                }

                val floatingButtonPadding = rememberWindowInsetsPaddingValues(
                    insets = WindowInsets.systemBars,
                    additionalStart = 8.dp,
                    additionalEnd = 8.dp,
                    additionalBottom = 8.dp,
                    additionalTop = 8.dp + 56.dp)

                SoundAuraMediaController(
                    padding = floatingButtonPadding,
                    alignment = if (widthIsConstrained)
                                    Alignment.BottomStart as BiasAlignment
                                else Alignment.TopEnd as BiasAlignment)

                AddTrackButton(
                    widthIsConstrained = widthIsConstrained,
                    modifier = Modifier.padding(floatingButtonPadding))

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(floatingButtonPadding))
            }
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
                content()
            }
        }
    }

    private fun mainContentAdditionalEndMargin(widthIsConstrained: Boolean) =
        if (widthIsConstrained) 8.dp
        else 8.dp + MediaControllerSizes.defaultStopTimerWidthDp.dp + 8.dp

    @Composable private fun MainContent(padding: PaddingValues) {
        // The track list state is remembered here so that the
        // scrolling position will not be lost if the user
        // navigates to the app settings screen and back.
        val trackListState = rememberLazyListState()

        SlideAnimatedContent(
            targetState = viewModel.showingAppSettings,
            leftToRight = !viewModel.showingAppSettings,
            modifier = Modifier.fillMaxSize()
        ) { showingAppSettingsScreen ->
            if (showingAppSettingsScreen)
                AppSettings(padding)
            else SoundAuraLibraryView(
                padding = padding,
                state = trackListState)
        }
    }

    @Composable private fun AddTrackButton(
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
                    val mediaControllerSize = MediaControllerSizes.defaultStopTimerWidthDp.dp
                    val buttonSize = 56.dp
                    (mediaControllerSize - buttonSize) / -2f
                }
            }, label = "Add button x offset animation",
            animationSpec = tween(tweenDuration * 5 / 4, 0, LinearOutSlowInEasing))

        val addButtonYDpOffset by animateDpAsState(
            targetValue = if (showingPresetSelector) (-16).dp else 0.dp,
            label = "Add button y offset animation",
            animationSpec = tween(tweenDuration, 0, LinearOutSlowInEasing))

        AddButton(
            backgroundColor = MaterialTheme.colors.secondaryVariant,
            modifier = modifier.graphicsLayer {
                translationX = addButtonXDpOffset.toPx()
                translationY = addButtonYDpOffset.toPx()
            })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?) =
        if (viewModel.onKeyDown(keyCode)) true
        else super.onKeyDown(keyCode, event)
}