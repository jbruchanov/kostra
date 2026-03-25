package com.jibru.kostra

import java.awt.GraphicsEnvironment
import java.util.Locale as JvmLocale

private fun JvmLocale.toKLocale(): KLocale = KLocale(
    language = language,
    region = country.takeIf { it.isNotEmpty() },
    script = script.takeIf { it.isNotEmpty() },
)

actual fun defaultQualifiers(): KQualifiers {
    val kQualifiers = KQualifiers(
        locale = JvmLocale.getDefault().toKLocale(),
        //taken from JVM LocalDensity
        dpi = GraphicsEnvironment.getLocalGraphicsEnvironment()
            ?.takeIf { !it.isHeadlessInstance }
            ?.defaultScreenDevice
            ?.defaultConfiguration
            ?.defaultTransform
            ?.scaleX?.toFloat()
            ?.let { KDpi.getClosest(it) }
            ?: KDpi.Undefined,
    )
    return kQualifiers
}
