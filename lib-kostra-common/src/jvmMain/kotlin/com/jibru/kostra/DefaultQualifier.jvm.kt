package com.jibru.kostra

import com.jibru.kostra.internal.Dpi
import com.jibru.kostra.internal.Locale
import com.jibru.kostra.internal.Qualifiers
import java.awt.GraphicsEnvironment
import java.util.Locale as JvmLocale

actual fun defaultQualifiers(): Qualifiers = Qualifiers(
    locale = JvmLocale.getDefault().let { Locale(it.language, it.country.takeIf { code -> code.isEmpty() || code.length == 2 }) },
    dpi = Dpi.getClosest(
        //taken from JVM LocalDensity
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .defaultConfiguration
            .defaultTransform
            .scaleX.toFloat(),
    ),
)
