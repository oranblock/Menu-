package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Organic swirled glaze, tomato sugo, aioli, crispy onions, or bowl grain base layer.
 */
class SauceSpreadPrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val t = inst.clampedThickness
        val seed = inst.uid.hashCode()
        val isFlatSugo = archetype == DishArchetype.FLAT && inst.typeId == "san_marzano"
        val isBowlBase = archetype == DishArchetype.BOWL && inst.typeId == "rice_base"

        val radius = when {
            isFlatSugo -> 1.68f
            isBowlBase -> 1.65f
            else -> 1.32f
        }
        val height = when {
            isBowlBase -> 0.46f * t
            isFlatSugo -> 0.06f * t
            else -> 0.085f * t
        }

        if (isBowlBase) {
            // Concave warm grain base for bowl dishes
            val rings = 12
            val segs = 44
            val rv = segs + 1
            val startV = appender.vCount
            for (ri in 0..rings) {
                val f = ri.toFloat() / rings
                val bowlY = baseY + (0.28f + 0.72f * f * f) * height
                for (ai in 0 until rv) {
                    val th = (ai.toFloat() / segs) * (2f * PI.toFloat())
                    val grain = sin(th * 13f + f * 18f + seed) * 0.015f
                    val px = cos(th) * radius * f
                    val pz = sin(th) * radius * f
                    val texU = (0.5f + px / (2f * radius)).coerceIn(0f, 1f)
                    val texV = (0.5f + pz / (2f * radius)).coerceIn(0f, 1f)
                    appender.addVertex(px, bowlY + grain, pz, texU, texV, 1f, 1f, 1f, 0.94f)
                }
            }
            appender.stitchGrid(startV, rings + 1, segs, flip = false)
            return baseY + height * 0.65f
        }

        PrimitiveGeometryHelpers.addPlanarSlice(
            appender = appender,
            cx = 0f,
            by = baseY,
            cz = 0f,
            radius = radius,
            height = height,
            seed = seed
        )
        return baseY + height * 0.85f
    }
}
