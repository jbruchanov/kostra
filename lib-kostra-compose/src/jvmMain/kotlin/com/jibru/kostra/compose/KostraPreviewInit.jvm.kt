package com.jibru.kostra.compose

import androidx.compose.runtime.Composable

@Composable
actual fun KostraPreviewInit() {
    //No-op: Compose Desktop previews don't need a pre-installed Context — JVM resources are read
    //straight from the classloader.
}
