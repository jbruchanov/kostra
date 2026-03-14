@file:Suppress("ReplaceManualRangeWithIndicesCalls")

package com.jibru.kostra

import com.jibru.kostra.ext.takeIfNotEmpty
import kotlin.jvm.JvmInline

private const val LetterCodeMin = 'a'.code
private const val LetterCodeMax = 'z'.code

//-1 to have 'a' as 1, not 0, to avoid clashing with 0 as undefined
private const val LetterOffset = LetterCodeMin - 1
private val LocaleOffsets = intArrayOf(1_00_00_00_00, 1_00_00_00, 1_00_00, 1_00, 1)

@JvmInline
value class KLocale(val key: Int) : Comparable<KLocale> {

    constructor(languageRegion: String) : this(packCode(languageRegion))
    constructor(language: String, region: String?) : this(packLanguageRegion(language, region))

    fun equalsLanguage(other: KLocale): Boolean = this.languageCode == other.languageCode

    fun languageLocale() = KLocale(key / LocaleOffsets[2] * LocaleOffsets[2])

    val language: String
        get() {
            if (key == 0) return ""
            return buildString(0, 2)
        }

    val region: String?
        get() {
            if (key % LocaleOffsets[2] == 0) return null
            return buildString(2, 4)
        }

    val languageRegion
        get() = buildString {
            append(language)
            region?.let { append(it) }
        }

    internal val languageCode: Int
        get() = key / LocaleOffsets[1]

    internal val regionCode: Int
        get() = key % LocaleOffsets[1]

    @Suppress("NOTHING_TO_INLINE")
    private inline fun buildString(from: Int, toExclusive: Int): String = buildString {
        for (i in from..<toExclusive) {
            val code = (key % LocaleOffsets[i] / (LocaleOffsets.getOrNull(i + 1) ?: 1))
            if (code != 0) {
                append((LetterOffset + code).toChar())
            }
        }
    }

    override fun compareTo(other: KLocale): Int = key.compareTo(other.key)

    override fun toString(): String {
        return if (key == 0) "KLocale.Undefined" else "KLocale($languageRegion)"
    }

    fun hasRegion(): Boolean = (key % LocaleOffsets[3]) != 0

    companion object {
        const val MaxLocaleLen = 2
        val Undefined = KLocale(0)

        //ceil(log(('z' - 'a' + 1).pow(4), 2.0))
        //can be optimised down if it's taken to binary math, instead of decimal
        const val Bits = 20
    }
}

@Suppress("NAME_SHADOWING")
private fun packLanguageRegion(language: String, region: String?): Int {
    val region = if (region?.getOrNull(0) == 'r') region.substring(1) else region
    require(language.isEmpty() || language.length == 2) { "Invalid language:'$language', must be 0 or 2 chars!" }
    require(region.isNullOrEmpty() || region.length == 2) { "Invalid region:'$region', must be null, 0 or 2 chars!" }
    if (language.isEmpty()) return 0
    val result =
        ((language[0].validCode() - LetterOffset) * LocaleOffsets[1]) +
            ((language[1].validCode() - LetterOffset) * LocaleOffsets[2]) +
            (((region?.getOrNull(0)?.validCode() ?: LetterOffset) - LetterOffset) * LocaleOffsets[3]) +
            (((region?.getOrNull(1)?.validCode() ?: LetterOffset) - LetterOffset) * LocaleOffsets[4])
    return result
}

private fun packCode(code: String): Int {
    return when {
        code.isEmpty() -> 0
        code.contains("-") -> packLanguageRegion(code.substringBefore("-"), code.substringAfter("-", "").takeIfNotEmpty())
        code.length in 1..4 ->
            ((code[0].validCode() - LetterOffset) * LocaleOffsets[1]) +
                (((code.getOrNull(1)?.validCode() ?: LetterOffset) - LetterOffset) * LocaleOffsets[2]) +
                (((code.getOrNull(2)?.validCode() ?: LetterOffset) - LetterOffset) * LocaleOffsets[3]) +
                (((code.getOrNull(3)?.validCode() ?: LetterOffset) - LetterOffset) * LocaleOffsets[4])

        else -> throw IllegalArgumentException("Invalid locale:'$code'")
    }
}

@Suppress("ConvertTwoComparisonsToRangeCheck", "ktlint:standard:discouraged-comment-location")
private fun Char.validCode(): Int {
    val c = code
    val code = if (c < LetterOffset) c + 32/*'A' vs 'a' offset*/ else c
    return requireNotNull(code.takeIf { LetterCodeMin <= it && it <= LetterCodeMax }) { "Invalid locale char:'$this must be 'a'-'z'!" }
}
