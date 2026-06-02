package com.jibru.kostra.internal

import com.jibru.kostra.UnableToOpenResourceStream
import java.io.InputStream

internal actual fun loadResource(key: String): ByteArray = JvmResourceImpl.getStream(key).readBytes()

internal fun openResource(key: String): InputStream = JvmResourceImpl.getStream(key)

private object JvmResourceImpl {
    fun getStream(key: String): InputStream {
        //Plugin stages at JAR path kostra_resources/<key> via the per-target source-set wiring
        //(jvmMain.resources.srcDir(staging) inside the kostra plugin).
        val path = "${KostraAssets.RootDir}/$key"
        val classLoader = Thread.currentThread().contextClassLoader ?: (javaClass.classLoader)
        val resourceStream = classLoader.getResourceAsStream(path)
        return resourceStream ?: throw UnableToOpenResourceStream(path)
    }
}
