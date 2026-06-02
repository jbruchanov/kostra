@file:Suppress("ConstPropertyName")

package com.jibru.kostra.plugin

import com.jibru.kostra.plugin.ext.capitalize
import java.io.File
import org.gradle.api.Project

object KostraPluginConfig {
    const val DslObjectName = "kostra"
    const val PackageName = "com.jibru.kostra"
    const val PackageNameCompose = "$PackageName.compose"
    const val PackageNameIcu = "$PackageName.icu"
    const val KClassName = "app.K"
    const val ResourcePropertyName = "Resources"
    const val AliasedImports = true
    const val ModuleResourceKeyName = "ModuleResourceKey"
    const val ComposePluginPackage = "org.jetbrains.compose"
    val ImageExts = setOf("bmp", "jpg", "jpeg", "png", "svg", "webp", "vxml")

    const val ComposeDefaultResourceProvider_x = "%sDefaultResourceProvider"

    fun Project.analysisFile() = File(defaultOutputDir(), "resources.obj")

    fun Project.outputSourceDir(variant: String = "") = File(defaultOutputDir(), "src${variant.capitalize()}")

    //Renamed from outputResourcesDir(): the staged files are wired into the Android variant's
    //ASSETS pipeline (and KMP target source-sets' resources for non-Android targets), so the
    //directory name reflects what's actually inside.
    fun Project.outputAssetsDir() = File(defaultOutputDir(), "assets")

    fun Project.defaultOutputDir() = File(layout.buildDirectory.asFile.get(), "generated/kostra")

    fun Project.fileWatcherLog() = File(defaultOutputDir(), "filewatcher.log")

    object Tasks {
        const val Group = "kostra"
        const val AnalyseResources = "analyseResources"
        const val GenerateResources = "generateResources"
        const val GenerateDefaults = "generateDefaults"
        const val GenerateDatabases = "generateDatabases"

        //Task name template used by tryAddNativeCopyTasks. Formatted with (what, capitalizedBinary).
        //Currently only one variant is registered:
        //  - "DBs" → copyDBsToNative<Binary>Output (kostra staging dir → binary out dir; the staging
        //    dir already includes user binary file resources via GenerateDatabasesTask.stageBinaryFiles)
        //The "Resources" variant was removed because it duplicated the user-source binary resources
        //already staged by the DB task — the second slot is preserved in case a future use-case
        //needs a separate-output variant.
        const val CopyResourcesForNativeTemplate_xy = "copy%sToNative%sOutput"
    }
}
