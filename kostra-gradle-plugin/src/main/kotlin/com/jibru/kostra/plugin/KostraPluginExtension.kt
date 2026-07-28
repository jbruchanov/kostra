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
     * Project paths (e.g. `":shared-lib1"`) of Kostra-bearing dependency modules whose resources
     * must be bundled into THIS module's native (Kotlin/Native) outputs.
     *
     * KMP does not merge a dependency module's native resources into a consumer's static framework
     * (compose-multiplatform#3391). A JVM consumer gets a dependency's resources via its JAR and an
     * Android consumer via AAR asset merging, but a Kotlin/Native consumer (e.g. an iOS static
     * framework) does not — so a module that depends on other Kostra modules and ships a native
     * target must list them here. For each path the plugin wires that module's generated
     * `kostra_resources/<...>` assets into every native target's main resources and makes this
     * module's `generateDatabases` depend on the dependency's, so the assets are generated before
     * any native resource packaging runs.
     *
     * Has no effect on JVM/Android targets. Empty by default.
     */
    abstract val nativeResourceDependencies: ListProperty<String>

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
     * Default: false, so `analyseResources` (and thus every build) doesn't fail on work-in-progress
     * translations. Enable it to fail the build eagerly, or leave it off and run the always-on
     * `validateResources` task on CI to enforce coverage on demand. Fails with the list of missing keys.
     */
    abstract val strictMode: Property<Boolean>

    /**
     * Not nicely working autoupdate, MVP, don't use IDE doesn't see the changes.
     */
    abstract val useFileWatcher: Property<Boolean>

    /**
     * Filename prefix applied to every Kostra-generated database file (e.g. "lib1_" yields
     * `assets/kostra_resources/lib1_binary.db`). Derived from [modulePrefix]; empty when no module
     * prefix is set, in which case DB files sit directly at the root of `kostra_resources/`.
     *
     * The name stayed as `outputDatabaseDirName` for source-compat with consumers but the value
     * is no longer a directory name — there is no longer a `kresources/` sub-folder; DBs live
     * at the root of the assets/kostra_resources/ tree alongside the binary file resources.
     */
    val outputDatabaseDirName: Provider<String>
        get() = modulePrefix.map { it.lowerCasedWith("") }.orElse("")

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
