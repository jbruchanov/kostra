package com.jibru.kostra.internal

/**
 * System-property name read by the Android host-test filesystem fallback (and set automatically by the
 * Kostra Gradle plugin on Android `Test` tasks): point it at the directory that contains the
 * `kostra_resources/` folder so Android JVM-host unit tests — which have no `AssetManager` and where
 * the DBs aren't on the classpath — can read resources straight from disk.
 *
 * Defined here in commonMain so the runtime and the Gradle plugin share a single source of truth.
 */
const val KostraResourceRootProperty: String = "kostra.resourcesRoot"

/**
 * A source of Kostra's packaged resources, addressed by their full asset-relative key — i.e. with the
 * [KostraAssets.RootDir] prefix, e.g. `kostra_resources/lib1_string-cs.db`,
 * `kostra_resources/images/foo.png`.
 *
 * Returns whole-file [ByteArray]s (kostra's resources — string/plural DBs, images, fonts — are small
 * enough that streaming buys little, and a `ByteArray` contract is multiplatform unlike
 * `java.io.InputStream`). Each platform ships a default implementation reading from its natural
 * location (JVM classloader, Android `AssetManager`, iOS `NSBundle`, native filesystem); a custom one
 * can be installed via [Companion.set] (e.g. a filesystem storage in a JVM-host unit test).
 *
 * The [Companion] doubles as the registry that resolves reads: an explicitly [set][Companion.set]
 * storage (if it has the key), else the [platformDefaultResourceStorage] for the current target.
 */
interface KostraResourceStorage {
    /** Read all bytes of [key], or throw if [key] is absent (e.g. `UnableToOpenResourceStream`). */
    fun read(key: String): ByteArray

    companion object {
        //Written once early (e.g. Application#attach on Android) before concurrent reads; plain var
        //(java's @Volatile isn't available in commonMain).
        private var installed: KostraResourceStorage? = null

        /** Install the [storage] consulted before the platform default. */
        fun set(storage: KostraResourceStorage) {
            installed = storage
        }

        /** The explicitly-installed storage, or `null` if only the platform default is in effect. */
        internal fun current(): KostraResourceStorage? = installed

        /**
         * Read [key] (a [KostraAssets.RootDir]-prefixed path): the installed storage if it can serve
         * it, else the platform default. The installed storage is probed by attempting the read and
         * falling through on any failure (storages fail fast on a missing key), so no separate
         * existence check is needed.
         */
        fun read(key: String): ByteArray {
            installed?.let { storage -> runCatching { storage.read(key) }.getOrNull()?.let { return it } }
            return platformDefaultResourceStorage.read(key)
        }
    }
}

/** The current target's built-in resource source (JVM classloader, Android assets, iOS NSBundle, native fs). */
internal expect val platformDefaultResourceStorage: KostraResourceStorage
