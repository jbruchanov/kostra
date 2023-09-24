@file:OptIn(ExperimentalResourceApi::class)
@file:Suppress("unused")

package com.jibru.kostra.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.jibru.kostra.AssetResourceKey
import com.jibru.kostra.KQualifiers
import com.jibru.kostra.KResources
import com.jibru.kostra.PainterResourceKey
import com.jibru.kostra.PluralResourceKey
import com.jibru.kostra.Plurals
import com.jibru.kostra.StringResourceKey
import com.jibru.kostra.assetPath
import com.jibru.kostra.icu.IFixedDecimal
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.resource

@Composable
fun KResources.string(key: StringResourceKey): String =
    string.get(key, LocalQualifiers.current)

@Composable
fun KResources.string(key: StringResourceKey, vararg formatArgs: Any): String =
    string.get(key, LocalQualifiers.current, *formatArgs)

@Composable
fun KResources.plural(key: PluralResourceKey, quantity: Int): String =
    plural.get(key, LocalQualifiers.current, quantity, Plurals.Type.Plurals)

@Composable
fun KResources.plural(key: PluralResourceKey, quantity: IFixedDecimal): String =
    plural.get(key, LocalQualifiers.current, quantity, Plurals.Type.Plurals)

@Composable
fun KResources.plural(key: PluralResourceKey, quantity: Int, vararg formatArgs: Any): String =
    plural.get(key, LocalQualifiers.current, quantity, Plurals.Type.Plurals, *formatArgs)

@Composable
fun KResources.plural(key: PluralResourceKey, quantity: IFixedDecimal, vararg formatArgs: Any): String =
    plural.get(key, LocalQualifiers.current, quantity, Plurals.Type.Plurals, *formatArgs)

@Composable
fun KResources.ordinal(key: PluralResourceKey, quantity: Int): String =
    plural.get(key, LocalQualifiers.current, quantity, Plurals.Type.Ordinals)

@Composable
fun KResources.ordinal(key: PluralResourceKey, quantity: IFixedDecimal): String =
    plural.get(key, LocalQualifiers.current, quantity, Plurals.Type.Ordinals)

@Composable
fun KResources.ordinal(key: PluralResourceKey, quantity: Int, vararg formatArgs: Any): String =
    plural.get(key, LocalQualifiers.current, quantity, Plurals.Type.Ordinals, *formatArgs)

@Composable
fun KResources.ordinal(key: PluralResourceKey, quantity: IFixedDecimal, vararg formatArgs: Any): String =
    plural.get(key, LocalQualifiers.current, quantity, Plurals.Type.Ordinals, *formatArgs)

@Composable
fun KResources.assetPath(key: AssetResourceKey): String =
    binary.get(key, LocalQualifiers.current)

//TODO: default values iOS bug
//https://github.com/JetBrains/compose-multiplatform/issues/3643
@Composable
fun KResources.assetPath(key: AssetResourceKey, qualifiers: KQualifiers): String =
    binary.get(key, qualifiers)

suspend fun KResources.binaryByteArray(key: AssetResourceKey, qualifiers: KQualifiers): ByteArray =
    resource(binary.get(key, qualifiers)).readBytes()

@Composable
internal fun KResources.composePainter(key: PainterResourceKey): Painter {
    val path = assetPath(key, LocalQualifiers.current)
    require(!path.endsWith(".svg", ignoreCase = true)) { "Unsupported SVG on current platform, key:$key, asset:'$path'" }
    return org.jetbrains.compose.resources.painterResource(path)
}

@Composable
expect fun KResources.painter(key: PainterResourceKey): Painter
