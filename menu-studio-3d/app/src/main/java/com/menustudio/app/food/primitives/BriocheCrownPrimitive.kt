package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pillowy flat-base Brioche Crown + plump rounded pale waist + high hemispherical dome
 * + 96 3D teardrop sesame seeds oriented and offset cleanly along the dome surface normal.
 */
class BriocheCrownPrimitive : FoodPrimitive {
    override fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float {
        val radius = 1.54f
        val domeH = 0.94f * inst.clampedThickness
        val seed = inst.uid.hashCode()
        val rings = 22
        val segs = 48
        val rv = segs + 1
        val startV = appender.vCount

        fun crownPt(t: Float, th: Float): FloatArray {
            val (r, y) = when {
                t < 0.08f -> ((t / 0.08f) * radius * 0.94f) to 0f
                t < 0.26f -> {
                    val f = (t - 0.08f) / 0.18f
                    (radius * (0.94f + sin(f * PI.toFloat() * 0.5f) * 0.06f)) to (f * domeH * 0.26f)
                }
                else -> {
                    val f = ((t - 0.26f) / 0.74f).coerceIn(0f, 1f)
                    val a = f * (PI.toFloat() * 0.5f)
                    val ringR = (cos(a) * radius).coerceAtLeast(if (t > 0.999f) 0f else 0.002f)
                    ringR to (domeH * 0.26f + sin(a) * domeH * 0.74f)
                }
            }
            val asym = 1f + sin(th * 3f + seed) * 0.020f + cos(th * 2f) * 0.012f
            return floatArrayOf(cos(th) * r * asym, baseY + y, sin(th) * r * asym)
        }

        fun crownNormal(t: Float, th: Float): FloatArray {
            val dt = 0.012f
            val da = 0.03f
            val pT0 = crownPt((t - dt).coerceIn(0.09f, 0.995f), th)
            val pT1 = crownPt((t + dt).coerceIn(0.09f, 0.995f), th)
            val pA0 = crownPt(t, th - da)
            val pA1 = crownPt(t, th + da)
            // Tangent along increasing t (upward toward apex) and tangent along increasing theta
            val tx = pT1[0] - pT0[0]; val ty = pT1[1] - pT0[1]; val tz = pT1[2] - pT0[2]
            val ax = pA1[0] - pA0[0]; val ay = pA1[1] - pA0[1]; val az = pA1[2] - pA0[2]
            var nx = ay * tz - az * ty
            var ny = az * tx - ax * tz
            var nz = ax * ty - ay * tx
            if (ny < 0f) {
                nx = -nx; ny = -ny; nz = -nz
            }
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
            return floatArrayOf(nx / len, ny / len, nz / len)
        }

        val isPretzel = inst.variant.contains("Pretzel", ignoreCase = true)
        for (ri in 0 until rings) {
            val t = ri.toFloat() / (rings - 1)
            // UV0.y maps 0.0f at apex (t = 1.0) -> 1.0f at pale waist (t = 0.10)
            val waistFactor = ((t - 0.10f) / 0.90f).coerceIn(0f, 1f)
            val texV = if (t < 0.08f) 0.96f else (1f - waistFactor)
            val ao = if (t < 0.08f) 0.86f else 0.96f
            for (ai in 0 until rv) {
                val th = (ai.toFloat() / segs) * (2f * PI.toFloat())
                val texU = (ai.toFloat() / segs) * 2.0f
                val p = crownPt(t, th)
                appender.addVertex(p[0], p[1], p[2], texU, texV, 1f, 1f, 1f, ao)
            }
        }
        appender.stitchGrid(startV, rings, segs, flip = false)

        if (!isPretzel) {
            val golden = 2.3999632f
            for (i in 0 until 96) {
                val frac = (i + 0.5f) / 96f
                // Distribute evenly along upper dome t in [0.46, 0.96]
                val t = (0.46f + (1f - sqrt(frac)) * 0.50f).coerceIn(0.46f, 0.96f)
                val th = i * golden
                val p = crownPt(t, th)
                val n = crownNormal(t, th)
                addSesameSeed(
                    appender = appender,
                    cx = p[0] + n[0] * 0.012f,
                    cy = p[1] + n[1] * 0.012f,
                    cz = p[2] + n[2] * 0.012f,
                    nx = n[0],
                    ny = n[1],
                    nz = n[2],
                    yaw = th + i * 0.7f,
                    roast = (i % 3) * 0.08f
                )
            }
        }
        return baseY + domeH
    }

    private fun addSesameSeed(
        appender: MeshAppender,
        cx: Float,
        cy: Float,
        cz: Float,
        nx: Float,
        ny: Float,
        nz: Float,
        yaw: Float,
        roast: Float
    ) {
        // Construct orthonormal tangent frame on the dome so every seed sits flush on slope
        val upRefX = if (kotlin.math.abs(ny) < 0.92f) 0f else 1f
        val upRefY = if (kotlin.math.abs(ny) < 0.92f) 1f else 0f
        var ux = upRefY * nz - 0f * ny
        var uy = 0f * nx - upRefX * nz
        var uz = upRefX * ny - upRefY * nx
        val uLen = sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(1e-5f)
        ux /= uLen; uy /= uLen; uz /= uLen
        val vx = ny * uz - nz * uy
        val vy = nz * ux - nx * uz
        val vz = nx * uy - ny * ux

        val cyYaw = cos(yaw)
        val syYaw = sin(yaw)
        val t1x = ux * cyYaw + vx * syYaw
        val t1y = uy * cyYaw + vy * syYaw
        val t1z = uz * cyYaw + vz * syYaw
        val t2x = -ux * syYaw + vx * cyYaw
        val t2y = -uy * syYaw + vy * cyYaw
        val t2z = -uz * syYaw + vz * cyYaw

        val rx = 0.026f
        val ry = 0.014f
        val rz = 0.052f
        val base = appender.vCount

        // Local coordinates (along tangent1, dome normal, tangent2) + distinct UVs to avoid NaN tangents
        val pts = arrayOf(
            floatArrayOf(0f, ry, 0f, 0.50f, 0.78f),
            floatArrayOf(0f, -ry * 0.35f, 0f, 0.50f, 0.86f),
            floatArrayOf(rx, 0f, 0f, 0.58f, 0.82f),
            floatArrayOf(0f, 0f, rz, 0.50f, 0.72f),
            floatArrayOf(-rx, 0f, 0f, 0.42f, 0.82f),
            floatArrayOf(0f, 0f, -rz * 0.82f, 0.50f, 0.90f)
        )
        val rCol = (1.16f - roast * 0.14f).coerceIn(0.88f, 1.20f)
        val gCol = (1.10f - roast * 0.20f).coerceIn(0.80f, 1.15f)
        val bCol = (0.96f - roast * 0.32f).coerceIn(0.62f, 1.02f)

        for (p in pts) {
            val lx = p[0]; val ly = p[1]; val lz = p[2]
            val wx = cx + t1x * lx + nx * ly + t2x * lz
            val wy = cy + t1y * lx + ny * ly + t2y * lz
            val wz = cz + t1z * lx + nz * ly + t2z * lz
            appender.addVertex(wx, wy, wz, p[3], p[4], rCol, gCol, bCol, 1f)
        }
        val tris = intArrayOf(0, 2, 3, 0, 3, 4, 0, 4, 5, 0, 5, 2, 1, 3, 2, 1, 4, 3, 1, 5, 4, 1, 2, 5)
        var i = 0
        while (i < tris.size) {
            appender.addTri(base + tris[i], base + tris[i + 1], base + tris[i + 2])
            i += 3
        }
    }
}
