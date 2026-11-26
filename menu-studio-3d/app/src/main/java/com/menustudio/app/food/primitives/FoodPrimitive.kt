package com.menustudio.app.food.primitives

import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuIngredientInstance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reusable pure-Kotlin mesh accumulator holding POS (xyz), UV0 (uv), COLOR (rgba), and INDICES.
 * Avoids heap allocations across slider drags on the FoodBakeWorker thread.
 */
class MeshAppender {
    var pos = FloatArray(4096 * 3)
    var uv = FloatArray(4096 * 2)
    var col = FloatArray(4096 * 4)
    var idx = IntArray(8192 * 3)
    var vCount = 0
    var iCount = 0

    fun reset() {
        vCount = 0
        iCount = 0
    }

    fun addVertex(
        x: Float,
        y: Float,
        z: Float,
        u: Float,
        v: Float,
        r: Float = 1f,
        g: Float = 1f,
        b: Float = 1f,
        ao: Float = 1f
    ): Int {
        val id = vCount++
        if (vCount * 3 > pos.size) pos = pos.copyOf(pos.size * 2)
        if (vCount * 2 > uv.size) uv = uv.copyOf(uv.size * 2)
        if (vCount * 4 > col.size) col = col.copyOf(col.size * 2)

        pos[id * 3] = x
        pos[id * 3 + 1] = y
        pos[id * 3 + 2] = z
        uv[id * 2] = u
        uv[id * 2 + 1] = v
        col[id * 4] = r
        col[id * 4 + 1] = g
        col[id * 4 + 2] = b
        col[id * 4 + 3] = ao
        return id
    }

    fun addTri(a: Int, b: Int, c: Int) {
        if (iCount + 3 > idx.size) idx = idx.copyOf(idx.size * 2)
        idx[iCount++] = a
        idx[iCount++] = b
        idx[iCount++] = c
    }

    fun stitchGrid(startV: Int, rows: Int, segs: Int, flip: Boolean) {
        val rv = segs + 1
        for (r in 0 until rows - 1) {
            for (a in 0 until segs) {
                val i0 = startV + r * rv + a
                val i1 = i0 + 1
                val i2 = startV + (r + 1) * rv + a
                val i3 = i2 + 1
                if (flip) {
                    addTri(i0, i1, i2)
                    addTri(i1, i3, i2)
                } else {
                    addTri(i0, i2, i1)
                    addTri(i1, i2, i3)
                }
            }
        }
    }

    fun stitchQuad(t0: Int, t1: Int, b0: Int, b1: Int) {
        addTri(t0, t1, b0)
        addTri(t1, b1, b0)
    }

    fun computeNormals(out: FloatArray) {
        java.util.Arrays.fill(out, 0, vCount * 3, 0f)
        var i = 0
        while (i < iCount) {
            val i0 = idx[i] * 3
            val i1 = idx[i + 1] * 3
            val i2 = idx[i + 2] * 3
            val ax = pos[i1] - pos[i0]
            val ay = pos[i1 + 1] - pos[i0 + 1]
            val az = pos[i1 + 2] - pos[i0 + 2]
            val bx = pos[i2] - pos[i0]
            val by = pos[i2 + 1] - pos[i0 + 1]
            val bz = pos[i2 + 2] - pos[i0 + 2]
            val nx = ay * bz - az * by
            val ny = az * bx - ax * bz
            val nz = ax * by - ay * bx
            out[i0] += nx; out[i0 + 1] += ny; out[i0 + 2] += nz
            out[i1] += nx; out[i1 + 1] += ny; out[i1 + 2] += nz
            out[i2] += nx; out[i2 + 1] += ny; out[i2 + 2] += nz
            i += 3
        }
        var v = 0
        while (v < vCount * 3) {
            val l = sqrt(out[v] * out[v] + out[v + 1] * out[v + 1] + out[v + 2] * out[v + 2])
            if (l > 1e-6f) {
                out[v] /= l; out[v + 1] /= l; out[v + 2] /= l
            } else {
                out[v] = 0f; out[v + 1] = 1f; out[v + 2] = 0f
            }
            v += 3
        }
    }
}

/**
 * Contract for per-ingredient sculpted UV-mapped geometry primitives.
 */
interface FoodPrimitive {
    fun bake(
        archetype: DishArchetype,
        inst: MenuIngredientInstance,
        orderIdx: Int,
        baseY: Float,
        appender: MeshAppender
    ): Float // returns exact physical topY for zero-clipping next layer
}

