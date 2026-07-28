package com.jibru.kostra.plugin.task

import com.jibru.kostra.plugin.KostraPluginConfig
import com.jibru.kostra.plugin.ResItem
import com.jibru.kostra.plugin.ResItemsProcessor
import java.io.FileInputStream
import java.io.ObjectInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Explicit, always-on strict-mode translation validation.
 *
 * [AnalyseResourcesTask] runs the same strict coverage checks (every base-language translation
 * that exists must define all string/plural keys), but only when the `kostra.strictMode` extension
 * flag is enabled. That couples enforcement to code generation: disabling `strictMode` to iterate
 * locally also disables the check everywhere.
 *
 * This task decouples the two. It reads the analysis produced by [AnalyseResourcesTask] and runs
 * [validateStrictModeCoverage] unconditionally, regardless of the `strictMode` flag. Typical use:
 * set `kostra.strictMode = false` for fast local dev, and run `./gradlew validateResources` on CI
 * to enforce full translation coverage as a hard gate.
 *
 * The default-fallback checks (every key has a value in the base/non-qualified resources) always
 * run inside [AnalyseResourcesTask] irrespective of `strictMode`, so they are already enforced by
 * the `analyseResources` dependency and not re-checked here.
 */
@DisableCachingByDefault(because = "Validation is fast and its only input is a generated analysis file")
abstract class ValidateResourcesTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourcesAnalysisFile: RegularFileProperty

    init {
        group = KostraPluginConfig.Tasks.Group
        description = "Fails if any base-language translation is missing string/plural keys " +
            "(strict-mode coverage), regardless of the kostra.strictMode flag. Intended for CI."
    }

    @TaskAction
    fun run() {
        @Suppress("UNCHECKED_CAST")
        val items = ObjectInputStream(FileInputStream(resourcesAnalysisFile.get().asFile))
            .use { it.readObject() as List<ResItem> }
        validateStrictModeCoverage(ResItemsProcessor(items))
    }
}
