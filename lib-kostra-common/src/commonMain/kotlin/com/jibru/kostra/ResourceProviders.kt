package com.jibru.kostra

import com.jibru.kostra.icu.FixedDecimal
import com.jibru.kostra.icu.IFixedDecimal
import com.jibru.text.sFormat

interface Strings {
    fun get(key: StringResourceKey, qualifiers: KQualifiers): String

    fun get(key: StringResourceKey, qualifiers: KQualifiers, vararg formatArgs: Any): String {
        return get(key, qualifiers).sFormat(*formatArgs)
    }

    companion object : Strings {
        override fun get(key: StringResourceKey, qualifiers: KQualifiers): String =
            throw MissingResourceException(key, qualifiers, "string")
    }
}

interface Plurals {

    enum class Type { Plurals, Ordinals }

    fun get(key: PluralResourceKey, qualifiers: KQualifiers, quantity: IFixedDecimal, type: Type): String

    fun get(key: PluralResourceKey, qualifiers: KQualifiers, quantity: IFixedDecimal, type: Type, vararg formatArgs: Any): String =
        get(key, qualifiers, quantity, type).sFormat(*formatArgs)

    fun get(key: PluralResourceKey, qualifiers: KQualifiers, quantity: Int, type: Type): String =
        get(key, qualifiers, FixedDecimal(quantity.toLong()), type)

    fun get(key: PluralResourceKey, qualifiers: KQualifiers, quantity: Int, type: Type, vararg formatArgs: Any): String =
        get(key, qualifiers, FixedDecimal(quantity.toLong()), type).sFormat(*formatArgs)

    companion object : Plurals {
        override fun get(key: PluralResourceKey, qualifiers: KQualifiers, quantity: IFixedDecimal, type: Type): String =
            throw MissingResourceException(key, qualifiers, "plural:$type")
    }
}

interface FileReferences {
    fun get(key: AssetResourceKey, qualifiers: KQualifiers): String

    companion object : FileReferences {
        override fun get(key: AssetResourceKey, qualifiers: KQualifiers): String =
            throw MissingResourceException(key, qualifiers, "fileRef")
    }
}
