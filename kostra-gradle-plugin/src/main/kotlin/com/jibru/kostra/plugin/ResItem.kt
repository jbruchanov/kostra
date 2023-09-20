package com.jibru.kostra.plugin

import com.jibru.kostra.BinaryResourceKey
import com.jibru.kostra.KQualifiers
import com.jibru.kostra.PainterResourceKey
import com.jibru.kostra.PluralResourceKey
import com.jibru.kostra.StringResourceKey
import com.jibru.kostra.plugin.ext.relativeTo
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.typeNameOf
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
    abstract val qualifiersKey: Int
    abstract val group: String

    abstract val resourceKeyType: TypeName

    val distinctKey by lazy(LazyThreadSafetyMode.NONE) { Triple(key, qualifiersKey, group) }
    open val isStringOrPlural = this is StringRes || this is Plurals
    open val resourcesGroup get() = group
    val qualifiers get() = KQualifiers(qualifiersKey)

    protected fun validateInput() {
        require(key.isNotEmpty()) { "Key is empty, $this" }
        require(group.isNotEmpty()) { "Group is empty, $this" }
    }

    data class StringRes(
        override val key: String,
        override val value: String,
        override val qualifiersKey: Int,
    ) : ResItem(), StringValueResItem, Serializable {
        override val group: String get() = String
        override val resourceKeyType: TypeName get() = typeNameOf<StringResourceKey>()

        init {
            validateInput()
        }
    }

    data class StringArray(
        override val key: String,
        val items: List<String>,
        override val qualifiersKey: Int,
    ) : ResItem(), Serializable {
        override val group: String get() = StringArray
        override val resourceKeyType: TypeName get() = throw UnsupportedOperationException("StringArray arrays not supported!")

        init {
            validateInput()
        }
    }

    data class Plurals(
        override val key: String,
        //indexes matching [Plural]
        val items: List<String?>,
        override val qualifiersKey: Int,
    ) : ResItem(), Serializable {
        override val group: String get() = Plural

        override val resourceKeyType: TypeName get() = typeNameOf<PluralResourceKey>()

        init {
            validateInput()
        }

        companion object {
            val EmptyItems = List<String?>(com.jibru.kostra.icu.PluralCategory.size) { null }
        }
    }

    @Suppress("ktlint:standard:discouraged-comment-location")
    data class FileRes(
        override val key: String,
        val file: File,
        override val qualifiersKey: Int,
        override val group: String,
        val root: File, // = file.parentFile.parentFile
    ) : ResItem(), StringValueResItem, Serializable {
        val drawable get() = group == Drawable
        override val value get() = file.relativeTo(root, ignoreCase = true)
        override val resourcesGroup: String get() = if (drawable) Drawable else Binary
        override val resourceKeyType: TypeName get() = if (drawable) typeNameOf<PainterResourceKey>() else typeNameOf<BinaryResourceKey>()

        init {
            validateInput()
        }
    }

    companion object {
        const val Drawable = "drawable"
        const val String = "string"
        const val Binary = "binary"
        const val Plural = "plural"
        const val StringArray = "stringArray"
    }
}
