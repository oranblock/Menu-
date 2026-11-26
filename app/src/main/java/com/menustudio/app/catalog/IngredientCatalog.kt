package com.menustudio.app.catalog

import com.menustudio.app.bridge.CustomDish
import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.IngredientCategory
import com.menustudio.app.bridge.MenuIngredientInstance
import java.util.concurrent.atomic.AtomicLong

/**
 * Metadata definition for an ingredient available in the bottom Add Dock and variant Adjust Bar.
 */
data class IngredientCatalogEntry(
    val typeId: String,
    val label: String,
    val shortChipLabel: String,
    val category: IngredientCategory,
    val variants: List<String>,
    val defaultVariant: String,
    val basePrice: Double,
    val baseKcal: Int,
    val accentHex: Long,
    val isTopCap: Boolean = false
)

object IngredientCatalog {

    private val uidCounter = AtomicLong(1000L)

    fun nextUid(typeId: String): String {
        return "${typeId}_${uidCounter.incrementAndGet()}"
    }

    val entries: List<IngredientCatalogEntry> = listOf(
        // Base
        IngredientCatalogEntry(
            typeId = "brioche_base",
            label = "Brioche Heel",
            shortChipLabel = "Base",
            category = IngredientCategory.BASE,
            variants = listOf("Toasted Brioche", "Sesame", "Potato Roll"),
            defaultVariant = "Toasted Brioche",
            basePrice = 2.00,
            baseKcal = 140,
            accentHex = 0xFFD99B52
        ),
        IngredientCatalogEntry(
            typeId = "brioche_top",
            label = "Brioche Crown",
            shortChipLabel = "Brioche",
            category = IngredientCategory.BASE,
            variants = listOf("Toasted Brioche", "Sesame", "Pretzel"),
            defaultVariant = "Toasted Brioche",
            basePrice = 2.00,
            baseKcal = 150,
            accentHex = 0xFFE3A85E,
            isTopCap = true
        ),
        IngredientCatalogEntry(
            typeId = "sourdough_crust",
            label = "Sourdough Crust",
            shortChipLabel = "Crust",
            category = IngredientCategory.BASE,
            variants = listOf("Wood-Fired", "Thin Roman", "Neapolitan"),
            defaultVariant = "Wood-Fired",
            basePrice = 6.00,
            baseKcal = 380,
            accentHex = 0xFFE0B370
        ),
        IngredientCatalogEntry(
            typeId = "rice_base",
            label = "Warm Rice Base",
            shortChipLabel = "Rice",
            category = IngredientCategory.BASE,
            variants = listOf("Jasmine", "Forbidden Black", "Quinoa Blend"),
            defaultVariant = "Jasmine",
            basePrice = 4.00,
            baseKcal = 240,
            accentHex = 0xFFEDE6D6
        ),

        // Mains
        IngredientCatalogEntry(
            typeId = "patty",
            label = "Beef Patty",
            shortChipLabel = "Patty",
            category = IngredientCategory.MAIN,
            variants = listOf("Smash", "Thick Pub", "Dry-Aged Wagyu"),
            defaultVariant = "Smash",
            basePrice = 4.50,
            baseKcal = 270,
            accentHex = 0xFF8C4A32
        ),
        IngredientCatalogEntry(
            typeId = "crispy_chicken",
            label = "Crispy Chicken",
            shortChipLabel = "Chicken",
            category = IngredientCategory.MAIN,
            variants = listOf("Buttermilk", "Nashville Hot", "Herb Grilled"),
            defaultVariant = "Herb Grilled",
            basePrice = 4.25,
            baseKcal = 230,
            accentHex = 0xFFC77D3B
        ),
        IngredientCatalogEntry(
            typeId = "portobello",
            label = "Roasted Mushroom",
            shortChipLabel = "Mushroom",
            category = IngredientCategory.MAIN,
            variants = listOf("Balsamic Glaze", "Garlic Thyme", "Crispy Panko"),
            defaultVariant = "Garlic Thyme",
            basePrice = 3.50,
            baseKcal = 110,
            accentHex = 0xFF7A5D4B
        ),

        // Cheese
        IngredientCatalogEntry(
            typeId = "cheddar",
            label = "Cheddar Slice",
            shortChipLabel = "Cheddar",
            category = IngredientCategory.CHEESE,
            variants = listOf("Aged Sharp", "Smoked", "White Vermont"),
            defaultVariant = "Aged Sharp",
            basePrice = 1.50,
            baseKcal = 110,
            accentHex = 0xFFF5A623
        ),
        IngredientCatalogEntry(
            typeId = "mozzarella",
            label = "Fresh Mozzarella",
            shortChipLabel = "Mozz",
            category = IngredientCategory.CHEESE,
            variants = listOf("Fior di Latte", "Buffalo", "Burrata Tear"),
            defaultVariant = "Fior di Latte",
            basePrice = 2.50,
            baseKcal = 140,
            accentHex = 0xFFF7F2E7
        ),
        IngredientCatalogEntry(
            typeId = "gruyere",
            label = "Melted Gruyere",
            shortChipLabel = "Gruyere",
            category = IngredientCategory.CHEESE,
            variants = listOf("Cave-Aged", "Caramelized", "Herb Melt"),
            defaultVariant = "Cave-Aged",
            basePrice = 2.00,
            baseKcal = 120,
            accentHex = 0xFFE9CE86
        ),

        // Fresh
        IngredientCatalogEntry(
            typeId = "lettuce",
            label = "Crisp Lettuce",
            shortChipLabel = "Lettuce",
            category = IngredientCategory.FRESH,
            variants = listOf("Little Gem", "Shredded Iceberg", "Baby Arugula"),
            defaultVariant = "Little Gem",
            basePrice = 0.75,
            baseKcal = 10,
            accentHex = 0xFF6BCB5B
        ),
        IngredientCatalogEntry(
            typeId = "tomato",
            label = "Heirloom Tomato",
            shortChipLabel = "Tomato",
            category = IngredientCategory.FRESH,
            variants = listOf("Brandywine", "Cherokee", "Green Zebra"),
            defaultVariant = "Brandywine",
            basePrice = 1.00,
            baseKcal = 18,
            accentHex = 0xFFE5483B
        ),
        IngredientCatalogEntry(
            typeId = "avocado",
            label = "Avocado Fan",
            shortChipLabel = "Avocado",
            category = IngredientCategory.FRESH,
            variants = listOf("Sliced Hass", "Lime Smashed", "Charred"),
            defaultVariant = "Sliced Hass",
            basePrice = 2.50,
            baseKcal = 120,
            accentHex = 0xFF8DB944
        ),
        IngredientCatalogEntry(
            typeId = "basil",
            label = "Sweet Basil",
            shortChipLabel = "Basil",
            category = IngredientCategory.FRESH,
            variants = listOf("Genovese Leaf", "Fried Crisp", "Micro Basil"),
            defaultVariant = "Genovese Leaf",
            basePrice = 0.75,
            baseKcal = 5,
            accentHex = 0xFF479D45
        ),

        // Sauces
        IngredientCatalogEntry(
            typeId = "house_sauce",
            label = "Studio Sauce",
            shortChipLabel = "Sauce",
            category = IngredientCategory.SAUCE,
            variants = listOf("Smoky House", "Truffle Aioli", "Spicy Yuzu"),
            defaultVariant = "Smoky House",
            basePrice = 0.75,
            baseKcal = 75,
            accentHex = 0xFFE87E45
        ),
        IngredientCatalogEntry(
            typeId = "san_marzano",
            label = "Tomato Sugo",
            shortChipLabel = "Sugo",
            category = IngredientCategory.SAUCE,
            variants = listOf("San Marzano", "Arrabbiata", "Roasted Garlic"),
            defaultVariant = "San Marzano",
            basePrice = 1.50,
            baseKcal = 45,
            accentHex = 0xFFC83428
        ),

        // Extras
        IngredientCatalogEntry(
            typeId = "bacon",
            label = "Smoked Bacon",
            shortChipLabel = "Bacon",
            category = IngredientCategory.EXTRA,
            variants = listOf("Applewood", "Black Pepper", "Maple Glazed"),
            defaultVariant = "Applewood",
            basePrice = 2.00,
            baseKcal = 130,
            accentHex = 0xFFB84639
        ),
        IngredientCatalogEntry(
            typeId = "pickles",
            label = "Dill Pickles",
            shortChipLabel = "Pickles",
            category = IngredientCategory.EXTRA,
            variants = listOf("Crinkle Dill", "Bread & Butter", "Jalapeno"),
            defaultVariant = "Crinkle Dill",
            basePrice = 0.75,
            baseKcal = 12,
            accentHex = 0xFF79A34B
        ),
        IngredientCatalogEntry(
            typeId = "crispy_onions",
            label = "Crispy Shallots",
            shortChipLabel = "Shallots",
            category = IngredientCategory.EXTRA,
            variants = listOf("Golden Fried", "Charred Rings", "Caramelized"),
            defaultVariant = "Golden Fried",
            basePrice = 1.25,
            baseKcal = 85,
            accentHex = 0xFFC98D46
        )
    )

