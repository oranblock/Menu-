package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 7 organically rounded, softly domed melted fior di latte cheese medallions
 * scattered over pizza sauce or skillet dishes so vibrant red sugo peaks between them.
 */
class MozzarellaPuddlesPrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val t = inst.clampedThickness
        val seed = inst.uid.hashCode()
        val height = 0.068f * t
        val spreadScale = when (archetype) {
            DishArchetype.FLAT -> 1.0f
            DishArchetype.BOWL -> 0.85f
            DishArchetype.STACK -> 0.72f
        }

        // Center puddle + 6 outer organic puddles (radius 0.34f..0.48f)
        val puddles = arrayOf(
            floatArrayOf(0.05f, 0.20f, 0.46f),
            floatArrayOf(0.84f, 0.15f, 0.44f),
            floatArrayOf(0.96f, 1.22f, 0.38f),
            floatArrayOf(0.82f, 2.28f, 0.47f),
            floatArrayOf(1.02f, 3.32f, 0.36f),
            floatArrayOf(0.86f, 4.35f, 0.43f),
            floatArrayOf(0.98f, 5.38f, 0.35f)
        )

        val rings = 8
        val segs = 28
        val rv = segs + 1

        for (pi in puddles.indices) {
            val radial = puddles[pi][0] * spreadScale
            val ang = puddles[pi][1] + (seed and 15) * 0.08f
            val rBase = puddles[pi][2] * (0.90f + (t - 1f) * 0.18f)
            val cx = cos(ang) * radial
            val cz = sin(ang) * radial
            val pBaseY = baseY + pi * 0.0025f
            val startV = appender.vCount

            for (ri in 0..rings) {
                val f = ri.toFloat() / rings
                // Soft melted pillow dome that tapers cleanly into the sugo at f = 1.0
                val domeY = sqrt((1f - f * f).coerceAtLeast(0f)) * height
                for (ai in 0 until rv) {
                    val th = (ai.toFloat() / segs) * (2f * PI.toFloat())
                    val wobble = 1f + sin(th * 3f + pi * 1.9f + seed) * 0.10f + cos(th * 5f - pi) * 0.045f
                    val rad = rBase * f * wobble
                    val px = cx + cos(th) * rad
                    val pz = cz + sin(th) * rad
                    val texU = (0.5f + cos(th) * f * 0.48f).coerceIn(0f, 1f)
                    val texV = (0.5f + sin(th) * f * 0.48f).coerceIn(0f, 1f)
                    val ao = (0.98f - f * f * 0.14f).coerceIn(0.78f, 1f)
                    appender.addVertex(px, pBaseY + domeY, pz, texU, texV, 1f, 1f, 1f, ao)
                }
            }
            appender.stitchGrid(startV, rings + 1, segs, flip = false)
        }
        return baseY + height * 0.85f
    }
}
