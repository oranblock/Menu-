package com.menustudio.app.bridge

import kotlin.math.roundToInt

/**
 * Ingredient category matching the studio bottom dock filter pills and color coding.
 */
enum class IngredientCategory(val wireId: String, val displayLabel: String) {
    BASE("base", "Base"),
    MAIN("main", "Mains"),
    CHEESE("cheese", "Cheese"),
    FRESH("fresh", "Fresh"),
    SAUCE("sauce", "Sauces"),
    EXTRA("extra", "Extras")
}

/**
 * Structural layout archetype supported by the procedural 3D food engine.
 */
enum class DishArchetype(val wireId: String, val displayLabel: String) {
    STACK("stack", "Stack"),
    FLAT("flat", "Flat"),
    BOWL("bowl", "Bowl")
}

/**
 * Single ingredient instance inside a custom dish stack.
 * Unique [uid] allows multiple instances of the same ingredient (e.g., double patty, double cheese).
 */
data class MenuIngredientInstance(
    val uid: String,
    val typeId: String,
    val label: String,
    val category: IngredientCategory,
    val variant: String,
    val thickness: Float = 1.0f,
    val price: Double,
    val kcal: Int,
    val visible: Boolean = true
) {
    val clampedThickness: Float
        get() = thickness.coerceIn(0.5f, 1.5f)

    val effectivePrice: Double
        get() = ((price * clampedThickness) * 100.0).roundToInt() / 100.0

    val effectiveKcal: Int
        get() = (kcal * clampedThickness).roundToInt()
}

/**
 * Custom dish model ordered bottom -> top in [layers].
 */
data class CustomDish(
    val id: String,
    val name: String,
    val archetype: DishArchetype,
    val layers: List<MenuIngredientInstance>
) {
    val totalPrice: Double
        get() {
            var sum = 0.0
            for (layer in layers) {
                if (layer.visible) sum += layer.effectivePrice
            }
            return (sum * 100.0).roundToInt() / 100.0
        }

    val totalKcal: Int
        get() {
            var sum = 0
            for (layer in layers) {
                if (layer.visible) sum += layer.effectiveKcal
            }
            return sum
        }
}
