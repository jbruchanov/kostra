package com.jibru.kostra.plugin

import com.google.common.truth.Truth.assertThat
import com.jibru.kostra.plugin.ext.minify
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.support.ParameterDeclarations
import test.IOTestTools
import java.io.File
import java.util.stream.Stream

class DefaultsKtGeneratorTest : IOTestTools {

    private val separator = "\n----\n"

    @Test
    @Disabled("creates test files")
    @Suppress("UNCHECKED_CAST")
    fun createConfigs() {
        val configs = ComposeDefaultsArgsProvider.Items

        configs.forEach { args ->
            val array = args.get()
            val fileName = array[0] as String
            val defaults = array[1] as List<ResourcesDefaults>
            val internalVisibility = array[2] as Boolean
            val gen = DefaultsKtGenerator(kClassName = "com.sample.app.K", internalVisibility = internalVisibility)
            val results = gen.generateComposeDefaults(*defaults.toTypedArray())
            val f = testResourceFile(fileName)
            f.parentFile.mkdirs()
            f.writeText(results.joinToString(separator = separator) { it.toString() })
        }
    }

    @ParameterizedTest(name = "generateComposeDefaults internal:{2} defaults:{1}")
    @ArgumentsSource(ComposeDefaultsArgsProvider::class)
    fun generateComposeDefaults(fileName: String, defaults: List<ResourcesDefaults>, internalVisibility: Boolean) {
        val gen = DefaultsKtGenerator(kClassName = "com.sample.app.K", internalVisibility = internalVisibility)
        val results = gen.generateComposeDefaults(*defaults.toTypedArray())

        val result = results.joinToString(separator = separator) { it.toString() }
        assertThat(result).isEqualTo(testResourceFile(fileName).readTextLfOnly())
    }

    @Test
    fun `generateCommonComposeDefaults WHEN modulePrefix defined`() {
        val gen = DefaultsKtGenerator(kClassName = "com.sample.app.K", modulePrefix = "ModulePrefix")
        val results = gen.generateComposeDefaults(*ResourcesDefaults.values())
            .map { it.minify() }
            .map { it.trim().split("ModulePrefixResources").size }

        assertThat(results).containsExactly(18, 25)
    }

    //git is changing text files from LF to CRLF, kt poet is using simply "\n",
    //so committing test outputs will breaks the test due to LF vs CRLF
    private fun File.readTextLfOnly() = this.readText().replace("\r\n", "\n")
}

private class ComposeDefaultsArgsProvider : ArgumentsProvider {
    override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> {
        return Stream.of(*Items)
    }

    companion object {
        val Items = arrayOf(
            Arguments.of("defaults_compose_common_public.txt", listOf(ResourcesDefaults.ComposeCommon), false),
            Arguments.of("defaults_compose_getters_public.txt", listOf(ResourcesDefaults.ComposeGetters), false),
            Arguments.of("defaults_compose_all_public.txt", ResourcesDefaults.AllCompose, false),
            Arguments.of("defaults_compose_common_internal.txt", listOf(ResourcesDefaults.ComposeCommon), true),
            Arguments.of("defaults_compose_getters_internal.txt", listOf(ResourcesDefaults.ComposeGetters), true),
            Arguments.of("defaults_compose_all_internal.txt", ResourcesDefaults.AllCompose, true),
            Arguments.of("defaults_kt_common_public.txt", listOf(ResourcesDefaults.Common), false),
            Arguments.of("defaults_kt_getters_public.txt", listOf(ResourcesDefaults.Getters), false),
            Arguments.of("defaults_kt_all_public.txt", ResourcesDefaults.AllBasic, false),
            Arguments.of("defaults_kt_common_internal.txt", listOf(ResourcesDefaults.Common), true),
            Arguments.of("defaults_kt_getters_internal.txt", listOf(ResourcesDefaults.Getters), true),
            Arguments.of("defaults_kt_all_internal.txt", ResourcesDefaults.AllBasic, true),
            Arguments.of("defaults_all.txt", ResourcesDefaults.values().toList(), false),
            Arguments.of("defaults_all_internal.txt", ResourcesDefaults.values().toList(), true),
        )
    }
}
