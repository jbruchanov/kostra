package com.jibru.kostra.plugin

import com.google.common.truth.Truth.assertThat
import com.jibru.kostra.KDpi
import com.jibru.kostra.KLocale
import com.jibru.kostra.KQualifiers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.File
import java.util.stream.Stream

class GroupQualifiersTest {
    @ParameterizedTest
    @ArgumentsSource(StrictGroupQualifiersArgsProvider::class)
    fun groupQualifiers(strict: Boolean?, path: String, qualifiers: GroupQualifiers) {
        if (strict == null) {
            assertThat(File(path).groupQualifiers(anyLocale = true)).isEqualTo(qualifiers)
            assertThat(File(path).groupQualifiers(anyLocale = false)).isEqualTo(qualifiers)
        } else {
            assertThat(File(path).groupQualifiers(anyLocale = !strict)).isEqualTo(qualifiers)
        }
    }

    @Test
    fun groupQualifiersInvalid() {
        assertThrows<IllegalArgumentException> { File("group-abcdefg").groupQualifiers(anyLocale = true) }
    }

    @Test
    fun groupQualifiers() {
        val list = listOf(
            f() to KQualifiers.Undefined,
            f("en") to KQualifiers(KLocale("en")),
            f("en-rUS") to KQualifiers(KLocale("en", "rUS")),
            f("xxhdpi") to KQualifiers(dpi = KDpi.XXHDPI),
            f("en", "xhdpi") to KQualifiers(locale = KLocale("en"), dpi = KDpi.XHDPI),
            f("en-rGB", "xxhdpi") to KQualifiers(locale = KLocale("en", "gb"), dpi = KDpi.XXHDPI),
            f("en-rGB", "xxhdpi", "land") to KQualifiers(locale = KLocale("en", "gb"), dpi = KDpi.XXHDPI),
            f("land", "en") to KQualifiers(KLocale("en")),
            f("xxhdpi", "en", "xyz") to KQualifiers(locale = KLocale("en"), dpi = KDpi.XXHDPI),
            f("123", "tvdpi", "456", "en-rGB") to KQualifiers(locale = KLocale("en", "gb"), dpi = KDpi.TVDPI),
        )

        Assertions.assertAll(
            list.map { (file, expected) ->
                Executable {
                    val (group, qualifiers) = file.groupQualifiers()
                    Assertions.assertEquals(expected, qualifiers)
                    Assertions.assertEquals(if (qualifiers.dpi != KDpi.Undefined) "drawable" else "value", group)
                }
            },
        )
    }

    @Test
    fun `groupQualifiers WHEN multiple same groups`() {
        assertThat(f("en", "cs").groupQualifiers().qualifiers).isEqualTo(KQualifiers("en"))
        assertThat(f("xhdpi", "xhdpi").groupQualifiers().qualifiers).isEqualTo(KQualifiers(dpi = KDpi.XHDPI))
        assertThat(f("en", "xxhdpi", "cs", "xhdpi").groupQualifiers().qualifiers).isEqualTo(KQualifiers("en", dpi = KDpi.XXHDPI))
    }

    @Test
    fun `groupQualifiers WHEN script in dash format`() {
        // zh-Hans and zh-Hant should be distinct
        val zhHans = File("group-zh-Hans").groupQualifiers()
        val zhHant = File("group-zh-Hant").groupQualifiers()
        assertThat(zhHans.qualifiers).isEqualTo(KQualifiers(KLocale("zh", null, "Hans")))
        assertThat(zhHant.qualifiers).isEqualTo(KQualifiers(KLocale("zh", null, "Hant")))
        assertThat(zhHans.qualifiers).isNotEqualTo(zhHant.qualifiers)

        // Script + region
        assertThat(File("group-zh-Hans-rCN").groupQualifiers().qualifiers)
            .isEqualTo(KQualifiers(KLocale("zh", "CN", "Hans")))
        assertThat(File("group-zh-Hant-rTW").groupQualifiers().qualifiers)
            .isEqualTo(KQualifiers(KLocale("zh", "TW", "Hant")))

        // Script + DPI
        assertThat(File("group-zh-Hans-xxhdpi").groupQualifiers().qualifiers)
            .isEqualTo(KQualifiers(KLocale("zh", null, "Hans"), KDpi.XXHDPI))

        // Script + region + DPI
        assertThat(File("group-zh-Hans-rCN-xxhdpi").groupQualifiers().qualifiers)
            .isEqualTo(KQualifiers(KLocale("zh", "CN", "Hans"), KDpi.XXHDPI))
    }

