package com.jibru.kostra.plugin.task

import com.jibru.kostra.plugin.KostraPluginConfig
import com.jibru.kostra.plugin.ResourcesDefaults
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Defaults generation is fast and inputs are simple properties")
abstract class GenerateDefaultsTask : DefaultTask() {

    @get:Input
    abstract val resourcesDefaults: ListProperty<ResourcesDefaults>

    @get:Input
    abstract val kClassName: Property<String>

    @get:Input
    @get:Optional
    abstract val modulePrefix: Property<String>

    @get:Input
    @get:Optional
    abstract val internalVisibility: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: Property<File>

    init {
        group = KostraPluginConfig.Tasks.Group
    }

    @TaskAction
    fun run() = with(TaskDelegate) {
        val outputDir = outputDir.get()
        outputDir.deleteRecursively()

        generateComposeDefaults(
            kClassName = kClassName.get(),
            resourcesDefaults = resourcesDefaults.get(),
            outputDir = outputDir,
            modulePrefix = modulePrefix.getOrElse(""),
            internalVisibility = internalVisibility.getOrElse(false),
        )
    }
}
