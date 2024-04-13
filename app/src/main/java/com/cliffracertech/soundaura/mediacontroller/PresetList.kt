/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.mediacontroller

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.model.database.Preset
import com.cliffracertech.soundaura.rememberMutableStateOf
import com.cliffracertech.soundaura.ui.MarqueeText
import com.cliffracertech.soundaura.ui.minTouchTargetSize
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * A view to display the name and modified status of a [Preset] instance. An
 * options button is also provided that provides options to rename, overwrite,
 * or delete the preset.
 *
 * @param modifier The [Modifier] to use for the view
 * @param presetName The name of the [Preset] instance
 * @param isModified Whether or not the [Preset] has unsaved changes.
 *     This will be indicated by an asterisk next to the [Preset]'s name.
 * @param onRenameClick The callback that will be invoked when the user
 *     clicks on the options menu's rename option
 * @param onOverwriteClick The callback that will be invoked when the
 *     user clicks on the options menu's overwrite option
 * @param onDeleteClick The callback that will be invoked when the user
 *     clicks on the option menu's delete option
 * @param onClick The callback that will be invoked when the user clicks the view
 */
@Composable fun PresetView(
    modifier: Modifier = Modifier,
    presetName: String,
    isModified: Boolean,
    onRenameClick: (String) -> Unit,
    onOverwriteClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onClick: (String) -> Unit
) = Row(
    modifier = modifier
        .minTouchTargetSize()
        .clickable(
            onClickLabel = stringResource(R.string.preset_click_label, presetName),
            role = Role.Button,
            onClick = { onClick(presetName) },
        ).padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically
) {

    Row(Modifier.weight(1f).padding(start = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarqueeText(presetName, Modifier.weight(1f, false))
        if (isModified) Text(" *")
    }
    var showingOptionsMenu by rememberSaveable { mutableStateOf(false) }

    IconButton({ showingOptionsMenu = true }) {
        Icon(imageVector = Icons.Default.MoreVert,
             contentDescription = stringResource(
                 R.string.item_options_button_description, presetName))

        DropdownMenu(
            expanded = showingOptionsMenu,
            onDismissRequest = { showingOptionsMenu = false }
        ) {
            DropdownMenuItem(onClick = {
                onRenameClick(presetName)
                showingOptionsMenu = false
            }) { Text(stringResource(R.string.rename)) }

            DropdownMenuItem(onClick = {
                onOverwriteClick(presetName)
                showingOptionsMenu = false
            }) { Text(stringResource(R.string.overwrite)) }

            DropdownMenuItem(onClick = {
                onDeleteClick(presetName)
                showingOptionsMenu = false
            }) { Text(stringResource(R.string.delete)) }
        }
    }
}

/** A collection of state and callbacks for a list of [Preset]s that provides
 * a popup options menu for each [Preset]. The current list of [Preset]s can
 * be accessed through the property [list]. Clicks on a given [Preset] should
 * call [onClick] with the clicked [Preset]'s [Preset.name] property. Likewise,
 * clicks on the rename, overwrite, and delete options in a [Preset]'s popup
 * options menu should utilize the properties [onRenameClick], [onOverwriteClick],
 * and [onDeleteClick], respectively. */
class PresetListState(
    private val listProvider: () -> ImmutableList<Preset>?,
    val onRenameClick: (String) -> Unit,
    val onOverwriteClick: (String) -> Unit,
    val onDeleteClick: (String) -> Unit,
    val onClick: (String) -> Unit,
) {
    val list get() = listProvider()
}

/**
 * Display a list of [Preset]s, with an options menu for each [Preset]
 * to allow for renaming, overwriting, and deletion. Optionally, a single
 * [Preset] identified as the active one will have the [selectionBrush]
 * applied to it to indicate this status.
 *
 * @param modifier The [Modifier] to use for the [Preset] list
 * @param activePresetState An [ActivePresetViewState] to provide the
 *     name of, and the is modified status for the active preset.
 * @param selectionBrush The [Brush] that will be applied with alpha = 0.5f
 *     to the active [Preset] to indicate its status as the active [Preset]
 * @param state The [PresetListState] that contains state and callbacks for
 *     the preset list
 */
@Composable fun PresetList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    activePresetState: ActivePresetViewState,
    selectionBrush: Brush,
    state: PresetListState,
) = CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onSurface) {
    Crossfade(
        targetState = state.list?.isEmpty(),
        modifier = modifier,
        label = "preset list empty message / content crossfade"
    ) { when(it) {
        null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(Modifier.size(50.dp))
        } true -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(stringResource(R.string.empty_preset_list_message),
                 modifier = Modifier.width(300.dp),
                 textAlign = TextAlign.Justify)
        } else -> LazyColumn(contentPadding = contentPadding) {
            items(
                items = state.list ?: emptyList(),
                key = Preset::name::get
            ) { preset ->
                val isActivePreset = preset.name == activePresetState.name
                PresetView(
                    modifier = if (!isActivePreset) Modifier
                               else Modifier.background(selectionBrush, alpha = 0.5f),
                    presetName = preset.name,
                    isModified = isActivePreset && activePresetState.isModified,
                    onRenameClick = state.onRenameClick,
                    onOverwriteClick = state.onOverwriteClick,
                    onDeleteClick = state.onDeleteClick,
                    onClick = state.onClick)
                Divider()
            }
        }
    }}
}

@Preview @Composable
fun PresetListPreview() = SoundAuraTheme {
    val list = remember { List(4) {
        Preset("Super Duper Extra Really Long Preset Name$it")
    }.toImmutableList() }
    var activePresetName by rememberMutableStateOf<String?>(list.first().name)

    PresetList(
        activePresetState = ActivePresetViewState(
            nameProvider = { activePresetName },
            isModifiedProvider = { true },
            onClick = {}),
        selectionBrush = Brush.horizontalGradient(
            listOf(MaterialTheme.colors.primaryVariant,
                   MaterialTheme.colors.secondaryVariant)),
        state = remember {
            PresetListState(
                listProvider = { list },
                onRenameClick = {},
                onOverwriteClick = {},
                onDeleteClick = {},
                onClick = { activePresetName = it })
        })
}