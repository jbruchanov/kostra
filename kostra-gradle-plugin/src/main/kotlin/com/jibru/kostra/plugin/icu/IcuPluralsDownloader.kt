package com.jibru.kostra.plugin.icu

import com.jibru.kostra.KLocale
import com.jibru.kostra.icu.PluralCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val root = Json.parseToJsonElement(tmpFile.readText()).jsonObject
        val supplemental = root.obj("supplemental")
        val version = supplemental.obj("version")

        val items = supplemental.obj(jsonObjName)
        val data = items.entries
            .filter { it.key.length <= KLocale.MaxLocaleLen }
            .associate { (locale, rulesElement) ->
                val rules = rulesElement.jsonObject.entries.associate { (ruleKey, ruleValue) ->
                    PluralCategory.of(ruleKey.removePrefix("pluralRule-count-")) to ruleValue.jsonPrimitive.content
                }
                KLocale(locale) to rules
            }

        return Result(
            unicodeVersion = version["_unicodeVersion"]!!.jsonPrimitive.content,
            cldrVersion = version["_cldrVersion"]!!.jsonPrimitive.content,
            data = data,
        )
    }

    private fun JsonObject.obj(name: String): JsonObject =
        this[name]?.jsonObject ?: error("Missing JSON object '$name' in ${this.keys}")
}
