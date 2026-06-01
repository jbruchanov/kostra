package com.jibru.kostra.plugin.task

import com.google.common.truth.Truth.assertThat
import com.jibru.kostra.KLocale
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class StrictModeCoverageTest {

    @Test
    fun `empty inputs are a no-op`() {
        validateStrictModeCoverage("string", keys = emptyList(), valuePresentByLocale = emptyMap())
        validateStrictModeCoverage("string", keys = listOf("a"), valuePresentByLocale = emptyMap())
        validateStrictModeCoverage("string", keys = null, valuePresentByLocale = mapOf(KLocale("en") to listOf(true)))
    }

    @Test
    fun `only default locale present is a no-op`() {
        // The undefined-locale check is the existing validateDefaultFallback's job.
        validateStrictModeCoverage(
            type = "string",
            keys = listOf("a", "b"),
            valuePresentByLocale = mapOf(KLocale.Undefined to listOf(true, false)),
        )
    }

    @Test
    fun `base language complete plus partial region variant passes`() {
        validateStrictModeCoverage(
            type = "string",
            keys = listOf("a", "b", "c"),
            valuePresentByLocale = mapOf(
                KLocale.Undefined to listOf(true, true, true),
                KLocale("en") to listOf(true, true, true),
                KLocale("en", "gb") to listOf(false, true, false),
                KLocale("en", "us") to listOf(true, false, false),
            ),
        )
    }

    @Test
    fun `missing key in base language is flagged`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            validateStrictModeCoverage(
                type = "string",
                keys = listOf("a", "b", "c"),
                valuePresentByLocale = mapOf(
                    KLocale("en") to listOf(true, false, true),
                    KLocale("en", "gb") to listOf(false, true, false),
                ),
            )
        }
        assertThat(ex.message).contains("language 'en' is missing string keys: 'b'")
    }

    @Test
    fun `region variants without a bare base language are valid`() {
        //Mirrors Android: `en-rGB` + `en-rUS` with no bare `en` is fine — the variants are
        //partial overrides layered on the complete default bucket. Must NOT throw.
        validateStrictModeCoverage(
            type = "string",
            keys = listOf("a", "b"),
            valuePresentByLocale = mapOf(
                KLocale.Undefined to listOf(true, true),
                KLocale("en", "gb") to listOf(true, true),
                KLocale("en", "us") to listOf(true, false),
            ),
        )
    }

    @Test
    fun `multiple base languages are each validated independently`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            validateStrictModeCoverage(
                type = "string",
                keys = listOf("a", "b"),
                valuePresentByLocale = mapOf(
                    KLocale("en") to listOf(true, true),
                    KLocale("en", "gb") to listOf(false, true),
                    KLocale("cs") to listOf(true, false),
                    KLocale("de") to listOf(false, false),
                ),
            )
        }
        // cs and de are each missing keys; en is complete so should not appear.
        val message = ex.message.orEmpty()
        assertThat(message).contains("'cs'")
        assertThat(message).contains("'de'")
        assertThat(message).doesNotContain("language 'en' is missing")
    }

    @Test
    fun `script variants without a bare base language are valid`() {
        //The canonical case: `zh-Hans` + `zh-Hant` with no bare `zh`. Valid — there is no
        //sensible single "Chinese" base, the scripts ARE the translations, and anything they
        //omit falls through to the complete default. Must NOT throw.
        validateStrictModeCoverage(
            type = "string",
            keys = listOf("a"),
            valuePresentByLocale = mapOf(
                KLocale.Undefined to listOf(true),
                KLocale("zh", null, "Hans") to listOf(true),
                KLocale("zh", null, "Hant") to listOf(true),
            ),
        )
    }

    @Test
    fun `script variant WITH an incomplete bare base is still flagged`() {
        //A bare `zh` file that DOES exist remains a declared full translation and must be
        //complete — the relaxation only covers the no-bare-base case.
        val ex = assertThrows(IllegalStateException::class.java) {
            validateStrictModeCoverage(
                type = "string",
                keys = listOf("a", "b"),
                valuePresentByLocale = mapOf(
                    KLocale.Undefined to listOf(true, true),
                    KLocale("zh") to listOf(true, false),
                    KLocale("zh", null, "Hant") to listOf(true, true),
                ),
            )
        }
        assertThat(ex.message).contains("language 'zh' is missing string keys: 'b'")
    }
}
