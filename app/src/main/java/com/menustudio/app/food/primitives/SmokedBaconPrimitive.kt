package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2 crossed wavy accordion-curled smoked bacon strips staggered cleanly in y (zero self-clip).
 * Lengthwise [0..1] UV0 aligns with horizontal fat marbling bands.
 */
class SmokedBaconPrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val h = 0.055f * inst.clampedThickness
        val seed = inst.uid.hashCode()
        addBaconStrip(appender, baseY, 0.36f, -0.14f, h, seed)
        addBaconStrip(appender, baseY + h * 1.20f, -0.40f, 0.14f, h, seed + 19)
        return baseY + h * 2.20f
    }

    private fun addBaconStrip(
        appender: MeshAppender,
        by: Float,
        rot: Float,
        zOff: Float,
        h: Float,
        seed: Int
    ) {
        val segsX = 24
        val segsZ = 4
        val rows = segsZ + 1
        val c = cos(rot)
        val s = sin(rot)

        fun addGrid(yOffset: Float, ao: Float): Int {
            val start = appender.vCount
            for (iz in 0..segsZ) {
                val tv = iz.toFloat() / segsZ
                val vz = (tv - 0.5f) * 0.44f
                for (ix in 0..segsX) {
                    val tu = ix.toFloat() / segsX
                    val vx = (tu - 0.5f) * 2.45f
                    val wave = sin(vx * 5.8f + seed) * 0.055f + (vx * vx) * 0.032f
                    val wx = vx * c - (vz + zOff) * s
                    val wz = vx * s + (vz + zOff) * c
                    appender.addVertex(wx, by + yOffset + wave, wz, tu, tv, 1f, 1f, 1f, ao)
                }
            }
            return start
        }

        val topS = addGrid(h, 0.94f)
        val botS = addGrid(0f, 0.72f)
        appender.stitchGrid(topS, rows, segsX, flip = false)
        appender.stitchGrid(botS, rows, segsX, flip = true)
    }
}
