/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.cliffracertech.soundaura.logd
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Collections.emptyList
import javax.inject.Inject
import javax.inject.Singleton

/** UriPermissionHandler describes the expected interface for a
 * manager of a limited number of persistable file permissions:
 * [acquirePermissionsFor] and [releasePermissionsFor]. */
interface UriPermissionHandler {
    val totalAllowance: Int
    fun getRemainingAllowance(): Int

    /**
     * Acquire permissions for each file [Uri] in [uris], if space permits. If
     * the size of [uris] is greater than the remaining permission count, then
     * no permissions will be acquired if [allowPartial] is false. Otherwise,
     * as many permissions as space permits will be acquired.
     *
     * @return The list of uris that the app does NOT have permission to use.
     */
    fun acquirePermissionsFor(
        uris: List<Uri>,
        allowPartial: Boolean = true
    ): List<Uri>

    /** Release any persisted permissions for the [Uri]s in [uris]. */
    fun releasePermissionsFor(uris: List<Uri>)
}

/** A mock [UriPermissionHandler] whose methods do nothing. */
class TestPermissionHandler: UriPermissionHandler {
    override val totalAllowance = 512
    override fun getRemainingAllowance() = 512
    override fun acquirePermissionsFor(
        uris: List<Uri>,
        allowPartial: Boolean
    ): List<Uri> = emptyList()
    override fun releasePermissionsFor(uris: List<Uri>) = Unit
}

/**
 * An implementation of [UriPermissionHandler] that takes into account if the
 * app has audio media access (i.e. it has the READ_MEDIA_AUDIO permission on
 * API >= 33, or the READ_EXTERNAL_STORAGE permission on API < 33), and if not
 * takes persistable [Uri] permissions granted by the Android system. If a
 * persistable [Uri] permission was not originally granted by the Android
 * system for any of the [Uri]s in the list passed to [acquirePermissionsFor],
 * the operation will fail.
 */
@Singleton
class AndroidUriPermissionHandler @Inject constructor(
    @ApplicationContext private val context: Context,
): UriPermissionHandler {
    private fun hasStoragePermission() = context.checkSelfPermission(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    override val totalAllowance =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) 128
        else                                               512

    override fun getRemainingAllowance() =
        totalAllowance - context.contentResolver.persistedUriPermissions.size

    override fun acquirePermissionsFor(
        uris: List<Uri>,
        allowPartial: Boolean
    ): List<Uri> {
        if (hasStoragePermission())
            return emptyList()

        val remainingSpace = getRemainingAllowance()
        val hasEnoughSpace = remainingSpace >= uris.size

        when {
            hasEnoughSpace -> uris
            allowPartial ->   uris.subList(0, remainingSpace)
            else ->           emptyList()
        }.forEach {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                logd("Attempted to obtain a persistable permission for " +
                     "$it when no persistable permission was granted.")
            }
        }

        return when {
            hasEnoughSpace -> emptyList()
            allowPartial ->   uris.subList(remainingSpace, uris.size)
            else ->           uris
        }
    }

    override fun releasePermissionsFor(uris: List<Uri>) {
        for (uri in uris) try {
            context.contentResolver.releasePersistableUriPermission(uri, 0)
        } catch (e: SecurityException) {
            logd("Attempted to release Uri permission for $uri " +
                 "when no permission was previously granted")
        }
    }
}