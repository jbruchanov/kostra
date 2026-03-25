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
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class AnalyseResourcesTask : DefaultTask() {
    @get:InputFiles
    abstract val resourceDirs: ListProperty<File>

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
}
