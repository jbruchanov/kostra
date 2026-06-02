package com.jibru.kostra.internal

import android.content.res.AssetManager
import com.jibru.kostra.UnableToOpenResourceStream
import java.io.FileNotFoundException
import java.io.InputStream

internal actual fun loadResource(key: String): ByteArray = AndroidResourceImpl.getStream(key).use { it.readBytes() }

internal fun openResource(key: String): InputStream = AndroidResourceImpl.getStream(key)

/**
 * Android resource lookup.
 *
 * Files end up at APK path `assets/kostra_resources/<key>` because the kostra Gradle plugin
 * registers its staging dir with AGP's assets pipeline
 * (`variant.sources.assets.addGeneratedSourceDirectory(...)`), so AGP merges the staged
 * `kostra_resources/<files>` into the APK's `assets/` folder for every Android plugin type
 * (`com.android.application`, `com.android.library`, `com.android.kotlin.multiplatform.library`).
 * Nothing kostra-managed lands at APK root.
 *
 * Lookup order in [getStream]:
 *  1. `AssetManager.open("kostra_resources/<key>")` via [KostraAndroidContextHolder]. AssetManager
 *     paths are relative to the APK `assets/` folder, so the lookup string has no `assets/` prefix.
 *  2. Classloader fallback walking `contextClassLoader → javaClass.classLoader → systemClassLoader`
 *     for `kostra_resources/<key>` as a JAR-classpath resource. Covers:
 *      - JVM-host unit tests on Android source sets, where no Application Context is installed.
 *      - Standalone host tests where AGP's assets pipeline isn't in play.
 *     Note: this fallback CANNOT save @Preview when the resource only exists in an AAR's assets/
 *     (not JAR-resources). Layoutlib's preview render classpath only sees JAR-classpath entries,
 *     so kostra resources that ride solely through `variant.sources.assets` aren't reachable from
 *     the classloader. The diagnostic on lookup failure includes a hint when this is the cause.
 *
 * Keys passed in stay prefix-free (e.g. `"lib1_string-cs.db"`, `"images/capital_city.png"`).
 */
internal object AndroidResourceImpl {

    fun getStream(key: String): InputStream {
        //File at APK `assets/kostra_resources/<key>` via AGP's assets pipeline. AssetManager paths
        //are relative to APK `assets/`, so we look it up as `kostra_resources/<key>`.
        val path = "${KostraAssets.RootDir}/$key"

        //1) AssetManager (runtime + @Preview when the preview AssetManager is populated).
        val assetsManager = KostraAndroidContextHolder.getOrNull()?.assets
        assetsManager?.tryOpen(path)?.let { return it }

        //2) Classloader fallback. Covers JVM-host unit tests (no Application Context) and @Preview
        //rendering of resources shipped via a jvm jar on the render classpath. We walk every
        //reasonable loader because layoutlib's contextClassLoader sometimes excludes transitive
        //non-Android JARs that our own loader does see (full rationale in classLoadersToTry / kdoc).
        classLoadersToTry().forEach { cl ->
            cl.getResourceAsStream(path)?.let { return it }
        }

        //3) Neither succeeded — fail with an actionable diagnostic.
        val missingContextCause = if (assetsManager == null) {
            runCatching { KostraAndroidContextHolder.get() }.exceptionOrNull() as? IllegalStateException
        } else {
            null
        }
        throw UnableToOpenResourceStream("$path | ${buildFailureDiagnostic(key, path, assetsManager)}").apply {
            missingContextCause?.let { initCause(it) }
        }
    }

