package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Domed glossy roasted Portobello mushroom cap with softly tucked-in rim and dark gill underside.
 */
class PortobelloPrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val radius = 1.38f
        val h = 0.30f * inst.clampedThickness
        val seed = inst.uid.hashCode()
        val segs = 48
        val rv = segs + 1
        val startV = appender.vCount

        // Profile from underside center -> recessed gill cavity -> tucked outer lip -> domed roasted cap apex
        val profile = arrayOf(
            floatArrayOf(0.00f, 0.14f, 0.72f),
            floatArrayOf(0.55f, 0.10f, 0.74f),
            floatArrayOf(0.86f, 0.02f, 0.80f),
            floatArrayOf(0.98f, 0.08f, 0.88f),
            floatArrayOf(1.00f, 0.32f, 0.94f),
            floatArrayOf(0.92f, 0.62f, 0.98f),
            floatArrayOf(0.72f, 0.86f, 1.00f),
            floatArrayOf(0.42f, 0.97f, 1.00f),
            floatArrayOf(0.00f, 1.00f, 1.00f)
        )

        for (ri in profile.indices) {
            val rf = profile[ri][0]
            val yf = profile[ri][1]
            val shade = profile[ri][2]
            for (ai in 0 until rv) {
                val th = (ai.toFloat() / segs) * (2f * PI.toFloat())
                val asym = 1f + sin(th * 3f + seed) * 0.045f + cos(th * 5f - seed) * 0.022f
                val px = cos(th) * radius * rf * asym
                val pz = sin(th) * radius * rf * asym
                val texU = (0.5f + px / (2.1f * radius)).coerceIn(0f, 1f)
                val texV = (0.5f + pz / (2.1f * radius)).coerceIn(0f, 1f)
                appender.addVertex(px, baseY + yf * h, pz, texU, texV, shade, shade * 0.96f, shade * 0.92f, shade)
            }
        }
        appender.stitchGrid(startV, profile.size, segs, flip = false)
        return baseY + h * 0.94f
    }
}
