package com.menustudio.app.food.textures

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Off-main-thread 512x512 procedural PBR texture generator using tileable continuous fBM noise,
 * 3x3 box smoothing, and Sobel normal filtering with zero embossed drawCircle heightfield artifacts.
 */
object ProceduralTextureBaker {

    const val TEX_SIZE = 512

    fun bitmapToRgbaBuffer(bitmap: Bitmap): ByteBuffer {
        val rgbaBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val byteCount = rgbaBitmap.byteCount
        val buf = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
        rgbaBitmap.copyPixelsToBuffer(buf)
        buf.flip()
        if (rgbaBitmap !== bitmap) rgbaBitmap.recycle()
        return buf
    }

    fun bake512(key: String): BakedTextureCpuData {
        val s = TEX_SIZE
        val albedoBmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val ormBmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val height = FloatArray(s * s) { 0.5f }

        val albedoCanvas = Canvas(albedoBmp)
        val ormCanvas = Canvas(ormBmp)

        val normalStrength: Float = when (key) {
            "brioche" -> { bakeBrioche(albedoCanvas, ormCanvas, height, s); 1.6f }
            "sourdough_crust" -> { bakeSourdoughCrust(albedoCanvas, ormCanvas, height, s); 2.2f }
            "patty" -> { bakePatty(albedoCanvas, ormCanvas, height, s); 3.6f }
            "chicken" -> { bakeChicken(albedoCanvas, ormCanvas, height, s); 3.4f }
            "mushroom" -> { bakeMushroom(albedoCanvas, ormCanvas, height, s); 2.0f }
            "cheese" -> { bakeCheese(albedoCanvas, ormCanvas, height, s); 1.6f }
            "mozzarella" -> { bakeMozzarella(albedoCanvas, ormCanvas, height, s); 1.4f }
            "tomato" -> { bakeTomato(albedoCanvas, ormCanvas, height, s); 2.2f }
            "lettuce" -> { bakeLettuce(albedoCanvas, ormCanvas, height, s); 2.6f }
            "basil" -> { bakeBasil(albedoCanvas, ormCanvas, height, s); 2.2f }
            "bacon" -> { bakeBacon(albedoCanvas, ormCanvas, height, s); 3.0f }
            "pickle" -> { bakePickle(albedoCanvas, ormCanvas, height, s); 3.0f }
            "avocado" -> { bakeAvocado(albedoCanvas, ormCanvas, height, s); 1.8f }
            "sauce" -> { bakeSauce(albedoCanvas, ormCanvas, height, s); 1.6f }
            else -> { bakeSlate(albedoCanvas, ormCanvas, height, s); 2.4f }
        }

        val smoothedHeight = boxBlur3x3Tileable(height, s)
        val albedoBuf = bitmapToRgbaBuffer(albedoBmp)
        val ormBuf = bitmapToRgbaBuffer(ormBmp)
        val normalBuf = sobelNormalFromHeight(smoothedHeight, s, normalStrength)

        albedoBmp.recycle()
        ormBmp.recycle()

        return BakedTextureCpuData(
            key = key,
            width = s,
            height = s,
            albedoRgba = albedoBuf,
            normalRgba = normalBuf,
            ormRgba = ormBuf
        )
    }

    /**
     * Tileable 2D value-noise & multi-octave fBM with periodic integer lattice wrapping (ix % freq)
     * so every texture tiles seamlessly across 3D triplanar boundaries.
     */
    fun tileableFbm(u: Float, v: Float, freq: Int, octaves: Int, seed: Int): Float {
        var sum = 0f
        var amp = 0.5f
        var norm = 0f
        var period = freq.coerceAtLeast(1)
        for (o in 0 until octaves) {
            sum += amp * tileableValueNoise(u, v, period, seed + o * 131)
            norm += amp
            amp *= 0.5f
            period *= 2
        }
        return if (norm > 0f) sum / norm else 0.5f
    }

