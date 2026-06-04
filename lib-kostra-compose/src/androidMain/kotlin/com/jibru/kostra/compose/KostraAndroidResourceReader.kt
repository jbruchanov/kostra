@file:OptIn(ExperimentalResourceApi::class)

package com.jibru.kostra.compose

import com.jibru.kostra.UnableToOpenResourceStream
import com.jibru.kostra.internal.KostraResourceStorage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException
import org.jetbrains.compose.resources.ResourceReader

/**
 * Android-specific [ResourceReader] used by the kostra Compose path.
 *
 * Delegates to [KostraResourceStorage], whose Android resolution is: the installed
 * [android.content.res.AssetManager] (runtime via `KostraAndroidContextProvider`, `@Preview` via
 * `KostraPreviewInit()`), then the classloader / `-Dkostra.resourcesRoot=…` filesystem fallback for
 * JVM-host unit tests. The kostra plugin stages every file at `kostra_resources/<key>`, which is the
 * path Compose passes here.
 */
internal object KostraAndroidResourceReader : ResourceReader {

    override suspend fun read(path: String): ByteArray = readBytes(path)

    override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray {
        val bytes = readBytes(path)
        val from = offset.toInt().coerceIn(0, bytes.size)
        val to = (offset + size).toInt().coerceIn(from, bytes.size)
        return bytes.copyOfRange(from, to)
    }

    override fun getUri(path: String): String =
        try {
            //If it resolves through the active storage (AssetManager at runtime), address it as an
            //APK asset; otherwise fall back to the resource's classpath URL (e.g. JVM-host tests).
            KostraResourceStorage.read(path)
            "file:///android_asset/$path"
        } catch (_: UnableToOpenResourceStream) {
            val classLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
            classLoader?.getResource(path)?.toURI()?.toString() ?: throwMissing(path)
        }

    private fun readBytes(path: String): ByteArray =
        try {
            KostraResourceStorage.read(path)
        } catch (_: UnableToOpenResourceStream) {
            throwMissing(path)
        }

    private fun throwMissing(path: String): Nothing = throw MissingResourceException(path)
}
