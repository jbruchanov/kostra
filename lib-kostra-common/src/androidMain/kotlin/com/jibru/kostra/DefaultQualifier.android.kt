package com.jibru.kostra

import android.content.res.Resources
import java.util.Locale as JvmLocale

private fun defaultDpi(resources: Resources = Resources.getSystem()): KDpi = KDpi.getClosest(resources.displayMetrics.density)
private fun jvmDefaultLocale() = JvmLocale.getDefault().let { KLocale(it.language, it.country.takeIf { code -> code.isEmpty() || code.length == 2 }) }
private fun androidDefaultLocale(resources: Resources = Resources.getSystem()) =
    resources.configuration.locales.let { if (it.size() > 0) KLocale(it[0].language, it[0].country) else null }

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
