package com.jibru.kostra.internal

import com.jibru.kostra.UnableToOpenResourceStream
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.posix.memcpy

internal actual fun loadResource(key: String): ByteArray = UIKitResource.readBytes(key)

private object UIKitResource {
    @OptIn(ExperimentalForeignApi::class)
    fun readBytes(path: String): ByteArray {
        val fileManager = NSFileManager.defaultManager()
        // todo: support fallback path at bundle root?
        val composeResourcesPath = NSBundle.mainBundle.resourcePath + "/compose-resources/" + path
        val contentsAtPath: NSData? = fileManager.contentsAtPath(composeResourcesPath)
        if (contentsAtPath != null) {
            val byteArray = ByteArray(contentsAtPath.length.toInt())
            byteArray.usePinned {
                memcpy(it.addressOf(0), contentsAtPath.bytes, contentsAtPath.length)
            }
            return byteArray
        } else {
            throw UnableToOpenResourceStream("Path:'$path'\nFullPath:'$composeResourcesPath'")
        }
    }
}
