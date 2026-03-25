package com.jibru.kostra

import kotlin.jvm.JvmInline

@JvmInline
value class KQualifiers(val key: Long) {
    constructor(locale: KLocale = KLocale.Undefined, dpi: KDpi = KDpi.Undefined) : this(pack(locale, dpi))
    constructor(locale: String, dpi: KDpi = KDpi.Undefined) : this(pack(KLocale(locale), dpi))

    val hasOnlyLocale get() = dpi == KDpi.Undefined
    val locale get() = KLocale(key shr KDpi.Bits)
    val dpi get() = KDpi.fromBits((key and KDpi.BitMask.toLong()).toInt())

    // Strip region from locale, keep language+script
    fun withLocaleNoRegion() = KQualifiers(locale.languageScriptLocale(), dpi)

    // Strip script from locale, keep language+region
    fun withLocaleNoScript() = KQualifiers(locale.languageRegionLocale(), dpi)

    // Strip region and script, keep language only
    fun withLocaleLanguageOnly() = KQualifiers(locale.languageLocale(), dpi)

    fun withNoLocale() = KQualifiers(KLocale.Undefined, dpi)

    fun withNoDpi() = KQualifiers(locale, dpi = KDpi.Undefined)

    fun copy(locale: KLocale = this.locale, dpi: KDpi = this.dpi) = KQualifiers(locale, dpi)

    override fun toString(): String {
        return if (this == Undefined) "Qualifiers.Undefined" else "Qualifiers(locale=$locale, dpi=$dpi)"
    }

    companion object {
        val Undefined = KQualifiers(0L)

        // Total bits used by KQualifiers = KLocale.Bits + KDpi.Bits
        const val Bits = KLocale.Bits + KDpi.Bits // 48 + 4 = 52
    }
}

private fun pack(locale: KLocale, dpi: KDpi) = (locale.key shl KDpi.Bits) + dpi.key.toLong()
