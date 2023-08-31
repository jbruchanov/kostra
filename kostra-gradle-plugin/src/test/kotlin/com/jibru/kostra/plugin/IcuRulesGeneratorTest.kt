package com.jibru.kostra.plugin

import com.google.common.truth.Truth.assertThat
import com.jibru.kostra.KLocale
import com.jibru.kostra.icu.PluralCategory
import com.jibru.kostra.plugin.ext.minify
import com.jibru.kostra.plugin.icu.IcuPluralsDownloader
import org.junit.jupiter.api.Test

class IcuRulesGeneratorTest {
    @Test
    fun loadPlurals() {
        val f = IcuRulesGenerator("com.test.icu", addLocaleComments = false)
            .generate(
                IcuPluralsDownloader.Result(
                    unicodeVersion = "test1",
                    cldrVersion = "test2",
                    data = mapOf(
                        KLocale("en") to mapOf(
                            PluralCategory.One to "i = 1 and v = 0 @integer 1",
                            PluralCategory.Other to
                                " @integer 0, 2~16, 100, 1000, 10000, 100000, 1000000, … @decimal 0.0~1.5, 10.0, 100.0, 1000.0, 10000.0, 100000.0, 1000000.0, …",
                        ),
                    ),
                ),
                type = IcuRulesGenerator.Type.Plurals,
            )
        assertThat(f.minify()).isEqualTo(
            """
            //
            // AutoGeneratedFile
            // UnicodeVersion:test1
            // CLDRVersion:test2
            //
            @file:Suppress("ktlint")
            package com.test.icu
            import com.jibru.kostra.KLocale
            import com.jibru.kostra.icu.Constraint
            import com.jibru.kostra.icu.Operand
            import com.jibru.kostra.icu.PluralCategory
            import com.jibru.kostra.icu.PluralRules
            import com.jibru.kostra.icu.Rule
            import kotlin.Suppress
            import kotlin.collections.Map
            private val pluralSpecs_01: PluralRules by lazy(LazyThreadSafetyMode.NONE) { PluralRules(
              rules = listOf(
                Rule(category = PluralCategory.One, constraint = Constraint.And(
                    Constraint.Range(0, true, Operand.i, true, 1.0, 1.0),
                    Constraint.Range(0, true, Operand.v, true, 0.0, 0.0))),
              )
            )}

            val PluralRuleSpecs: Map<KLocale, PluralRules> = buildMap(1) {
              put(KLocale(5_14_00_00), pluralSpecs_01)
            }
            """.trimIndent(),
        )
    }
}
