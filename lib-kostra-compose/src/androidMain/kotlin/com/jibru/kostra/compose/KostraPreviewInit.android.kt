package com.jibru.kostra.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.jibru.kostra.internal.KostraAndroidContextHolder

@Composable
actual fun KostraPreviewInit() {
    //Prefer applicationContext over the per-render stub. Compose Multiplatform's
    //PreviewContextConfigurationEffect uses the same shape (`LocalContext.current.applicationContext`)
    //since the BridgeContext layoutlib hands to LocalContext.current is short-lived and rebuilt
    //per render, while the synthetic ApplicationContext persists. For runtime (non-preview) both
    //references point to the real ApplicationContext, so this is a no-op outside @Preview.
    //
    //Caveat for preview: layoutlib's ApplicationContext.assets observed in current Android Studio
    //is typically empty (see the diagnostic added in ResourceLoader.android.kt). The actual
    //preview-time delivery path for kostra files is the JVM-classpath fallback inside
    //AndroidResourceImpl, not this AssetManager — but installing the Context is still the right
    //thing to do, because it lets [KostraAndroidContextHolder]-based callers (image readers,
    //tests) work in preview too the moment AGP/layoutlib starts populating the preview assets.
    //
    //Falls back to LocalContext.current if .applicationContext is null (custom preview hosts).
    val ctx = LocalContext.current
    KostraAndroidContextHolder.set(ctx.applicationContext ?: ctx)
}
