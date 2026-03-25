package com.jibru.kostra

import android.content.res.Resources
import java.util.Locale as JvmLocale

private fun JvmLocale.toKLocale(): KLocale = KLocale(
    language = language,
    region = country.takeIf { code -> code.isEmpty() || code.length in 2..3 },
    script = script.takeIf { s -> s.isNotEmpty() }
)

private fun defaultDpi(resources: Resources = Resources.getSystem()): KDpi = KDpi.getClosest(resources.displayMetrics.density)
private fun jvmDefaultLocale() = JvmLocale.getDefault().toKLocale()

private fun androidDefaultLocale(resources: Resources = Resources.getSystem()) = resources.configuration.locales.let {
    if (it.size() > 0) it[0].toKLocale() else null
}

actual fun defaultQualifiers(): KQualifiers = KQualifiers(
    locale = androidDefaultLocale() ?: jvmDefaultLocale(),
    dpi = defaultDpi(),
)

fun defaultQualifiers(resources: Resources): KQualifiers {
    return KQualifiers(
        locale = androidDefaultLocale(resources) ?: jvmDefaultLocale(),
        dpi = defaultDpi(resources),
    )
}
