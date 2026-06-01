package com.jibru.kostra.plugin.task

import com.google.common.truth.Truth.assertThat
import com.jibru.kostra.KLocale
import com.jibru.kostra.KQualifiers
import com.jibru.kostra.icu.PluralCategory
import com.jibru.kostra.icu.PluralCategory.Companion.toPluralList
import com.jibru.kostra.plugin.ResItem
import com.jibru.kostra.plugin.ResItemsProcessor
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Integration coverage that wires real [ResItem]s through [ResItemsProcessor] and feeds the
 * processor's outputs into [validateStrictModeCoverage]. This catches data-shape mismatches
 * between the processor and the validator that pure-function tests miss.
 */
internal class StrictModeIntegrationTest {

    // region: strings

    @Test
    fun `strings - base language complete and region partial passes`() {
        val items = listOf(
            ResItem.StringRes("a", "a", KQualifiers.Undefined.key),
            ResItem.StringRes("b", "b", KQualifiers.Undefined.key),
            ResItem.StringRes("a", "a-en", KQualifiers(KLocale("en")).key),
            ResItem.StringRes("b", "b-en", KQualifiers(KLocale("en")).key),
            // en-GB overrides only "a" — partial variant, allowed.
            ResItem.StringRes("a", "a-en-gb", KQualifiers(KLocale("en", "GB")).key),
        )
        validateViaProcessorForStrings(items)
    }

    @Test
    fun `strings - missing key in base language fails`() {
        val items = listOf(
            ResItem.StringRes("a", "a", KQualifiers.Undefined.key),
            ResItem.StringRes("b", "b", KQualifiers.Undefined.key),
            // en has only "a" — missing "b"; en-GB overrides "a"
            ResItem.StringRes("a", "a-en", KQualifiers(KLocale("en")).key),
            ResItem.StringRes("a", "a-en-gb", KQualifiers(KLocale("en", "GB")).key),
        )
        val ex = assertThrows(IllegalStateException::class.java) { validateViaProcessorForStrings(items) }
        assertThat(ex.message).contains("language 'en' is missing string keys: 'b'")
    }

    @Test
    fun `strings - only region variants without a bare base language is valid`() {
        val items = listOf(
            ResItem.StringRes("a", "a", KQualifiers.Undefined.key),
            // No KLocale("en") at all, just en-GB and en-US — valid: partial overrides on the
            // complete default, mirroring Android's region -> language -> default fallback.
            ResItem.StringRes("a", "a-en-gb", KQualifiers(KLocale("en", "GB")).key),
            ResItem.StringRes("a", "a-en-us", KQualifiers(KLocale("en", "US")).key),
        )
        // Must NOT throw.
        validateViaProcessorForStrings(items)
    }

    @Test
    fun `strings - multiple languages each validated independently`() {
        val items = listOf(
            ResItem.StringRes("a", "a", KQualifiers.Undefined.key),
            ResItem.StringRes("b", "b", KQualifiers.Undefined.key),
            // en is complete
            ResItem.StringRes("a", "a-en", KQualifiers(KLocale("en")).key),
            ResItem.StringRes("b", "b-en", KQualifiers(KLocale("en")).key),
            // cs is missing "b"
            ResItem.StringRes("a", "a-cs", KQualifiers(KLocale("cs")).key),
            // de is missing both
        )
        val ex = assertThrows(IllegalStateException::class.java) { validateViaProcessorForStrings(items) }
        val message = ex.message.orEmpty()
        assertThat(message).contains("language 'cs' is missing string keys: 'b'")
        assertThat(message).doesNotContain("language 'en' is missing")
    }

    // endregion

    // region: plurals

    @Test
    fun `plurals - base language complete and region partial passes`() {
        val items = listOf(
            ResItem.Plurals("apples", mapOf(PluralCategory.One to "1 apple", PluralCategory.Other to "x apples").toPluralList(), KQualifiers.Undefined.key),
            ResItem.Plurals("apples", mapOf(PluralCategory.One to "1 apple-en", PluralCategory.Other to "x apples-en").toPluralList(), KQualifiers(KLocale("en")).key),
            // en-GB only overrides — partial variant, allowed.
            ResItem.Plurals("apples", mapOf(PluralCategory.Other to "x apples-gb").toPluralList(), KQualifiers(KLocale("en", "GB")).key),
        )
        validateViaProcessorForPlurals(items)
    }

    @Test
    fun `plurals - missing key in base language fails`() {
        val items = listOf(
            ResItem.Plurals("apples", mapOf(PluralCategory.One to "1 apple", PluralCategory.Other to "x apples").toPluralList(), KQualifiers.Undefined.key),
            ResItem.Plurals("days", mapOf(PluralCategory.One to "1 day", PluralCategory.Other to "x days").toPluralList(), KQualifiers.Undefined.key),
            // en has 'apples' but not 'days'
            ResItem.Plurals("apples", mapOf(PluralCategory.One to "1 apple-en", PluralCategory.Other to "x apples-en").toPluralList(), KQualifiers(KLocale("en")).key),
            // en-GB overrides 'apples' only
            ResItem.Plurals("apples", mapOf(PluralCategory.Other to "x apples-gb").toPluralList(), KQualifiers(KLocale("en", "GB")).key),
        )
        val ex = assertThrows(IllegalStateException::class.java) { validateViaProcessorForPlurals(items) }
        assertThat(ex.message).contains("language 'en' is missing plural keys: 'days'")
    }

    @Test
    fun `plurals - only region variant without a bare base language is valid`() {
        val items = listOf(
            ResItem.Plurals("apples", mapOf(PluralCategory.One to "1 apple", PluralCategory.Other to "x apples").toPluralList(), KQualifiers.Undefined.key),
            // No base "en", just en-GB — valid: partial override on the complete default.
            ResItem.Plurals("apples", mapOf(PluralCategory.Other to "x apples-gb").toPluralList(), KQualifiers(KLocale("en", "GB")).key),
        )
        // Must NOT throw.
        validateViaProcessorForPlurals(items)
    }

    // endregion

    private fun validateViaProcessorForStrings(items: List<ResItem>) {
        val processor = ResItemsProcessor(items)
        validateStrictModeCoverage(
            type = "string",
            keys = processor.stringsDistinctKeys,
            valuePresentByLocale = processor.stringsForDbs
                .mapValues { (_, values) -> values.map { it != null } },
        )
    }

    private fun validateViaProcessorForPlurals(items: List<ResItem>) {
        val processor = ResItemsProcessor(items)
        val perLocale = processor.pluralsPerLocale ?: return
        validateStrictModeCoverage(
            type = "plural",
            keys = processor.pluralsDistinctKeys,
            valuePresentByLocale = perLocale
                .mapValues { (_, items) -> items.map { (_, categoryItems) -> categoryItems.any { it != null } } },
        )
    }
}
