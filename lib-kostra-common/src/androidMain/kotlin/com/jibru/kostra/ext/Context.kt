package com.jibru.kostra.ext

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

internal fun Context.isOrWrapsActivity(): Boolean {
    var current: Context? = this
    while (current != null) {
        if (current is Activity) return true
        current = (current as? ContextWrapper)?.baseContext?.takeIf { it !== current }
    }
    return false
}

internal fun Context.describeChain(): String = buildString {
    var current: Context? = this@describeChain
    var depth = 0
    while (current != null && depth < 8) {
        if (depth > 0) append(" -> ")
        append(current.javaClass.name)
        val next = (current as? ContextWrapper)?.baseContext
        if (next === current) break
        current = next
        depth++
    }
}
