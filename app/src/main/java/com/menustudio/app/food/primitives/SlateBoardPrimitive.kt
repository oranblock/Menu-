package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Grounded static dark slate serving plate (radius = 2.35f, height = 0.14f) at y = -0.14f
 * beneath every dish (STACK, FLAT, BOWL) so food casts crisp contact shadows.
 */
class SlateBoardPrimitive : FoodPrimitive {

    companion object {
        const val BOARD_RADIUS = 2.35f
        const val BOARD_HEIGHT = 0.14f
        const val BOARD_BASE_Y = -0.14f
    }

    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float = bakeBoard(appender, baseY)

    fun bakeBoard(appender: MeshAppender, baseY: Float = BOARD_BASE_Y): Float {
        val r = BOARD_RADIUS
        val h = BOARD_HEIGHT
        val segs = 56
        val rv = segs + 1
        val startV = appender.vCount
        val profile = arrayOf(
            0.0f to 0.0f,
            0.94f to 0.0f,
            0.99f to 0.18f,
            1.0f to 0.82f,
            0.96f to 1.0f,
            0.0f to 1.0f
        )

        for ((rf, yf) in profile) {
            for (ai in 0 until rv) {
                val th = (ai.toFloat() / segs) * (2f * PI.toFloat())
                val stoneChip = 1f + sin(th * 11f) * 0.008f + cos(th * 17f) * 0.005f
                val px = cos(th) * r * rf * stoneChip
                val pz = sin(th) * r * rf * stoneChip
                val texU = (0.5f + px / (2f * r)).coerceIn(0f, 1f)
                val texV = (0.5f + pz / (2f * r)).coerceIn(0f, 1f)
                val ao = if (yf > 0.8f) 0.96f else 0.80f
                appender.addVertex(px, baseY + yf * h, pz, texU, texV, 1f, 1f, 1f, ao)
            }
        }
        appender.stitchGrid(startV, profile.size, segs, flip = false)
        return baseY + h
    }
}
