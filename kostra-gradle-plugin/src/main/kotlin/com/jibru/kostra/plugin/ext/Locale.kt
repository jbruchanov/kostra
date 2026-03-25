package com.jibru.kostra.plugin.ext

import com.jibru.kostra.KLocale

// Format key as a readable Long literal with underscore digit grouping
internal fun KLocale.formattedDbKey(): String {
    val str = key.toString()
    // Add underscore every 3 digits from the right for readability
    val grouped = str.reversed().chunked(3).joinToString("_").reversed()
    return "${grouped}L"
}

// Format per-category breakdown for comments: "lang|region|script"
internal fun KLocale.categoryComment(): String = buildString {
    append(language)
    region?.let { append('|'); append(it) }
    script?.let { append('|'); append(it) }
}
