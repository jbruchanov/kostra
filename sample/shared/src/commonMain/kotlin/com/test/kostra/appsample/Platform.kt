package com.test.kostra.appsample

interface Platform {
    val name: String
    val isIos: Boolean get() = false
    val isAndroid: Boolean get() = false
    val isJvm: Boolean get() = false
}

expect fun getPlatform(): Platform
