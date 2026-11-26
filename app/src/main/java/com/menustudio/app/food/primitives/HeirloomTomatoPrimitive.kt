package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance

/**
 * 3 distinct circular heirloom tomato slices staggered in (x, z, y) with radial planar UV0
 * centered on every slice so the 5-locule seed map renders crisply on each piece.
 */
class HeirloomTomatoPrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val thick = 0.13f * inst.clampedThickness
        val seed = inst.uid.hashCode()
        val slices = arrayOf(
            floatArrayOf(-0.52f, 0.00f, -0.22f, 0.74f),
            floatArrayOf(0.54f, 0.00f, 0.08f, 0.68f),
            floatArrayOf(-0.04f, thick * 0.72f, 0.54f, 0.60f)
        )
        for ((idx, s) in slices.withIndex()) {
            PrimitiveGeometryHelpers.addPlanarSlice(
                appender = appender,
                cx = s[0],
                by = baseY + s[1],
                cz = s[2],
                radius = s[3],
                height = thick,
                seed = seed + idx * 17,
                loculeDepth = 0.22f
            )
        }
        return baseY + thick * 1.42f
    }
}
