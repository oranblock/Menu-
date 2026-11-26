package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 7 smooth pointed-oval Genovese basil leaves with gentle U-shaped midrib cupping
 * and natural downward tip droop scattered organically across the dish.
 */
class SweetBasilPrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val t = inst.clampedThickness
        val seed = inst.uid.hashCode()
        val length = 0.56f * (0.88f + t * 0.14f)
        val width = 0.30f * (0.88f + t * 0.14f)
        val rows = 10
        val cols = 8
        val colVerts = cols + 1
        val leafCount = 7

        val placements = arrayOf(
            floatArrayOf(0.18f, 0.35f),
            floatArrayOf(0.68f, 1.05f),
            floatArrayOf(0.92f, 2.02f),
            floatArrayOf(0.58f, 2.95f),
            floatArrayOf(0.96f, 3.88f),
            floatArrayOf(0.52f, 4.78f),
            floatArrayOf(0.84f, 5.62f)
        )
        val radiusScale = if (archetype == DishArchetype.STACK) 0.82f else 1.0f

        for (li in 0 until leafCount) {
            val radial = placements[li][0] * radiusScale
            val posAng = placements[li][1] + (seed and 7) * 0.12f
            val cx = cos(posAng) * radial
            val cz = sin(posAng) * radial
            val yaw = posAng + 1.1f + (li % 3) * 0.65f
            val cy = cos(yaw)
            val sy = sin(yaw)
            val pitch = 0.08f + (li % 3) * 0.045f
            val nestOffset = if (archetype == DishArchetype.FLAT) 0.024f else 0.016f
            val leafBaseY = baseY + nestOffset + li * 0.007f
            val startV = appender.vCount

            for (ri in 0..rows) {
                val uAlong = ri.toFloat() / rows // 0 = stem base, 1 = pointed tip
                val profileWidth = width * sin(uAlong * PI.toFloat()).let {
                    if (it < 0f) 0f else kotlin.math.sqrt(it) * (1f - 0.18f * uAlong)
                }
                val centerZ = (uAlong - 0.5f) * length
                val archY = sin(uAlong * PI.toFloat()) * 0.032f - (uAlong * uAlong) * 0.048f

                for (ci in 0 until colVerts) {
                    val lat = (ci.toFloat() / cols) * 2f - 1f // -1..1 across leaf blade
                    val localX = lat * profileWidth
                    // Gentle U-shaped midrib cupping
                    val cupY = abs(lat) * 0.045f + sin(uAlong * 4f + li) * 0.006f
                    val localY = archY + cupY + (uAlong - 0.5f) * pitch

                    val wx = cx + localX * cy - centerZ * sy
                    val wz = cz + localX * sy + centerZ * cy
                    val wy = leafBaseY + localY

                    val texU = (0.5f + lat * 0.44f).coerceIn(0f, 1f)
                    val texV = (0.94f - uAlong * 0.88f).coerceIn(0f, 1f)
                    val ao = (0.90f + abs(lat) * 0.08f).coerceIn(0.75f, 1f)
                    appender.addVertex(wx, wy, wz, texU, texV, 1f, 1f, 1f, ao)
                }
            }
            appender.stitchGrid(startV, rows + 1, cols, flip = false)
        }
        return baseY + 0.075f * t
    }
}