    /**
     * Builds the human-readable diagnostic appended to [UnableToOpenResourceStream] when both the
     * AssetManager and the classloader fallback miss. Reports the held Context, what the
     * AssetManager can list (or why it's null), the classloader walk, and — for the common
     * @Preview-only failure — a hint naming the likely misconfigured module. Never throws: every
     * reflective/IO probe is wrapped so diagnostic generation can't mask the real error.
     */
    private fun buildFailureDiagnostic(key: String, path: String, assetsManager: AssetManager?): String = buildString {
        val ctxState = KostraAndroidContextHolder.getOrNull()
        append("path='$path'")
        append(", context=").append(ctxState?.let { it.javaClass.name } ?: "null")

        if (assetsManager != null) {
            val rootList = runCatching { assetsManager.list("")?.toList().orEmpty() }
                .getOrElse { listOf("<list-failed:${it.javaClass.simpleName}>") }
            val kostraList = runCatching { assetsManager.list(KostraAssets.RootDir)?.toList().orEmpty() }
                .getOrElse { listOf("<list-failed:${it.javaClass.simpleName}>") }
            append(", assets.list(\"\")=").append(rootList)
            append(", assets.list(\"${KostraAssets.RootDir}\")=").append(kostraList)
        } else {
            append(", assets=null (no Application Context — call KostraPreviewInit() at the " +
                "top of @Preview, or KostraAndroidContextHolder.set(applicationContext) in Application.onCreate, " +
                "or declare KostraAndroidContextProvider in your manifest)")
        }

        //Report classloader chain + whether each loader sees ANYTHING under kostra_resources/.
        //If every entry is `<none>`, the file isn't on the render classpath — see hint below.
        //If some loader DOES list entries but our key isn't among them, the staging task didn't
        //include this key.
        var anyClassLoaderSawRoot = false
        classLoadersToTry().forEachIndexed { i, cl ->
            append(", cl[$i]=").append(cl.javaClass.name)
            val res = runCatching { cl.getResources(KostraAssets.RootDir).toList().map { it.toString() } }
                .getOrElse { listOf("<list-failed:${it.javaClass.simpleName}>") }
            if (res.isNotEmpty()) anyClassLoaderSawRoot = true
            append("(roots=").append(if (res.isEmpty()) "<none>" else res.take(3)).append(")")
        }

        appendPreviewMissingAndroidTargetHint(key, ctxState, anyClassLoaderSawRoot)
    }

    /**
     * Compose Preview's render classloader (IntelliJ's PathClassLoader) only loads JARs that have
     * an Android variant — it does NOT include a KMP module's `jvm` jar even when the consumer's
     * Android variant transitively depends on it. So a kostra-bearing KMP module without an
     * `android(…)` target ships its DBs only in the jvm jar, which is reachable at runtime via
     * AGP's mergeJavaResources (→ APK root) but invisible to @Preview. When the failure looks like
     * exactly that case (layoutlib context + no classloader saw the root), append a hint naming the
     * likely culprit module, inferred from the key's `<prefix>_` convention.
     */
    private fun StringBuilder.appendPreviewMissingAndroidTargetHint(
        key: String,
        ctxState: Any?,
        anyClassLoaderSawRoot: Boolean,
    ) {
        val previewLikely = ctxState?.javaClass?.name?.startsWith("com.android.layoutlib.bridge") == true
        if (!previewLikely || anyClassLoaderSawRoot) return

        //Extract the module prefix from the failed key, e.g. "lib2_string-cs.db" → "lib2".
        //Strings/plurals follow `<prefix>_string-<locale>.db` / `<prefix>_plural-<locale>.db`.
        //Binary keys also use the `<prefix>_` convention. No prefix on the key (e.g. the consuming
        //module's own DB) means it's a different problem — likely missing KostraPreviewInit() or a
        //generator config issue, not a missing android target.
        val keyHead = key.substringBefore('/')
        val likelyModule = keyHead.substringBefore('_').takeIf { it.isNotEmpty() && it != keyHead }
        append(
            ". HINT: the previewed composable transitively depends on a kostra-bearing " +
                "KMP module without an `android(…)` target — Android Studio's Compose " +
                "Preview classpath excludes pure-jvm jars, so the DB isn't visible at " +
                "render time (it IS at runtime via APK packaging). Add an `android(…)` " +
                "target to the offending module",
        )
        if (likelyModule != null) {
            append(" — based on the failing key, this looks like the `").append(likelyModule).append("` module")
        }
        append(".")
    }

    private fun AssetManager.tryOpen(path: String): InputStream? = try {
        open(path)
    } catch (_: FileNotFoundException) {
        null
    }

    /**
     * Classloaders to query for kostra resources, in fallback order. Walking multiple loaders is
     * required because Compose Preview's layoutlib host occasionally hands us a contextClassLoader
     * that excludes transitive non-Android JARs the project legitimately depends on (see kdoc
     * on [getStream]). Distinct + non-null only — `LinkedHashSet` preserves walk order.
     */
    private fun classLoadersToTry(): Set<ClassLoader> = linkedSetOf<ClassLoader>().apply {
        Thread.currentThread().contextClassLoader?.let(::add)
        javaClass.classLoader?.let(::add)
        ClassLoader.getSystemClassLoader()?.let(::add)
    }
}
