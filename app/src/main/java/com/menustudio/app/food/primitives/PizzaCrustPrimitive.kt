package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Raised bubbly Neapolitan cornicione rim + flat center with planar radial [0..1] UV0.
 */
class PizzaCrustPrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val r = 1.98f
        val h = 0.24f * inst.clampedThickness
        val seed = inst.uid.hashCode()
        val rings = 12
        val segs = 56
        val rv = segs + 1
        val startV = appender.vCount

        for (ri in 0..rings) {
            val f = ri.toFloat() / rings
            val rimBump = exp(-((f - 0.90f) / 0.11f).pow(2)) * h * 1.35f
            val y = baseY + h * 0.25f + rimBump
            for (ai in 0 until rv) {
                val th = (ai.toFloat() / segs) * (2f * PI.toFloat())
                val bubble = sin(th * 9f + seed) * 0.035f * (if (f > 0.75f) 1f else 0.1f)
                val px = cos(th) * r * f
                val pz = sin(th) * r * f
                val texU = (0.5f + px / (2f * r)).coerceIn(0f, 1f)
                val texV = (0.5f + pz / (2f * r)).coerceIn(0f, 1f)
                appender.addVertex(px, y + bubble, pz, texU, texV, 1f, 1f, 1f, 0.92f)
            }
        }
        appender.stitchGrid(startV, rings + 1, segs, flip = false)
        return baseY + h * 0.42f
    }
}
