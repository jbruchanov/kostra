@file:OptIn(ExperimentalResourceApi::class)

package com.jibru.kostra.compose

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.LocalResourceReader
import org.jetbrains.compose.resources.ResourceReader

/**
 * Kostra's [ResourceReader] override, or `null` to keep Compose-Multiplatform's default.
 *
 * Returns a kostra reader on the platforms whose default CMP reader can't locate kostra files in
 * every context — Android ([KostraAndroidResourceReader]) and desktop JVM ([JvmResourcesReader]) —
 * and `null` on iOS, where CMP's default bundle-based reader already finds them.
 */
internal expect fun resourceReaderOrNull(): ResourceReader?

/**
 * The [ResourceReader] kostra's Compose painter path should read through.
 *
 * Compose-Multiplatform's `imageResource()` / `vectorResource()` resolve their bytes via whatever
 * reader is installed in [LocalResourceReader]. Kostra stages its files at `kostra_resources/<key>`;
 * on Android `@Preview` (empty preview AssetManager) and JVM-host tests, CMP's default reader can't
 * reach them, so [composePainter] installs this reader around those calls instead.
 *
 * Resolution: use [resourceReaderOrNull] when the platform needs an override (Android, desktop JVM),
 * otherwise fall back to CMP's current [LocalResourceReader] (iOS, and any custom reader a host has
 * already provided up the tree). `@Composable` because the fallback reads a CompositionLocal.
 *
 * At real app runtime the override is effectively redundant — kostra ships files at the canonical
 * path CMP's own readers look — but it's required for preview/test image rendering.
 */
@Composable
fun kostraResourcesReader(): ResourceReader = resourceReaderOrNull() ?: LocalResourceReader.current
