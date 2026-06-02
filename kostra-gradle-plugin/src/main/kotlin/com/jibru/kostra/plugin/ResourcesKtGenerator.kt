package com.jibru.kostra.plugin

import com.jibru.kostra.AssetResourceKey
import com.jibru.kostra.BinaryResourceKey
import com.jibru.kostra.KAppResources
import com.jibru.kostra.KLocale
import com.jibru.kostra.PainterResourceKey
import com.jibru.kostra.PluralResourceKey
import com.jibru.kostra.ResourceKey
import com.jibru.kostra.StringResourceKey
import com.jibru.kostra.internal.FileDatabase
import com.jibru.kostra.internal.PluralDatabase
import com.jibru.kostra.internal.StringDatabase
import com.jibru.kostra.plugin.ext.addDefaultSuppressAnnotation
import com.jibru.kostra.plugin.ext.applyIf
import com.jibru.kostra.plugin.ext.applyIfNotNull
import com.jibru.kostra.plugin.ext.asLocalResourceType
import com.jibru.kostra.plugin.ext.formattedDbKey
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import kotlin.reflect.KClass

class ResourcesKtGenerator(
    items: List<ResItem>,
    className: String = KostraPluginConfig.KClassName,
    //Filename prefix prepended to every DB file reference in the generated K class. Empty means
    //"no prefix" → DBs sit at the root of assets/kostra_resources/ (e.g. "binary.db"). With a
    //module prefix it becomes e.g. "lib1_" → "lib1_binary.db". There is no longer a kresources/
    //sub-folder; DB files share the root of kostra_resources/ with the binary file resources.
    private val resDbsFolderName: String = "",
    private val resourcePropertyName: String = KostraPluginConfig.ResourcePropertyName,
    private val internalVisibility: Boolean = false,
    private val useAliasImports: Boolean = true,
) : ResItemsProcessor(items) {

    private val packageName = className.substringBeforeLast(".", "")
    private val className = className.substringAfterLast(".")
    private val ifaceName = "I${this.className}"

    init {
        check(packageName != "com.jibru.kostra") { "'$packageName' package is forbidden!" }
    }

    fun generateKClass(interfaces: Boolean = false): FileSpec {
        return FileSpec.builder(packageName, className)
            .addDefaultSuppressAnnotation()
            .tryAddAliasedImports()
            .addType(
                TypeSpec
                    .objectBuilder(className)
                    .addModifiers(if (internalVisibility) KModifier.INTERNAL else KModifier.PUBLIC)
                    .apply {
                        tryAddPositionIndexedObject(
                            items = stringsDistinctKeys,
                            objectName = ResItem.String,
                            type = StringResourceKey::class.asLocalResourceType(packageName),
                            iface = ClassName(packageName, ifaceName, ResItem.String).takeIf { interfaces },
                        )
                        tryAddPositionIndexedObject(
                            items = pluralsDistinctKeys,
                            objectName = ResItem.Plural,
                            type = PluralResourceKey::class.asLocalResourceType(packageName),
                            iface = ClassName(packageName, ifaceName, ResItem.Plural).takeIf { interfaces },
                        )
                        otherItemsPerGroupPerKey.onEach { (group, items) ->
                            addLongKeyObjectWithProperties(
                                items = items.keys,
                                objectName = group,
                                localPackageName = packageName,
                                iface = ClassName(packageName, ifaceName, group).takeIf { interfaces },
                            )
                        }
                    }
                    .build(),
            )
            .build()
    }

    fun generateIfaces(): FileSpec {
        return FileSpec.builder(packageName, ifaceName)
            .addDefaultSuppressAnnotation()
            .tryAddAliasedImports()
            .addType(
                TypeSpec
                    .interfaceBuilder(ifaceName)
                    .addModifiers(if (internalVisibility) KModifier.INTERNAL else KModifier.PUBLIC)
                    .apply {
                        tryAddPositionIndexedIface(stringsDistinctKeys, ResItem.String, StringResourceKey::class.asLocalResourceType(packageName))
                        tryAddPositionIndexedIface(pluralsDistinctKeys, ResItem.Plural, PluralResourceKey::class.asLocalResourceType(packageName))
                        otherItemsPerGroupPerKey.onEach { (group, items) ->
                            addLongKeyIface(items.keys, group, packageName)
                        }
                    }
                    .build(),
            )
            .build()
    }

    fun generateResources(): FileSpec {
        val typeAppResources = KAppResources::class

        return FileSpec.builder(packageName, resourcePropertyName)
            .addDefaultSuppressAnnotation()
            .addProperty(
                PropertySpec.builder(resourcePropertyName, typeAppResources)
                    .addModifiers(if (internalVisibility) KModifier.INTERNAL else KModifier.PUBLIC)
                    .initializer(
                        CodeBlock.Builder()
                            .apply {
                                addStatement("%T(", typeAppResources)
                                indent()
                                if (hasStrings) {
                                    addResourcesProperty(
                                        propertyName = ResItem.String,
                                        propertyType = StringDatabase::class,
                                        locales = stringsAndPluralsForDb.getValue(ResItem.String).keys,
                                        //resDbsFolderName is now a FILENAME PREFIX (e.g. "lib1_" or ""),
                                        //no longer a sub-folder name — concatenate, don't slash-join.
                                        "$resDbsFolderName${ResItem.String}-%s.db",
                                    )
                                }
                                if (hasPlurals) {
                                    addResourcesProperty(
                                        propertyName = ResItem.Plural,
                                        propertyType = PluralDatabase::class,
                                        locales = stringsAndPluralsForDb.getValue(ResItem.Plural).keys,
                                        "$resDbsFolderName${ResItem.Plural}-%s.db",
                                    )
                                }
                                if (hasAnyFiles) {
                                    addStatement("%L = %T(%S),", ResItem.Binary, FileDatabase::class, "$resDbsFolderName${ResItem.Binary}.db")
                                }
                                unindent()
                                addStatement(")")
                            }.build(),
                    )
                    .build(),
            )
            .build()
    }

    fun generateResourceProviders(addJvmInline: Boolean = true): String {
        //kotlin poet seems to be quite broken for value class
        //so done fully manually
        fun valueClass(name: String, superIfaces: List<String>) = buildString {
            if (internalVisibility) {
                append("internal ")
            }
            append("value class $name(override val key: Int) : ${superIfaces.joinToString()}")
        }

        val assertResourceKeyAlias = "A"
        val assertResourceKeyName = AssetResourceKey::class.simpleName!!
        return FileSpec.builder(packageName, KostraPluginConfig.ModuleResourceKeyName)
            .addDefaultSuppressAnnotation()
            .apply {
                AliasedImports.onEach { (klass, alias) ->
                    addAliasedImport(klass, alias)
                }
                addAliasedImport(AssetResourceKey::class, assertResourceKeyAlias)
            }
            .applyIf(addJvmInline) { addImport("kotlin.jvm", "JvmInline") }
            .build()
            .toString()
            .let {
                it + buildString {
                    appendLine("interface $assertResourceKeyName : $assertResourceKeyAlias")
                    AliasedImports.onEach { (klass, alias) ->
                        if (addJvmInline) {
                            appendLine("@JvmInline")
                        }
                        val superIfaces = buildList {
                            add(alias)
                            if (klass == PainterResourceKey::class || klass == BinaryResourceKey::class) {
                                add(assertResourceKeyName)
                            }
                        }

                        appendLine(valueClass(klass.simpleName!!, superIfaces))
                    }
                }
            }
    }

    /**
     * ```
     * string = StringDatabase(
     *   mapOf(
     *     Locale.Undefined to "__kostra/string-default.db",
     *     Locale(5_14_07_02) to "__kostra/string-engb.db",
     *   )
     * )
     * ```
     */
    private fun CodeBlock.Builder.addResourcesProperty(
        propertyName: String,
        propertyType: KClass<out Any>,
        locales: Collection<KLocale>,
        fileNameTemplate: String,
    ) {
        addStatement("%L = %T(", propertyName, propertyType)
        indent()
        addStatement("mapOf(")
        indent()
        locales.onEach {
            if (it == KLocale.Undefined) {
                add("%T.Undefined", KLocale::class)
            } else {
                add("%T(%L)", KLocale::class, it.formattedDbKey())
            }
            val tag = if (it == KLocale.Undefined) "default" else it.tag
            addStatement(" to %S,", fileNameTemplate.format(tag))
        }
        unindent()
        addStatement(")")
        unindent()
        addStatement("),")
    }

    private fun FileSpec.Builder.addAliasedImport(kClass: KClass<out ResourceKey>): FileSpec.Builder =
        apply { addAliasedImport(kClass.asLocalResourceType(packageName), AliasedImports.getValue(kClass)) }

    private fun FileSpec.Builder.tryAddAliasedImports(): FileSpec.Builder {
        if (useAliasImports) {
            if (hasStrings) {
                addAliasedImport(StringResourceKey::class)
            }
            if (hasPlurals) {
                addAliasedImport(PluralResourceKey::class)
            }
            if (hasPainters) {
                addAliasedImport(PainterResourceKey::class)
            }
            if (hasOthers) {
                addAliasedImport(BinaryResourceKey::class)
            }
        }
        return this
    }

    companion object {
        internal val AliasedImports = mapOf(
            StringResourceKey::class to "S",
            PluralResourceKey::class to "P",
            PainterResourceKey::class to "D",
            BinaryResourceKey::class to "B",
        )
    }
}

