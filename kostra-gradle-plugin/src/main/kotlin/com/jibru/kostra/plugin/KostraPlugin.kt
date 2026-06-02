@file:Suppress("unused")
@file:OptIn(FlowPreview::class, DelicateCoroutinesApi::class)

package com.jibru.kostra.plugin

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import com.jibru.kostra.plugin.KostraPluginConfig.defaultOutputDir
import com.jibru.kostra.plugin.KostraPluginConfig.fileWatcherLog
import com.jibru.kostra.plugin.KostraPluginConfig.outputSourceDir
import com.jibru.kostra.plugin.ext.appendLog
import com.jibru.kostra.plugin.ext.hasComposePlugin
import com.jibru.kostra.plugin.ext.hasJvmPlugin
import com.jibru.kostra.plugin.ext.hasKmpPlugin
import com.jibru.kostra.plugin.ext.jvmMainSourceSet
import com.jibru.kostra.plugin.ext.kmpMainSourceSet
import com.jibru.kostra.plugin.ext.useJvmInline
import com.jibru.kostra.plugin.task.AnalyseResourcesTask
import com.jibru.kostra.plugin.task.GenerateCodeTask
import com.jibru.kostra.plugin.task.GenerateDatabasesTask
import com.jibru.kostra.plugin.task.GenerateDefaultsTask
import com.jibru.kostra.plugin.task.TaskDelegate
import java.io.File
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractExecutable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.slf4j.LoggerFactory

class KostraPlugin : Plugin<Project> {

    private val logger = LoggerFactory.getLogger(KostraPlugin::class.java)

