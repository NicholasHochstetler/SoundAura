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


    @Test fun initial_state_has_both_buttons_collapsed_and_doesnt_show_app_settings() {
        assertThat(instance.addButtonState.isCollapsed).isTrue()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun toggle_add_button_state_expands_collapsed_add_button() {
        instance.toggleAddButtonExpandedState()
        assertThat(instance.addButtonState.isExpanded).isTrue()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun toggle_add_button_state_collapses_expanded_add_button() {
        instance.toggleAddButtonExpandedState()
        instance.toggleAddButtonExpandedState()
        assertThat(instance.addButtonState.isCollapsed).isTrue()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun collapse_add_button() {
        instance.toggleAddButtonExpandedState()
        instance.collapseAddButton()
        assertThat(instance.addButtonState.isCollapsed).isTrue()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun expanding_add_button_also_collapses_preset_selector() {
        instance.showPresetSelector()
        instance.toggleAddButtonExpandedState()
        assertThat(instance.addButtonState.isExpanded).isTrue()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun show_preset_selector() {
        instance.showPresetSelector()
        assertThat(instance.addButtonState.isCollapsed).isTrue()
        assertThat(instance.mediaControllerState.isExpanded).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun collapse_preset_selector() {
        instance.showPresetSelector()
        instance.hidePresetSelector()
        assertThat(instance.addButtonState.isCollapsed).isTrue()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun showing_preset_selector_also_collapses_add_button() {
        instance.toggleAddButtonExpandedState()
        instance.showPresetSelector()
        assertThat(instance.addButtonState.isCollapsed).isTrue()
        assertThat(instance.mediaControllerState.isExpanded).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun show_settings_shows_settings_and_hides_buttons() {
        instance.showAppSettings()
        assertThat(instance.showingAppSettings).isTrue()
        assertThat(instance.addButtonState.isHidden).isTrue()
        assertThat(instance.mediaControllerState.isHidden).isTrue()
    }

    @Test fun hide_settings_hides_settings_and_shows_buttons() {
        instance.showAppSettings()
        instance.hideAppSettings()
        assertThat(instance.showingAppSettings).isFalse()
        assertThat(instance.addButtonState.isCollapsed).isTrue()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
    }

    @Test fun add_button_methods_no_op_when_showing_settings() {
        instance.showAppSettings()
        instance.toggleAddButtonExpandedState()
        assertThat(instance.addButtonState.isHidden).isTrue()

        instance.collapseAddButton()
        assertThat(instance.addButtonState.isHidden).isTrue()
    }

    @Test fun media_controller_methods_no_op_when_showing_settings() {
        instance.showAppSettings()
        instance.showPresetSelector()
        assertThat(instance.mediaControllerState.isHidden).isTrue()

        instance.hidePresetSelector()
        assertThat(instance.mediaControllerState.isHidden).isTrue()
    }

    @Test fun back_button_does_nothing_when_not_showing_settings_and_w_collapsed_buttons() {
        assertThat(instance.onBackButtonClick()).isFalse()
        assertThat(instance.addButtonState.isCollapsed).isTrue()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun back_button_hides_app_settings() {
        instance.showAppSettings()
        assertThat(instance.onBackButtonClick()).isTrue()
        assertThat(instance.addButtonState.isCollapsed).isTrue()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun back_button_collapses_add_button() {
        instance.toggleAddButtonExpandedState()
        assertThat(instance.onBackButtonClick()).isTrue()
        assertThat(instance.addButtonState.isCollapsed).isTrue()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun back_button_collapses_preset_selector() {
        instance.showPresetSelector()
        assertThat(instance.onBackButtonClick()).isTrue()
        assertThat(instance.addButtonState.isCollapsed).isTrue()
        assertThat(instance.mediaControllerState.isCollapsed).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
    }
}