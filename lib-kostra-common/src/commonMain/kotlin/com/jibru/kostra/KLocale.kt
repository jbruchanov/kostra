@file:Suppress("ReplaceManualRangeWithIndicesCalls")

package com.jibru.kostra

import com.jibru.kostra.ext.takeIfNotEmpty
import kotlin.jvm.JvmInline

// --- Character encoding constants ---
private const val LetterCodeMin = 'a'.code
private const val LetterCodeMax = 'z'.code

// 'a' = 1, 'b' = 2, ..., 'z' = 26, 0 = empty/padding
private const val LetterOffset = LetterCodeMin - 1

// --- Base-27 positional encoding constants ---
// Base for encoding: 26 letters + 1 for empty (0)
private const val Base = 27L

// Slot counts per category
private const val LanguageSlots = 3 // up to 3-char language code (e.g., "ars")
private const val RegionSlots = 3 // up to 3-char region code
private const val ScriptSlots = 4 // up to 4-char script/variant (e.g., "Hans")
// Total slots = LanguageSlots + RegionSlots + ScriptSlots = 10

// Category boundary powers of 27
// ScriptMod: 27^ScriptSlots — separates region from script portion
private const val ScriptMod = Base * Base * Base * Base // 27^4 = 531,441
// RegionMod: 27^RegionSlots — number of distinct values per region category
private const val RegionMod = Base * Base * Base // 27^3 = 19,683
// RegionBound: 27^(RegionSlots+ScriptSlots) — separates language from region+script
private const val RegionBound = ScriptMod * RegionMod // 27^7 = 10,460,353,203

// Per-slot powers for encoding/decoding individual characters
// Index 0 = highest (lang[0]), index 9 = lowest (script[3])
private val SlotPowers = longArrayOf(
    7_625_597_484_987L, // 27^9  lang[0]
    282_429_536_481L, // 27^8  lang[1]
    10_460_353_203L, // 27^7  lang[2]
    387_420_489L, // 27^6  region[0]
    14_348_907L, // 27^5  region[1]
    531_441L, // 27^4  region[2]
    19_683L, // 27^3  script[0]
    729L, // 27^2  script[1]
    27L, // 27^1  script[2]
    1L, // 27^0  script[3]
)

@JvmInline
value class KLocale(val key: Long) : Comparable<KLocale> {

    constructor(languageRegion: String) : this(packCode(languageRegion))
    constructor(language: String, region: String?) : this(packLocale(language, region, null))
    constructor(language: String, region: String?, script: String?) : this(packLocale(language, region, script))

    fun equalsLanguage(other: KLocale): Boolean = this.languageCode == other.languageCode

    // Strip region and script, keep language only
    fun languageLocale() = KLocale(key / RegionBound * RegionBound)

    // Strip script, keep language+region
    fun languageRegionLocale() = KLocale(key / ScriptMod * ScriptMod)

    // Strip region, keep language+script
    fun languageScriptLocale() = KLocale(languageCode * RegionBound + scriptCode)

    val language: String
        get() {
            if (key == 0L) return ""
            return buildString(0, LanguageSlots)
        }

    val region: String?
        get() {
            if (regionCode == 0L) return null
            return buildString(LanguageSlots, LanguageSlots + RegionSlots)
        }

    val script: String?
        get() {
            if (scriptCode == 0L) return null
            return buildString(LanguageSlots + RegionSlots, LanguageSlots + RegionSlots + ScriptSlots)
        }

    val languageRegion
        get() = buildString {
            append(language)
            region?.let { append(it) }
        }

    /** Full locale tag including all components: language[+script][+region], e.g., "zh-hans" or "zh-hans-cn" */
    val tag: String
        get() = buildString {
            append(language)
            script?.let { append("-$it") }
            region?.let { append("-$it") }
        }

    internal val languageCode: Long
        get() = key / RegionBound

    internal val regionCode: Long
        get() = (key / ScriptMod) % RegionMod

    internal val scriptCode: Long
        get() = key % ScriptMod

    @Suppress("NOTHING_TO_INLINE")
    private inline fun buildString(from: Int, toExclusive: Int): String = buildString {
        for (i in from..<toExclusive) {
            val code = ((key / SlotPowers[i]) % Base).toInt()
            if (code != 0) {
                append((LetterOffset + code).toChar())
            }
        }
    }

    override fun compareTo(other: KLocale): Int = key.compareTo(other.key)

    override fun toString(): String {
        return if (key == 0L) "KLocale.Undefined" else "KLocale($tag)"
    }

    fun hasRegion(): Boolean = regionCode != 0L

    fun hasScript(): Boolean = scriptCode != 0L

    companion object {
        const val MaxLocaleLen = 3
        const val MaxScriptLen = 4
        val Undefined = KLocale(0L)

        // ceil(log2(27^10)) — bits needed to encode any locale value
        const val Bits = 48
    }
}

