@file:OptIn(ExperimentalResourceApi::class)

package com.jibru.kostra.compose

import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException
import org.jetbrains.compose.resources.ResourceReader
import java.io.InputStream

internal actual fun jvmResourceReaderOrNull(): ResourceReader? = JvmResourcesReader
