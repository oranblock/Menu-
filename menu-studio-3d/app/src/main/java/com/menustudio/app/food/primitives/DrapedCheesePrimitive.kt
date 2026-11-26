package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Melted rotated cheese square with softly rounded corners and smooth gravity drape
 * hugging the sides of the patty instead of sticking out rigidly.
 */
class DrapedCheesePrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val halfSize = if (archetype == DishArchetype.FLAT) 1.54f else 1.50f
        val thick = 0.055f * inst.clampedThickness
        val rotRad = (PI / 4.0).toFloat() + orderIdx * 0.42f
        val seed = inst.uid.hashCode()
        val segs = 24
        val row = segs + 1
        val cosR = cos(rotRad)
        val sinR = sin(rotRad)

        fun roundedCornerXY(vx: Float, vz: Float): FloatArray {
            val dist = hypot(vx, vz)
            val cornerPull = if (dist > 0.96f) {
                1f - PrimitiveGeometryHelpers.smoothstep(0.96f, 1.414f, dist) * 0.14f
            } else 1f
            val lx = vx * halfSize * cornerPull
            val lz = vz * halfSize * cornerPull
            return floatArrayOf(lx * cosR - lz * sinR, lx * sinR + lz * cosR, dist)
        }

        fun sampleCheeseY(vx: Float, vz: Float, dist: Float): Float {
            val shoulderClearance = 0.022f * (1f - PrimitiveGeometryHelpers.smoothstep(0.86f, 1.12f, dist))
            val droop = -0.28f * PrimitiveGeometryHelpers.smoothstep(0.88f, 1.36f, dist)
            val ripple = sin(vx * 4.5f + seed) * cos(vz * 4.5f) * 0.005f
            return baseY + thick + shoulderClearance + droop + ripple
        }

        val topStart = appender.vCount
        for (iz in 0..segs) {
            val vz = (iz.toFloat() / segs) * 2f - 1f
            val tv = iz.toFloat() / segs
            for (ix in 0..segs) {
                val vx = (ix.toFloat() / segs) * 2f - 1f
                val tu = ix.toFloat() / segs
                val pt = roundedCornerXY(vx, vz)
                appender.addVertex(pt[0], sampleCheeseY(vx, vz, pt[2]), pt[1], tu, tv, 1f, 1f, 1f, 0.96f)
            }
        }
        val botStart = appender.vCount
        for (iz in 0..segs) {
            val vz = (iz.toFloat() / segs) * 2f - 1f
            val tv = iz.toFloat() / segs
            for (ix in 0..segs) {
                val vx = (ix.toFloat() / segs) * 2f - 1f
                val tu = ix.toFloat() / segs
                val pt = roundedCornerXY(vx, vz)
                appender.addVertex(pt[0], sampleCheeseY(vx, vz, pt[2]) - thick, pt[1], tu, tv, 0.92f, 0.90f, 0.88f, 0.74f)
            }
        }
        appender.stitchGrid(topStart, row, segs, flip = false)
        appender.stitchGrid(botStart, row, segs, flip = true)
        for (i in 0 until segs) {
            appender.stitchQuad(topStart + i, topStart + i + 1, botStart + i, botStart + i + 1)
            val s = segs * row
            appender.stitchQuad(topStart + s + i + 1, topStart + s + i, botStart + s + i + 1, botStart + s + i)
            appender.stitchQuad(topStart + (i + 1) * row, topStart + i * row, botStart + (i + 1) * row, botStart + i * row)
            appender.stitchQuad(topStart + i * row + segs, topStart + (i + 1) * row + segs, botStart + i * row + segs, botStart + (i + 1) * row + segs)
        }
        return baseY + thick * 0.92f
    }
}
