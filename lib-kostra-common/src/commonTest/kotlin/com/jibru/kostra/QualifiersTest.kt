package com.jibru.kostra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualifiersTest {

    @Test
    fun pack() {
        assertEquals(KLocale("cs"), KQualifiers("cs").locale)
        assertEquals(KLocale("cs", "cz"), KQualifiers("cs-CZ").locale)
        assertEquals(KLocale("cs"), KQualifiers("cs", KDpi.XXHDPI).locale)
        assertEquals(KLocale("cs", "cz"), KQualifiers("cs-CZ", KDpi.NoDpi).locale)

        assertEquals(KDpi.Undefined, KQualifiers("cs").dpi)
        assertEquals(KDpi.Undefined, KQualifiers("cs-CZ").dpi)
        assertEquals(KDpi.XXHDPI, KQualifiers("cs", KDpi.XXHDPI).dpi)
        assertEquals(KDpi.NoDpi, KQualifiers("cs-CZ", KDpi.NoDpi).dpi)
    }

    @Test
    fun hasOnlyLocale() {
        assertTrue(KQualifiers("en").hasOnlyLocale)
        assertTrue(KQualifiers("en-GB").hasOnlyLocale)
        assertFalse(KQualifiers("en-GB", KDpi.XXHDPI).hasOnlyLocale)
        KDpi.values().forEach {
            val expected = it == KDpi.Undefined
            assertEquals(expected, KQualifiers("en", it).hasOnlyLocale)
        }
    }

    @Test
    fun withLocaleNoRegion() {
        assertEquals(KQualifiers("en", dpi = KDpi.XXHDPI), KQualifiers("en-GB", dpi = KDpi.XXHDPI).withLocaleNoRegion())
        assertEquals(KQualifiers("en", dpi = KDpi.XXHDPI), KQualifiers("en", dpi = KDpi.XXHDPI).withLocaleNoRegion())
        assertEquals(KQualifiers(KLocale.Undefined, dpi = KDpi.XXHDPI), KQualifiers(KLocale.Undefined, dpi = KDpi.XXHDPI).withLocaleNoRegion())

        assertEquals(KQualifiers("en", dpi = KDpi.Undefined), KQualifiers("en-GB", dpi = KDpi.Undefined).withLocaleNoRegion())
        assertEquals(KQualifiers("en", dpi = KDpi.Undefined), KQualifiers("en", dpi = KDpi.Undefined).withLocaleNoRegion())
        assertEquals(KQualifiers(KLocale.Undefined, dpi = KDpi.Undefined), KQualifiers(KLocale.Undefined, dpi = KDpi.Undefined).withLocaleNoRegion())
    }

    @Test
    fun withLocaleLanguageOnly() {
        assertEquals(KQualifiers("en", dpi = KDpi.XXHDPI), KQualifiers("en-GB", dpi = KDpi.XXHDPI).withLocaleLanguageOnly())
        assertEquals(
            KQualifiers(KLocale("zh"), dpi = KDpi.Undefined),
            KQualifiers(KLocale("zh", null, "Hans"), dpi = KDpi.Undefined).withLocaleLanguageOnly(),
        )
    }

    @Test
    fun withNoLocale() {
        assertEquals(KQualifiers(dpi = KDpi.XXHDPI), KQualifiers("en-GB", dpi = KDpi.XXHDPI).withNoLocale())
        assertEquals(KQualifiers(dpi = KDpi.XXHDPI), KQualifiers("en", dpi = KDpi.XXHDPI).withNoLocale())
        assertEquals(KQualifiers(dpi = KDpi.XXHDPI), KQualifiers(KLocale.Undefined, dpi = KDpi.XXHDPI).withNoLocale())

        assertEquals(KQualifiers(dpi = KDpi.Undefined), KQualifiers("en-GB", dpi = KDpi.Undefined).withNoLocale())
        assertEquals(KQualifiers(dpi = KDpi.Undefined), KQualifiers("en", dpi = KDpi.Undefined).withNoLocale())
        assertEquals(KQualifiers(dpi = KDpi.Undefined), KQualifiers(KLocale.Undefined, dpi = KDpi.Undefined).withNoLocale())
    }

    @Test
    fun withNoDpi() {
        assertEquals(KQualifiers("en-GB", dpi = KDpi.Undefined), KQualifiers("en-GB", dpi = KDpi.XXHDPI).withNoDpi())
        assertEquals(KQualifiers("en", dpi = KDpi.Undefined), KQualifiers("en", dpi = KDpi.XXHDPI).withNoDpi())
        assertEquals(KQualifiers(KLocale.Undefined, dpi = KDpi.Undefined), KQualifiers(KLocale.Undefined, dpi = KDpi.XXHDPI).withNoDpi())

        assertEquals(KQualifiers("en-GB", dpi = KDpi.Undefined), KQualifiers("en-GB", dpi = KDpi.Undefined).withNoDpi())
        assertEquals(KQualifiers("en", dpi = KDpi.Undefined), KQualifiers("en", dpi = KDpi.Undefined).withNoDpi())
        assertEquals(KQualifiers(KLocale.Undefined, dpi = KDpi.Undefined), KQualifiers(KLocale.Undefined, dpi = KDpi.Undefined).withNoDpi())
    }
}
