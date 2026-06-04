package com.jibru.kostra.internal

import com.jibru.kostra.UnableToOpenResourceStream
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.posix.memcpy

internal actual val platformDefaultResourceStorage: KostraResourceStorage = NSBundleResourceStorage

/**
 * [KostraResourceStorage] backed by the iOS app bundle. KMP packages JAR resources under
 * `<bundle>/compose-resources/<jar-path>`, and the kostra plugin stages at `kostra_resources/<key>`,
 * so a [key] of `kostra_resources/<x>` maps to `<resourcePath>/compose-resources/kostra_resources/<x>`.
 */
private object NSBundleResourceStorage : KostraResourceStorage {

    @OptIn(ExperimentalForeignApi::class)
    override fun read(key: String): ByteArray {
        val path = fullPath(key)
        val contents: NSData = NSFileManager.defaultManager().contentsAtPath(path)
            ?: throw UnableToOpenResourceStream("Key:'$key'\nFullPath:'$path'")
        val bytes = ByteArray(contents.length.toInt())
        if (bytes.isNotEmpty()) {
            bytes.usePinned { memcpy(it.addressOf(0), contents.bytes, contents.length) }
        }
        return bytes
    }

    private fun fullPath(key: String): String =
        NSBundle.mainBundle.resourcePath + "/compose-resources/" + key
}
