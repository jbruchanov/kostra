package com.jibru.kostra.plugin

import com.ibm.icu.text.PluralRules
import com.jibru.kostra.KLocale
import com.jibru.kostra.icu.PluralCategory
import com.jibru.kostra.plugin.ext.addDefaultSuppressAnnotation
import com.jibru.kostra.plugin.ext.formattedDbKey
import com.jibru.kostra.plugin.ext.minify
import com.jibru.kostra.plugin.icu.IcuPluralRules
import com.jibru.kostra.plugin.icu.IcuPluralsDownloader
import com.jibru.kostra.plugin.icu.toPluralRules
import com.jibru.kostra.plugin.icu.toProperty
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asTypeName
import java.io.File

/**
 * Entry point for `generateRuleSpecsForKostraCommonLib` gradle task
 */
fun main(args: Array<String>) {
    require(args.size == 1) { "Invalid args:${args.joinToString()}, must be size 1" }

    val icuPluralsDownloader = IcuPluralsDownloader()
    generateIcuRules(icuPluralsDownloader.loadPlurals(), File("${args[0]}/PluralRuleSpecs.kt"), IcuRulesGenerator.Type.Plurals)
    generateIcuRules(icuPluralsDownloader.loadOrdinals(), File("${args[0]}/OrdinalRuleSpecs.kt"), IcuRulesGenerator.Type.Ordinals)
}

private fun generateIcuRules(data: IcuPluralsDownloader.Result, outputFile: File, type: IcuRulesGenerator.Type) {
    val fileSpec = IcuRulesGenerator(
        KostraPluginConfig.PackageNameIcu,
        addLocaleComments = true,
        addDefaultValue = true,
    ).generate(data, type)

    outputFile.parentFile.mkdirs()

    val minify = true
    if (minify) {
        outputFile.writeText(fileSpec.minify())
    } else {
        outputFile.writeText(fileSpec.toString())
    }
}

class IcuRulesGenerator(
    private val fullPackageName: String,
    private val addLocaleComments: Boolean,
    private val addDefaultValue: Boolean,
) {

    private val alwaysOtherPluralRuleMember = MemberName("com.jibru.kostra.icu", "PluralRules.AlwaysOther")

    enum class Type(val fileName: String, val privatePropertyNameTemplate: String, val publicPropertyName: String) {
        Plurals("PluralRuleSpecs", "pluralSpecs_%s", "PluralRuleSpecs"),
        Ordinals("OrdinalRuleSpecs", "ordinalSpecs_%s", "OrdinalRuleSpecs"),
    }

    fun generate(data: IcuPluralsDownloader.Result, type: Type): FileSpec {
        return generate(
            data = data,
            fileName = type.fileName,
            privatePropertyTemplateName = type.privatePropertyNameTemplate,
            publicPropertyName = type.publicPropertyName,
        )
    }

    private fun generate(
        data: IcuPluralsDownloader.Result,
        fileName: String,
        privatePropertyTemplateName: String,
        publicPropertyName: String,
    ): FileSpec {
        val fb = FileSpec.builder(fullPackageName, fileName)
        fb.addFileComment("\n")
        fb.addFileComment("AutoGeneratedFile\n")
        fb.addFileComment("UnicodeVersion:${data.unicodeVersion}\n")
        fb.addFileComment("CLDRVersion:${data.cldrVersion}\n")
        fb.addDefaultSuppressAnnotation()

        val prc = data.data
            .map { it.key to it.value }
            .groupBy { it.second }
            .mapValues { it.value.map { v -> v.first } }
            .toList()
            .mapIndexed { index: Int, pair: Pair<Map<PluralCategory, String>, List<KLocale>> -> Triple(index + 1, pair.second, pair.first) }

        prc.forEach { (index, listOfLocales, records) ->
            val icu4jRules = PluralRules.createRules(records.map { "${it.key.keyword}: ${it.value}" }.joinToString(";"))
            val rules = IcuPluralRules(icu4jRules).toPluralRules()
            fb.addProperty(
                rules.toProperty(
                    name = privatePropertyTemplateName.format((index).toString().padStart(2, '0')),
                    comment = if (addLocaleComments && false/*not needed, another comment below*/) listOfLocales.joinToString { it.languageRegion } else null,
                ),
            )
        }

        val specsList = prc
            .map { (index, locales) -> locales.map { index to it } }
            .flatten()
            .sortedBy { it.second }

        fb.addProperty(
            PropertySpec.builder(publicPropertyName, Map::class.parameterizedBy(KLocale::class, com.jibru.kostra.icu.PluralRules::class))
                .initializer(
                    codeBlock = CodeBlock.builder()
                        .addStatement("buildMap(%L) {", specsList.size)
                        .indent()
                        .apply {
                            specsList.forEach { (index, locale) ->
                                val key = locale.formattedDbKey()
                                add("put(%T(%L)", KLocale::class.asTypeName(), key)
                                if (addLocaleComments) {
                                    add("/*%L*/", locale.languageRegion)
                                }
                                addStatement(", %L)", privatePropertyTemplateName.format((index).toString().padStart(2, '0')))
                            }
                        }
                        .unindent()
                        .add("}")
                        .apply {
                            if (addDefaultValue) {
                                addStatement(".withDefault { %M }", alwaysOtherPluralRuleMember)
                            } else {
                                add("\n")
                            }
                        }
                        .build(),
                )
                .build(),
        )

        return fb.build()
    }
}
