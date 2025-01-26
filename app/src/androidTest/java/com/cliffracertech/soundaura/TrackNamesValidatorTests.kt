/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.SoundAuraDatabase
import com.cliffracertech.soundaura.model.database.TrackNamesValidator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackNamesValidatorTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val coroutineScope = TestCoroutineScope()

    private lateinit var instance: TrackNamesValidator
    private lateinit var db: SoundAuraDatabase
    private lateinit var playlistDao: PlaylistDao

    private val existingNames = List(5) { "track $it" }
    private val newNames = List(5) { "new track $it" }

    @Before fun init() {
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        playlistDao = db.playlistDao()
        runBlocking {
            val uris = List(5) { "uri $it".toUri() }
            playlistDao.insertSingleTrackPlaylists(existingNames, uris, uris)
        }
        instance = TrackNamesValidator(playlistDao, coroutineScope, newNames)
    }

    @After fun clean_up() {
        db.close()
        coroutineScope.cancel()
    }

    @Test fun begins_with_provided_names_without_errors() = runTest {
        assertThat(instance.values).containsExactlyElementsIn(newNames).inOrder()
        waitUntil { false } // we need to wait until the validator retrieves the
                            // existing track/playlist names from the database
        assertThat(instance.errorIndices).isEmpty()
        assertThat(instance.message).isNull()
    }

    @Test fun set_values() = runTest {
        instance.setValue(1, existingNames[1])
        assertThat(instance.values).containsExactly(
            newNames[0], existingNames[1], newNames[2], newNames[3], newNames[4]
        ).inOrder()

        instance.setValue(3, existingNames[3])
        assertThat(instance.values).containsExactly(
            newNames[0], existingNames[1], newNames[2], existingNames[3], newNames[4]
        ).inOrder()
    }

    @Test fun existing_names_cause_errors() = runTest {
        instance.setValue(1, existingNames[1])
        waitUntil { instance.errorIndices.isNotEmpty() }
        assertThat(instance.errorIndices).containsExactly(1)

        instance.setValue(3, existingNames[3])
        waitUntil { instance.errorIndices.size == 2 }
        assertThat(instance.errorIndices).containsExactly(1, 3)

        instance.setValue(1, newNames[1])
        instance.setValue(3, newNames[3])
        waitUntil { instance.errorIndices.isEmpty() }
        assertThat(instance.errorIndices).isEmpty()
    }

    @Test fun blank_names_cause_errors() = runTest {
        instance.setValue(2, "")
        waitUntil { instance.errorIndices.isNotEmpty() }
        assertThat(instance.errorIndices).containsExactly(2)

        instance.setValue(4, "")
        waitUntil { instance.errorIndices.size == 2 }
        assertThat(instance.errorIndices).containsExactly(2, 4).inOrder()

        instance.setValue(2, "a")
        instance.setValue(4, "b")
        waitUntil { instance.errorIndices.isEmpty() }
        assertThat(instance.errorIndices).isEmpty()
    }

    @Test fun duplicate_new_names_are_both_errors() = runTest {
        instance.setValue(3, newNames[1])
        waitUntil { instance.errorIndices.size == 2 }
        assertThat(instance.errorIndices).containsExactly(1, 3)
        instance.setValue(1, newNames[3])
        waitUntil { instance.errorIndices.isEmpty() }
        assertThat(instance.errorIndices).isEmpty()
    }

    @Test fun error_message_updates() = runTest {
        instance.setValue(1, existingNames[1])
        waitUntil { instance.message != null }
        assertThat(instance.message?.stringResource?.stringResId)
            .isEqualTo(R.string.add_multiple_tracks_name_error_message)

        instance.setValue(3, existingNames[3])
        waitUntil { instance.message == null } // should time out
        assertThat(instance.message?.stringResource?.stringResId)
            .isEqualTo(R.string.add_multiple_tracks_name_error_message)

        instance.setValue(1, newNames[1])
        instance.setValue(3, newNames[3])
        waitUntil { instance.message == null }
        assertThat(instance.message).isNull()
    }

    @Test fun validation_success() = runTest {
        val result = instance.validate()
        assertThat(result).isNotNull()
        assertThat(result).containsExactlyElementsIn(newNames).inOrder()
    }

    @Test fun validation_failure() = runTest {
        instance.setValue(3, existingNames[3])
        assertThat(instance.validate()).isNull()
    }
}
