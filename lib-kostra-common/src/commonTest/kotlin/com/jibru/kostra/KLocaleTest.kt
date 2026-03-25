package com.jibru.kostra

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.Test

class KLocaleTest {
    @Test
    fun pack() {
        assertEquals(0L, KLocale.Undefined.key)
        assertEquals(0L, KLocale("", null).key)
        assertNotEquals(0L, KLocale("en", null).key)
        assertNotEquals(0L, KLocale("aa", null).key)
        assertNotEquals(0L, KLocale("en", "").key)
        assertNotEquals(0L, KLocale("en", "US").key)
        assertNotEquals(0L, KLocale("AA", "ZZ").key)
        assertNotEquals(0L, KLocale("en-US").key)
        assertNotEquals(0L, KLocale("aa-AA").key)
        assertNotEquals(0L, KLocale("zz-ZZ").key)
        assertNotEquals(0L, KLocale("en-rUS").key)
        assertNotEquals(0L, KLocale("aa-rAA").key)
        assertNotEquals(0L, KLocale("zz-rZZ").key)

        // 3-char language codes
        assertNotEquals(0L, KLocale("ars", null).key)
        assertNotEquals(0L, KLocale("ast", null).key)
        assertNotEquals(0L, KLocale("bal", null).key)

        // script/variant
        assertNotEquals(0L, KLocale("zh", null, "Hans").key)
        assertNotEquals(0L, KLocale("zh-Hans").key)

        // Invalid inputs
        assertFailsWith<IllegalArgumentException> { KLocale("e", null) }
        assertFailsWith<IllegalArgumentException> { KLocale("enGB", null) }
        assertFailsWith<IllegalArgumentException> { KLocale("en", "u") }
        assertFailsWith<IllegalArgumentException> { KLocale("en", "uSSS") }
        assertFailsWith<IllegalArgumentException> { KLocale("!!") }
    }

    @Test
    fun packAny() {
        assertEquals(0L, KLocale.Undefined.key)
        assertNotEquals(0L, KLocale("ab").key)
        assertNotEquals(0L, KLocale("abc").key)
    }

    @Test
    fun languageRegion() {
        assertEquals("", KLocale.Undefined.language)
        assertEquals("cs", KLocale("CS").language)
        assertEquals("cs", KLocale("CS", "CZ").language)
        assertEquals(null, KLocale.Undefined.region)
        assertEquals("en", KLocale("en-rUS").language)
        assertEquals("cs", KLocale("CS", "CZ").language)
        assertEquals("aa", KLocale("aa").language)
        assertEquals("aa", KLocale("aa", "AA").language)
        assertEquals("zz", KLocale("zz").language)
        assertEquals("zz", KLocale("zz", "zz").language)

        assertEquals(null, KLocale.Undefined.region)
        assertEquals(null, KLocale("CS").region)
        assertEquals("cz", KLocale("CS", "CZ").region)
        assertEquals("aa", KLocale("aa", "aa").region)
        assertEquals("aa", KLocale("aa", "AA").region)
        assertEquals("zz", KLocale("zz", "zz").region)

        assertEquals("", KLocale.Undefined.languageRegion)
        assertEquals("enus", KLocale("en-rUS").languageRegion)
        assertEquals("cscz", KLocale("CS", "CZ").languageRegion)
        assertEquals("aa", KLocale("aa").languageRegion)
        assertEquals("aaaa", KLocale("aa", "AA").languageRegion)
        assertEquals("zz", KLocale("zz").languageRegion)
        assertEquals("zzzz", KLocale("zz", "zz").languageRegion)
    }

    @Test
    fun threeCharLanguage() {
        assertEquals("ars", KLocale("ars").language)
        assertEquals("ast", KLocale("ast").language)
        assertEquals("bal", KLocale("bal").language)
        assertNull(KLocale("ars").region)

        // 3-char language + 2-char region
        assertEquals("ars", KLocale("ars", "SA").language)
        assertEquals("sa", KLocale("ars", "SA").region)

        // 3-char language + 3-char region
        assertEquals("ars", KLocale("ars", "SAU").language)
        assertEquals("sau", KLocale("ars", "SAU").region)
    }

