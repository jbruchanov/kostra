package com.jibru.kostra.internal

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Auto-installed ContentProvider that captures the Application Context for Kostra runtime use.
 *
 * Declared in lib-kostra-common's AndroidManifest with a stable `${applicationId}.kostra-init`
 * authority (auto-merged into consumer apps by AGP). Android runs ContentProvider.onCreate() during
 * Application#attach, before any Activity, so [AndroidContextHolder] is always populated before
 * user code can call into Kostra.
 *
 * All other ContentProvider methods are no-ops — this provider is a one-shot context-capture hook,
 * not a data source.
 */
class KostraAndroidContextProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let { KostraAndroidContextHolder.set(it.applicationContext ?: it) }
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
