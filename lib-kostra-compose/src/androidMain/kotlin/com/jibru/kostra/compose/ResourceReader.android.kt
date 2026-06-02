@file:OptIn(ExperimentalResourceApi::class)

package com.jibru.kostra.compose

import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.ResourceReader

//Android Compose path: read via AssetManager (the assets pipeline puts files at APK
//assets/kostra_resources/<...>; classloader can't see those, only the AssetManager can).
internal actual fun resourceReaderOrNull(): ResourceReader? = KostraAndroidResourceReader