    private val byTypeId: Map<String, IngredientCatalogEntry> = entries.associateBy { it.typeId }

    fun findByTypeId(typeId: String): IngredientCatalogEntry? = byTypeId[typeId]

    fun createInstance(
        typeId: String,
        variantOverride: String? = null,
        thickness: Float = 1.0f
    ): MenuIngredientInstance {
        val entry = findByTypeId(typeId)
            ?: error("Unknown ingredient typeId: $typeId")
        return MenuIngredientInstance(
            uid = nextUid(typeId),
            typeId = entry.typeId,
            label = entry.shortChipLabel,
            category = entry.category,
            variant = variantOverride ?: entry.defaultVariant,
            thickness = thickness.coerceIn(0.5f, 1.5f),
            price = entry.basePrice,
            kcal = entry.baseKcal,
            visible = true
        )
    }

    fun defaultDishes(): List<CustomDish> = listOf(
        CustomDish(
            id = "dish_signature_smash",
            name = "Signature Smash",
            archetype = DishArchetype.STACK,
            layers = listOf(
                createInstance("brioche_base"),
                createInstance("house_sauce"),
                createInstance("patty", variantOverride = "Smash"),
                createInstance("cheddar"),
                createInstance("tomato", variantOverride = "Brandywine"),
                createInstance("lettuce"),
                createInstance("brioche_top")
            )
        ),
        CustomDish(
            id = "dish_margherita",
            name = "Wood-Fired Pie",
            archetype = DishArchetype.FLAT,
            layers = listOf(
                createInstance("sourdough_crust"),
                createInstance("san_marzano"),
                createInstance("mozzarella", variantOverride = "Fior di Latte"),
                createInstance("tomato", variantOverride = "Cherokee"),
                createInstance("basil")
            )
        ),
        CustomDish(
            id = "dish_harvest_bowl",
            name = "Harvest Miso Bowl",
            archetype = DishArchetype.BOWL,
            layers = listOf(
                createInstance("rice_base"),
                createInstance("crispy_chicken", variantOverride = "Herb Grilled"),
                createInstance("avocado", variantOverride = "Sliced Hass"),
                createInstance("tomato", variantOverride = "Green Zebra"),
                createInstance("house_sauce", variantOverride = "Spicy Yuzu")
            )
        )
    )
}
