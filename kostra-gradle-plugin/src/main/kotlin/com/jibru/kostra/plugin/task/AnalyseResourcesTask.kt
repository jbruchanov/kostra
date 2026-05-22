package com.jibru.kostra.plugin.task

import com.jibru.kostra.KLocale
import com.jibru.kostra.plugin.KostraPluginConfig
import com.jibru.kostra.plugin.KostraPluginExtension
import com.jibru.kostra.plugin.ResItem
import com.jibru.kostra.plugin.ResItemsProcessor
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Resource analysis depends on absolute directory contents and is fast to recompute")
abstract class AnalyseResourcesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDirs: ListProperty<File>

    @get:Input
    @get:Optional
    abstract val strictMode: Property<Boolean>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = KostraPluginConfig.Tasks.Group
    }

    @TaskAction
    fun run() = with(TaskDelegate) {
        val extension = project.extensions.getByType(KostraPluginExtension::class.java)
        val items = analyseCode(
            resourceDirs = extension.allResourceDirs(),
            fileResolverConfig = extension.toFileResolverConfig(),
        )

        // Validate default/fallback database has all keys defined
        val processor = ResItemsProcessor(items)
        validateDefaultFallback("string", processor.stringsForDbs, processor.stringsDistinctKeys)
        validateDefaultPluralFallback(processor)

        if (strictMode.getOrElse(true)) {
            validateStrictModeStringCoverage(processor)
            validateStrictModePluralCoverage(processor)
        }

        val outputFile = outputFile.get().asFile
        outputFile.parentFile.mkdirs()
        ObjectOutputStream(FileOutputStream(outputFile)).use {
            it.writeObject(items)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun validateDefaultPluralFallback(processor: ResItemsProcessor) {
        val pluralData = processor.stringsAndPluralsForDb[ResItem.Plural]
            ?.let { it as? Map<KLocale, List<Pair<String, ResItem.Plurals?>>> }
            ?: return
        val defaults = pluralData[KLocale.Undefined] ?: run {
            val allKeys = processor.pluralsDistinctKeys ?: return
            throw IllegalStateException(
                "Default/fallback plural database is missing! All plural keys must have a default value defined in the base (non-qualified) resource file.\n" +
                    "Missing default for keys: ${allKeys.joinToString { "'$it'" }}",
            )
        }
        // Check only that each plural key is defined (not null), ignoring individual category nulls
        val missingKeys = defaults.filter { (_, item) -> item == null }.map { (key, _) -> key }
        if (missingKeys.isNotEmpty()) {
            throw IllegalStateException(
                "Default/fallback plural database is missing values for keys: ${missingKeys.joinToString { "'$it'" }}\n" +
                    "All plural keys must have a default value defined in the base (non-qualified) resource file.",
            )
        }
    }

    private fun validateDefaultFallback(type: String, data: Map<KLocale, List<String?>>, keys: List<String>?) {
        if (data.isEmpty() || keys.isNullOrEmpty()) return
        val defaults = data[KLocale.Undefined]
        if (defaults == null) {
            throw IllegalStateException(
                "Default/fallback $type database is missing! All $type keys must have a default value defined in the base (non-qualified) resource file.\n" +
                    "Missing default for keys: ${keys.joinToString { "'$it'" }}",
            )
        }
        check(keys.size == defaults.size) {
            "Default/fallback $type database size mismatch: ${keys.size} keys but ${defaults.size} defaults"
        }
        val missingKeys = keys.indices
            .filter { i -> defaults[i] == null }
            .map { i -> keys[i] }
        if (missingKeys.isNotEmpty()) {
            throw IllegalStateException(
                "Default/fallback $type database is missing values for keys: ${missingKeys.joinToString { "'$it'" }}\n" +
                    "All $type keys must have a default value defined in the base (non-qualified) resource file.",
            )
        }
    }

    private fun validateStrictModeStringCoverage(processor: ResItemsProcessor) {
        validateStrictModeCoverage(
            type = "string",
            keys = processor.stringsDistinctKeys,
            valuePresentByLocale = processor.stringsForDbs
                .mapValues { (_, values) -> values.map { it != null } },
        )
    }

    private fun validateStrictModePluralCoverage(processor: ResItemsProcessor) {
        val perLocale = processor.pluralsPerLocale ?: return
        validateStrictModeCoverage(
            type = "plural",
            keys = processor.pluralsDistinctKeys,
            // A missing plural entry is filled with `ResItem.Plurals.EmptyItems` — a list of the
            // correct size but with every category null. Treat "all categories null" as missing.
            valuePresentByLocale = perLocale
                .mapValues { (_, items) ->
                    items.map { (_, categoryItems) -> categoryItems.any { it != null } }
                },
        )
    }
}

/**
 * Strict-mode check: for every base language present in resources, the language-only locale
 * (e.g. `en` when `en-rUK` or `en-rUS` exist) must define all [keys]. Region/script variants
 * may be partial — they only override what differs from the base.
 *
 * The undefined/default locale ([KLocale.Undefined]) is validated separately by the
 * default-fallback check and not re-checked here.
 *
 * Throws [IllegalStateException] listing every missing translation when the rule is violated.
 */
internal fun validateStrictModeCoverage(
    type: String,
    keys: List<String>?,
    valuePresentByLocale: Map<KLocale, List<Boolean>>,
) {
    if (valuePresentByLocale.isEmpty() || keys.isNullOrEmpty()) return

    val baseLanguages = valuePresentByLocale.keys
        .filter { it != KLocale.Undefined }
        .map { it.languageLocale() }
        .toSet()

    val errors = mutableListOf<String>()
    baseLanguages.sortedBy { it.tag }.forEach { baseLanguage ->
        val baseValues = valuePresentByLocale[baseLanguage]
        if (baseValues == null) {
            val variants = valuePresentByLocale.keys
                .filter { it != KLocale.Undefined && it.languageLocale() == baseLanguage && it != baseLanguage }
                .map { it.tag }
                .sorted()
            errors += "  language '${baseLanguage.tag}' is missing the base translation " +
                "(only region/script variants exist: ${variants.joinToString()}). " +
                "Add a base translation for '${baseLanguage.tag}' that defines all $type keys."
            return@forEach
        }
        val missing = keys.indices.filter { i -> !baseValues[i] }.map { keys[it] }
        if (missing.isNotEmpty()) {
            errors += "  language '${baseLanguage.tag}' is missing $type keys: " +
                missing.joinToString { "'$it'" }
        }
    }
    if (errors.isNotEmpty()) {
        throw IllegalStateException(
            "Strict mode: every base language must define all $type keys " +
                "(region/script variants like `en-rUK` may be partial). " +
                "Disable with `kostra.strictMode = false`.\n" +
                errors.joinToString("\n"),
        )
    }
}
