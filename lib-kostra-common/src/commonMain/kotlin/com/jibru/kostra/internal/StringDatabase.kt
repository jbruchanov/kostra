package com.jibru.kostra.internal

import com.jibru.kostra.KLocale
import com.jibru.kostra.MissingResourceException
import com.jibru.kostra.KQualifiers
import com.jibru.kostra.StringResourceKey
import com.jibru.kostra.Strings
import com.jibru.kostra.database.BinaryDatabase
import com.jibru.kostra.database.Database

open class StringDatabase(localeDatabases: Map<KLocale, String>) : Strings {
    private val dbs: Map<KLocale, Lazy<Database>> = localeDatabases.mapValues { (_, file) ->
        lazy { BinaryDatabase(loadResource(file)) }
    }

    protected open fun getValue(key: StringResourceKey, locale: KLocale): String? {
        return dbs[locale]?.value?.getListValue(key.key)
    }

    override fun get(key: StringResourceKey, qualifiers: KQualifiers): String {
        val locale = qualifiers.locale
        //try lang+script+region (exact)
        return getValue(key, locale)
            //try lang+script (strip region)
            ?: locale.takeIf { it.hasRegion() }?.let { getValue(key, it.languageScriptLocale()) }
            //try lang+region (strip script)
            ?: locale.takeIf { it.hasScript() }?.let { getValue(key, it.languageRegionLocale()) }
            //try lang only
            ?: locale.takeIf { it != KLocale.Undefined }?.let { getValue(key, it.languageLocale()) }
            //fallback
            ?: getValue(key, KLocale.Undefined)
            ?: throw MissingResourceException(key, qualifiers, "string")
    }
}
