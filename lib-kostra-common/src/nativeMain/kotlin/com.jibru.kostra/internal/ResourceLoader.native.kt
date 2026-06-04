@file:OptIn(ExperimentalForeignApi::class)

package com.jibru.kostra.internal

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import platform.posix.FILE
import platform.posix.SEEK_END
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.getcwd
import platform.posix.opendir
import platform.posix.rewind

internal actual val platformDefaultResourceStorage: KostraResourceStorage = NativeResourceStorage

/**
 * [KostraResourceStorage] reading from the filesystem next to the executable. The kostra plugin
 * stages every resource at `kostra_resources/<key>`; native consumers must arrange the same layout
 * next to the executable (see README "Native variant"). [key] arrives already prefixed with the
 * assets root.
 */
private object NativeResourceStorage : KostraResourceStorage {

    //readAllBytes throws (requireNotNull on fopen) for a missing key.
    override fun read(key: String): ByteArray = readAllBytes(resolve(key))

    private fun resolve(key: String): String = "$currentDir$pathSeparator$key"

    private val currentDir by lazy {
        val dir = opendir(".")
        memScoped {
            //0.convert(), the convert seems tobe necessary for build in linux
            val buffer = getcwd(null, 0.convert())
            buffer?.toKString() ?: ""
        }.also {
            closedir(dir)
        }
    }

    private val pathSeparator by lazy {
        val seps = listOf('\\', '/')
        //very naive way
        seps.minBy { currentDir.indexOf(it).takeIf { i -> i != -1 } ?: Int.MAX_VALUE }
    }

    private fun readAllBytes(path: String): ByteArray {
        //b very important to binary reading, otherwise might end before real end of file
        val file: CPointer<FILE>? = fopen(path, "rb")
        requireNotNull(file) { "Unable to open '$path'" }

        fseek(file, 0, SEEK_END)
        val size = ftell(file)
        rewind(file)

        return memScoped {
            val tmp = allocArray<ByteVar>(size)
            val read = fread(tmp, sizeOf<ByteVar>().convert(), size.convert(), file)
            require(read.toLong() == size.toLong()) { "Read $read vs fileLen:$size must be equal!" }
            tmp.readBytes(size.convert())
        }.also {
            fclose(file)
        }
    }
}
