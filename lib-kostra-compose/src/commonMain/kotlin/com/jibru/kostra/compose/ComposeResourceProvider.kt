@file:OptIn(InternalResourceApi::class, ExperimentalResourceApi::class)
@file:Suppress("unused")

package com.jibru.kostra.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.jibru.kostra.KQualifiers
import com.jibru.kostra.KResources
import com.jibru.kostra.PainterResourceKey
import com.jibru.kostra.assetPath
import com.jibru.kostra.internal.KostraAssets
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LocalResourceReader
import org.jetbrains.compose.resources.ResourceItem
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun KResources.composePainter(key: PainterResourceKey, qualifiers: KQualifiers): Painter {
    //Cache the asset-path lookup + DrawableResource per (key, qualifiers). Without this remember,
    //a fresh DrawableResource is allocated on every recomposition and Compose-Resources' internal
    //caches (keyed on the DrawableResource) get silently invalidated.
    val resolved = remember(this, key, qualifiers) {
        val assetKey = assetPath(key, qualifiers)
        require(!assetKey.endsWith(".svg", ignoreCase = true)) {
            "Unsupported SVG on current platform, key:$key, asset:'$assetKey'"
        }
        //The kostra plugin stages every file under `<outDir>/kostra_resources/<assetKey>`. On
        //Android the staging dir is wired into AGP's assets pipeline (APK assets/kostra_resources/),
        //and the same `kostra_resources/<assetKey>` path is what AssetManager / classloader /
        //NSBundle expect on each platform. Compose-MP's ResourceReader calls the underlying
        //platform reader (our KostraAndroidResourceReader on Android, JvmResourcesReader on JVM,
        //default on iOS) with this path.
        val readerPath = "${KostraAssets.RootDir}/$assetKey"
        val isXml = readerPath.endsWith(".xml") || readerPath.endsWith(".vxml")
        ResolvedPainter(readerPath = readerPath, isXml = isXml, drawable = readerPath.toDrawableResource())
    }
    val (readerPath, isXml, drawable) = resolved

    //Compose-Multiplatform's default Android ResourceReader hits AssetManager first and the
    //classloader only as a fallback — at @Preview time the AssetManager has no kostra files
    //(KMP-Android library AARs can't ship an `assets/` folder, the files live in the AAR's
    //classes.jar at JAR path `assets/kostra_resources/<...>`). Install Kostra's classloader-based
    //reader here so vectorResource/imageResource resolve via the classloader, which DOES find
    //the file at preview time and at app runtime alike.
    //
    //CompositionLocalProvider returns Unit, so use the 1-slot holder idiom to thread the painter
    //out of the scope. This is the standard Compose workaround for "want a typed return value
    //from inside a CompositionLocal-scoped block" and is invoked once per resolved (key,
    //qualifiers) tuple thanks to the surrounding remember.
    val reader = kostraResourcesReader()
    val painterSlot = remember { arrayOfNulls<Painter>(1) }
    CompositionLocalProvider(LocalResourceReader provides reader) {
        painterSlot[0] = if (isXml) {
            rememberVectorPainter(vectorResource(drawable))
        } else {
            val imageResource = imageResource(drawable)
            remember(imageResource) { BitmapPainter(imageResource) }
        }
    }
    return painterSlot[0] ?: error("Kostra: unable to load painter for key '$key' at '$readerPath'")
}

private data class ResolvedPainter(val readerPath: String, val isXml: Boolean, val drawable: DrawableResource)

private fun String.toDrawableResource() = DrawableResource(this, setOf(ResourceItem(path = this, qualifiers = emptySet(), offset = 0L, size = -1)))

@Composable
expect fun KResources.painter(key: PainterResourceKey, qualifiers: KQualifiers): Painter
