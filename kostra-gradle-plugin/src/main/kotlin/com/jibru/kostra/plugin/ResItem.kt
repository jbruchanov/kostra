package com.jibru.kostra.plugin

import com.jibru.kostra.internal.Qualifiers
import java.io.File

sealed class ResItem {
    abstract val key: String
    abstract val qualifiers: Qualifiers
    abstract val group: String

    val distinctKey by lazy(LazyThreadSafetyMode.NONE) { Triple(key, qualifiers, group) }

    data class StringRes(
        override val key: String,
        val value: String,
        override val qualifiers: Qualifiers,
    ) : ResItem() {
        override val group: String = "string"
    }

    data class StringArray(
        override val key: String,
        val items: List<String>,
        override val qualifiers: Qualifiers,
    ) : ResItem() {
        override val group: String = "stringArray"
    }

    data class Plurals(
        override val key: String,
        val items: Map<String, String>,
        override val qualifiers: Qualifiers,
    ) : ResItem() {
        override val group: String = "plural"
    }

    data class FileRes(
        override val key: String,
        val file: File,
        override val qualifiers: Qualifiers,
        override val group: String,
    ) : ResItem() {
        val drawable = group == GroupDrawable
    }

    companion object {
        const val GroupDrawable = "drawable"
    }
}
