package com.jibru.kostra.plugin.task

import com.jibru.kostra.plugin.KostraPluginConfig
import com.jibru.kostra.plugin.ResItem
import com.jibru.kostra.plugin.ext.useJvmInline
import org.gradle.api.DefaultTask
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

@DisableCachingByDefault(because = "Code generation is fast and inputs are a generated analysis file")
abstract class GenerateCodeTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourcesAnalysisFile: RegularFileProperty

    @get:Input
    abstract val kClassName: Property<String>

    @get:Input
    abstract val resDbsFolderName: Property<String>

    @get:Input
    @get:Optional
    abstract val modulePrefix: Property<String>

    @get:Input
    @get:Optional
    abstract val internalVisibility: Property<Boolean>

    @get:Input
    abstract val interfaces: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: Property<File>

    init {
        group = KostraPluginConfig.Tasks.Group
    }

    @Suppress("UNCHECKED_CAST")
    @TaskAction
    fun run() = with(TaskDelegate) {
        val items = ObjectInputStream(FileInputStream(resourcesAnalysisFile.get().asFile)).readObject() as List<ResItem>
        val outputDir = outputDir.get()
        outputDir.deleteRecursively()

        generateResources(
            items = items,
            kClassName = kClassName.get(),
            outputDir = outputDir,
            resDbsFolderName = resDbsFolderName.get(),
            modulePrefix = modulePrefix.getOrElse(""),
            internalVisibility = internalVisibility.getOrElse(false),
            interfaces = interfaces.get(),
            addJvmInline = project.useJvmInline(),
        )
    }
}
