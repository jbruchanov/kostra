@file:OptIn(ExperimentalResourceApi::class)

package com.jibru.kostra.compose

import android.content.res.AssetManager
import com.jibru.kostra.internal.KostraAndroidContextHolder
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException
import org.jetbrains.compose.resources.ResourceReader
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Android-specific [ResourceReader] used by the kostra Compose path.
 *
 * Reads via the Application Context's [AssetManager]. The kostra plugin places every file at APK
 * `assets/kostra_resources/<key>` via AGP's real assets pipeline
 * (`KotlinMultiplatformAndroidComponentsExtension.onVariants { variant.sources.assets.addGenerated... }`),
 * so `AssetManager.open("kostra_resources/<key>")` is the canonical read path — both at runtime
 * (Application Context auto-installed by `KostraAndroidContextProvider`) and in `@Preview`
 * (Context installed manually by `KostraPreviewInit()`).
 *
 * Falls back to the classloader for JVM-host unit tests that run on the Android source set
 * without an Application Context — there the files are on the test classpath at the same
 * `kostra_resources/<key>` path.
 *
 * This replaces the older [JvmResourcesReader]-on-Android wiring, which used the classloader
 * everywhere. Since the assets pipeline now puts files in the APK's real `assets/` folder (not in
 * `classes.dex`'s JAR resources), a classloader-only lookup couldn't reach them on Android.
 */
internal object KostraAndroidResourceReader : ResourceReader {

    override suspend fun read(path: String): ByteArray = openStream(path).use { it.readBytes() }

    override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray {
        val result = ByteArray(size.toInt())
        openStream(path).use { input ->
            input.skipBytes(offset)
            input.readBytes(result, 0, size.toInt())
        }
        return result
    }

    override fun getUri(path: String): String {
        //AssetManager-backed assets are addressed via the special file:///android_asset/<path> URI;
        //the classloader fallback uses the resource's classpath URL when present.
        val assets = KostraAndroidContextHolder.getOrNull()?.assets
        return if (assets != null && assets.hasFile(path)) {
            "file:///android_asset/$path"
        } else {
            val classLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
            classLoader?.getResource(path)?.toURI()?.toString() ?: throwMissing(path)
        }
    }

    private fun openStream(path: String): InputStream {
        //Compose passes `kostra_resources/<key>` — AssetManager-relative path. AGP puts files at
        //APK `assets/kostra_resources/<key>` via the variant.sources.assets pipeline, so the same
        //path works directly with AssetManager.
        val assets: AssetManager? = KostraAndroidContextHolder.getOrNull()?.assets
        if (assets != null) {
            try {
                return assets.open(path)
            } catch (_: FileNotFoundException) {
                // fall through to classloader
            }
        }
        //Classloader fallback for JVM-host unit-test variants (Android source set running on JVM).
        val classLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
        return classLoader?.getResourceAsStream(path) ?: throwMissing(path)
    }

    private fun AssetManager.hasFile(path: String): Boolean = try {
        open(path).close()
        true
    } catch (_: FileNotFoundException) {
        false
    }

    //AssetManager doesn't expose skipNBytes (API 34+) — emulate it.
    private fun InputStream.skipBytes(offset: Long) {
        var skipped = 0L
        while (skipped < offset) {
            val n = skip(offset - skipped)
            if (n <= 0) break
            skipped += n
        }
    }

    //AssetManager doesn't expose readNBytes (API 34+) — emulate it.
    private fun InputStream.readBytes(byteArray: ByteArray, offset: Int, size: Int) {
        var read = 0
        while (read < size) {
            val n = read(byteArray, offset + read, size - read)
            if (n <= 0) break
            read += n
        }
    }

    private fun throwMissing(path: String): Nothing = throw MissingResourceException(path)
}
