@file:OptIn(ExperimentalResourceApi::class)

package com.jibru.kostra.compose

import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.ResourceReader

internal actual fun resourceReaderOrNull(): ResourceReader? = JvmResourcesReader
