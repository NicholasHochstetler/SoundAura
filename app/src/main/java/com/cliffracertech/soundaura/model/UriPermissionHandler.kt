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
import javax.inject.Inject
import javax.inject.Singleton

/** UriPermissionHandler describes the expected interface for a
 * manager of a limited number of file permissions, each of which
 * is described via a [Uri].
 * [acquirePermissionsFor] and [releasePermissionsFor]. */
interface UriPermissionHandler {
    /** The maximum number of file permissions permitted by the platform. */
    val totalAllowance: Int

    /** The number of file permissions already used. */
    val usedAllowance: Int

    /** The number of file permissions remaining. */
    val remainingAllowance get() = totalAllowance - usedAllowance

    /**
     * Acquire permissions for each file [Uri] in [uris], if space permits. If
     * the size of [uris] is greater than the remaining permission count, then
     * no permissions will be acquired.
     *
     * @return Whether all of the permissions were successfully acquired
     */
    fun acquirePermissionsFor(uris: List<Uri>): Boolean

    /** Release any persisted permissions for the [Uri]s in [uris]. */
    fun releasePermissionsFor(uris: List<Uri>)
}

/** A mock [UriPermissionHandler] whose methods simulate
 * a limited number of permission allowances. */
class TestPermissionHandler: UriPermissionHandler {
    private val grantedPermissions = mutableSetOf<Uri>()

    override val totalAllowance = 12
    override val usedAllowance get() = grantedPermissions.size

    override fun acquirePermissionsFor(uris: List<Uri>): Boolean {
        val newUris = uris.filterNot(grantedPermissions::contains)
        val hasEnoughSpace = remainingAllowance >= uris.size
        if (hasEnoughSpace)
            grantedPermissions.addAll(newUris)
        return hasEnoughSpace
    }
    override fun releasePermissionsFor(uris: List<Uri>) {
        grantedPermissions.removeAll(uris.toSet())
    }
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
    private val hasStoragePermission get() = context.checkSelfPermission(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    override val totalAllowance =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) 128
        else                                               512

    override val usedAllowance get() =
        context.contentResolver.persistedUriPermissions.size

    override fun acquirePermissionsFor(uris: List<Uri>): Boolean = when {
        hasStoragePermission -> true
        remainingAllowance < uris.size -> false
        else -> {
            var successfulGrants = 0
            for (uri in uris) try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                successfulGrants++
            } catch (e: SecurityException) {
                logd("Attempted to obtain a persistable permission for " +
                     "$uri when no persistable permission was granted.")
                releasePermissionsFor(uris.subList(0, successfulGrants))
                break
            }
            successfulGrants == uris.size
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