/**
 * ```
 * object objectName {
 *    val item1: S = S(0)
 *    val item2: P = P(1)
 * }
 * ```
 */
private fun TypeSpec.Builder.tryAddPositionIndexedObject(
    items: List<String>?,
    objectName: String,
    type: TypeName,
    iface: TypeName? = null,
) {
    items ?: return
    addType(
        TypeSpec
            .objectBuilder(objectName)
            .applyIfNotNull(iface) { addSuperinterface(it) }
            .apply {
                items.forEachIndexed { index, key ->
                    addProperty(
                        PropertySpec.builder(key, type, KModifier.PUBLIC)
                            .applyIfNotNull(iface) { addModifiers(KModifier.OVERRIDE) }
                            .initializer("%T(%L)", type, index)
                            .build(),
                    )
                }
            }
            .build(),
    )
}

private fun TypeSpec.Builder.tryAddPositionIndexedIface(
    items: List<String>?,
    objectName: String,
    type: TypeName,
) {
    items ?: return
    addType(
        TypeSpec
            .interfaceBuilder(objectName)
            .apply {
                items.onEach { key ->
                    addProperty(PropertySpec.builder(key, type, KModifier.PUBLIC).build())
                }
            }
            .build(),
    )
}

/**
 * ```
 * object objectName {
 *    val resItemKey1: D = D(dbKey1)
 *    val resItemKey2: B = B(dbKey2)
 * }
 * ```
 */
