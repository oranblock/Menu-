package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance

/**
 * 4 overlapping crinkle-cut garlic dill pickle chips with sinusoidal knife-ridge displacement.
 */
class PickleChipsPrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val h = 0.07f * inst.clampedThickness
        val seed = inst.uid.hashCode()
        val chips = arrayOf(
            floatArrayOf(-0.56f, 0.0f, -0.34f),
            floatArrayOf(0.54f, 0.0f, -0.28f),
            floatArrayOf(0.40f, h * 0.75f, 0.50f),
            floatArrayOf(-0.44f, h * 0.75f, 0.44f)
        )
        for ((i, c) in chips.withIndex()) {
            PrimitiveGeometryHelpers.addPlanarSlice(
                appender = appender,
                cx = c[0],
                by = baseY + c[1],
                cz = c[2],
                radius = 0.44f,
                height = h,
                seed = seed + i * 11,
                crinkleAmp = h * 0.24f
            )
        }
        return baseY + h * 1.45f
    }
}
