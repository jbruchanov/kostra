package com.test.kostra.appsample

class JvmPlatform : Platform {
    override val name: String = "jvm-${System.getProperty("java.version")}"
    override val isJvm: Boolean = true
}

actual fun getPlatform(): Platform = JvmPlatform()