    @Test
    fun `groupQualifiers WHEN BCP 47 format`() {
        // b+lang+script
        assertThat(File("group-b+zh+Hant").groupQualifiers().qualifiers)
            .isEqualTo(KQualifiers(KLocale("zh", null, "Hant")))
        assertThat(File("group-b+zh+Hans").groupQualifiers().qualifiers)
            .isEqualTo(KQualifiers(KLocale("zh", null, "Hans")))

        // b+lang+script+region
        assertThat(File("group-b+zh+Hant+TW").groupQualifiers().qualifiers)
            .isEqualTo(KQualifiers(KLocale("zh", "TW", "Hant")))

        // b+lang+region (no script)
        assertThat(File("group-b+en+US").groupQualifiers().qualifiers)
            .isEqualTo(KQualifiers(KLocale("en", "US")))

        // b+lang+script + DPI
        assertThat(File("group-b+zh+Hans-xxhdpi").groupQualifiers().qualifiers)
            .isEqualTo(KQualifiers(KLocale("zh", null, "Hans"), KDpi.XXHDPI))

        // zh-Hans ≠ zh-Hant via BCP 47
        assertThat(File("group-b+zh+Hans").groupQualifiers().qualifiers)
            .isNotEqualTo(File("group-b+zh+Hant").groupQualifiers().qualifiers)

        // BCP 47 works same in strict and anyLocale modes
        assertThat(File("group-b+zh+Hant").groupQualifiers(anyLocale = true).qualifiers)
            .isEqualTo(File("group-b+zh+Hant").groupQualifiers(anyLocale = false).qualifiers)
    }

    @Test
    fun `groupQualifiers WHEN spaces THEN ignored`() {
        assertThat(File("value - xxhdpi - en").groupQualifiers().qualifiers).isEqualTo(KQualifiers.Undefined)
        assertThat(File("value -xxhdpi -en ").groupQualifiers().qualifiers).isEqualTo(KQualifiers.Undefined)
    }

    private fun f(vararg qualifiers: String): File {
        val value = qualifiers.joinToString(prefix = "-", separator = "-")
        val group = if (qualifiers.any { it.endsWith("dpi") }) "drawable" else "value"
        return File("$group$value")
    }
}

