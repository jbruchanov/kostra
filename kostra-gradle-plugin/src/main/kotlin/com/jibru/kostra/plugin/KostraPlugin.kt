@file:Suppress("unused")
@file:OptIn(FlowPreview::class, DelicateCoroutinesApi::class)

package com.jibru.kostra.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.tasks.factory.dependsOn
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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractExecutable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.slf4j.LoggerFactory
import java.io.File

class KostraPlugin : Plugin<Project> {

    private val logger = LoggerFactory.getLogger(KostraPlugin::class.java)

    override fun apply(target: Project) = with(KostraPluginConfig) {
        if (GradleVersion.current() < GradleVersion.version("8.0")) {
            logger.warn(
                "Kostra gradle plugin isn't tested on plugins < 8.0, in case of any error, try latest gradle, run:\n" +
                    "./gradlew wrapper --gradle-version latest or ./gradlew wrapper --gradle-version 8.3",
            )
        }

        val extension = target.extensions.create(DslObjectName, KostraPluginExtension::class.java)

        val analyseResourcesTaskProvider = target.tasks
            .register(KostraPluginConfig.Tasks.AnalyseResources, AnalyseResourcesTask::class.java) {
                it.outputFile.set(target.analysisFile())
                it.resourceDirs.addAll(extension.resourceDirs)
                it.resourceDirs.addAll(extension.androidResources.resourceDirs)
            }

        val generateResourcesTaskProvider = target.tasks
            .register(KostraPluginConfig.Tasks.GenerateResources, GenerateCodeTask::class.java) { task ->
                task.kClassName.set(extension.kClassName)
                task.resourcesAnalysisFile.set(analyseResourcesTaskProvider.get().outputFile)
                task.resDbsFolderName.set(ResourceDbFolderName)
                task.modulePrefix.set(extension.modulePrefix)
                task.internalVisibility.set(extension.internalVisibility)
                task.interfaces.set(extension.interfaces)
                task.outputDir.set(target.outputSourceDir())
                task.dependsOn(analyseResourcesTaskProvider)
            }

        val generateResourcesDefaultsTaskProvider = createGenerateDefaultsTask(
            project = target,
            extension = extension,
        ).apply { dependsOn(generateResourcesTaskProvider) }

        val generateDbsTaskTaskProvider = target.tasks
            .register(KostraPluginConfig.Tasks.GenerateDatabases, GenerateDatabasesTask::class.java) {
                it.resourcesAnalysisFile.set(analyseResourcesTaskProvider.flatMap { v -> v.outputFile })
                it.databaseDir.set(extension.outputDatabaseDirName)
                it.outputDir.set(target.outputResourcesDir())
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

        target.afterEvaluate { project ->
            if (extension.autoConfig.get()) {
                tryUpdateSourceSets(
                    project = project,
                    extension = extension,
                    generateCodeTaskProvider = generateResourcesTaskProvider,
                    generateDefaultsTaskProvider = generateResourcesDefaultsTaskProvider,
                    generateDbsTaskProvider = generateDbsTaskTaskProvider,
                )
                tryAddNativeCopyTasks(project, extension, generateDbsTaskTaskProvider)
            }
            updateFileWatcher(target, extension)
        }
    }

    private fun tryAddNativeCopyTasks(
        project: Project,
        extension: KostraPluginExtension,
        generateDbTaskProvider: TaskProvider<GenerateDatabasesTask>,
    ) {
        val otherTaskDeps = { task: Task, linkNativeVariant: String ->
            project.tasks.getByName("link${linkNativeVariant}Native").dependsOn(task)
            project.tasks.getByName("nativeProcessResources").mustRunAfter(task)
        }

        project.extensions.findByType(KotlinMultiplatformExtension::class.java)
            ?.targets
            ?.findByName("native")
            ?.let { it as? KotlinNativeTarget }
            ?.binaries
            ?.filterIsInstance<AbstractExecutable>()
            ?.map { it.name.replaceFirstChar { c -> c.uppercase() } to it.outputDirectory }
            ?.onEach { (name, outputDir) ->
                //copy predefined resources
                project.tasks.register(KostraPluginConfig.Tasks.CopyResourcesForNativeTemplate_xy.format("Resources", name), Copy::class.java) {
                    it.group = KostraPluginConfig.Tasks.Group
                    it.from(extension.resourceDirs)
                    it.into(outputDir)

                    otherTaskDeps(it, name)
                }

                //copy generated string dbs
                project.tasks.register(KostraPluginConfig.Tasks.CopyResourcesForNativeTemplate_xy.format("DBs", name), Copy::class.java) {
                    it.group = KostraPluginConfig.Tasks.Group
                    it.from(generateDbTaskProvider)
                    it.into(outputDir)
                    otherTaskDeps(it, name)
                }
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

                    //put into kostra extension the resource folders
                    extension.resourceDirs.set(extension.resourceDirs.get() + commonMainSourceSet.resources.srcDirs)
                    //let KMP know about kostra resource dir
                    commonMainSourceSet.resources.srcDir(generateDbsTaskProvider)
                    //looks like this is might help to fix KS-02, otherwise the task is just not properly hooked up
                    //and the DBs are not generated
                    commonMainSourceSet.kotlin.srcDir(generateDbsTaskProvider)
                }

            /*
            //future template to have specific code generate per target
            kotlinMultiplatformExtension
                .targets
                .filter { it.name == "metadata" }
                .also { check(it.isNotEmpty()) { "Undefined metadata/common target in '${project.name}'" } }
                .let { ktTargets ->
                    ktTargets.onEach { ktTarget ->
                        ktTarget.compilations.onEach { compilation ->
                            compilation.defaultSourceSet.kotlin.srcDir(null)
                        }
                    }
                }
             */
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

        //android plugin doesn't seem to be taking stuff from KMP common, mostlikely because "jvm resources" are not same as "android res" resources
        run Android@{
            val sourceSets = project.extensions.findByType(LibraryExtension::class.java)?.sourceSets
                ?: project.extensions.findByType(AppExtension::class.java)?.sourceSets

            (sourceSets ?: return@Android)
                .findByName("main")
                ?.resources
                .let { resources ->
                    if (resources == null) {
                        logger.warn("Kostra: ${project.name}:main resources found, unable to finish auto setup!")
                        return@let
                    }
                    //add kostra resources part of android resources (not res <- android resources, just "jar" resources)
                    //we don't want androidResources.resourceDirs here, those are parsed and converted into own db
                    resources.srcDir(extension.resourceDirs.get())
                    resources.srcDir(generateDbsTaskProvider)
                }
            /*
                previous only
                resources.srcDir(generateDbsTaskProvider)
                didn't work, for simply assembleDebug, it didn't have the generateDatabases tasks part of the pipeline.
                It was also causing this in other project, not in the sample app project
                https://github.com/JetBrains/compose-multiplatform/issues/3850
                Hooking up the generateDbsTaskProvider also to variants processJavaResourcesProvider seems to be solving both issues.
             */
            val variants = project.extensions.findByType(LibraryExtension::class.java)?.libraryVariants
                ?: project.extensions.findByType(AppExtension::class.java)?.applicationVariants

            variants?.forEach {
                it.processJavaResourcesProvider.dependsOn(generateDbsTaskProvider)
            }
        }
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
                resDbsFolderName = extension.outputDatabaseDirName.get(),
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
