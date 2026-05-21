package test

import java.io.File

private object StringEscapeUtils {
    fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}

private val DefaultLocation = "build/resources-test"

fun resources(
    root: File = File(DefaultLocation),
    autoDelete: Boolean = true,
    block: ResourcesBuilder.() -> Unit,
) = ResourcesBuilder(root).apply {
    if (autoDelete) delete()
    block()
    if (autoDelete) delete()
}

fun testResources(
    root: File = File(DefaultLocation),
    autoDelete: Boolean = true,
    block: ResourcesBuilder.() -> Unit,
) {
    resources(root, autoDelete, block)
}

class ResourcesBuilder(val resourcesRoot: File) {

    private var files = mutableMapOf<File, File.() -> Unit>()

    fun addFile(file: String) {
        files[File(resourcesRoot, file)] = {
            parentFile.mkdirs()
            createNewFile()
            writeBytes(byteArrayOf(0))
            deleteOnExit()
        }
    }

    fun addStrings(file: String, strings: List<String>) = addStrings(file, strings = strings.associateBy { it })

    fun addStrings(file: String, strings: Map<String, String> = emptyMap(), plurals: Map<String, Map<String, String>> = emptyMap()) {
        require(strings.isNotEmpty() || plurals.isNotEmpty()) { "Strings file must be nonEmpty, strings and plurals are empty" }
        files[File(resourcesRoot, file)] = {
            parentFile.mkdirs()
            createNewFile()
            deleteOnExit()
            writeText(
                buildString {
                    appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                    appendLine("<resources>")
                    strings.forEach { (key, value) ->
                        StringEscapeUtils.escapeXml(key)
                        appendLine("<string name=\"${StringEscapeUtils.escapeXml(key)}\">${StringEscapeUtils.escapeXml(value)}</string>")
                    }
                    plurals.forEach { (key, values) ->
                        appendLine("<plurals name=\"${StringEscapeUtils.escapeXml(key)}\">")
                        values.forEach { (quantity, value) ->
                            appendLine("<item quantity=\"${StringEscapeUtils.escapeXml(quantity)}\">${StringEscapeUtils.escapeXml(value)}</item>")
                        }
                        appendLine("</plurals>")
                    }
                    appendLine("</resources>")
                },
            )
        }
    }

    fun buildResources() {
        files.forEach { it.value(it.key) }
    }

    fun delete() {
        resourcesRoot.deleteRecursively()
    }

    fun file(relativePath: String) = File(resourcesRoot, relativePath)
}
