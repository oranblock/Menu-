package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Flat pan-baked Brioche Heel with rounded lower rim and planar-mapped toasted top face.
 */
class BriocheHeelPrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val radius = 1.48f
        val h = 0.40f * inst.clampedThickness
        val seed = inst.uid.hashCode()
        val segs = 44
        val rv = segs + 1
        val startV = appender.vCount
        val profile = arrayOf(
            0f to 0f, 0.84f to 0f, 0.96f to 0.12f, 1.0f to 0.35f,
            1.0f to 0.72f, 0.96f to 0.92f, 0.92f to 1.0f, 0f to 1.0f
        )

        for (ri in profile.indices) {
            val (rf, yf) = profile[ri]
            val isTop = ri >= 6
            for (ai in 0 until rv) {
                val th = (ai.toFloat() / segs) * (2f * PI.toFloat())
                val asym = 1f + sin(th * 3f + seed) * 0.02f
                val px = cos(th) * radius * rf * asym
                val pz = sin(th) * radius * rf * asym
                val (texU, texV) = if (isTop) {
                    (0.5f + px / (2f * radius)) to (0.5f + pz / (2f * radius))
                } else {
                    (ai.toFloat() / segs * 2f) to (1f - yf * 0.45f)
                }
                val toastShade = if (isTop) 0.92f else 1.0f
                appender.addVertex(px, baseY + yf * h, pz, texU, texV, toastShade, toastShade * 0.96f, toastShade * 0.90f, 0.92f)
            }
        }
        appender.stitchGrid(startV, profile.size, segs, flip = false)
        return baseY + h * 0.94f
    }
}
