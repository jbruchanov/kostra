@file:OptIn(ExperimentalResourceApi::class)
@file:Suppress("unused")

package com.jibru.kostra.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.LocalResourceReader
import org.jetbrains.compose.resources.ResourceReader

internal expect fun jvmResourceReaderOrNull(): ResourceReader?

@Composable
fun kostraResourcesReader(): ResourceReader = jvmResourceReaderOrNull() ?: LocalResourceReader.current
