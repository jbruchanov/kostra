package com.jibru.kostra.plugin

import com.google.common.truth.Truth.assertThat
import com.jibru.kostra.plugin.ext.minify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import test.RealProjectRef
import test.testResources

class ResourcesKtGeneratorResourcesTest {

    @Test
    fun allResourceFiles() = testResources {
        addStrings(
            "values/strings.xml",
            strings = mapOf("item1" to "src1Item1", "item2" to "src2Item2"),
            plurals = mapOf("dog" to mapOf("other" to "dogs", "one" to "dog \"%s\"")),
        )
        addStrings("values-en/strings.xml", strings = mapOf("item1" to "src1Item1En", "item2" to "src1Item2En"))
        addStrings("values-de/strings.xml", strings = mapOf("item1" to "src2Item1De", "item2" to "src2Item2De"))
        addFile("drawable/image.png")
        addFile("drawable-en-xxhdpi/image.png")
        addFile("audio/sound.mp3")
        addFile("audio-en-rGB/sound.mp3")
        addFile("audio-de-fun/sound.mp3")
        addFile("audio-cs/sound.mp4")

        buildResources()

        val items = FileResolver().resolve(resourcesRoot)
        val gen = ResourcesKtGenerator(items, className = "com.sample.app.K")

        val result = buildString {
            val files = listOf(
                gen.generateKClass(),
                gen.generateResources(),
            )

            val div = "-".repeat(16)
            files
                .filterNotNull()
                .forEach {
                    appendLine("$div ${it.packageName}.${it.name} $div")
                    appendLine(it.minify())
                }
        }

        assertThat(result.trim()).isEqualTo(
            """
            ---------------- com.sample.app.K ----------------
            @file:Suppress("ktlint")
            package com.sample.app
            import kotlin.Suppress
            import com.sample.app.BinaryResourceKey as B
            import com.sample.app.PainterResourceKey as D
            import com.sample.app.PluralResourceKey as P
            import com.sample.app.StringResourceKey as S
            object K {
              object string {
                val item1: S = S(0)
                val item2: S = S(1)
              }
              object plural {
                val dog: P = P(0)
              }
              object audio {
                val sound: B = B(1)
              }
              object drawable {
                val image: D = D(2)
              }
            }
            ---------------- com.sample.app.Resources ----------------
            @file:Suppress("ktlint")
            package com.sample.app
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
                  KLocale(1_182_019_911_939L) to "string-de.db",
                  KLocale(1_558_592_627_247L) to "string-en.db",
                )
              ),
              plural = PluralDatabase(
                mapOf(
                  KLocale.Undefined to "plural-default.db",
                )
              ),
              binary = FileDatabase("binary.db"),
            )
            """.trimIndent(),
        )
    }

    @Test
    fun testImages() = testResources {
        addFile("flagxml/flag.png")
        addFile("flagsvg/flag.svg")
        buildResources()

        val items = FileResolver().resolve(resourcesRoot)
        val gen = ResourcesKtGenerator(items, resDbsFolderName = "com.sample.app.K", useAliasImports = false)
        val result = gen.generateKClass().minify()

        assertThat(result.trim()).isEqualTo(
            """
                @file:Suppress("ktlint")
                package app
                import kotlin.Suppress
                object K {
                  object flagsvg {
                    val flag: PainterResourceKey = PainterResourceKey(1)
                  }
                  object flagxml {
                    val flag: PainterResourceKey = PainterResourceKey(2)
                  }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `generateKClass WHEN interfaces = true`() = testResources {
        addStrings(
            "values/strings.xml",
            strings = mapOf("item" to "src1Item1"),
            plurals = mapOf("dog" to mapOf("other" to "dogs")),
        )
        addFile("drawable/image.png")
        addFile("binary/test.bin")

        buildResources()

        val items = FileResolver().resolve(resourcesRoot)
        val gen = ResourcesKtGenerator(items, useAliasImports = true)
        val result = gen.generateKClass(interfaces = true).minify()
        assertThat(result).isEqualTo(
            """
            @file:Suppress("ktlint")
            package app
            import kotlin.Suppress
            import app.BinaryResourceKey as B
            import app.PainterResourceKey as D
            import app.PluralResourceKey as P
            import app.StringResourceKey as S
            object K {
              object string : IK.string {
                override val item: S = S(0)
              }
              object plural : IK.plural {
                override val dog: P = P(0)
              }
              object binary : IK.binary {
                override val test: B = B(1)
              }
              object drawable : IK.drawable {
                override val image: D = D(2)
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun generateIfaces() = testResources {
        addStrings(
            "values/strings.xml",
            strings = mapOf("item" to "src1Item1"),
            plurals = mapOf("dog" to mapOf("other" to "dogs")),
        )
        addFile("drawable/image.png")
        addFile("binary/test.bin")

        buildResources()

        val items = FileResolver().resolve(resourcesRoot)
        val gen = ResourcesKtGenerator(items, useAliasImports = true)
        val result = gen.generateIfaces().minify()
        assertThat(result).isEqualTo(
            """
            @file:Suppress("ktlint")
            package app
            import kotlin.Suppress
            import app.BinaryResourceKey as B
            import app.PainterResourceKey as D
            import app.PluralResourceKey as P
            import app.StringResourceKey as S
            interface IK {
              interface string {
                val item: S
              }
              interface plural {
                val dog: P
              }
              interface binary {
                val test: B
              }
              interface drawable {
                val image: D
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `generateIfaces WHEN different kClass`() = testResources {
        val gen = ResourcesKtGenerator(emptyList(), className = "app.Res")
        val result = gen.generateIfaces().minify()
        assertThat(result).contains("interface IRes")
    }

    @Test
    fun `generateIfaces WHEN internal`() = testResources {
        val gen = ResourcesKtGenerator(emptyList(), className = "app.Res", internalVisibility = true)
        val result = gen.generateIfaces().minify()
        assertThat(result).contains("internal interface IRes")
    }

    @Test
    fun `generateKClass WHEN different kClass`() = testResources {
        addStrings(
            "values/strings.xml",
            strings = mapOf("item" to "src1Item1"),
        )
        buildResources()
        val items = FileResolver().resolve(resourcesRoot)
        val gen = ResourcesKtGenerator(items, className = "app.Res")
        val result = gen.generateKClass(interfaces = true).minify()
        assertThat(result).contains("object string : IRes.string {")
    }

    @Test
    @EnabledIf("hasRealProjectLocation")
    fun testRealProject() {
        val items = FileResolver().resolve(RealProjectRef.resources()!!)
        val gen = ResourcesKtGenerator(items, resDbsFolderName = "com.sample.app.K")

        val result = buildString {
            val files = listOf(
                gen.generateKClass(),
                gen.generateResources(),
            )

            val div = "-".repeat(16)
            files
                .forEach {
                    appendLine("$div ${it.packageName}.${it.name} $div")
                    appendLine(it.minify())
                }
        }
        println(result)
    }

    private fun hasRealProjectLocation() = RealProjectRef.resources() != null
}
