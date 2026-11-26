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
 * Irregular hand-smashed / thick pub Wagyu patty with planar top/bottom UV0 to prevent pole pinching.
 */
class WagyuPattyPrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val isSmash = inst.variant.contains("Smash", ignoreCase = true)
        val radius = when {
            inst.typeId == "crispy_chicken" -> 1.44f
            inst.typeId == "portobello" -> 1.38f
            isSmash -> 1.50f
            else -> 1.36f
        }
        val h = (if (isSmash || inst.typeId == "portobello") 0.24f else 0.44f) * inst.clampedThickness
        val laceAmp = if (isSmash) 0.12f else 0.045f
        val seed = inst.uid.hashCode()
        val rings = 16
        val segs = 56
        val rv = segs + 1
        val startV = appender.vCount

        for (ri in 0 until rings) {
            val u = (ri.toFloat() / (rings - 1)) * 2f - 1f
            val absU = abs(u)
            val rf = sqrt((1f - absU * absU).coerceAtLeast(0f))
            val yLocal = (0.5f + 0.5f * sign(u) * absU.pow(0.45f)) * h
            for (ai in 0 until rv) {
                val th = (ai.toFloat() / segs) * (2f * PI.toFloat())
                val irreg = 1f + sin(th * 5f + seed) * laceAmp + cos(th * 11f - seed) * (laceAmp * 0.45f)
                val r = radius * rf * irreg
                val px = cos(th) * r
                val pz = sin(th) * r
                val lumpy = sin(th * 7f + u * 6f) * 0.024f * (1f - absU * 0.5f)
                // Planar projection avoids polar UV pinching on seared top & side curds
                val texU = (0.5f + px / (2.1f * radius)).coerceIn(0f, 1f)
                val texV = (0.5f + pz / (2.1f * radius)).coerceIn(0f, 1f)
                val ao = (0.88f + (1f - absU) * 0.10f).coerceIn(0.7f, 1f)
                appender.addVertex(px, baseY + yLocal + lumpy, pz, texU, texV, 1f, 1f, 1f, ao)
            }
        }
        appender.stitchGrid(startV, rings, segs, flip = false)
        return baseY + h * 0.90f
    }
}
