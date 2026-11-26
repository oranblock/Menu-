package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Lush butterhead lettuce: 1 central cushion leaf closing any center hole + 5 outer cupped leaves
 * curling softly past the bun rim without jagged sawtooth spikes.
 */
class RuffledLettucePrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val bunR = 1.54f
        val h = 0.11f * inst.clampedThickness
        val seed = inst.uid.hashCode()
        val rings = 9
        val segs = 48
        val rv = segs + 1

        fun addLeaf(li: Int, angle: Float, centerOffset: Float, leafRadius: Float, yStep: Float) {
            val cx = cos(angle) * centerOffset
            val cz = sin(angle) * centerOffset
            val startV = appender.vCount

            for (ri in 0..rings) {
                val f = ri.toFloat() / rings
                for (ai in 0 until rv) {
                    val th = (ai.toFloat() / segs) * (2f * PI.toFloat())
                    val scallop = 1f + sin(th * 5f + li * 1.7f + seed) * 0.065f + cos(th * 9f) * 0.025f
                    val rad = leafRadius * f * scallop
                    val wx = cx + cos(th + angle) * rad
                    val wz = cz + sin(th + angle) * rad
                    val over = (hypot(wx, wz) - bunR * 0.85f).coerceAtLeast(0f)
                    val cupLift = (f * f) * 0.024f
                    val ruffle = (sin(th * 5f + li * 1.7f) * 0.045f + cos(th * 9f) * 0.018f) * f * f
                    val wy = baseY + 0.012f + yStep + cupLift + over * 0.24f + ruffle

                    val texU = (0.5f + sin(th) * f * 0.45f).coerceIn(0f, 1f)
                    val texV = (0.94f - (cos(th) * f * 0.45f + 0.45f)).coerceIn(0f, 1f)
                    val ao = (0.86f + f * 0.12f).coerceIn(0.75f, 1f)
                    appender.addVertex(wx, wy, wz, texU, texV, 1f, 1f, 1f, ao)
                }
            }
            appender.stitchGrid(startV, rings + 1, segs, flip = false)
        }

        // 1 central cushion leaf closes the middle against top-down holes
        addLeaf(li = 0, angle = 0.4f, centerOffset = 0.06f, leafRadius = 1.15f, yStep = 0.0f)

        // 5 outer cupped leaves stepped cleanly in y
        val outerCount = 5
        for (i in 0 until outerCount) {
            val angle = (i.toFloat() / outerCount) * (2f * PI.toFloat()) + 0.22f
            addLeaf(li = i + 1, angle = angle, centerOffset = 0.34f, leafRadius = 1.24f, yStep = (i + 1) * 0.016f)
        }

        return baseY + h * 1.05f
    }
}
