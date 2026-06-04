@file:Suppress("unused")

package com.jibru.kostra.compose

import androidx.compose.runtime.Composable

/**
 * Initializes the platform-specific context Kostra needs for loading resources in IDE previews.
 *
 * Call this at the top of `@Preview` composables that live in `commonMain` and access Kostra
 * resources:
 *
 * ```
 * @Preview
 * @Composable
 * fun MyScreenPreview() {
 *     KostraPreviewInit()
 *     MyScreen()
 * }
 * ```
 *
 * Platform behaviour:
 *  - **Android**: installs `LocalContext.current` into
 *    [com.jibru.kostra.internal.KostraResourceStorage]. Required because Android Studio's
 *    `@Preview` host does NOT instantiate library `ContentProvider`s, so the auto-installer
 *    `KostraAndroidContextProvider` never fires and Kostra's asset lookups would otherwise throw.
 *    Intended for `@Preview` only — at app runtime the ContentProvider has already installed the
 *    Application Context, and calling this in a real Activity would be rejected by Kostra's
 *    Activity-guard (passing an Activity context as the holder would leak it).
 *  - **JVM / iOS / native**: no-op. Preview hosts on these platforms either don't exist or load
 *    resources via the standard classloader / bundle path that doesn't require pre-initialised
 *    state.
 */
@Composable
expect fun KostraPreviewEffect()