@Suppress("NAME_SHADOWING")
private fun packLocale(language: String, region: String?, script: String?): Long {
    val region = if (region?.getOrNull(0) == 'r') region.substring(1) else region
    require(language.isEmpty() || language.length in 2..3) { "Invalid language:'$language', must be 0, 2, or 3 chars!" }
    require(region.isNullOrEmpty() || region.length in 2..3) { "Invalid region:'$region', must be null, 0, 2, or 3 chars!" }
    require(script.isNullOrEmpty() || script.length in 2..4) { "Invalid script:'$script', must be null, 0, 2, 3, or 4 chars!" }
    if (language.isEmpty()) return 0L

    // Language: pack into slots 0-2 (right-aligned, 0-padded on left for 2-char codes)
    val langOffset = LanguageSlots - language.length
    var result = 0L
    for (i in language.indices) {
        result += (language[i].validCode() - LetterOffset) * SlotPowers[langOffset + i]
    }

    // Region: pack into slots 3-5 (right-aligned, 0-padded on left for 2-char codes)
    if (!region.isNullOrEmpty()) {
        val regOffset = LanguageSlots + (RegionSlots - region.length)
        for (i in region.indices) {
            result += (region[i].validCode() - LetterOffset) * SlotPowers[regOffset + i]
        }
    }

    // Script: pack into slots 6-9 (right-aligned, 0-padded on left for shorter codes)
    if (!script.isNullOrEmpty()) {
        val scrOffset = LanguageSlots + RegionSlots + (ScriptSlots - script.length)
        for (i in script.indices) {
            result += (script[i].validCode() - LetterOffset) * SlotPowers[scrOffset + i]
        }
    }

    return result
}

private fun packCode(code: String): Long {
    return when {
        code.isEmpty() -> 0L
        code.contains("-") -> {
            val parts = code.split("-")
            val language = parts[0]
            val rest = parts.drop(1)
            // Determine region vs script from remaining parts
            // Convention: region is 2-3 chars (may have 'r' prefix), script is 4 chars
            var region: String? = null
            var script: String? = null
            for (part in rest) {
                val p = if (part.getOrNull(0) == 'r' && part.length == 3) part.substring(1) else part
                when (p.length) {
                    in 2..3 -> if (region == null) region = p else script = p
                    4 -> script = p
                    else -> throw IllegalArgumentException("Invalid locale part:'$part' in '$code'")
                }
            }
            packLocale(language, region, script)
        }
        code.length in 1..3 -> {
            // Just a language code (1-3 chars)
            val langOffset = LanguageSlots - code.length
            var result = 0L
            for (i in code.indices) {
                result += (code[i].validCode() - LetterOffset) * SlotPowers[langOffset + i]
            }
            result
        }
        code.length == 4 -> {
            // 4 chars without dash: 2-char language + 2-char region (e.g., "enUS")
            packLocale(code.substring(0, 2), code.substring(2, 4), null)
        }
        code.length in 5..6 -> {
            // 5-6 chars without dash: 2-or-3-char language + remaining as region
            // Convention: first 2 or 3 chars are language, rest is region
            // If first 3 chars form a valid language and remainder is 2-3 chars, use 3+remainder
            // Otherwise use 2+remainder
            val lang3Rest = code.length - 3
            if (lang3Rest in 2..3) {
                packLocale(code.substring(0, 3), code.substring(3), null)
            } else {
                packLocale(code.substring(0, 2), code.substring(2), null)
            }
        }
        else -> throw IllegalArgumentException("Invalid locale:'$code'")
    }
}

@Suppress("ConvertTwoComparisonsToRangeCheck", "ktlint:standard:discouraged-comment-location")
private fun Char.validCode(): Int {
    val c = code
    val code = if (c < LetterOffset) c + 32/*'A' vs 'a' offset*/ else c
    return requireNotNull(code.takeIf { LetterCodeMin <= it && it <= LetterCodeMax }) { "Invalid locale char:'$this must be 'a'-'z'!" }
}
