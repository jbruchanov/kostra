package com.jibru.kostra.compose.ext

import androidx.compose.ui.text.intl.Locale
import com.jibru.kostra.KLocale

fun Locale.toKLocale(): KLocale = KLocale(
    language = language,
    region = region.takeIf { it.isNotEmpty() },
    script = script.takeIf { it.isNotEmpty() },
)
