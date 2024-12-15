/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import android.content.Context
import androidx.annotation.StringRes

/** A holder of a string resource, which can be resolved to a
 * [String] by calling the method [resolve] with a [Context]. */
class StringResource(
    private val string: String?,
    @StringRes val stringResId: Int = 0,
    vararg args: Any
) {
    data class Id(@StringRes val id: Int)
    @Suppress("CanBePrimaryConstructorProperty")
    private val args = args

    constructor(@StringRes stringResId: Int):
            this(null, stringResId)
    constructor(@StringRes stringResId: Int, stringVar: String):
            this(null, stringResId, stringVar)
    constructor(@StringRes stringResId: Int, intVar: Int):
            this(null, stringResId, intVar)
    constructor(@StringRes stringResId: Int, stringVarId: Id):
            this(null, stringResId, stringVarId)
    constructor(@StringRes stringResId: Int, vararg args: Any):
            this(null, stringResId, args)

    fun resolve(context: Context?) = string ?: when {
        context == null -> ""
        args.isEmpty() -> context.getString(stringResId)
        else -> {
            val resolvedArgs = Array(args.size) {
                val arg = args[it]
                if (arg !is Id) arg
                else context.getString(arg.id)
            }
            context.getString(stringResId, *resolvedArgs)
        }
    }
}