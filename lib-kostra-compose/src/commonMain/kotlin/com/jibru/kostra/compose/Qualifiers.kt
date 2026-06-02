package com.jibru.kostra.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalWithComputedDefaultOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.intl.Locale
import com.jibru.kostra.DefaultQualifiersProvider
import com.jibru.kostra.KDpi
import com.jibru.kostra.KLocale
import com.jibru.kostra.KQualifiers

/**
 * Kostra qualifiers active in the current composition.
 *
 * The default lambda runs in `CompositionLocalAccessorScope` and re-evaluates each time anyone
 * reads `LocalQualifiers.current` without an outer provider:
 *
 *  - If consumer code has registered a delegate on [DefaultQualifiersProvider]
 *    ([DefaultQualifiersProvider.hasOverriddenDefault]), that delegate's value wins. This is the
 *    intentional knob for hosts that compute qualifiers from a custom source (e.g. an in-app
 *    locale switcher unrelated to the Compose locale).
 *  - Otherwise:
 *    - **DPI** comes from [LocalDensity] via `currentValue` — the accessor scope subscribes to
 *      that CompositionLocal, so `@Preview(device = "spec:…,dpi=N")` and runtime density changes
 *      re-resolve the qualifier automatically. This is verified against the sample's MDPI / HDPI /
 *      XHDPI previews in `SampleScreenPreview`.
 *    - **Locale** comes from Compose's [Locale.current]. Important caveat: `Locale.current` is a
 *      delegate property that reads `Resources.getSystem().configuration`, NOT a CompositionLocal,
 *      and `@Preview(locale = "…")` overrides `LocalConfiguration` but does NOT override the
 *      system Resources. So preview-locale overrides may not propagate here — runtime locale and
 *      explicit [ProvideLocalQualifiers] do. If you need preview-locale awareness, prefer wrapping
 *      the preview body in `ProvideLocalQualifiers(KQualifiers(KLocale("cs"), …)) { … }`.
 *
 * Manual per-scope overrides via [ProvideLocalQualifiers] / `CompositionLocalProvider(LocalQualifiers provides …)`
 * always take precedence — the default lambda only runs when no provider is in scope.
 */
val LocalQualifiers: ProvidableCompositionLocal<KQualifiers> = compositionLocalWithComputedDefaultOf {
    if (DefaultQualifiersProvider.hasOverriddenDefault) {
        DefaultQualifiersProvider.current
    } else {
        KQualifiers(
            locale = Locale.current.toKLocale(),
            dpi = KDpi.getClosest(LocalDensity.currentValue.density),
        )
    }
}

@Composable
fun ProvideLocalQualifiers(qualifiers: KQualifiers = DefaultQualifiersProvider.current, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalQualifiers provides qualifiers) {
        content()
    }
}

internal fun Locale.toKLocale(): KLocale = KLocale(
    language = language,
    region = region.takeIf { it.isNotEmpty() },
    script = script.takeIf { it.isNotEmpty() },
)
