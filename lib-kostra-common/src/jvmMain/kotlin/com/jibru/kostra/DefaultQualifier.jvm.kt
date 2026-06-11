package com.jibru.kostra

import java.awt.GraphicsEnvironment
import java.util.Locale as JvmLocale

private fun JvmLocale.toKLocale(): KLocale = KLocale(
    language = language,
    region = country.takeIf { it.isNotEmpty() },
    script = script.takeIf { it.isNotEmpty() },
)

//Density scale of a standard desktop monitor at the default 96-DPI baseline (e.g. a
//1280x1024 display): 96/96 = 1.0, which maps to MDPI. Used when no real screen is available
//so density-qualified-only resources still resolve instead of failing with KDpi.Undefined.
//Headless JVMs hit this: CI, Gradle test workers (java.awt.headless=true by default), and
//sessions with no WindowServer.
private const val HeadlessDefaultDensity = 1.0f

actual fun defaultQualifiers(): KQualifiers = KQualifiers(
    locale = JvmLocale.getDefault().toKLocale(),
    //taken from JVM LocalDensity; falls back to a default desktop density when headless
    dpi = KDpi.getClosest(screenDensityOrDefault()),
)

//runCatching also swallows java.awt.AWTError ("WindowServer is not available"), which is
//thrown — not returned as headless — when java.awt.headless=false but no display backs the
//session (e.g. an SSH/background process). Both that and a genuinely headless environment
//fall back to [HeadlessDefaultDensity].
private fun screenDensityOrDefault(): Float = runCatching {
    GraphicsEnvironment.getLocalGraphicsEnvironment()
        .takeIf { !it.isHeadlessInstance }
        ?.defaultScreenDevice
        ?.defaultConfiguration
        ?.defaultTransform
        ?.scaleX?.toFloat()
}.getOrNull() ?: HeadlessDefaultDensity