    override fun apply(target: Project) = with(KostraPluginConfig) {
        if (GradleVersion.current() < GradleVersion.version("8.0")) {
            logger.warn(
                "Kostra gradle plugin isn't tested on plugins < 8.0, in case of any error, try latest gradle, run:\n" +
                    "./gradlew wrapper --gradle-version latest or ./gradlew wrapper --gradle-version 8.3",
            )
        }

        //Auto-opt into AGP's KMP-Android Android-resources pipeline. Without this, the new
        //com.android.kotlin.multiplatform.library plugin keeps `variant.sources.assets` as null,
        //and we can't ship anything to the AAR's assets/ folder. Done via pluginManager.withPlugin
        //so we mutate the flag BEFORE AGP finalizes the variant. Safe no-op on non-KMP-Android.
        target.pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
            runCatching {
                target.extensions.findByType(KotlinMultiplatformExtension::class.java)
                    ?.targets
                    ?.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget::class.java)
                    ?.configureEach { it.androidResources.enable = true }
            }.onFailure {
                target.logger.info(
                    "Kostra: failed to auto-enable kotlin.android.androidResources.enable on ${target.path} " +
                        "(${it.javaClass.simpleName}: ${it.message}). Set it manually in build.gradle " +
                        "if you hit `variant.sources.assets is null` warnings.",
                )
            }
        }

        val extension = target.extensions.create(DslObjectName, KostraPluginExtension::class.java)

        val analyseResourcesTaskProvider = target.tasks
            .register(KostraPluginConfig.Tasks.AnalyseResources, AnalyseResourcesTask::class.java) {
                it.outputFile.set(target.analysisFile())
                it.resourceDirs.addAll(extension.resourceDirs)
                it.resourceDirs.addAll(extension.androidResources.resourceDirs)
                it.strictMode.set(extension.strictMode)
            }

        val generateResourcesTaskProvider = target.tasks
            .register(KostraPluginConfig.Tasks.GenerateResources, GenerateCodeTask::class.java) { task ->
                task.kClassName.set(extension.kClassName)
                task.resourcesAnalysisFile.set(analyseResourcesTaskProvider.get().outputFile)
                //Base filename prefix is empty — modulePrefix (if any) is folded in by TaskDelegate
                //via lowerCasedWith() at generation time. DB files end up at kostra_resources/<file>
                //with no kresources/ sub-folder.
                task.resDbsFolderName.set("")
                task.modulePrefix.set(extension.modulePrefix)
                task.internalVisibility.set(extension.internalVisibility)
                task.interfaces.set(extension.interfaces)
                task.outputDir.set(target.outputSourceDir())
                task.dependsOn(analyseResourcesTaskProvider)
            }

        val generateResourcesDefaultsTaskProvider = createGenerateDefaultsTask(
            project = target,
            extension = extension,
        ).apply { configure { it.dependsOn(generateResourcesTaskProvider) } }

        val generateDbsTaskTaskProvider = target.tasks
            .register(KostraPluginConfig.Tasks.GenerateDatabases, GenerateDatabasesTask::class.java) {
                it.resourcesAnalysisFile.set(analyseResourcesTaskProvider.flatMap { v -> v.outputFile })
                it.databaseDir.set(extension.outputDatabaseDirName)
                it.outputDir.fileValue(target.outputAssetsDir())
                it.dependsOn(analyseResourcesTaskProvider)
            }

        target.tasks.findByName("clean")?.apply {
            finalizedBy(generateResourcesTaskProvider, generateResourcesDefaultsTaskProvider)
        }

        target.defaultTasks(generateResourcesTaskProvider.name)

        extension.apply {
            autoConfig.set(true)
            useFileWatcher.set(false)
            strictLocale.set(true)
            strictMode.set(true)
            kClassName.set(KClassName)
            modulePrefix.set("")
            internalVisibility.set(false)
            interfaces.set(modulePrefix.map { it.isNotEmpty() })
            failOnDuplicates.set(true)
            resourcesDefaults.set(
                when {
                    target.hasComposePlugin() -> ResourcesDefaults.AllCompose
                    else -> ResourcesDefaults.AllBasic
                },
            )
        }
        extension.androidResources.apply {
            stringFiles.addAll(FileResolverConfig.Defaults.stringFiles)
            painterGroups.set(FileResolverConfig.Defaults.painterGroups)
            painterExtensions.set(FileResolverConfig.Defaults.imageExtensions)
        }

        //Wire the kostra staging dir into the Android variant's assets pipeline.
        //
        //AGP places the dir's contents at APK `assets/kostra_resources/<...>` (the real assets/
        //folder, not apk-root). At runtime `AssetManager.open("kostra_resources/<key>")` reads it
        //via KostraAndroidContextHolder → ResourceLoader.android.kt.
        //
        //Two extension types are tried in turn, because they don't overlap and a module applies
        //at most one Android plugin variant:
        //  - KotlinMultiplatformAndroidComponentsExtension — used by KMP-Android library modules
        //    (`com.android.kotlin.multiplatform.library`). This is the ONLY way to reach
        //    `variant.sources.assets` on those variants; the base AndroidComponentsExtension
        //    returns null. Compose Multiplatform's resource plugin follows the same pattern.
        //  - AndroidComponentsExtension — used by plain `com.android.application` /
        //    `com.android.library` modules.
        //
        //The `package<Variant>Resources` dependsOn(...) is the workaround Compose Multiplatform
        //ships: it makes Android Studio's Compose Preview resource-packaging step depend on the
        //kostra task so the preview's AssetManager has the staged files at render time. Note:
        //in practice layoutlib's ApplicationContext.assets observed during @Preview is still
        //often empty (see ResourceLoader.android.kt for the classloader fallback path the
        //preview render actually uses), but the dependsOn keeps the build-time ordering correct.
        val androidSetup: (com.android.build.api.variant.Variant) -> Unit = { variant ->
            variant.sources.assets?.addGeneratedSourceDirectory(
                generateDbsTaskTaskProvider,
                GenerateDatabasesTask::outputDir,
            )
                ?: target.logger.warn(
                    "Kostra: ${target.path} variant '${variant.name}' has no assets pipeline " +
                        "(variant.sources.assets is null). Auto-enable of " +
                        "`kotlin.android.androidResources.enable = true` apparently didn't apply — " +
                        "set it manually in build.gradle if kostra resources don't reach assets/.",
                )
            val packageTaskName = "package${variant.name.replaceFirstChar { it.uppercase() }}Resources"
            target.tasks.configureEach { task ->
                if (task.name == packageTaskName) task.dependsOn(generateDbsTaskTaskProvider)
            }
        }
        target.extensions.findByType(KotlinMultiplatformAndroidComponentsExtension::class.java)
            ?.let { kmpAndroid -> kmpAndroid.onVariants { variant -> androidSetup(variant) } }
            ?: target.extensions.findByType(AndroidComponentsExtension::class.java)
                ?.onVariants { variant -> androidSetup(variant) }

        target.afterEvaluate { project ->
            if (extension.autoConfig.get()) {
                tryUpdateSourceSets(
                    project = project,
                    extension = extension,
                    generateCodeTaskProvider = generateResourcesTaskProvider,
                    generateDefaultsTaskProvider = generateResourcesDefaultsTaskProvider,
                    generateDbsTaskProvider = generateDbsTaskTaskProvider,
                )
                tryAddNativeCopyTasks(project, generateDbsTaskTaskProvider)
            }
            updateFileWatcher(target, extension)
        }
    }

    /**
     * For standalone Kotlin/Native executable apps (e.g. `appNativeConsole`) the linked binary in
     * `build/bin/native/<buildType>Executable/` is just an `.exe` — the per-target wiring puts
     * kostra files into `processedResources/native/main/` but the runtime reads them by relative
     * path next to the executable, so we need an explicit copy step.
     *
     * Convention this hook depends on (set up by `build-native-lib.gradle.createNativeTarget`):
     *  - The KMP native target is named literally `"native"` (`mingwX64("native")` /
     *    `linuxX64("native")` / `macosX64("native")`). That naming is what makes Gradle generate
     *    the task names this method references — for any other target name the function
     *    silently no-ops (intentionally — multi-target setups like iOS frameworks have their own
     *    resource bundling pipeline via CocoaPods/Xcode, and adding generic copy tasks there
     *    would conflict).
     *  - The link task per executable binary follows KMP's naming: a binary called
     *    `debugExecutable` produces `linkDebugExecutableNative`, hence
     *    `link${binaryName.capitalize()}Native`.
     *  - `nativeProcessResources` is the standard `<targetName>ProcessResources` for the
     *    target named "native".
     */
    private fun tryAddNativeCopyTasks(
        project: Project,
        generateDbTaskProvider: TaskProvider<GenerateDatabasesTask>,
    ) {
        val wireDeps = { copyTask: TaskProvider<Copy>, capitalizedBinaryName: String ->
            //Link runs after the copy so the binary directory is fully populated by the time
            //the linker writes its output (the link task DOESN'T clean its destination dir).
            //
            //`tasks.named(...)` is the lazy/typed lookup — it resolves the existing TaskProvider
            //(KMP registers link tasks via `tasks.register`, not eagerly) and queues a configure
            //action against it, so the dependency wiring happens at the right point regardless
            //of registration order. `findByName` returned null in newer KMP because link tasks
            //aren't realized at afterEvaluate time; `named` works against TaskProviders, so it
            //doesn't care whether the task has been realized yet.
            val linkTaskName = "link${capitalizedBinaryName}Native"
            runCatching {
                project.tasks.named(linkTaskName).configure { it.dependsOn(copyTask) }
            }.onFailure {
                project.logger.info("Kostra: link task '$linkTaskName' not registered — skipping wire-up.")
            }
            //Avoid a parallel-execution race between `nativeProcessResources` (which writes into
            //the KMP processedResources tree) and our copy (which writes into the binary's
            //outputDirectory). They share no output paths, but mustRunAfter keeps Gradle's
            //task-graph deterministic across both. `nativeProcessResources` always exists for a
            //KMP target named "native", so the lazy lookup never fails here in practice.
            runCatching {
                project.tasks.named("nativeProcessResources").configure { it.mustRunAfter(copyTask) }
            }
        }

        project.extensions.findByType(KotlinMultiplatformExtension::class.java)
            ?.targets
            ?.findByName("native")
            ?.let { it as? KotlinNativeTarget }
            ?.binaries
            ?.filterIsInstance<AbstractExecutable>()
            ?.map { binary -> binary.name.replaceFirstChar { c -> c.uppercase() } to binary.outputDirectory }
            ?.onEach { (capitalizedName, outputDir) ->
                //Single copy task: GenerateDatabasesTask.outputDir already contains the full
                //`kostra_resources/<...>` tree — both generated DBs AND user binary file
                //resources, which `stageBinaryFiles()` mirrors into the staging dir keyed on
                //`ResItem.FileRes#value`. So copying the DB task's output verbatim next to the
                //executable produces exactly one `kostra_resources/` folder with everything the
                //native runtime's resource loader needs. (Previously a second
                //`copyResourcesToNative…Output` task also copied `extension.resourceDirs` to
                //bin root, producing duplicate files at `<bin>/flagssvg/…` and
                //`<bin>/kostra_resources/flagssvg/…` — removed.)
                val dbsCopy = project.tasks.register(
                    KostraPluginConfig.Tasks.CopyResourcesForNativeTemplate_xy.format("DBs", capitalizedName),
                    Copy::class.java,
                ) {
                    it.group = KostraPluginConfig.Tasks.Group
                    it.from(generateDbTaskProvider)
                    it.into(outputDir)
                }
                wireDeps(dbsCopy, capitalizedName)
            }
    }

    private fun tryUpdateSourceSets(
        project: Project,
        extension: KostraPluginExtension,
        generateCodeTaskProvider: TaskProvider<GenerateCodeTask>,
        generateDefaultsTaskProvider: TaskProvider<GenerateDefaultsTask>,
        generateDbsTaskProvider: TaskProvider<GenerateDatabasesTask>,
    ) {
        var updatedKmpSourceSets = false
        run KotlinMultiplatform@{
            (project.takeIf { it.hasKmpPlugin() } ?: return@KotlinMultiplatform)
                .kmpMainSourceSet()
                .let { commonMainSourceSet ->
                    if (commonMainSourceSet == null) {
                        logger.warn("Kostra: ${project.name}:commonMain source set not found, unable to finish auto setup!")
                        return@let
                    }

                    updatedKmpSourceSets = true
                    //let kmp know about kostra sourceDir
                    commonMainSourceSet.kotlin.srcDir(generateCodeTaskProvider)
                    commonMainSourceSet.kotlin.srcDir(generateDefaultsTaskProvider)
                    //KS-02: kotlin compilation must see the generated DBs as an input too,
                    //otherwise the DB task isn't scheduled before kotlin compilation in some builds.
                    commonMainSourceSet.kotlin.srcDir(generateDbsTaskProvider)

                    //Capture the user-source srcDirs into extension.resourceDirs so the plugin's
                    //analysis sees them. We do NOT take over commonMain.resources here: adding the
                    //staging dir to commonMain.resources would feed it into the Android target's
                    //JAR-resources pipeline AND the variant.sources.assets pipeline (above),
                    //producing duplicates at both APK root AND APK assets/. The per-target wiring
                    //below adds the staging dir only to non-Android targets.
                    extension.resourceDirs.set(extension.resourceDirs.get() + commonMainSourceSet.resources.srcDirs)
                }

            //Per-target resource wiring for non-Android KMP targets. The Android target gets
            //the staging dir via variant.sources.assets at plugin-apply time. Skip:
            //  * common (metadata) — its compilation's defaultSourceSet IS commonMain, so we'd
            //    propagate via KMP inheritance to nativeMain / jvmMain / etc. AND also add it
            //    to those source sets here, producing duplicate-srcDir errors.
            //  * androidJvm — covered by the assets pipeline; feeding via kotlin source-set
            //    would double-package (apk-assets + apk-root).
            project.extensions.findByType(KotlinMultiplatformExtension::class.java)
                ?.targets
                ?.forEach { kmpTarget ->
                    val pt = kmpTarget.platformType
                    if (pt == org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.common) return@forEach
                    if (pt == org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm) return@forEach
                    runCatching {
                        kmpTarget.compilations
                            .getByName("main")
                            .defaultSourceSet
                            .resources
                            .srcDir(generateDbsTaskProvider)
                    }
                }
        }

        run JavaPlugin@{
            (project.takeIf { it.hasJvmPlugin() } ?: return@JavaPlugin)
                .jvmMainSourceSet()
                .let { mainSourceSet ->
                    if (updatedKmpSourceSets) {
                        logger.warn("Kostra: ${project.name}:main jvm skipped, sourceset updated for KMP!")
                        return@let
                    }
                    if (mainSourceSet == null) {
                        logger.warn("Kostra: ${project.name}:main jvm source set not found, unable to finish auto setup!")
                        return@let
                    }
                    //let java know about kostra sourceDir
                    mainSourceSet.java.srcDir(generateCodeTaskProvider)
                    mainSourceSet.java.srcDir(generateDefaultsTaskProvider)

                    //put into kostra extension the resource folders, to let KGP know what we currently have
                    extension.resourceDirs.set(extension.resourceDirs.get() + mainSourceSet.resources.srcDirs)

                    //let java know about kostra resource dir
                    mainSourceSet.resources.srcDir(generateDbsTaskProvider)
                    logger.info("Java updated resourceDirs:${mainSourceSet.resources.srcDirs.joinToString()}")
                }
        }

        //No Android-specific srcDir manipulation needed here. Android variants get their wiring at
        //plugin-apply time via `variant.sources.assets.addGeneratedSourceDirectory(...)` in
        //`androidSetup` above — the staging dir's `kostra_resources/<...>` contents land at APK
        //`assets/kostra_resources/<...>`. This `tryUpdateSourceSets` deliberately skips the
        //androidJvm platform type (see the per-target loop above) so we don't double-package.
    }

    private fun createGenerateDefaultsTask(
        project: Project,
        extension: KostraPluginExtension,
    ): TaskProvider<GenerateDefaultsTask> {
        val taskProvider = project.tasks.register(
            KostraPluginConfig.Tasks.GenerateDefaults,
            GenerateDefaultsTask::class.java,
        ) {
            it.group = KostraPluginConfig.Tasks.Group
            it.resourcesDefaults.set(extension.resourcesDefaults)
            it.kClassName.set(extension.kClassName)
            it.modulePrefix.set(extension.modulePrefix)
            it.internalVisibility.set(extension.internalVisibility)
            it.outputDir.set(project.outputSourceDir("Common"))
        }
        return taskProvider
    }

    private var fileWatchers = mutableMapOf<Project, FileWatcher>()
    private var fileWatcherJobs = mutableMapOf<Project, Job?>()

    private fun updateFileWatcher(target: Project, extension: KostraPluginExtension) {
        fileWatcherJobs[target]?.cancel()
        fileWatcherJobs.remove(target)
        val folders = extension.allResourceDirs().filter { it.isDirectory }
        if (folders.isNotEmpty() && extension.useFileWatcher.get()) {
            val taskDelegateConfig = TaskDelegate.Config(
                resourceDirs = extension.resourceDirs.get() + extension.androidResources.resourceDirs.get(),
                fileResolverConfig = extension.toFileResolverConfig(),
                kClassName = extension.kClassName.get(),
                outputDir = File(target.defaultOutputDir(), "src"),
                //Pass the BASE prefix (empty). TaskDelegate.generateResources folds in
                //modulePrefix via lowerCasedWith(), so passing the pre-combined value here would
                //double-prefix it ("lib1_lib1_<file>.db"). Same convention as the eager task path.
                resDbsFolderName = "",
                modulePrefix = extension.modulePrefix.get(),
                interfaces = extension.interfaces.get(),
                addJvmInline = target.useJvmInline(),
            )
            val log = target.fileWatcherLog()
            val fileWatcher = fileWatchers.getOrPut(target) {
                FileWatcher(
                    log = log,
                    stringsRegexps = extension.androidResources.stringFiles.get().map { it.toRegex() },
                )
            }
            log.appendLog("Start")
            fileWatcherJobs[target] = GlobalScope.launch(Dispatchers.IO) {
                logger.info("START filewatcher for ${target.name}, log:'${log.absolutePath}'")
                fileWatcher.flowChanges(folders)
                    .debounce(1000L)
                    .collect {
                        onFileWatchedNotified(log = null, taskDelegateConfig)
                    }
            }.apply {
                invokeOnCompletion {
                    log.appendLog("STOP")
                    logger.info("STOP Filewatcher for ${target.name}, log:'${log.absolutePath}'")
                }
            }
        }
    }

    private fun onFileWatchedNotified(
        log: File?,
        config: TaskDelegate.Config,
    ) = with(TaskDelegate) {
        runCatching {
            val items = analyseCode(
                resourceDirs = config.resourceDirs,
                fileResolverConfig = config.fileResolverConfig,
            )
            generateResources(
                items = items,
                kClassName = config.kClassName,
                outputDir = config.outputDir,
                resDbsFolderName = config.resDbsFolderName,
                modulePrefix = config.modulePrefix,
                interfaces = config.interfaces,
                addJvmInline = config.addJvmInline,
            )
        }.exceptionOrNull()?.also {
            log?.let { log ->
                log.appendLog((it.message ?: "null"))
                log.appendLog(it.stackTraceToString())
            }
        }
    }
}
