package com.jibru.kostra

import com.jibru.kostra.internal.JvmKostraResourceStorage
import com.jibru.kostra.internal.KostraAssets
import com.jibru.kostra.internal.KostraResourceStorage
import com.jibru.kostra.internal.platformDefaultResourceStorage
import java.io.InputStream

/**
 * JVM convenience returning a [java.io.InputStream] for APIs like `ImageIO.read`. When the active
 * storage is a [JvmKostraResourceStorage] (the classloader-backed default is) it streams straight
 * off the classpath; otherwise it reads the whole resource via [binaryByteArray] and wraps it.
 * Honors an installed [KostraResourceStorage] either way.
 */
fun KResources.binaryInputStream(key: AssetResourceKey, qualifiers: KQualifiers): InputStream {
    val storage = KostraResourceStorage.current() ?: platformDefaultResourceStorage
    return (storage as? JvmKostraResourceStorage)
        ?.openStream("${KostraAssets.RootDir}/${binary.get(key, qualifiers)}")
        ?: binaryByteArray(key, qualifiers).inputStream()
}
