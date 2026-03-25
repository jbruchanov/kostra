package com.jibru.kostra.internal

import com.jibru.kostra.KLocale
import com.jibru.kostra.KQualifiers
import com.jibru.kostra.MissingResourceException
import com.jibru.kostra.PluralResourceKey
import com.jibru.kostra.Plurals
import com.jibru.kostra.database.BinaryDatabase
import com.jibru.kostra.icu.IFixedDecimal
import com.jibru.kostra.icu.OrdinalRuleSpecs
import com.jibru.kostra.icu.PluralCategory
import com.jibru.kostra.icu.PluralRuleSpecs

open class PluralDatabase(localeDatabases: Map<KLocale, String>) : Plurals {
    private val dbs = localeDatabases.mapValues { (_, file) ->
        lazy { BinaryDatabase(loadResource(file)) }
    }

    private val stride = PluralCategory.size

    protected open fun getValue(key: PluralResourceKey, locale: KLocale, plural: PluralCategory): String? {
        val dbKey = (key.key * stride) + plural.index
        return dbs[locale]?.value?.getListValue(dbKey)
    }

    override fun get(key: PluralResourceKey, qualifiers: KQualifiers, quantity: IFixedDecimal, type: Plurals.Type): String {
        val locale = qualifiers.locale
        //try lang+script+region (exact)
        return getValue(key, locale, type.pluralCategory(quantity, locale))
            //try lang+script (strip region)
            ?: locale.takeIf { it.hasRegion() }
                ?.let { it.languageScriptLocale() }
                ?.let { l -> getValue(key, l, type.pluralCategory(quantity, l)) }
            //try lang+region (strip script)
            ?: locale.takeIf { it.hasScript() }
                ?.let { it.languageRegionLocale() }
                ?.let { l -> getValue(key, l, type.pluralCategory(quantity, l)) }
            //try lang only
            ?: locale.takeIf { it != KLocale.Undefined }
                ?.let { it.languageLocale() }
                ?.let { l -> getValue(key, l, type.pluralCategory(quantity, l)) }
            //fallback
            ?: getValue(key, KLocale.Undefined, type.pluralCategory(quantity, locale))
            ?: getValue(key, KLocale.Undefined, PluralCategory.Other)
            ?: throw MissingResourceException(key, qualifiers, "plural")
    }

    private fun Plurals.Type.pluralCategory(quantity: IFixedDecimal, locale: KLocale): PluralCategory {
        val specs = specs[locale]
            ?: specs[locale.languageLocale()]
            ?: return PluralCategory.Other
        return specs.select(quantity)
    }

    private val Plurals.Type.specs
        get() = when (this) {
            Plurals.Type.Plurals -> PluralRuleSpecs
            Plurals.Type.Ordinals -> OrdinalRuleSpecs
        }
}
