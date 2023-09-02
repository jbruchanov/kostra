package com.jibru.kostra.appsample.jvm

import com.jibru.kostra.K
import com.jibru.kostra.KDpi
import com.jibru.kostra.Resources
import com.jibru.kostra.assetPath
import com.jibru.kostra.binaryInputStream
import com.jibru.kostra.defaultQualifiers
import com.jibru.kostra.icu.FixedDecimal
import com.jibru.kostra.ordinal
import com.jibru.kostra.plural
import com.jibru.kostra.string
import java.util.Locale
import javax.imageio.ImageIO

fun main() {
    val test = {
        println("-".repeat(32))
        println("Current locale:${Locale.getDefault()}")
        println("Strings:")
        val items = listOf(K.string.action_add, K.string.action_remove, K.string.color, K.string.plurals, K.string.ordinals)
        println(items.joinToString { Resources.string(it) })
        println("Plurals:")
        println(
            listOf(
                Resources.plural(K.plural.bug_x, 0, 0f),
                Resources.plural(K.plural.bug_x, FixedDecimal(0.5), 0.5f),
                Resources.plural(K.plural.bug_x, 1, 1),
                Resources.plural(K.plural.bug_x, 10, 10)
            ).joinToString()
        )
        println("Ordinals:")
        println((0..5).joinToString { Resources.ordinal(K.plural.day_x, it, it) })

        ImageIO.read(Resources.binaryInputStream(K.drawable.capital_city)).also {
            val assetPath = Resources.assetPath(K.drawable.capital_city)
            println("$assetPath imageRes:${it.width}x${it.height}")
        }
        val xxHdpiQualifiers = defaultQualifiers().copy(dpi = KDpi.XXHDPI)
        ImageIO.read(Resources.binaryInputStream(K.drawable.capital_city, qualifiers = xxHdpiQualifiers)).also {
            val assetPath = Resources.assetPath(K.drawable.capital_city, xxHdpiQualifiers)
            if (it != null) {
                println("$assetPath imageRes:${it.width}x${it.height}")
            } else {
                println("Unable to load $assetPath, (webp not supported by ImageIO!?)")
            }
        }
        Unit
    }

    Locale.setDefault(Locale("en", "US"))
    test()
    Locale.setDefault(Locale("en", "GB"))
    test()
    Locale.setDefault(Locale("cs"))
    test()
}
