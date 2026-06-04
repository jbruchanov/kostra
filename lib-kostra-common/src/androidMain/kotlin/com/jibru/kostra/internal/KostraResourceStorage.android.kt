package com.jibru.kostra.internal

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import com.jibru.kostra.UnableToOpenResourceStream
import com.jibru.kostra.ext.describeChain
import com.jibru.kostra.ext.isOrWrapsActivity
import com.jibru.kostra.internal.AndroidDefaultResourceStorage.buildFailureDiagnostic
import java.io.File

/**
 * Android default storage: the runtime/@Preview path is an [AssetManager] installed via [set];
 * absent that (JVM-host unit tests, or @Preview before assets are populated) this default resolves
 * via the classloader and the [KostraResourceRootProperty] filesystem fallback.
 */
internal actual val platformDefaultResourceStorage: KostraResourceStorage = AndroidDefaultResourceStorage

/**
 * Install the default [AssetManager]-backed storage from [ctx]. The Context is **not** retained —
 * only its application [AssetManager].
 *
 * **Do not pass an Activity** (or a [ContextWrapper] chain bottoming out at one): holding
 * Activity-derived state for the process lifetime leaks the view hierarchy. Pass
 * [android.app.Application] or [Context.getApplicationContext].
 *
 * @throws IllegalArgumentException if [ctx] is an Activity or wraps one.
 */
fun KostraResourceStorage.Companion.set(ctx: Context) {
    require(!ctx.isOrWrapsActivity()) {
        "KostraResourceStorage.set() refuses Activity contexts (leaks the Activity for the whole " +
            "process lifetime). Pass Application or Context.applicationContext instead. Got: ${ctx.describeChain()}"
    }
    set(AssetManagerResourceStorage((ctx.applicationContext ?: ctx).assets))
}

/** [KostraResourceStorage] backed by an Android [AssetManager] (keys are APK-asset-relative paths). */
internal class AssetManagerResourceStorage(private val assets: AssetManager) : KostraResourceStorage {
    //assets.open throws FileNotFoundException for a missing key (before any read).
    override fun read(key: String): ByteArray = assets.open(key).use { it.readBytes() }
}

/** [KostraResourceStorage] backed by a directory on disk; reads `<root>/<key>`. */
internal class FileSystemResourceStorage(private val root: File) : KostraResourceStorage {
    //File.readBytes throws FileNotFoundException for a missing key.
    override fun read(key: String): ByteArray = File(root, key).readBytes()
}

/**
 * Android fallback used when no AssetManager storage is installed. Resolves via the
 * [KostraResourceRootProperty] filesystem dir (JVM-host unit tests) and the classloader (@Preview via
 * a jvm jar on the render classpath, standalone host tests). On a miss it throws with an actionable
 * diagnostic (see [buildFailureDiagnostic]).
 */
internal object AndroidDefaultResourceStorage : KostraResourceStorage {

    override fun read(key: String): ByteArray {
        propertyFilesystemStorage?.let { storage -> runCatching { storage.read(key) }.getOrNull()?.let { return it } }
        classLoadersToTry().forEach { cl -> cl.getResourceAsStream(key)?.use { return it.readBytes() } }
        throw UnableToOpenResourceStream("$key | ${buildFailureDiagnostic(key)}")
    }

    private val propertyFilesystemStorage: KostraResourceStorage? by lazy {
        System.getProperty(KostraResourceRootProperty)
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?.let(::FileSystemResourceStorage)
    }

    /**
     * Builds the diagnostic appended to [UnableToOpenResourceStream]: the installed storage (if any),
     * the property fallback, the classloader walk, and — for the common @Preview-only failure — a hint
     * naming the likely misconfigured module. Never throws.
     */
    private fun buildFailureDiagnostic(key: String): String = buildString {
        val installed = KostraResourceStorage.current()
        append("key='$key'")
        append(", installed=").append(installed?.javaClass?.name ?: "null")
        append(", -$KostraResourceRootProperty=").append(System.getProperty(KostraResourceRootProperty) ?: "<unset>")

        var anyClassLoaderSawRoot = false
        classLoadersToTry().forEachIndexed { i, cl ->
            append(", cl[$i]=").append(cl.javaClass.name)
            val res = runCatching { cl.getResources(KostraAssets.RootDir).toList().map { it.toString() } }
                .getOrElse { listOf("<list-failed:${it.javaClass.simpleName}>") }
            if (res.isNotEmpty()) anyClassLoaderSawRoot = true
            append("(roots=").append(if (res.isEmpty()) "<none>" else res.take(3)).append(")")
        }
        appendPreviewMissingAndroidTargetHint(key, storageInstalled = installed != null, anyClassLoaderSawRoot = anyClassLoaderSawRoot)
    }

    /**
     * Compose Preview's render classloader only loads JARs that have an Android variant — not a KMP
     * module's `jvm` jar. So a kostra-bearing KMP module without an `android(…)` target ships its DBs
     * only in the jvm jar (reachable at runtime via APK packaging, invisible to @Preview). When a
     * storage is installed (e.g. layoutlib's AssetManager via KostraPreviewInit) yet no classloader
     * saw the root, append a hint naming the likely culprit module from the key's `<prefix>_` convention.
     */
    private fun StringBuilder.appendPreviewMissingAndroidTargetHint(
        key: String,
        storageInstalled: Boolean,
        anyClassLoaderSawRoot: Boolean,
    ) {
        if (!storageInstalled || anyClassLoaderSawRoot) return
        val keyHead = key.removePrefix("${KostraAssets.RootDir}/").substringBefore('/')
        val likelyModule = keyHead.substringBefore('_').takeIf { it.isNotEmpty() && it != keyHead }
        append(
            ". HINT: if this is a @Preview, the previewed composable likely transitively depends on a " +
                "kostra-bearing KMP module without an `android(…)` target — Android Studio's Compose " +
                "Preview classpath excludes pure-jvm jars, so the DB isn't visible at render time (it IS " +
                "at runtime via APK packaging). Add an `android(…)` target to the offending module",
        )
        if (likelyModule != null) {
            append(" — based on the failing key, this looks like the `").append(likelyModule).append("` module")
        }
        append(".")
    }

    /**
     * Classloaders to query, in fallback order. Walking multiple loaders is required because Compose
     * Preview's layoutlib host occasionally hands us a contextClassLoader that excludes transitive
     * non-Android JARs the project legitimately depends on. Distinct + non-null; `LinkedHashSet`
     * preserves walk order.
     */
    private fun classLoadersToTry(): Set<ClassLoader> = linkedSetOf<ClassLoader>().apply {
        Thread.currentThread().contextClassLoader?.let(::add)
        javaClass.classLoader?.let(::add)
        ClassLoader.getSystemClassLoader()?.let(::add)
    }
}
