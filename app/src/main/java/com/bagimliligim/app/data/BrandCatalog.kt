package com.bagimliligim.app.data

import com.bagimliligim.app.data.local.CigaretteBrandEntity
import java.text.Normalizer
import java.util.Locale

/** Names are identifiers for personal records, never recommendations or advertisements. */
object BrandCatalog {
    private val families = linkedMapOf(
        "Winston" to listOf("Dark Blue", "Deep Blue", "Blue", "Red", "Slender Blue", "Slender Gray", "Slender Long", "Slender Q Line", "Slims", "Slims Q Line", "Xsence"),
        "Tekel 2000" to listOf("Blue", "Red"),
        "Parliament" to listOf("Reserve", "Night Blue", "Night Blue Long", "Aqua Blue", "Aqua Blue Slims", "Midnight Blue", "Midnight"),
        "Marlboro" to listOf("Red", "Red Long", "Touch Blue", "Touch Gray", "Touch White", "Edge", "Edge Blue", "Edge Slims"),
        "Camel" to listOf("Yellow", "Brown", "Black", "White", "Deep Blue", "Slender", "Slims Q Line"),
        "Kent" to listOf("Blue", "White", "Dark Blue", "Dark Blue Long", "D Range Blue", "D Range Gray", "D Range Black", "D Range Long", "Switch", "Switch Dark Blue", "Slims Black", "Slims Gray", "Slims Long Blue", "Slims Long Gray"),
        "Tekel 2001" to listOf("Blue", "Red"),
        "Muratti" to listOf("", "Blu"),
        "Chesterfield" to listOf("Red", "Blue", "Navy"),
        "L&M" to listOf("Red", "Blue"),
        "Lark" to listOf("Red", "Blue"),
        "LD" to listOf("", "Slims"),
        "Monte Carlo" to listOf(""),
        "Pall Mall" to listOf("Red", "Blue"),
        "Viceroy" to listOf("Red", "Blue"),
        "Rothmans" to listOf(""),
        "Davidoff" to listOf("Burgundy", "Ivory", "White", "Slims Ivory", "Slims White", "C Line Blue"),
        "Maltepe" to listOf(""), "Samsun" to listOf(""), "West" to listOf(""),
        "Bianca" to listOf(""), "Medley" to listOf(""), "MMC" to listOf(""), "Polo" to listOf(""),
    )
    fun id(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
    val brands = families.flatMap { (family, variants) -> variants.map { variant ->
        val name = "$family $variant".trim()
        CigaretteBrandEntity(id(name), name, family)
    } }
}
