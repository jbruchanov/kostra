package com.jibru.kostra.internal

import com.jibru.kostra.AssetResourceKey
import com.jibru.kostra.FileReferences
import com.jibru.kostra.KDpi
import com.jibru.kostra.KLocale
import com.jibru.kostra.KQualifiers
import com.jibru.kostra.MissingResourceException
import com.jibru.kostra.database.BinaryDatabase

open class FileDatabase(database: String) : FileReferences {
    private val data by lazy {
        BinaryDatabase(loadResource(database)).toBinarySearchMap()
    }

    protected open fun getValue(key: AssetResourceKey, qualifiers: KQualifiers): String? {
        val dbKey = (key.key.toLong() shl KQualifiers.Bits) or qualifiers.key
        return data[dbKey]
    }

    override fun get(key: AssetResourceKey, qualifiers: KQualifiers): String {
        val locale = qualifiers.locale
        //lang+script+region[+dpi] (exact)
        return getValue(key, qualifiers) ?: getValue(key, qualifiers.withNoDpi())
            //lang+script[+dpi] (strip region)
            ?: locale.takeIf { it.hasRegion() }
                ?.let { qualifiers.withLocaleNoRegion() }
                ?.let { q -> getValue(key, q) ?: getValue(key, q.withNoDpi()) }
            //lang+region[+dpi] (strip script)
            ?: locale.takeIf { it.hasScript() }
                ?.let { qualifiers.withLocaleNoScript() }
                ?.let { q -> getValue(key, q) ?: getValue(key, q.withNoDpi()) }
            //lang[+dpi] (language only)
            ?: locale.takeIf { it != KLocale.Undefined }
                ?.let { qualifiers.withLocaleLanguageOnly() }
                ?.let { q -> getValue(key, q) ?: getValue(key, q.withNoDpi()) }
            //just dpi
            ?: qualifiers.dpi.takeIf { it != KDpi.Undefined }?.let { getValue(key, KQualifiers(dpi = it)) }
            //just locale
            ?: locale.takeIf { it != KLocale.Undefined }?.let { getValue(key, KQualifiers(locale = it)) }
            //fallback
            ?: getValue(key, KQualifiers.Undefined)
            ?: throw MissingResourceException(key, qualifiers, "file")
    }
}