    private fun tileableValueNoise(u: Float, v: Float, period: Int, seed: Int): Float {
        val x = u * period
        val y = v * period
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val fx = x - ix
        val fy = y - iy
        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)
        val a = hash2(ix, iy, period, seed)
        val b = hash2(ix + 1, iy, period, seed)
        val c = hash2(ix, iy + 1, period, seed)
        val d = hash2(ix + 1, iy + 1, period, seed)
        val ab = a + (b - a) * ux
        val cd = c + (d - c) * ux
        return ab + (cd - ab) * uy
    }

    private fun hash2(ix: Int, iy: Int, period: Int, seed: Int): Float {
        val wx = ((ix % period) + period) % period
        val wy = ((iy % period) + period) % period
        var n = wx * 374761393 + wy * 668265263 + seed * 1442695041
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0x7FFFFFFF).toFloat() / 2147483647.0f
    }

    private fun boxBlur3x3Tileable(src: FloatArray, size: Int): FloatArray {
        val out = FloatArray(size * size)
        for (y in 0 until size) {
            val ym = if (y == 0) size - 1 else y - 1
            val yp = if (y == size - 1) 0 else y + 1
            val r0 = ym * size
            val r1 = y * size
            val r2 = yp * size
            for (x in 0 until size) {
                val xm = if (x == 0) size - 1 else x - 1
                val xp = if (x == size - 1) 0 else x + 1
                out[r1 + x] = (
                    src[r0 + xm] + src[r0 + x] + src[r0 + xp] +
                    src[r1 + xm] + src[r1 + x] + src[r1 + xp] +
                    src[r2 + xm] + src[r2 + x] + src[r2 + xp]
                ) / 9.0f
            }
        }
        return out
    }

    fun sobelNormalFromHeight(height: FloatArray, size: Int, strength: Float): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder())
        fun hAt(x: Int, y: Int): Float {
            val wx = ((x % size) + size) % size
            val wy = ((y % size) + size) % size
            return height[wy * size + wx]
        }

        for (y in 0 until size) {
            for (x in 0 until size) {
                val tl = hAt(x - 1, y - 1); val tc = hAt(x, y - 1); val tr = hAt(x + 1, y - 1)
                val ml = hAt(x - 1, y);     val mr = hAt(x + 1, y)
                val bl = hAt(x - 1, y + 1); val bc = hAt(x, y + 1); val br = hAt(x + 1, y + 1)

                val dx = (tr + 2f * mr + br) - (tl + 2f * ml + bl)
                val dy = (bl + 2f * bc + br) - (tl + 2f * tc + tr)

                var nx = -dx * strength
                var ny = -dy * strength
                var nz = 1.0f
                val invLen = 1.0f / sqrt(nx * nx + ny * ny + nz * nz)
                nx *= invLen; ny *= invLen; nz *= invLen

                buf.put(((nx * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255).toByte())
                buf.put(((ny * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255).toByte())
                buf.put(((nz * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255).toByte())
                buf.put(255.toByte())
            }
        }
        buf.flip()
        return buf
    }

    private fun ormColor(ao: Float, roughness: Float, wetness: Float): Int {
        val r = (ao.coerceIn(0f, 1f) * 255f).toInt()
        val g = (roughness.coerceIn(0f, 1f) * 255f).toInt()
        val b = (wetness.coerceIn(0f, 1f) * 255f).toInt()
        return Color.rgb(r, g, b)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerpRgb(c0: Int, c1: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val r = lerp(Color.red(c0).toFloat(), Color.red(c1).toFloat(), k).toInt()
        val g = lerp(Color.green(c0).toFloat(), Color.green(c1).toFloat(), k).toInt()
        val b = lerp(Color.blue(c0).toFloat(), Color.blue(c1).toFloat(), k).toInt()
        return Color.rgb(r, g, b)
    }

    // 1. BRIOCHE: Velvety soft continuous golden-mahogany gradient + fine micro-pore fBM
    private fun bakeBrioche(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        val mahogany = Color.rgb(154, 72, 20)
        val amber = Color.rgb(218, 138, 48)
        val waist = Color.rgb(242, 212, 152)

        for (y in 0 until s) {
            val v = y / fs
            val baseCol = when {
                v < 0.42f -> lerpRgb(mahogany, amber, smoothstep(0.0f, 0.42f, v))
                v < 0.80f -> lerpRgb(amber, waist, smoothstep(0.42f, 0.80f, v))
                else -> lerpRgb(waist, Color.rgb(226, 182, 112), smoothstep(0.80f, 1.0f, v))
            }
            val roughBase = when {
                v < 0.42f -> lerp(0.32f, 0.48f, smoothstep(0f, 0.42f, v))
                v < 0.80f -> lerp(0.48f, 0.84f, smoothstep(0.42f, 0.80f, v))
                else -> 0.82f
            }
            val wetBase = lerp(0.58f, 0.08f, smoothstep(0.0f, 0.78f, v))

            for (x in 0 until s) {
                val u = x / fs
                val mottle = tileableFbm(u, v, freq = 6, octaves = 3, seed = 101)
                val pore = tileableFbm(u, v, freq = 28, octaves = 3, seed = 109)
                val shade = (0.90f + mottle * 0.14f + (pore - 0.5f) * 0.10f).coerceIn(0.78f, 1.08f)
                val r = (Color.red(baseCol) * shade).toInt().coerceIn(0, 255)
                val g = (Color.green(baseCol) * shade).toInt().coerceIn(0, 255)
                val b = (Color.blue(baseCol) * shade).toInt().coerceIn(0, 255)
                val idx = y * s + x
                pixels[idx] = Color.rgb(r, g, b)
                ormPixels[idx] = ormColor(0.92f + pore * 0.08f, (roughBase + (0.5f - pore) * 0.10f).coerceIn(0.25f, 0.92f), wetBase)
                h[idx] = (0.46f + mottle * 0.05f + pore * 0.07f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)
    }

    // 2. SOURDOUGH CRUST: Soft toasted oven-spring flour grain + smooth wood-fired blister mottling
    private fun bakeSourdoughCrust(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        val doughPale = Color.rgb(228, 192, 136)
        val bakedGold = Color.rgb(198, 136, 68)
        val woodChar = Color.rgb(68, 36, 18)

        for (y in 0 until s) {
            val v = y / fs
            for (x in 0 until s) {
                val u = x / fs
                val warp = tileableFbm(u, v, freq = 4, octaves = 2, seed = 121) * 0.16f
                val loaf = tileableFbm(u + warp, v - warp, freq = 6, octaves = 4, seed = 127)
                val crumb = tileableFbm(u, v, freq = 24, octaves = 3, seed = 133)

                val charMix = smoothstep(0.66f, 0.84f, loaf)
                val flourMix = smoothstep(0.22f, 0.40f, 1f - loaf) * smoothstep(0.55f, 0.78f, crumb)
                var col = lerpRgb(bakedGold, woodChar, charMix)
                col = lerpRgb(col, doughPale, flourMix * 0.60f)

                val idx = y * s + x
                pixels[idx] = col
                val rough = lerp(0.68f, 0.88f, charMix + flourMix * 0.3f)
                ormPixels[idx] = ormColor(0.86f + crumb * 0.12f, rough, 0.08f)
                h[idx] = (0.42f + loaf * 0.14f + crumb * 0.08f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)
    }

    // 3. WAGYU PATTY: Domain-warped coarse butcher grind fBM + juicy crevices + albedo-only seasoning
    private fun bakePatty(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        val charCol = Color.rgb(38, 18, 11)
        val roastCol = Color.rgb(102, 46, 25)
        val fatCrevice = Color.rgb(74, 34, 17)

        for (y in 0 until s) {
            val v = y / fs
            for (x in 0 until s) {
                val u = x / fs
                val warp = tileableFbm(u, v, freq = 6, octaves = 3, seed = 201)
                val grind = tileableFbm(u + warp * 0.18f, v + warp * 0.18f, freq = 8, octaves = 4, seed = 211)
                val fine = tileableFbm(u, v, freq = 32, octaves = 2, seed = 223)
                val ridge = (grind * 0.78f + fine * 0.22f).coerceIn(0f, 1f)

                val charMask = smoothstep(0.56f, 0.74f, ridge)
                val creviceMask = smoothstep(0.42f, 0.24f, ridge)

                var col = lerpRgb(roastCol, charCol, charMask)
                col = lerpRgb(col, fatCrevice, creviceMask * 0.65f)

                // Albedo-only 1-2px cracked pepper & flaky salt (never in heightfield!)
                val speckHash = hash2(x, y, s, 299)
                if (speckHash > 0.9965f) {
                    col = Color.rgb(238, 232, 222)
                } else if (speckHash < 0.0045f) {
                    col = Color.rgb(18, 14, 12)
                }

                val rough = lerp(0.56f, 0.85f, charMask) * (1f - creviceMask * 0.70f) + creviceMask * 0.16f
                val wetness = lerp(0.28f, 0.06f, charMask) + creviceMask * 0.60f

                val idx = y * s + x
                pixels[idx] = col
                ormPixels[idx] = ormColor(0.78f + ridge * 0.20f, rough.coerceIn(0.14f, 0.88f), wetness.coerceIn(0.05f, 0.90f))
                h[idx] = (0.34f + ridge * 0.34f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)
    }

    // 4. CRISPY CHICKEN: Craggy buttermilk fried golden-amber ridges
    private fun bakeChicken(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        val deepCrust = Color.rgb(143, 74, 22)   // #8F4A16
        val midGold = Color.rgb(201, 130, 52)    // #C98234
        val crispEdge = Color.rgb(230, 166, 86)

        for (y in 0 until s) {
            val v = y / fs
            for (x in 0 until s) {
                val u = x / fs
                val n1 = tileableFbm(u, v, freq = 10, octaves = 4, seed = 311)
                val ridgeBase = 1f - abs(n1 * 2f - 1f)
                val crag = (ridgeBase * ridgeBase) * 0.72f + tileableFbm(u, v, freq = 26, octaves = 3, seed = 317) * 0.28f

                var col = lerpRgb(deepCrust, midGold, smoothstep(0.24f, 0.64f, crag))
                col = lerpRgb(col, crispEdge, smoothstep(0.64f, 0.90f, crag))

                val idx = y * s + x
                pixels[idx] = col
                val rough = lerp(0.62f, 0.84f, crag)
                ormPixels[idx] = ormColor(0.76f + crag * 0.22f, rough, lerp(0.28f, 0.12f, crag))
                h[idx] = (0.32f + crag * 0.36f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)
    }

    // 5. PORTOBELLO MUSHROOM: Deep roasted umber-chestnut cap + balsamic gloss
    private fun bakeMushroom(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        val darkGlaze = Color.rgb(36, 22, 15)    // #24160F
        val roastedCap = Color.rgb(74, 51, 37)   // #4A3325
        val warmEdge = Color.rgb(104, 74, 54)

        for (y in 0 until s) {
            val v = y / fs
            for (x in 0 until s) {
                val u = x / fs
                val fiber = tileableFbm(u, v * 0.5f, freq = 8, octaves = 3, seed = 349)
                val skin = tileableFbm(u, v, freq = 16, octaves = 3, seed = 353)
                val tone = fiber * 0.65f + skin * 0.35f

                var col = lerpRgb(darkGlaze, roastedCap, smoothstep(0.28f, 0.68f, tone))
                col = lerpRgb(col, warmEdge, smoothstep(0.68f, 0.92f, tone))

                val idx = y * s + x
                pixels[idx] = col
                val rough = lerp(0.24f, 0.48f, tone)
                val wet = lerp(0.80f, 0.50f, tone)
                ormPixels[idx] = ormColor(0.84f + tone * 0.14f, rough, wet)
                h[idx] = (0.44f + fiber * 0.10f + skin * 0.06f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)
    }

    // 6. CHEDDAR CHEESE: Smooth aged cheddar butterfat sheen + soft caramel fBM
    private fun bakeCheese(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        val richGold = Color.rgb(248, 178, 38)
        val deepSharp = Color.rgb(228, 134, 18)

        for (y in 0 until s) {
            val v = y / fs
            for (x in 0 until s) {
                val u = x / fs
                val melt = tileableFbm(u, v, freq = 5, octaves = 3, seed = 383)
                val grain = tileableFbm(u, v, freq = 20, octaves = 2, seed = 389)
                val idx = y * s + x
                pixels[idx] = lerpRgb(deepSharp, richGold, smoothstep(0.25f, 0.78f, melt))
                ormPixels[idx] = ormColor(0.94f + grain * 0.06f, lerp(0.24f, 0.36f, melt), lerp(0.68f, 0.52f, melt))
                h[idx] = (0.48f + (melt - 0.5f) * 0.08f + (grain - 0.5f) * 0.04f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)
    }

    // 7. MOZZARELLA: Snowy milky fior di latte curds with glossy melted sheen
    private fun bakeMozzarella(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        val warmCream = Color.rgb(238, 229, 206)
        val milkWhite = Color.rgb(252, 249, 242)

        for (y in 0 until s) {
            val v = y / fs
            for (x in 0 until s) {
                val u = x / fs
                val stretch = tileableFbm(u, v * 0.5f, freq = 6, octaves = 3, seed = 401)
                val curd = tileableFbm(u, v, freq = 18, octaves = 2, seed = 409)
                val idx = y * s + x
                pixels[idx] = lerpRgb(warmCream, milkWhite, smoothstep(0.28f, 0.75f, stretch * 0.7f + curd * 0.3f))
                ormPixels[idx] = ormColor(0.95f + curd * 0.05f, lerp(0.14f, 0.28f, curd), lerp(0.84f, 0.62f, curd))
                h[idx] = (0.48f + (stretch - 0.5f) * 0.08f + (curd - 0.5f) * 0.04f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)
    }

    // 8. TOMATO: Translucent ruby heirloom cut face with soft locule gel transition & wet sheen
    private fun bakeTomato(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val cx = fs * 0.5f
        val cy = fs * 0.5f
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)

        val corePink = Color.rgb(238, 118, 96)
        val fleshRed = Color.rgb(212, 40, 28)
        val skinRim = Color.rgb(154, 18, 16)
        val seedGel = Color.rgb(122, 14, 18)

        for (y in 0 until s) {
            for (x in 0 until s) {
                val u = x / fs
                val v = y / fs
                val dx = (x - cx) / (fs * 0.5f)
                val dy = (y - cy) / (fs * 0.5f)
                val rad = hypot(dx, dy)
                val theta = atan2(dy, dx)

                val loculeBand = exp(-((rad - 0.52f) / 0.21f) * ((rad - 0.52f) / 0.21f)) *
                    smoothstep(-0.15f, 0.55f, cos(theta * 5f))
                val coreMix = smoothstep(0.24f, 0.0f, rad)
                val rimMix = smoothstep(0.76f, 1.0f, rad)

                var col = lerpRgb(fleshRed, corePink, coreMix * 0.85f)
                col = lerpRgb(col, seedGel, loculeBand * 0.88f)
                col = lerpRgb(col, skinRim, rimMix)

                // Soft pale-gold seeds softly blended inside locule pockets (albedo only)
                val seedWave = sin(rad * 34f + theta * 5f) * cos(theta * 15f)
                val seedGlow = smoothstep(0.68f, 0.96f, seedWave) * smoothstep(0.45f, 0.85f, loculeBand)
                col = lerpRgb(col, Color.rgb(244, 212, 118), seedGlow * 0.85f)

                val idx = y * s + x
                val cellFbm = tileableFbm(u, v, freq = 14, octaves = 2, seed = 433)
                pixels[idx] = col
                val rough = lerp(0.18f, 0.10f, loculeBand)
                val wet = lerp(0.76f, 0.96f, loculeBand)
                ormPixels[idx] = ormColor(0.94f - loculeBand * 0.10f, rough, wet)
                h[idx] = (0.64f - loculeBand * 0.32f + (cellFbm - 0.5f) * 0.05f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)
    }

    // 9. LETTUCE: Smooth organic butterhead blade + soft semi-transparent blurred lime veins
    private fun bakeLettuce(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        val paleHeart = Color.rgb(208, 232, 152)
        val freshMid = Color.rgb(98, 182, 64)
        val deepBlade = Color.rgb(46, 134, 42)

        for (y in 0 until s) {
            val v = y / fs
            for (x in 0 until s) {
                val u = x / fs
                val bladeNoise = tileableFbm(u, v, freq = 8, octaves = 3, seed = 509)
                val tVert = smoothstep(1.0f, 0.05f, v)
                var col = lerpRgb(paleHeart, freshMid, smoothstep(0.0f, 0.48f, tVert))
                col = lerpRgb(col, deepBlade, smoothstep(0.48f, 1.0f, tVert))
                val mod = 0.92f + bladeNoise * 0.14f
                val idx = y * s + x
                pixels[idx] = Color.rgb(
                    (Color.red(col) * mod).toInt().coerceIn(0, 255),
                    (Color.green(col) * mod).toInt().coerceIn(0, 255),
                    (Color.blue(col) * mod).toInt().coerceIn(0, 255)
                )
                ormPixels[idx] = ormColor(0.92f + bladeNoise * 0.06f, 0.44f, 0.34f)
                val mid = exp(-((u - 0.5f) / 0.055f) * ((u - 0.5f) / 0.055f)) * (1f - tVert * 0.5f)
                val crinkle = sin(u * 18f + sin(v * 10f) * 1.4f) * 0.09f + (bladeNoise - 0.5f) * 0.08f
                h[idx] = (0.50f + mid * 0.24f + crinkle).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)

        // Soft semi-transparent blurred lime-yellow curved veins (never white ruler lines!)
        val veinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(95, 225, 245, 175)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        }
        val ormVein = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ormColor(0.95f, 0.34f, 0.46f)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        }
        veinPaint.strokeWidth = 10f
        ormVein.strokeWidth = 10f
        val midrib = Path().apply {
            moveTo(fs * 0.50f, fs * 0.98f)
            quadTo(fs * 0.515f, fs * 0.56f, fs * 0.50f, fs * 0.16f)
        }
        albedo.drawPath(midrib, veinPaint)
        orm.drawPath(midrib, ormVein)

        for (i in 1..6) {
            val yStart = fs * (0.90f - i * 0.11f)
            val yEnd = yStart - fs * 0.15f
            veinPaint.strokeWidth = (5.5f - i * 0.45f).coerceAtLeast(2.2f)
            val pathL = Path().apply {
                moveTo(fs * 0.50f, yStart)
                quadTo(fs * 0.32f, yStart - 22f, fs * 0.14f, yEnd)
            }
            val pathR = Path().apply {
                moveTo(fs * 0.50f, yStart)
                quadTo(fs * 0.68f, yStart - 22f, fs * 0.86f, yEnd)
            }
            albedo.drawPath(pathL, veinPaint)
            albedo.drawPath(pathR, veinPaint)
        }
    }

    // 10. BASIL: Rich satin Genovese green with soft curved midrib & velvet specular
    private fun bakeBasil(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        val deepBasil = Color.rgb(22, 78, 25)    // #164E19
        val brightBasil = Color.rgb(38, 108, 34) // #266C22

        for (y in 0 until s) {
            val v = y / fs
            for (x in 0 until s) {
                val u = x / fs
                val leafTone = tileableFbm(u, v, freq = 6, octaves = 3, seed = 557)
                val pore = tileableFbm(u, v, freq = 20, octaves = 2, seed = 563)
                val centerCup = exp(-((u - 0.5f) / 0.18f) * ((u - 0.5f) / 0.18f))

                val idx = y * s + x
                pixels[idx] = lerpRgb(deepBasil, brightBasil, smoothstep(0.28f, 0.78f, leafTone * 0.7f + centerCup * 0.3f))
                ormPixels[idx] = ormColor(0.90f + pore * 0.08f, lerp(0.28f, 0.42f, pore), lerp(0.50f, 0.34f, pore))
                val rib = exp(-((u - 0.5f) / 0.045f) * ((u - 0.5f) / 0.045f)) * (1f - smoothstep(0.9f, 0.05f, v) * 0.5f)
                h[idx] = (0.48f + rib * 0.22f + (pore - 0.5f) * 0.08f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)

        val midribPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 130, 186, 98)
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(3.5f, BlurMaskFilter.Blur.NORMAL)
        }
        albedo.drawLine(fs * 0.5f, fs * 0.94f, fs * 0.5f, fs * 0.16f, midribPaint)
    }

    // 11. BACON: Smoked mahogany lean bands alternating with glistening golden-ivory fat + albedo pepper
    private fun bakeBacon(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        val fatCol = Color.rgb(236, 210, 170)
        val leanCol = Color.rgb(116, 36, 26)
        val darkSmoke = Color.rgb(72, 22, 16)

        for (y in 0 until s) {
            val v = y / fs
            for (x in 0 until s) {
                val u = x / fs
                val warp = tileableFbm(u, v, freq = 4, octaves = 2, seed = 601) * 0.08f
                val band = abs(v + sin(u * 14f) * 0.05f + warp - 0.5f)
                val fatMix = maxOf(
                    1f - smoothstep(0.06f, 0.12f, abs(band - 0.12f)),
                    1f - smoothstep(0.04f, 0.09f, abs(band - 0.32f))
                ).coerceIn(0f, 1f)
                val meatGrain = tileableFbm(u, v, freq = 18, octaves = 3, seed = 607)

                var col = lerpRgb(lerpRgb(darkSmoke, leanCol, meatGrain), fatCol, fatMix)
                if (hash2(x, y, s, 613) > 0.996f) {
                    col = Color.rgb(22, 16, 14)
                }

                val idx = y * s + x
                pixels[idx] = col
                ormPixels[idx] = ormColor(0.86f + meatGrain * 0.12f, lerp(0.68f, 0.18f, fatMix), lerp(0.24f, 0.86f, fatMix))
                h[idx] = (0.44f + fatMix * 0.14f + sin(u * 20f) * 0.06f + (meatGrain - 0.5f) * 0.08f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)
    }

    // 12. PICKLE: Brined translucent lime-olive radial slice + crinkle-cut corrugation
    private fun bakePickle(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val cx = fs * 0.5f
        val cy = fs * 0.5f
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        val centerCol = Color.rgb(206, 224, 152)
        val midCol = Color.rgb(122, 166, 62)
        val skinCol = Color.rgb(46, 86, 24)

        for (y in 0 until s) {
            for (x in 0 until s) {
                val u = x / fs
                val v = y / fs
                val dx = (x - cx) / (fs * 0.5f)
                val dy = (y - cy) / (fs * 0.5f)
                val rad = hypot(dx, dy)
                val fbm = tileableFbm(u, v, freq = 10, octaves = 3, seed = 701)

                var col = if (rad < 0.70f) {
                    lerpRgb(centerCol, midCol, smoothstep(0.05f, 0.70f, rad))
                } else {
                    lerpRgb(midCol, skinCol, smoothstep(0.70f, 1.0f, rad))
                }
                if (hash2(x, y, s, 709) > 0.996f) {
                    col = Color.rgb(34, 72, 26)
                }

                val idx = y * s + x
                pixels[idx] = col
                ormPixels[idx] = ormColor(0.90f, 0.18f, 0.84f)
                val ridge = sin(u * 18f * PI.toFloat() * 2f) * 0.24f
                h[idx] = (0.50f + ridge + (fbm - 0.5f) * 0.08f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)
    }

    // 13. AVOCADO: Creamy butter-yellow interior transitioning smoothly to emerald rim
    private fun bakeAvocado(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val cx = fs * 0.5f
        val cy = fs * 0.5f
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        val butter = Color.rgb(228, 224, 122)
        val lime = Color.rgb(162, 198, 74)
        val emerald = Color.rgb(74, 134, 42)

        for (y in 0 until s) {
            for (x in 0 until s) {
                val u = x / fs
                val v = y / fs
                val rad = hypot((x - cx) / (fs * 0.65f), (y - cy) / (fs * 0.65f))
                val creamy = tileableFbm(u, v, freq = 8, octaves = 3, seed = 809)
                val col = if (rad < 0.62f) {
                    lerpRgb(butter, lime, smoothstep(0.0f, 0.62f, rad))
                } else {
                    lerpRgb(lime, emerald, smoothstep(0.62f, 1.0f, rad))
                }
                val idx = y * s + x
                pixels[idx] = col
                ormPixels[idx] = ormColor(0.95f, 0.30f, 0.58f)
                h[idx] = (0.50f + sin(v * 64f) * 0.03f + (creamy - 0.5f) * 0.06f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)
    }

    // 14. SAUCE: Creamy emulsion swirls + albedo herb flecks
    private fun bakeSauce(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        val innerCream = Color.rgb(236, 174, 108)
        val outerGlaze = Color.rgb(218, 134, 72)

        for (y in 0 until s) {
            val v = y / fs
            for (x in 0 until s) {
                val u = x / fs
                val swirl = tileableFbm(u, v, freq = 6, octaves = 3, seed = 907)
                var col = lerpRgb(outerGlaze, innerCream, swirl)
                if (hash2(x, y, s, 911) > 0.996f) {
                    col = Color.rgb(36, 48, 24)
                }
                val idx = y * s + x
                pixels[idx] = col
                ormPixels[idx] = ormColor(0.95f, 0.22f, 0.80f)
                h[idx] = (0.50f + (swirl - 0.5f) * 0.12f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)
    }

    // 15. SLATE: Dark basalt stone slate slab (#1A1816) with mineral fBM grain
    private fun bakeSlate(albedo: Canvas, orm: Canvas, h: FloatArray, s: Int) {
        val fs = s.toFloat()
        val pixels = IntArray(s * s)
        val ormPixels = IntArray(s * s)
        for (y in 0 until s) {
            val v = y / fs
            for (x in 0 until s) {
                val u = x / fs
                val cleave = tileableFbm(u, v * 0.6f, freq = 5, octaves = 4, seed = 1013)
                val mineral = tileableFbm(u, v, freq = 24, octaves = 2, seed = 1019)
                val tone = cleave * 0.72f + mineral * 0.28f
                val lum = (20 + tone * 24).toInt().coerceIn(14, 50)
                val idx = y * s + x
                pixels[idx] = Color.rgb(lum + 2, lum, lum - 2)
                ormPixels[idx] = ormColor(0.86f + mineral * 0.12f, (0.76f + cleave * 0.14f).coerceIn(0.72f, 0.92f), 0.05f)
                h[idx] = (0.42f + cleave * 0.22f + mineral * 0.08f).coerceIn(0f, 1f)
            }
        }
        drawIntPixels(albedo, pixels, s)
        drawIntPixels(orm, ormPixels, s)
    }

    private fun drawIntPixels(canvas: Canvas, pixels: IntArray, size: Int) {
        val bmp = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        bmp.recycle()
    }
}
