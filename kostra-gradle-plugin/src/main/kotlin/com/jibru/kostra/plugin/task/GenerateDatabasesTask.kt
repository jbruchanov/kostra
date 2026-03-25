package com.jibru.kostra.plugin.task

import com.jibru.kostra.KLocale
import com.jibru.kostra.database.BinaryDatabase
import com.jibru.kostra.plugin.KostraPluginConfig
import com.jibru.kostra.plugin.ResItem
import com.jibru.kostra.plugin.ResItemsProcessor
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream

abstract class GenerateDatabasesTask : DefaultTask() {

    @get:InputFile
    abstract val resourcesAnalysisFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val databaseDir: Property<String>

    @get:OutputDirectory
    abstract val outputDir: Property<File>

    init {
        group = KostraPluginConfig.Tasks.Group
    }

    @TaskAction
    fun run() {
        @Suppress("UNCHECKED_CAST")
        val items = ObjectInputStream(FileInputStream(resourcesAnalysisFile.get().asFile)).readObject() as List<ResItem>
        val processor = ResItemsProcessor(items)
        val outDir = outputDir.get()
        val dbDir = databaseDir.orNull?.let { File(outDir, it) } ?: outDir
        outDir.deleteRecursively()
        dbDir.mkdirs()

        saveDataIntoDb(type = "strings", data = processor.stringsForDbs, "${ResItem.String}-%s.db", dbDir)
        saveDataIntoDb(type = "plurals", data = processor.pluralsForDbs, "${ResItem.Plural}-%s.db", dbDir)

        run {
            val db = File(dbDir, "${ResItem.Binary}.db")
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
