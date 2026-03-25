@file:Suppress("UNUSED_DESTRUCTURED_PARAMETER_ENTRY")

package com.jibru.kostra.plugin

import com.jibru.kostra.BinaryResourceKey
import com.jibru.kostra.KLocale
import com.jibru.kostra.KQualifiers
import com.jibru.kostra.PainterResourceKey
import com.jibru.kostra.ResourceKey
import com.jibru.kostra.plugin.ext.distinctByLast
import com.jibru.kostra.plugin.ext.setOf
import kotlin.reflect.KClass

data class ResItemKeyDbKey(
    val resItemKey: String,
    val dbRootKey: Int,
    val type: KClass<out ResourceKey>,
) {
    init {
        require(resItemKey.isNotEmpty()) { "Invalid input $this, resItemKey is empty" }
    }
}

open class ResItemsProcessor(private val items: List<ResItem>) {
    val distinctItemsByDistinctKey by lazy { items.distinctByLast { it.distinctKey } }

    val allItemsPerGroup by lazy {
        distinctItemsByDistinctKey
            .groupBy { it.group.replaceFirstChar { k -> k.lowercase() } }
            .mapValues { it.value.sortedBy { it.key } }
    }

    val hasStrings by lazy { allItemsPerGroup.containsKey(ResItem.String) }
    val hasPlurals by lazy { allItemsPerGroup.containsKey(ResItem.Plural) }
    val hasPainters by lazy { allItemsPerGroup.values.any { l -> l.any { v -> (v as? ResItem.FileRes)?.image == true } } }
    val hasOthers by lazy { allItemsPerGroup.values.any { l -> l.any { v -> (v as? ResItem.FileRes)?.image == false } } }
    val hasAnyFiles by lazy { (allItemsPerGroup.keys - setOf(ResItem.String, ResItem.Plural)).isNotEmpty() }

    //Map<Group, Map<Locale, List<Pair<Key, ResItem?>>>>
    val stringsAndPluralsForDb by lazy {
        allItemsPerGroup
            .filterKeys { it == ResItem.String || it == ResItem.Plural }
            .mapValues { (group, itemsPerGroup) ->
                val itemsPerKey = itemsPerGroup
                    .groupBy { it.key }
                    .mapValues { (key, listOfItems) -> listOfItems.associate { item -> item.qualifiers.locale to item } }

                val locales = itemsPerGroup.setOf { it.qualifiers.locale }
                val keys = itemsPerGroup.setOf { it.key }.sorted()

                locales.associateWith { locale ->
                    keys.map { key ->
                        //key necessary for plurals here
                        key to itemsPerKey.getValue(key)[locale]
                    }
                }
            }
    }

    val otherItemsPerGroupPerKey by lazy {
        //counting from 1 to avoid having 0 as valid db key
        var counter = 1
        allItemsPerGroup
            .filterKeys { !(it == ResItem.String || it == ResItem.Plural) }
            .toSortedMap()
            .mapValues { (_, items) ->
                items.groupBy { it.key }.mapKeys {
                    val allImages = it.value.all { r -> r.isImageFile }
                    val type = if (allImages) PainterResourceKey::class else BinaryResourceKey::class
                    ResItemKeyDbKey(it.key, counter++, type)
                }
            }
    }

    //TODO: test
    @Suppress("UNCHECKED_CAST")
    val stringsDistinctKeys by lazy {
        stringsAndPluralsForDb[ResItem.String]
            ?.let { it as? Map<KLocale, List<Pair<String, ResItem.StringRes?>>> }
            ?.values?.firstOrNull()?.map { it.first }
        /*stringsAndPluralsForDb[ResItem.String]
            ?.let { it as? Map<Locale, List<Pair<String, ResItem.StringRes?>>> }
            ?.values?.map { items -> items.map { item -> item.first } }*/
    }

    @Suppress("UNCHECKED_CAST")
    val pluralsPerLocale by lazy {
        stringsAndPluralsForDb[ResItem.Plural]
            ?.let { it as? Map<KLocale, List<Pair<String, ResItem.Plurals?>>> }
            ?.mapValues { (locale, plurals) -> plurals.map { (k, v) -> k to (v?.items ?: ResItem.Plurals.EmptyItems) } }
    }

    val pluralsDistinctKeys by lazy {
        pluralsPerLocale?.values?.firstOrNull()?.map { it.first }
    }

    @Suppress("UNCHECKED_CAST")
    val stringsForDbs by lazy {
        stringsAndPluralsForDb[ResItem.String]
            ?.let { it as? Map<KLocale, List<Pair<String, ResItem.StringRes?>>> }
            ?.mapValues { it.value.map { it.second?.value } }
            ?: emptyMap()
    }

    @Suppress("UNCHECKED_CAST")
    val pluralsForDbs by lazy {
        pluralsPerLocale
            ?.mapValues { it.value.map { (key, item) -> item }.flatten() }
            ?: emptyMap()
    }

    val otherForDbs by lazy {
        otherItemsPerGroupPerKey.values
            .map {
                it
                    .mapValues { (baseDbKey, items) ->
                        val dbKey1 = (baseDbKey.dbRootKey.toLong() shl KQualifiers.Bits)
                        items.map { resItem ->
                            val item = resItem as ResItem.FileRes
                            val dbKey = dbKey1 or item.qualifiers.key
                            dbKey to item.value
                        }
                    }
                    .values
                    .flatten()
            }
            .flatten()
    }
}
