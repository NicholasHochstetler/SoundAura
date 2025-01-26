/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import com.cliffracertech.soundaura.model.NavigationState
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class NavigationStateTests {
    private lateinit var instance: NavigationState

    @Before fun init() { instance = NavigationState() }


    @Test fun initial_state() {
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun show_preset_selector() {
        instance.showPresetSelector()
        assertThat(instance.mediaControllerState.isExpanded).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun collapse_preset_selector() {
        instance.showPresetSelector()
        instance.hidePresetSelector()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun showAppSettings_shows_settings_and_hides_media_controller() {
        instance.showAppSettings()
        assertThat(instance.showingAppSettings).isTrue()
        assertThat(instance.mediaControllerState.isHidden).isTrue()
    }

    @Test fun hideAppSettings_hides_settings_and_shows_media_controller() {
        instance.showAppSettings()
        instance.hideAppSettings()
        assertThat(instance.showingAppSettings).isFalse()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
    }

    @Test fun media_controller_methods_no_op_when_showing_settings() {
        instance.showAppSettings()
        instance.showPresetSelector()
        assertThat(instance.mediaControllerState.isHidden).isTrue()

        instance.hidePresetSelector()
        assertThat(instance.mediaControllerState.isHidden).isTrue()
    }

    @Test fun back_button_does_nothing_in_default_nav_state() {
        assertThat(instance.onBackButtonClick()).isFalse()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun back_button_hides_app_settings() {
        instance.showAppSettings()
        assertThat(instance.onBackButtonClick()).isTrue()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun back_button_collapses_preset_selector() {
        instance.showPresetSelector()
        assertThat(instance.onBackButtonClick()).isTrue()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }
}