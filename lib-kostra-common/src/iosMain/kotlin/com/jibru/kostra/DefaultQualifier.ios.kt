package com.jibru.kostra

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.scriptCode
import platform.UIKit.UIScreen

actual fun defaultQualifiers(): KQualifiers = KQualifiers(
    locale = NSLocale.currentLocale.let { l -> KLocale(l.languageCode, l.countryCode, l.scriptCode?.takeIf { it.isNotEmpty() }) },
    dpi = KDpi.getClosest(UIScreen.mainScreen.scale.toFloat()),
)
