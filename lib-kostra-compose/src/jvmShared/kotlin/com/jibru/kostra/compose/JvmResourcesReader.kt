package com.jibru.kostra.compose

import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException
import org.jetbrains.compose.resources.ResourceReader
import java.io.InputStream

/**
 * Copy of default resources loader for android, just going directly to class resources instead of assetManager and instrumentation.
 */
@OptIn(ExperimentalResourceApi::class)
internal object JvmResourcesReader : ResourceReader {

    override suspend fun read(path: String): ByteArray {
        val resource = getResourceAsStream(path)
        return resource.use { input -> input.readBytes() }
    }

    override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray {
        val resource = getResourceAsStream(path)
        val result = ByteArray(size.toInt())
        resource.use { input ->
            input.skipBytes(offset)
            input.readBytes(result, 0, size.toInt())
        }
        return result
    }

    //skipNBytes requires API 34
    private fun InputStream.skipBytes(offset: Long) {
        var skippedBytes = 0L
        while (skippedBytes < offset) {
            val count = skip(offset - skippedBytes)
            if (count == 0L) break
            skippedBytes += count
        }
    }

    //readNBytes requires API 34
    private fun InputStream.readBytes(byteArray: ByteArray, offset: Int, size: Int) {
        var readBytes = 0
        while (readBytes < size) {
            val count = read(byteArray, offset + readBytes, size - readBytes)
            if (count <= 0) break
            readBytes += count
        }
    }

    override fun getUri(path: String): String {
        val classLoader = getClassLoader()
        val resource = classLoader.getResource(path) ?: throwMissingResourceException(path)
        return resource.toURI().toString()
    }

    private fun getResourceAsStream(path: String): InputStream {
        val classLoader = getClassLoader()
        return classLoader.getResourceAsStream(path) ?: throwMissingResourceException(path)
    }

    private fun throwMissingResourceException(path: String): Nothing {
        throw MissingResourceException(path)
    }

    private fun getClassLoader(): ClassLoader {
        return (this::class as Any).javaClass.classLoader
    }
}