private class StrictGroupQualifiersArgsProvider : ArgumentsProvider {
    override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
        return Stream.of(
            //strict, path, qualifiers
            //locale
            Arguments.of(null, "group", GroupQualifiers("group", KQualifiers.Undefined)),
            Arguments.of(null, "GROUP", GroupQualifiers("group", KQualifiers.Undefined)),
            Arguments.of(null, "grOUp", GroupQualifiers("group", KQualifiers.Undefined)),
            Arguments.of(null, "group1-en", GroupQualifiers("group1", KQualifiers("en"))),
            Arguments.of(null, "group2-en-rGB", GroupQualifiers("group2", KQualifiers("enGB"))),
            Arguments.of(null, "group-en-rUS", GroupQualifiers("group", KQualifiers("enUS"))),
            //dpi
            Arguments.of(null, "tst", GroupQualifiers("tst", KQualifiers(dpi = KDpi.Undefined))),
            Arguments.of(null, "tst-nodpi", GroupQualifiers("tst", KQualifiers(dpi = KDpi.NoDpi))),
            Arguments.of(null, "tst-LDPI", GroupQualifiers("tst", KQualifiers(dpi = KDpi.LDPI))),
            Arguments.of(null, "tst-MDPI", GroupQualifiers("tst", KQualifiers(dpi = KDpi.MDPI))),
            Arguments.of(null, "tst-HDPI", GroupQualifiers("tst", KQualifiers(dpi = KDpi.HDPI))),
            Arguments.of(null, "tst-XHDPI", GroupQualifiers("tst", KQualifiers(dpi = KDpi.XHDPI))),
            Arguments.of(null, "tst-XXHDPI", GroupQualifiers("tst", KQualifiers(dpi = KDpi.XXHDPI))),
            Arguments.of(null, "tst-XXXHDPI", GroupQualifiers("tst", KQualifiers(dpi = KDpi.XXXHDPI))),
            Arguments.of(null, "tst-TVDPI", GroupQualifiers("tst", KQualifiers(dpi = KDpi.TVDPI))),
            //mixed
            Arguments.of(null, "group1-en-xxhdpi", GroupQualifiers("group1", KQualifiers("en", KDpi.XXHDPI))),
            Arguments.of(null, "group2-xhdpi-en-rGB", GroupQualifiers("group2", KQualifiers("enGB", KDpi.XHDPI))),
            //script (dash format)
            Arguments.of(null, "group-zh-Hans", GroupQualifiers("group", KQualifiers(KLocale("zh", null, "Hans")))),
            Arguments.of(null, "group-zh-Hant", GroupQualifiers("group", KQualifiers(KLocale("zh", null, "Hant")))),
            Arguments.of(null, "group-zh-Hans-rCN", GroupQualifiers("group", KQualifiers(KLocale("zh", "CN", "Hans")))),
            //BCP 47 format
            Arguments.of(null, "group-b+zh+Hant", GroupQualifiers("group", KQualifiers(KLocale("zh", null, "Hant")))),
            Arguments.of(null, "group-b+zh+Hant+TW", GroupQualifiers("group", KQualifiers(KLocale("zh", "TW", "Hant")))),
            Arguments.of(null, "group-b+en+US", GroupQualifiers("group", KQualifiers(KLocale("en", "US")))),
            Arguments.of(null, "group1-b+zh+Hans-xxhdpi", GroupQualifiers("group1", KQualifiers(KLocale("zh", null, "Hans"), KDpi.XXHDPI))),
            //somehow invalid
            Arguments.of(true, "group-en-xhdpi-rUS", GroupQualifiers("group", KQualifiers("en", KDpi.XHDPI))),
            Arguments.of(true, "group-ab-vvhdpi", GroupQualifiers("group", KQualifiers.Undefined)),
            Arguments.of(true, "group-ab", GroupQualifiers("group", KQualifiers.Undefined)),
            Arguments.of(true, "group-abc", GroupQualifiers("group", KQualifiers.Undefined)),
            Arguments.of(true, "group-abcd", GroupQualifiers("group", KQualifiers.Undefined)),
            Arguments.of(true, "group-ab-cd", GroupQualifiers("group", KQualifiers.Undefined)),
            Arguments.of(true, "group-ab-rCD", GroupQualifiers("group", KQualifiers.Undefined)),
            //allowed any locale
            Arguments.of(false, "xyz", GroupQualifiers("xyz", KQualifiers.Undefined)),
            Arguments.of(false, "xyz-", GroupQualifiers("xyz", KQualifiers.Undefined)),
            Arguments.of(false, "xyz-a", GroupQualifiers("xyz", KQualifiers("a"))),
            Arguments.of(false, "xyz-ab", GroupQualifiers("xyz", KQualifiers("ab"))),
            Arguments.of(false, "xyz-abc", GroupQualifiers("xyz", KQualifiers("abc"))),
            Arguments.of(false, "xyz-abcd", GroupQualifiers("xyz", KQualifiers("abcd"))),
            Arguments.of(false, "xyz-ab-cd", GroupQualifiers("xyz", KQualifiers("abcd"))),
            Arguments.of(false, "xyz-ab-rCD", GroupQualifiers("xyz", KQualifiers("abcd"))),
            Arguments.of(false, "xyz-ab-rCDE", GroupQualifiers("xyz", KQualifiers("ab"))),
            Arguments.of(false, "group-tvdpi-ab-rCDE", GroupQualifiers("group", KQualifiers("ab", KDpi.TVDPI))),
            Arguments.of(false, "group-ab-rCDE-ldpi", GroupQualifiers("group", KQualifiers("ab", KDpi.LDPI))),
            Arguments.of(false, "group-xxxhdpi-abcd", GroupQualifiers("group", KQualifiers("abcd", KDpi.XXXHDPI))),
            Arguments.of(false, "group-ab-cd-hdpi", GroupQualifiers("group", KQualifiers("abcd", KDpi.HDPI))),
            Arguments.of(false, "group-ab-mdpi-cd", GroupQualifiers("group", KQualifiers("ab", KDpi.MDPI))),
        )
    }
}
