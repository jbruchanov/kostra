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
 * Strict-mode check: every base-language translation that EXISTS must define all [keys].
 *
 * "Base language" = the language-only locale (`en`, `pt`, `zh`). The rule mirrors Android's
 * own resource-fallback semantics rather than being stricter than them:
 *
 *  - If a language file exists for the bare language (e.g. `strings-de.xml` → `de`), it is a
 *    declared full translation and MUST define every key — a half-translated language is a bug.
 *
 *  - If a language has ONLY region/script variants and NO bare base (e.g. `zh-Hans` + `zh-Hant`
 *    with no `zh`, or `en-rUS` with no `en`), that is VALID. The variants are partial overrides
 *    layered on top of the default bucket; any key a variant doesn't define resolves through
 *    `variant → language → default`, exactly as Android resolves `values-zh-rCN → values-zh →
 *    values`. The default bucket is guaranteed complete by the separate default-fallback check,
 *    so nothing can fall through to "no value". Requiring a bare `zh`/`en`/`pt` base here would
 *    be stricter than Android itself and forces redundant duplicate files.
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
        //No bare-language file (only region/script variants) → valid: the variants are partial
        //overrides on top of the complete default. See KDoc. Skip without error.
        val baseValues = valuePresentByLocale[baseLanguage] ?: return@forEach
        //A bare-language file that DOES exist must be complete.
        val missing = keys.indices.filter { i -> !baseValues[i] }.map { keys[it] }
        if (missing.isNotEmpty()) {
            errors += "  language '${baseLanguage.tag}' is missing $type keys: " +
                missing.joinToString { "'$it'" }
        }
    }
    if (errors.isNotEmpty()) {
        throw IllegalStateException(
            "Strict mode: every base-language translation that exists must define all $type keys " +
                "(region/script variants like `en-rUK` may be partial, and a language with only " +
                "variants and no bare base is allowed). Disable with `kostra.strictMode = false`.\n" +
                errors.joinToString("\n"),
        )
    }
}
