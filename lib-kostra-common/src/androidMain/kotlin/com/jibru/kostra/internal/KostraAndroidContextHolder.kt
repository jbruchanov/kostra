package com.jibru.kostra.internal

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.jibru.kostra.internal.KostraAndroidContextHolder.get
import com.jibru.kostra.internal.KostraAndroidContextHolder.set

/**
 * Application Context used by Kostra to load resources from APK assets/ on Android.
 *
 * **Why this exists.** Kostra packages its generated database files and binary resources into the
 * APK's `assets/` directory (the same workaround Compose Multiplatform uses, because Kotlin Multiplatform
 * commonMain JVM resources don't reliably reach the Android classloader). Loading from `assets/` at
 * runtime requires an [android.content.res.AssetManager], which in turn needs an [android.content.Context].
 * Kostra's resource APIs are context-less by design, so we capture an Application Context up-front
 * via the mechanisms below.
 *
 * **How the context is discovered (in order):**
 * 1. Primary: [KostraAndroidContextProvider], a tiny ContentProvider declared in lib-kostra-common's
 *    AndroidManifest. AGP manifest-merges it into every consumer APK. Android instantiates content
 *    providers during `Application#attach`, **before** any Activity, so the context is set before any
 *    Kostra call runs.
 * 2. Manual override: call [set] from your `Application.onCreate()` (or anywhere with access to the
 *    Application Context). Useful for hosts that suppress library content providers (tools:node="remove",
 *    process restrictions, instrumentation, etc.).
 * 3. Last-resort fallback: reflection on the internal `android.app.ActivityThread.currentApplication()`.
 *    Same trick AndroidX uses; documented but not officially supported.
 *
 * If none of the above succeed, resource loads will fail with a clear message instructing the user
 * to register the provider or call [set] manually.
 */
@SuppressLint("StaticFieldLeak")
object KostraAndroidContextHolder {

    @Volatile
    private var context: Context? = null

    /**
     * Manually install the Application Context. Call this from `Application.onCreate()` when the
     * auto-installed [KostraAndroidContextProvider] cannot run in your process.
     *
     * **Do not pass an Activity** (or a [ContextWrapper] chain that bottoms out at an Activity).
     * Holding an Activity in a static field leaks the entire view hierarchy on rotation; Kostra
     * resource lookups happen for the lifetime of the process, so the leak would be permanent.
     * Pass [android.app.Application] or [Context.getApplicationContext] instead.
     *
     * @throws IllegalArgumentException if [ctx] is an Activity or wraps one.
     */
    fun set(ctx: Context) {
        require(!ctx.isOrWrapsActivity()) {
            "KostraAndroidContextHolder.set() refuses Activity contexts (leaks the Activity for the whole " +
                "process lifetime). Pass Application or Context.applicationContext instead. Got: ${ctx.describeChain()}"
        }
        context = ctx.applicationContext ?: ctx
    }

    /**
     * Best-effort Context lookup that never throws: returns the auto-installed Application Context,
     * the manually-set Context, or the reflected `ActivityThread.currentApplication()`, or `null`
     * if none of those is available yet. Use this when the caller has a sensible no-context fallback
     * (e.g. the Compose ResourceReader falling back to classloader); prefer [get] when missing
     * context is genuinely an error and you want the actionable Fix-1/Fix-2 message thrown.
     */
    fun getOrNull(): Context? = context ?: reflectActivityThreadApplication()?.also { context = it }

    /**
     * Returns the Application Context, throwing [IllegalStateException] with a Fix-1/Fix-2 hint
     * when neither the ContentProvider nor [set] nor the reflection fallback produced one.
     */
    fun get(): Context = getOrNull()
        ?: throw IllegalStateException(
            "Kostra Android Context is not available. Resources cannot be loaded from APK assets/ without it.\n" +
                "Fix one of:\n" +
                "  1) Make sure '${KostraAndroidContextProvider::class.java.name}' is declared in your merged " +
                "AndroidManifest. lib-kostra-common ships it automatically; it may have been removed via " +
                "tools:node=\"remove\" or excluded by a custom manifest-merger rule.\n" +
                "  2) Or call '${KostraAndroidContextHolder::class.java.name}.set(applicationContext)' from " +
                "your Application.onCreate() before any Kostra resource access.\n" +
                "Reflection fallback via ActivityThread.currentApplication() also returned null, meaning the " +
                "Android process hasn't fully attached an Application yet."
        )

    @SuppressLint("PrivateApi")
    private fun reflectActivityThreadApplication(): Application? = try {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val currentApplication = activityThreadClass.getMethod("currentApplication")
        currentApplication.isAccessible = true
        currentApplication.invoke(null) as? Application
    } catch (t: Throwable) {
        Log.d("Kostra", "ActivityThread.currentApplication() reflection failed: ${t.message}")
        null
    }

    private fun Context.isOrWrapsActivity(): Boolean {
        var current: Context? = this
        while (current != null) {
            if (current is Activity) return true
            current = (current as? ContextWrapper)?.baseContext?.takeIf { it !== current }
        }
        return false
    }

    private fun Context.describeChain(): String = buildString {
        var current: Context? = this@describeChain
        var depth = 0
        while (current != null && depth < 8) {
            if (depth > 0) append(" -> ")
            append(current.javaClass.name)
            val next = (current as? ContextWrapper)?.baseContext
            if (next === current) break
            current = next
            depth++
        }
    }
}
