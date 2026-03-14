@file:Suppress("UNCHECKED_CAST")

package com.jibru.kostra.plugin.icu

import com.jibru.kostra.KLocale
import com.jibru.kostra.icu.PluralCategory
import groovy.json.JsonSlurper
import java.io.File
import java.net.URI

class IcuPluralsDownloader(
    private val cacheLocation: File = File("build/icu_data/"),
) {

    data class Result(
        val unicodeVersion: String,
        val cldrVersion: String,
        val data: Map<KLocale, Map<PluralCategory, String>>,
    )

    fun loadPlurals(): Result = load("plurals.json", "plurals-type-cardinal")

    fun loadOrdinals(): Result = load("ordinals.json", "plurals-type-ordinal")

    private fun load(url: String, jsonObjName: String): Result {
        val uri = URI.create("https://raw.githubusercontent.com/unicode-org/cldr-json/master/cldr-json/cldr-core/supplemental/$url")
        val tmpFile = File(cacheLocation, uri.path.substringAfterLast("/"))
        tmpFile.parentFile.mkdirs()
        if (!(tmpFile.exists() && tmpFile.length() > 0)) {
            tmpFile.writeBytes(uri.toURL().openStream().readAllBytes())
        }
        val rawJsonData = JsonSlurper().parse(tmpFile.inputStream()) as Map<String, *>
        val baseObject = rawJsonData.obj("supplemental")
        val version = baseObject.obj("version")

        val items = baseObject.obj(jsonObjName) as Map<String, Map<String, String>>
        val data = items
            .filter { it.key.length <= KLocale.MaxLocaleLen }
            .map { obj ->
                KLocale(obj.key) to obj.value.map { it.key.replace("pluralRule-count-", "").let { PluralCategory.of(it) } to it.value }.toMap()
            }.toMap()

        return Result(
            unicodeVersion = version["_unicodeVersion"].toString(),
            cldrVersion = version["_cldrVersion"].toString(),
            data = data,
        )
    }

    private fun Map<String, *>.obj(name: String) = this[name] as Map<String, *>
}
