package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 4 shingled crescent Hass avocado slices fanned around the center with staggered Y.
 */
class AvocadoFanPrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val h = 0.10f * inst.clampedThickness
        val slices = 4
        for (i in 0 until slices) {
            val yaw = (i.toFloat() / slices) * (2f * PI.toFloat()) + 0.35f
            val cx = cos(yaw) * 0.50f
            val cz = sin(yaw) * 0.50f
            addCrescentSlice(appender, cx, baseY + i * (h * 0.22f), cz, yaw, 0.68f, h)
        }
        return baseY + h * 1.45f
    }

    private fun addCrescentSlice(
        appender: MeshAppender,
        cx: Float,
        by: Float,
        cz: Float,
        yaw: Float,
        radius: Float,
        height: Float
    ) {
        val uSteps = 14
        val vSteps = 6
        val rows = vSteps + 1
        val c = cos(yaw)
        val s = sin(yaw)

        fun buildFace(yOff: Float, ao: Float): Int {
            val start = appender.vCount
            for (iv in 0..vSteps) {
                val rFrac = 0.42f + (iv.toFloat() / vSteps) * 0.58f
                val tv = iv.toFloat() / vSteps
                for (iu in 0..uSteps) {
                    val tu = iu.toFloat() / uSteps
                    val ang = (tu - 0.5f) * PI.toFloat() * 0.85f
                    val lx = cos(ang) * radius * rFrac - radius * 0.45f
                    val lz = sin(ang) * radius * rFrac
                    val wx = cx + lx * c - lz * s
                    val wz = cz + lx * s + lz * c
                    appender.addVertex(wx, by + yOff, wz, tu, tv, 1f, 1f, 1f, ao)
                }
            }
            return start
        }

        val topS = buildFace(height, 0.96f)
        val botS = buildFace(0f, 0.78f)
        appender.stitchGrid(topS, rows, uSteps, flip = false)
        appender.stitchGrid(botS, rows, uSteps, flip = true)
    }
}