internal object PrimitiveGeometryHelpers {
    fun addPlanarSlice(
        appender: MeshAppender,
        cx: Float,
        by: Float,
        cz: Float,
        radius: Float,
        height: Float,
        seed: Int,
        loculeDepth: Float = 0.0f,
        crinkleAmp: Float = 0.0f
    ) {
        val rings = 10
        val segs = 32
        val rv = segs + 1
        val startV = appender.vCount

        for (ri in 0 until rings) {
            val u = (ri.toFloat() / (rings - 1)) * 2f - 1f
            val absU = abs(u)
            val isFace = absU > 0.22f
            val rf = if (isFace) ((1f - absU) / 0.78f).coerceIn(0f, 1f) else 1f
            val yLocal = (u * 0.5f + 0.5f) * height

            for (ai in 0 until rv) {
                val th = (ai.toFloat() / segs) * (2f * PI.toFloat())
                val rib = 1f + sin(th * 5f + seed) * 0.04f
                val rad = radius * rf * rib
                val lx = cos(th) * rad
                val lz = sin(th) * rad

                val locule = if (isFace && loculeDepth > 0f) {
                    exp(-((rf - 0.54f) / 0.18f).pow(2)) * (0.5f + 0.5f * cos(th * 5f)).pow(2f)
                } else 0f
                val crinkle = if (crinkleAmp > 0f && isFace) {
                    sin((lx / radius) * 10f * PI.toFloat()) * crinkleAmp
                } else 0f
                val y = by + yLocal - sign(u) * locule * (height * loculeDepth) + crinkle

                val texU = (0.5f + lx / (2f * radius)).coerceIn(0f, 1f)
                val texV = (0.5f + lz / (2f * radius)).coerceIn(0f, 1f)
                val shade = if (!isFace) 0.90f else 1.0f
                val ao = (0.94f - locule * 0.22f).coerceIn(0.6f, 1f)
                appender.addVertex(cx + lx, y, cz + lz, texU, texV, shade, shade, shade, ao)
            }
        }
        appender.stitchGrid(startV, rings, segs, flip = false)
    }

    fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/**
 * Routes every [IngredientCatalog] typeId cleanly to its dedicated [FoodPrimitive] and texture key.
 */
object FoodPrimitiveRegistry {
    private val briocheCrown = BriocheCrownPrimitive()
    private val briocheHeel = BriocheHeelPrimitive()
    private val wagyuPatty = WagyuPattyPrimitive()
    private val crispyChicken = CrispyChickenPrimitive()
    private val portobello = PortobelloPrimitive()
    private val drapedCheese = DrapedCheesePrimitive()
    private val mozzarellaPuddles = MozzarellaPuddlesPrimitive()
    private val heirloomTomato = HeirloomTomatoPrimitive()
    private val ruffledLettuce = RuffledLettucePrimitive()
    private val sweetBasil = SweetBasilPrimitive()
    private val smokedBacon = SmokedBaconPrimitive()
    private val pickleChips = PickleChipsPrimitive()
    private val avocadoFan = AvocadoFanPrimitive()
    private val pizzaCrust = PizzaCrustPrimitive()
    private val sauceSpread = SauceSpreadPrimitive()

    val slateBoard: SlateBoardPrimitive = SlateBoardPrimitive()

    fun resolvePrimitive(typeId: String): FoodPrimitive = when (typeId) {
        "brioche_top" -> briocheCrown
        "brioche_base" -> briocheHeel
        "patty" -> wagyuPatty
        "crispy_chicken" -> crispyChicken
        "portobello" -> portobello
        "cheddar", "gruyere" -> drapedCheese
        "mozzarella" -> mozzarellaPuddles
        "tomato" -> heirloomTomato
        "lettuce" -> ruffledLettuce
        "basil" -> sweetBasil
        "bacon" -> smokedBacon
        "pickles" -> pickleChips
        "avocado" -> avocadoFan
        "sourdough_crust" -> pizzaCrust
        else -> sauceSpread
    }

    fun resolveTextureKey(typeId: String): String = when (typeId) {
        "brioche_top", "brioche_base" -> "brioche"
        "sourdough_crust" -> "sourdough_crust"
        "patty" -> "patty"
        "crispy_chicken" -> "chicken"
        "portobello" -> "mushroom"
        "cheddar", "gruyere", "rice_base" -> "cheese"
        "mozzarella" -> "mozzarella"
        "tomato", "san_marzano" -> "tomato"
        "lettuce" -> "lettuce"
        "basil" -> "basil"
        "bacon" -> "bacon"
        "pickles" -> "pickle"
        "avocado" -> "avocado"
        "house_sauce", "crispy_onions" -> "sauce"
        else -> "sauce"
    }
}
