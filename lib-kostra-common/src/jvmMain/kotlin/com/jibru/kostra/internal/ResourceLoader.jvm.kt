package com.jibru.kostra.internal

import com.jibru.kostra.UnableToOpenResourceStream
import java.io.InputStream

internal actual val platformDefaultResourceStorage: KostraResourceStorage = ClassLoaderResourceStorage

/**
 * A [KostraResourceStorage] that can additionally hand out a lazy [InputStream] for a key — a
 * JVM-only capability for stream-consuming APIs (e.g. `ImageIO.read`). `KResources.binaryInputStream`
 * type-checks for it; a storage that doesn't implement it falls back to reading the whole resource.
 */
interface JvmKostraResourceStorage : KostraResourceStorage {
    /** Open [key] (a [KostraAssets.RootDir]-prefixed path) as a stream, or throw if absent. */
    fun openStream(key: String): InputStream
}

/**
 * [JvmKostraResourceStorage] backed by the JVM classloader. The kostra plugin stages resources at
 * JAR path `kostra_resources/<key>` (jvmMain.resources.srcDir(staging)).
 */
internal object ClassLoaderResourceStorage : JvmKostraResourceStorage {
    //openStream throws UnableToOpenResourceStream for a missing key.
    override fun read(key: String): ByteArray = openStream(key).use { it.readBytes() }

    override fun openStream(key: String): InputStream =
        classLoader().getResourceAsStream(key) ?: throw UnableToOpenResourceStream(key)

    private fun classLoader(): ClassLoader =
        Thread.currentThread().contextClassLoader ?: javaClass.classLoader
}
