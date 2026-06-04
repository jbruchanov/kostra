package com.jibru.kostra.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import com.jibru.kostra.KQualifiers
import com.jibru.kostra.KResources
import com.jibru.kostra.PainterResourceKey
import com.jibru.kostra.assetPath
import com.jibru.kostra.binaryByteArray
import org.jetbrains.compose.resources.decodeToSvgPainter

@Composable
private fun KResources.svgPainter(key: PainterResourceKey, qualifiers: KQualifiers): Painter {
    val density = LocalDensity.current
    return remember(this, key, qualifiers, density) {
        binaryByteArray(key, qualifiers).decodeToSvgPainter(density)
    }
}

@Composable
actual fun KResources.painter(key: PainterResourceKey, qualifiers: KQualifiers): Painter {
    val path = assetPath(key, qualifiers)
    return if (path.endsWith(".svg", ignoreCase = true)) {
        svgPainter(key, qualifiers)
    } else {
        composePainter(key, qualifiers)
    }
}
