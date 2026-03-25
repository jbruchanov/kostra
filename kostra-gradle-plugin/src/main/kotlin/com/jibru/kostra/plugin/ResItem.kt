package com.jibru.kostra.plugin

import com.jibru.kostra.KQualifiers
import com.jibru.kostra.plugin.ext.isImage
import com.jibru.kostra.plugin.ext.relativeTo
import java.io.File
import java.io.Serializable

interface StringValueResItem {
    val key: String
    val value: String
    val qualifiers: KQualifiers
    val group: String
}

sealed class ResItem : Serializable {
    abstract val key: String
    abstract val qualifiersKey: Long
    abstract val group: String

    val distinctKey by lazy(LazyThreadSafetyMode.NONE) { Triple(key, qualifiersKey, group) }
    open val isStringOrPlural = this is StringRes || this is Plurals
    val qualifiers get() = KQualifiers(qualifiersKey)

    val isImageFile: Boolean get() = (this as? FileRes)?.image == true
    var origin: File? = null

    protected fun validateInput() {
        require(key.isNotEmpty()) { "Key is empty, $this" }
        require(group.isNotEmpty()) { "Group is empty, $this" }
    }

    data class StringRes(
        override val key: String,
        override val value: String,
        override val qualifiersKey: Long,
    ) : ResItem(), StringValueResItem, Serializable {
        override val group: String get() = String

        init {
            validateInput()
        }

        override fun toString(): String {
            return "StringRes(key='$key', value='$value', qualifiers=${KQualifiers(qualifiersKey)})"
        }
    }

    data class StringArray(
        override val key: String,
        val items: List<String>,
        override val qualifiersKey: Long,
    ) : ResItem(), Serializable {
        override val group: String get() = StringArray

        init {
            validateInput()
        }
    }

    data class Plurals(
        override val key: String,
        //indexes matching [Plural]
        val items: List<String?>,
        override val qualifiersKey: Long,
    ) : ResItem(), Serializable {
        override val group: String get() = Plural

        init {
            validateInput()
        }

        companion object {
            val EmptyItems = List<String?>(com.jibru.kostra.icu.PluralCategory.size) { null }
        }

        override fun toString(): String {
            return "Plurals(key='$key', items=$items, qualifiers=${KQualifiers(qualifiersKey)})"
        }
    }

    @Suppress("ktlint:standard:discouraged-comment-location")
    data class FileRes(
        override val key: String,
        val file: File,
        override val qualifiersKey: Long,
        override val group: String,
        val root: File, // = file.parentFile.parentFile
        val image: Boolean = file.isImage(),
    ) : ResItem(), StringValueResItem, Serializable {
        override val value get() = file.relativeTo(root, ignoreCase = true)

        init {
            validateInput()
            origin = file
        }

        override fun toString(): String {
            return "FileRes(key='$key', file=$file, qualifiers=${KQualifiers(qualifiersKey)}, group='$group', root=$root, image=$image)"
        }
    }

    companion object {
        const val String = "string"
        const val Binary = "binary"
        const val Plural = "plural"
        const val StringArray = "stringArray"
    }
}
