package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Irregular craggy hand-breaded buttermilk chicken filet with natural oval asymmetry (1.52 x 1.28).
 */
class CrispyChickenPrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val rx = 1.52f
        val rz = 1.28f
        val h = 0.36f * inst.clampedThickness
        val seed = inst.uid.hashCode()
        val rings = 16
        val segs = 52
        val rv = segs + 1
        val startV = appender.vCount

        for (ri in 0 until rings) {
            val u = (ri.toFloat() / (rings - 1)) * 2f - 1f
            val absU = abs(u)
            val rf = sqrt((1f - absU * absU).coerceAtLeast(0f))
            val yLocal = (0.5f + 0.5f * sign(u) * absU.pow(0.48f)) * h
            for (ai in 0 until rv) {
                val th = (ai.toFloat() / segs) * (2f * PI.toFloat())
                // Hand-cut tapered breast silhouette + craggy flour flakes
                val taper = 1f + 0.14f * cos(th - 0.5f) - 0.08f * sin(th * 2f)
                val crag = 1f + sin(th * 6f + seed) * 0.075f + cos(th * 13f - seed) * 0.040f
                val px = cos(th) * rx * rf * taper * crag
                val pz = sin(th) * rz * rf * taper * crag
                val flakeY = (sin(th * 9f + u * 7f) + cos(th * 15f - u * 5f)) * 0.022f * (1f - absU * 0.35f)
                val texU = (0.5f + px / (2.2f * rx)).coerceIn(0f, 1f)
                val texV = (0.5f + pz / (2.2f * rz)).coerceIn(0f, 1f)
                val ao = (0.88f + (1f - absU) * 0.11f).coerceIn(0.72f, 1f)
                appender.addVertex(px, baseY + yLocal + flakeY, pz, texU, texV, 1f, 1f, 1f, ao)
            }
        }
        appender.stitchGrid(startV, rings, segs, flip = false)
        return baseY + h * 0.90f
    }
}