private fun TypeSpec.Builder.addLongKeyObjectWithProperties(
    items: Set<ResItemKeyDbKey>,
    objectName: String,
    localPackageName: String,
    iface: TypeName? = null,
) {
    addType(
        TypeSpec
            .objectBuilder(objectName)
            .applyIfNotNull(iface) { addSuperinterface(it) }
            .apply {
                items.forEach { (resItemKey, dbRootKey, type) ->
                    addProperty(
                        PropertySpec.builder(resItemKey, type.asLocalResourceType(localPackageName), KModifier.PUBLIC)
                            .applyIfNotNull(iface) { addModifiers(KModifier.OVERRIDE) }
                            .initializer("%T(%L)", type.asLocalResourceType(localPackageName), dbRootKey)
                            .build(),
                    )
                }
            }
            .build(),
    )
}

/**
 * ```
 * interface objectName {
 *    val resItemKey1: D
 *    val resItemKey2: B
 * }
 * ```
 */
private fun TypeSpec.Builder.addLongKeyIface(
    items: Set<ResItemKeyDbKey>,
    objectName: String,
    localPackageName: String,
) {
    addType(
        TypeSpec
            .interfaceBuilder(objectName)
            .apply {
                items.forEach { (resItemKey, _, type) ->
                    addProperty(PropertySpec.builder(resItemKey, type.asLocalResourceType(localPackageName), KModifier.PUBLIC).build())
                }
            }
            .build(),
    )
}
