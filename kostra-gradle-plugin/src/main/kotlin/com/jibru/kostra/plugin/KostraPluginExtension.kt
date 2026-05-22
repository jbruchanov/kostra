package com.jibru.kostra.plugin

import com.jibru.kostra.plugin.ext.lowerCasedWith
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import java.io.File

abstract class KostraPluginExtension {
    /**
     * enable autoconfig, if disabled, the tasks run/configuration must be done manually
     */
    abstract val autoConfig: Property<Boolean>

    /**
     * enable failure during processing when duplicate keys found
     */
    abstract val failOnDuplicates: Property<Boolean>

    /**
     * generate for all K object records also interfaces for potential resource merging via class delegation
     */
    abstract val interfaces: Property<Boolean>

    /**
     * mark all the generated code as internal to avoid leaking outside a module
     */
    abstract val internalVisibility: Property<Boolean>

    /**
     * full package name of generated K class, by default 'app.K`,
     * `kClassName' from gradle.kts, 'KClassName' from .gradle 🙄
     */
    abstract val kClassName: Property<String>

    /**
     * Add a unique prefix in multi module setup to avoid reference conflicts
     */
    abstract val modulePrefix: Property<String>

    /**
     * Define what defaults helpers should be generated.
     */
    abstract val resourcesDefaults: ListProperty<ResourcesDefaults>

    /**
     * use only locale qualifiers on files known to java, so for example '-xxxx' qualifier
     * will be ignored as it's not java known locale and will be ignored
     */
    abstract val strictLocale: Property<Boolean>

    /**
     * In strict mode every base language present in resources must define ALL string/plural keys.
     * Region/script variants (e.g. `en-rUK`, `en-rUS`, `zh-Hant`) may be partial — they only override
     * what differs from the base. Different languages each need their own complete translation.
     * Default: true. Build fails with the list of missing keys when violated.
     */
    abstract val strictMode: Property<Boolean>

    /**
     * Not nicely working autoupdate, MVP, don't use IDE doesn't see the changes.
     */
    abstract val useFileWatcher: Property<Boolean>

    val outputDatabaseDirName: Provider<String>
        get() = modulePrefix.map { it.lowerCasedWith(KostraPluginConfig.ResourceDbFolderName) }.orElse(KostraPluginConfig.ResourceDbFolderName)

    @get:Internal
    abstract val resourceDirs: ListProperty<File>

    @get:Nested
    abstract val androidResources: AndroidResourcesExtension

    @Internal
    fun allResourceDirs(): List<File> = resourceDirs.get() + androidResources.resourceDirs.get()

    fun androidResources(action: Action<in AndroidResourcesExtension>) {
        action.execute(androidResources)
    }

    fun toFileResolverConfig(): FileResolverConfig = with(androidResources) {
        val defaults = FileResolverConfig.Defaults
        return FileResolverConfig(
            keyMapper = keyMapper.orNull ?: defaults.keyMapper,
            stringFiles = stringFiles.get().toSet(),
            painterGroups = painterGroups.get().toSet(),
            imageExtensions = painterExtensions.get().toSet(),
            strictLocale = strictLocale.get(),
            modulePrefix = modulePrefix.getOrElse(""),
            failOnDuplicates = failOnDuplicates.getOrElse(defaults.failOnDuplicates)
        )
    }
}

typealias KeyMapper = (String, File) -> String

abstract class AndroidResourcesExtension {

    /**
     * lambda to convert keys, useful for example for converting snake_case to camelCase
     * be sure to not break uniqueness of these keys
     */
    @get:Optional
    abstract val keyMapper: Property<KeyMapper>

    /**
     * list of string regexps which will be parsed as Android strings xml.
     * Otherwise, taken as regular file using BinaryResourceKey
     */
    abstract val stringFiles: ListProperty<String>

    /**
     * List of string regexps to mark as "Painter" groups. Any XML file belonging to a group matching regexp
     * will be PainterResourceKey useful for Android XML VectorDrawables to be marked as PainterResourceKey,
     * otherwise they will be BinaryResourceKey
     */
    abstract val painterGroups: ListProperty<String>

    /**
     * list of file extensions always marked as PainterResourceKey
     * [KostraPluginConfig.ImageExts]
     */
    abstract val painterExtensions: ListProperty<String>

    /**
     * list of file extensions always marked as PainterResourceKey
     * KostraPluginConfig#ImageExts
     * abstract val painterExtensions: ListProperty<String>
     */
    @get:Optional
    abstract val resourceDirs: ListProperty<File>

    fun keyMapper(lambda: KeyMapper) {
        keyMapper.set(lambda)
    }

    fun keyMapper(closure: Closure<String>) {
        val wrapper: KeyMapper = { key, file ->
            closure.call(key, file)
        }
        keyMapper.set(wrapper)
    }
}
