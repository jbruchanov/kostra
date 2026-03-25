package com.jibru.kostra.plugin

import com.jibru.kostra.KDpi
import com.jibru.kostra.KLocale
import com.jibru.kostra.KQualifiers
import com.jibru.kostra.plugin.ext.takeIfNotEmpty
import java.io.File

private val locales = java.util.Locale.getAvailableLocales().map { it.toLanguageTag().lowercase() }.toSortedSet()
private val dpiMap = KDpi.entries.associateBy { it.qualifier }
private val dpiValues = dpiMap.keys.filter { it.isNotEmpty() }.toSet()

data class GroupQualifiers(
    val group: String,
    val qualifiers: KQualifiers,
)

const val QualifierDivider = "-"

internal fun File.groupQualifiers(anyLocale: Boolean = false): GroupQualifiers {
    val source = name
        .let { if (isFile) it.substringBeforeLast(".") else it }
        .lowercase()
    val group = source.substringBefore(QualifierDivider)
    val qualifiers = source.substringAfter(QualifierDivider, "")
        .takeIfNotEmpty()
        ?.split(QualifierDivider)
        ?.let { list ->
            val otherModifiers = list.toMutableSet()

            val strDpi = otherModifiers.intersect(dpiValues)
                .firstOrNull()
                ?.also { otherModifiers.remove(it) }

            // Check for BCP 47 tag (b+lang[+script][+region]), e.g., "b+zh+hant" or "b+zh+hant+tw"
            val bcp47 = otherModifiers.firstOrNull { it.startsWith("b+") }

            val strLocale: String?
            var strLocaleRegion: String? = null
            var strLocaleScript: String? = null

            if (bcp47 != null) {
                otherModifiers.remove(bcp47)
                val parts = bcp47.removePrefix("b+").split("+")
                strLocale = parts.getOrNull(0)
                for (i in 1 until parts.size) {
                    val p = parts[i]
                    when (p.length) {
                        4 -> if (strLocaleScript == null) strLocaleScript = p
                        in 2..3 -> if (strLocaleRegion == null) strLocaleRegion = p
                    }
                }
            } else {
                // Existing dash-based format parsing
                strLocale = (if (anyLocale) otherModifiers.firstOrNull() else otherModifiers.intersect(locales).firstOrNull())
                    ?.also { otherModifiers.remove(it) }

                // Scan items following the locale for region and/or script
                // BCP 47 order: language-script-region, e.g., "zh-Hans-rCN"
                if (strLocale != null) {
                    val localeIdx = list.indexOf(strLocale)
                    var nextIdx = localeIdx + 1
                    repeat(2) {
                        val item = list.getOrNull(nextIdx) ?: return@repeat
                        // Skip DPI values that appear in the original list between locale parts
                        if (item in dpiValues) return@repeat
                        // Script: exactly 4 alphabetic chars (e.g., "hans", "hant", "latn")
                        // Exclude r-prefix region attempts (e.g., "rcde" from "rCDE")
                        val isRegionAttempt = item.startsWith("r") && item.length in 3..4
                        if (strLocaleScript == null && item.length == 4 && item.all { c -> c.isLetter() } && !isRegionAttempt) {
                            if (anyLocale || locales.any { tag -> tag.startsWith("$strLocale-$item") }) {
                                strLocaleScript = item
                                otherModifiers.remove(item)
                                nextIdx++
                                return@repeat
                            }
                        }
                        // Region: "r" prefix + 2 chars, or just 2 chars in anyLocale mode
                        if (strLocaleRegion == null) {
                            val rPrefixRegion = item.startsWith("r") && item.length == 3
                            val twoCharRegion = item.length == 2
                            if (rPrefixRegion || (anyLocale && twoCharRegion)) {
                                val region = (if (item.startsWith("r")) item.drop(1) else item).take(2)
                                if (anyLocale || locales.contains("$strLocale-$region")) {
                                    strLocaleRegion = region
                                    otherModifiers.remove(item)
                                    nextIdx++
                                    return@repeat
                                }
                            }
                        }
                    }
                }
            }

            try {
                val locale = strLocale?.let {
                    if (strLocaleRegion != null || strLocaleScript != null) {
                        KLocale(it, strLocaleRegion, strLocaleScript)
                    } else {
                        KLocale(it) // let packCode handle compound strings (e.g., "abcd" → lang+region)
                    }
                } ?: KLocale.Undefined
                val dpi = strDpi?.let { dpiMap.getValue(it) } ?: KDpi.Undefined
                KQualifiers(locale = locale, dpi = dpi)
            } catch (e: Throwable) {
                throw IllegalArgumentException("Unable to parse GroupQualifiers, path:'$absolutePath'", e)
            }
        } ?: KQualifiers.Undefined

    return GroupQualifiers(group, qualifiers)
}
