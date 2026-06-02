package com.jibru.kostra.plugin.task

import com.jibru.kostra.KLocale
import com.jibru.kostra.database.BinaryDatabase
import com.jibru.kostra.internal.KostraAssets
import com.jibru.kostra.plugin.KostraPluginConfig
import com.jibru.kostra.plugin.ResItem
import com.jibru.kostra.plugin.ResItemsProcessor
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream

/**
 * Writes the Kostra-managed databases AND stages binary file resources into a single output
 * directory whose only top-level entry is the [KostraAssets.RootDir] (`kostra_resources/`) subfolder.
 *
 * The task's `outputDir` is registered with the platform-appropriate pipeline by [KostraPlugin]:
 *  - Android variants: `variant.sources.assets.addGeneratedSourceDirectory(...)` — AGP packages
 *    `kostra_resources/<files>` into APK `assets/kostra_resources/<files>`. The JAR-resources /
 *    `commonMain.resources` path is deliberately NOT used: it would also propagate to the AAR's
 *    classes.jar and AGP would then merge those into APK root, polluting it with `kostra_resources/`.
 *  - Every other KMP target (`jvm`, `iosArm64`, `iosSimulatorArm64`, native, …):
 *    `compilation.main.defaultSourceSet.resources.srcDir(...)` — files appear at JAR-resource
 *    path `kostra_resources/<files>` for JVM and at the analogous on-disk path inside the
 *    processedResources / .framework / .klib output for Kotlin/Native.
 *  - Standalone executables (KMP target named `"native"`): also copied verbatim next to the
 *    linked exe via the `copyDBsToNative<Binary>Output` task, since the native runtime resolves
 *    keys relative to the executable's directory.
 *
 * Runtime loaders ([com.jibru.kostra.internal.loadResource]) prepend [KostraAssets.RootDir] to keys
 * before delegating to the platform reader, so K-class keys stay prefix-free.
 */
@DisableCachingByDefault(because = "Database generation is fast and inputs are a generated analysis file")
abstract class GenerateDatabasesTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourcesAnalysisFile: RegularFileProperty

    /**
     * Filename prefix for the generated database files (e.g. `"lib1_"` → `lib1_binary.db`). Empty
     * means "no prefix" → DBs live at the root of [outputDir]/kostra_resources/. The property name
     * was kept for source-compat with callers; the value is no longer a sub-directory.
     */
    @get:Input
    @get:Optional
    abstract val databaseDir: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        group = KostraPluginConfig.Tasks.Group
    }

    @TaskAction
    fun run() {
        @Suppress("UNCHECKED_CAST")
        val items = ObjectInputStream(FileInputStream(resourcesAnalysisFile.get().asFile)).readObject() as List<ResItem>
        val processor = ResItemsProcessor(items)
        val outDir = outputDir.get().asFile
        //Stage everything at <outDir>/kostra_resources/<...>. The plugin wires <outDir>:
        //  - into AGP's assets pipeline (Android variants) via variant.sources.assets, so AGP
        //    packages the contents at APK `assets/kostra_resources/<...>` — no APK root entries.
        //  - into compilation.defaultSourceSet.resources (non-Android KMP targets), so the same
        //    files appear inside the JVM jar / iOS klib / native processedResources at
        //    `kostra_resources/<...>`.
        //Single staging layout keeps every platform reading the same relative paths.
        val assetRoot = File(outDir, KostraAssets.RootDir)
        //databaseDir is repurposed as a filename prefix; DBs land at the root of assetRoot.
        val filePrefix = databaseDir.orNull.orEmpty()
        outDir.deleteRecursively()
        assetRoot.mkdirs()

        saveDataIntoDb(type = "strings", data = processor.stringsForDbs, "$filePrefix${ResItem.String}-%s.db", assetRoot)
        saveDataIntoDb(type = "plurals", data = processor.pluralsForDbs, "$filePrefix${ResItem.Plural}-%s.db", assetRoot)

        run {
            val db = File(assetRoot, "$filePrefix${ResItem.Binary}.db")
            val data = BinaryDatabase().apply { setPairs(processor.otherForDbs) }.save()
            db.writeBytes(data)
            if (logger.isInfoEnabled) {
                logger.info(buildString {
                    append("Saving kostra binary database:'${db.absolutePath}'\n")
                    append("Items:${processor.otherForDbs.size}, ")
                    append("Size:${data.size}b")
                })
            }
        }

        //Stage every binary file resource alongside the DBs at the same assets/ root so the runtime
        //loader resolves both DB lookups and binary lookups through the same single staging dir.
        stageBinaryFiles(items, assetRoot)
    }

    private fun stageBinaryFiles(items: List<ResItem>, assetRoot: File) {
        var copied = 0
        items.asSequence()
            .filterIsInstance<ResItem.FileRes>()
            .forEach { item ->
                //FileRes.value is the relative path String produced by the custom
                //com.jibru.kostra.plugin.ext.File#relativeTo extension — already the lookup key.
                val target = File(assetRoot, item.value)
                target.parentFile.mkdirs()
                item.file.copyTo(target, overwrite = true)
                copied++
            }
        if (logger.isInfoEnabled) {
            logger.info("Kostra: staged $copied binary resource(s) under '${assetRoot.absolutePath}'")
        }
    }

    private fun saveDataIntoDb(type: String, data: Map<KLocale, List<String?>>, fileNameTemplate: String, location: File) {
        data.forEach { (locale, items) ->
            val tag = if (locale == KLocale.Undefined) "default" else locale.tag
            val db = File(location, fileNameTemplate.format(tag))
            val dbData = BinaryDatabase().apply { setList(items) }.save()
            db.writeBytes(dbData)

            if (logger.isInfoEnabled) {
                logger.info(buildString {
                    append("Saving kostra $type database:'${db.absolutePath}'\n")
                    append("Locale:$locale, ")
                    append("Items:${items.size}, ")
                    append("Size:${dbData.size}b")
                    if (logger.isDebugEnabled) {
                        append("\nData:\n")
                        items.forEachIndexed { index, s ->
                            append("${index.toString().padStart(3)}:'$s'\n")
                        }
                    }
                })
            }
        }
    }
}
