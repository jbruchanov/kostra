package com.jibru.kostra.internal

/**
 * Layout used by the Kostra Gradle plugin and the runtime loaders to find resource files.
 *
 * The plugin writes every file at `<task-output>/kostra_resources/<key>` and wires that staging
 * dir into AGP's assets pipeline via `variant.sources.assets.addGeneratedSourceDirectory`. AGP
 * then places the contents at APK `assets/kostra_resources/<key>` — inside the APK's `assets/`
 * folder. AssetManager paths are relative to APK `assets/`, so [RootDir] is what runtime / preview
 * loaders pass to `AssetManager.open`.
 *
 * For non-Android KMP targets (JVM, iOS, native) the same `kostra_resources/<key>` path is also
 * the JAR-resource / bundle path; classloader / NSBundle lookups read there.
 *
 * K-class keys stay prefix-free (e.g. `"binary.db"`, `"lib1/gear1.png"`); runtime loaders prepend
 * [RootDir] before delegating to the platform reader.
 */
object KostraAssets {
    /** Sub-folder under APK `assets/` (Android) and the resource root on every other platform. */
    const val RootDir = "kostra_resources"
}
