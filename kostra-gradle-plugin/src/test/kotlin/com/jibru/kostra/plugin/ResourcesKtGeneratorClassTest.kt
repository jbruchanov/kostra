package com.jibru.kostra.plugin

import com.google.common.truth.Truth.assertThat
import com.jibru.kostra.KLocale
import com.jibru.kostra.KQualifiers
import com.jibru.kostra.icu.PluralCategory
import com.jibru.kostra.icu.PluralCategory.Companion.toPluralList
import com.jibru.kostra.plugin.ext.minify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import test.RealProjectRef
import java.io.File

class ResourcesKtGeneratorClassTest {

    @Test
    fun generateKClass() {
        val result = ResourcesKtGenerator(
            listOf(
                string("str1"),
                string("2str"),
                string("str1"),
                plurals("p2", mapOf(PluralCategory.Other to "other").toPluralList()),
                plurals("p1", mapOf(PluralCategory.One to "one").toPluralList()),
                file("img", group = "painter"),
                file("icon2", group = "painter"),
                file("audio1", group = "raw"),
                file("test", group = "_"),
            ),
            className = "com.sample.app.K",
        ).generateKClass().minify()

        assertThat(result).isEqualTo(
            """
            @file:Suppress("ktlint")
            package com.sample.app
            import kotlin.Suppress
            import com.sample.app.BinaryResourceKey as B
            import com.sample.app.PluralResourceKey as P
            import com.sample.app.StringResourceKey as S
            object K {
              object string {
                val `2str`: S = S(0)
                val str1: S = S(1)
              }
              object plural {
                val p1: P = P(0)
                val p2: P = P(1)
              }
              object `_` {
                val test: B = B(1)
              }
              object painter {
                val icon2: B = B(2)
                val img: B = B(3)
              }
              object raw {
                val audio1: B = B(4)
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `generateKClass WHEN internal visibility`() {
        val result = ResourcesKtGenerator(emptyList(), internalVisibility = true).generateKClass().minify()
        assertThat(result).contains("internal object K")
    }

    @Test
    fun `generateResources WHEN internal visibility`() {
        val result = ResourcesKtGenerator(emptyList(), internalVisibility = true).generateResources().minify()
        assertThat(result).contains("internal val Resources")
    }

    @Test
    fun `generateKClass no package`() {
        val gen = ResourcesKtGenerator(items = listOf(string("str1")), className = "ResQ")
        val result = gen.generateKClass().minify()
        assertThat(result).isEqualTo(
            """
            @file:Suppress("ktlint")
            import kotlin.Suppress
            import StringResourceKey as S
            object ResQ {
              object string {
                val str1: S = S(0)
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `generateKClass WHEN drawable with unexpected extension`() {
        val gen = ResourcesKtGenerator(
            items = listOf(
                ResItem.FileRes("imagePng", File("drawable/imagePng.png"), KQualifiers.Undefined.key, group = "painter", root = File(".")),
                ResItem.FileRes("imageBin", File("drawable/imageBin.bin"), KQualifiers.Undefined.key, group = "painter", root = File(".")),
            ),
            className = "K",
        )
        val result = gen.generateKClass().minify()
        assertThat(result).isEqualTo(
            """
            @file:Suppress("ktlint")
            import kotlin.Suppress
            import BinaryResourceKey as B
            import PainterResourceKey as D
            object K {
              object painter {
                val imageBin: B = B(1)
                val imagePng: D = D(2)
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `generateKClass WHEN multiple strings and plurals`() {
        val items = listOf(
            ResItem.StringRes("item1", "item1", KQualifiers.Undefined.key),
            ResItem.StringRes("item2", "item2", KQualifiers.Undefined.key),
            ResItem.StringRes("item1", "item1Cs", KQualifiers(locale = KLocale("cs")).key),
            ResItem.StringRes("item2", "item2En", KQualifiers(locale = KLocale("en")).key),
            ResItem.Plurals("dog", ResItem.Plurals.EmptyItems, KQualifiers.Undefined.key),
            ResItem.Plurals("dog", ResItem.Plurals.EmptyItems, KQualifiers(locale = KLocale("cs")).key),
        )

        val result = ResourcesKtGenerator(items = items, className = "app.K").generateKClass().minify()
        assertThat(result).isEqualTo(
            """
            @file:Suppress("ktlint")
            package app
            import kotlin.Suppress
            import app.PluralResourceKey as P
            import app.StringResourceKey as S
            object K {
              object string {
                val item1: S = S(0)
                val item2: S = S(1)
              }
              object plural {
                val dog: P = P(0)
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun generateResources() {
        val items = listOf(
            ResItem.StringRes("item1", "item1", KQualifiers.Undefined.key),
            ResItem.StringRes("item2", "item2", KQualifiers.Undefined.key),
            ResItem.StringRes("item1", "item1Cs", KQualifiers(locale = KLocale("cs")).key),
            ResItem.StringRes("item2", "item2En", KQualifiers(locale = KLocale("en")).key),
            ResItem.StringRes("item2", "item2EnGb", KQualifiers(locale = KLocale("en-GB")).key),
            ResItem.Plurals("dog", ResItem.Plurals.EmptyItems, KQualifiers.Undefined.key),
            ResItem.Plurals("dog", ResItem.Plurals.EmptyItems, KQualifiers(locale = KLocale("cs")).key),
            ResItem.FileRes("image", File("x.png"), KQualifiers.Undefined.key, "painter", File("x")),
            ResItem.FileRes("sound", File("s.mp3"), KQualifiers.Undefined.key, "sound", File("x")),
        )

        val result = ResourcesKtGenerator(items = items, className = "K").generateResources().minify()
        assertThat(result).isEqualTo(
            """
            @file:Suppress("ktlint")
            import com.jibru.kostra.KAppResources
            import com.jibru.kostra.KLocale
            import com.jibru.kostra.`internal`.FileDatabase
            import com.jibru.kostra.`internal`.PluralDatabase
            import com.jibru.kostra.`internal`.StringDatabase
            import kotlin.Suppress
            val Resources: KAppResources = KAppResources(
              string = StringDatabase(
                mapOf(
                  KLocale.Undefined to "string-default.db",
                  KLocale(1_046_035_320_300L) to "string-cs.db",
                  KLocale(1_558_592_627_247L) to "string-en.db",
                  KLocale(1_558_694_132_478L) to "string-en-gb.db",
                )
              ),
              plural = PluralDatabase(
                mapOf(
                  KLocale.Undefined to "plural-default.db",
                  KLocale(1_046_035_320_300L) to "plural-cs.db",
                )
              ),
              binary = FileDatabase("binary.db"),
            )
            """.trimIndent(),
        )
    }

    @EnabledIf("hasRealProjectRef")
    @Test
    fun `generateKClassX`() {
        val items = FileResolver().resolve(RealProjectRef.resources()!!)
        val x = ResourcesKtGenerator(items = items, className = "K")
            .generateKClass().minify()
        println(x)
    }

    private fun hasRealProjectRef() = RealProjectRef.isDefined()

    private fun string(key: String) = ResItem.StringRes(key, value = "", qualifiersKey = KQualifiers.Undefined.key)

    private fun plurals(key: String, items: List<String?>) = ResItem.Plurals(key, items = items, qualifiersKey = KQualifiers.Undefined.key)

    private fun file(key: String, group: String) = ResItem.FileRes(key, File("X"), qualifiersKey = KQualifiers.Undefined.key, group = group, root = File("."))
}