    @Test
    fun script() {
        // zh-Hans
        val zhHans = KLocale("zh", null, "Hans")
        assertEquals("zh", zhHans.language)
        assertNull(zhHans.region)
        assertEquals("hans", zhHans.script)
        assertTrue(zhHans.hasScript())

        // zh-Hant
        val zhHant = KLocale("zh", null, "Hant")
        assertEquals("zh", zhHant.language)
        assertEquals("hant", zhHant.script)

        // Parse from string: "zh-Hans"
        val parsed = KLocale("zh-Hans")
        assertEquals("zh", parsed.language)
        assertEquals("hans", parsed.script)
        assertNull(parsed.region)

        // No script
        assertFalse(KLocale("en").hasScript())
        assertNull(KLocale("en").script)
        assertFalse(KLocale("en", "US").hasScript())
    }

    @Test
    fun equalsLanguage() {
        assertTrue(KLocale.Undefined.equalsLanguage(KLocale.Undefined))
        assertTrue(KLocale("en").equalsLanguage(KLocale("EN")))
        assertTrue(KLocale("en", "US").equalsLanguage(KLocale("en", "US")))
        assertTrue(KLocale("en", "GB").equalsLanguage(KLocale("EN", "gb")))

        assertFalse(KLocale.Undefined.equalsLanguage(KLocale("cs")))
        assertFalse(KLocale("en").equalsLanguage(KLocale.Undefined))
        assertFalse(KLocale("en").equalsLanguage(KLocale("cs")))

        // 3-char language
        assertTrue(KLocale("ars").equalsLanguage(KLocale("ars", "SA")))
        assertFalse(KLocale("ars").equalsLanguage(KLocale("ast")))
    }

    @Test
    fun languageLocale() {
        assertEquals(KLocale.Undefined, KLocale.Undefined.languageLocale())
        assertEquals(KLocale("en"), KLocale("en").languageLocale())
        assertEquals(KLocale("en"), KLocale("en", "GB").languageLocale())
        // 3-char: strips region
        assertEquals(KLocale("ars"), KLocale("ars", "SA").languageLocale())
        // Strips script too
        assertEquals(KLocale("zh"), KLocale("zh", null, "Hans").languageLocale())
    }

    @Test
    fun hasRegion() {
        assertFalse(KLocale.Undefined.hasRegion())
        assertFalse(KLocale("en").hasRegion())
        assertTrue(KLocale("en", "gb").hasRegion())
        assertTrue(KLocale("ars", "SA").hasRegion())
    }

    @Test
    fun twoChatPaddedWithZero() {
        // 2-char codes should be right-aligned with 0 prefix in their 3-slot category
        // "en" and "EN" should produce same key
        assertEquals(KLocale("en").key, KLocale("EN").key)
        // "en-US" should equal "EN-us"
        assertEquals(KLocale("en-US").key, KLocale("EN-us").key)
    }

    @Test
    fun distinctKeys() {
        // Different locales should have different keys
        val en = KLocale("en")
        val enUS = KLocale("en", "US")
        val enGB = KLocale("en", "GB")
        val cs = KLocale("cs")
        val ars = KLocale("ars")
        val zhHans = KLocale("zh", null, "Hans")
        val zhHant = KLocale("zh", null, "Hant")

        val keys = listOf(en, enUS, enGB, cs, ars, zhHans, zhHant).map { it.key }
        assertEquals(keys.size, keys.distinct().size, "All keys should be unique")
    }

    @Test
    fun tag() {
        assertEquals("en", KLocale("en").tag)
        assertEquals("en-us", KLocale("en", "US").tag)
        assertEquals("cs", KLocale("cs").tag)
        assertEquals("zh-hans", KLocale("zh", null, "Hans").tag)
        assertEquals("zh-hant", KLocale("zh", null, "Hant").tag)
        assertEquals("zh-hans-cn", KLocale("zh", "CN", "Hans").tag)
        assertEquals("zh-hant-tw", KLocale("zh", "TW", "Hant").tag)
        assertEquals("", KLocale.Undefined.tag)
    }
}
