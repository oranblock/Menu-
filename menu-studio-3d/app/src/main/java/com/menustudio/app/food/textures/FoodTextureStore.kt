package com.menustudio.app.food.textures

import android.content.Context
import android.graphics.BitmapFactory
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Thread-safe PBR Texture cache & asset-first loader with automatic 512x512 procedural fallback.
 *   - Decodes assets/textures/<key>_{albedo,normal,orm}.{webp,png} OR bakes procedurally off main thread.
 *   - Uploads Filament Texture handles strictly on the render thread with mipmaps + 8x anisotropy.
 */
class FoodTextureStore(private val appContext: Context) {

    private val cpuPreparedKeys = ConcurrentHashMap.newKeySet<String>()
    private val pendingCpuUploads = ConcurrentLinkedQueue<BakedTextureCpuData>()
    private val gpuTextures = HashMap<String, FoodTextureSet>(16)

    val sampler: TextureSampler by lazy(LazyThreadSafetyMode.NONE) {
        TextureSampler(
            TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
            TextureSampler.MagFilter.LINEAR,
            TextureSampler.WrapMode.REPEAT
        ).apply {
            anisotropy = 8.0f
        }
    }

    /**
     * Safe to call on background FoodBakeWorker thread!
     * Loads from assets/textures/ if present, otherwise falls back to 512x512 ProceduralTextureBaker.
     */
    fun ensureBakedOffMainThread(key: String) {
        if (!cpuPreparedKeys.add(key)) return
        val cpuData = tryLoadFromAssets(key) ?: ProceduralTextureBaker.bake512(key)
        pendingCpuUploads.offer(cpuData)
    }

    /**
     * MUST be called strictly on the Filament Choreographer render thread.
     * Returns true if at least one new texture set became available on GPU.
     */
    fun uploadPendingOnRenderThread(engine: Engine): Boolean {
        var uploadedAny = false
        while (true) {
            val data = pendingCpuUploads.poll() ?: break
            if (gpuTextures.containsKey(data.key)) continue

            val albedoTex = createTexture2D(
                engine = engine,
                width = data.width,
                height = data.height,
                internalFormat = Texture.InternalFormat.SRGB8_A8,
                rgbaData = data.albedoRgba
            )
            val normalTex = createTexture2D(
                engine = engine,
                width = data.width,
                height = data.height,
                internalFormat = Texture.InternalFormat.RGBA8,
                rgbaData = data.normalRgba
            )
            val ormTex = createTexture2D(
                engine = engine,
                width = data.width,
                height = data.height,
                internalFormat = Texture.InternalFormat.RGBA8,
                rgbaData = data.ormRgba
            )
            gpuTextures[data.key] = FoodTextureSet(data.key, albedoTex, normalTex, ormTex)
            uploadedAny = true
        }
        return uploadedAny
    }

    fun getTextureSet(key: String): FoodTextureSet? = gpuTextures[key]

    /**
     * Strictly destroy every Texture AFTER entities and MaterialInstances are destroyed,
     * and BEFORE Material is destroyed.
     */
    fun destroyAll(engine: Engine) {
        pendingCpuUploads.clear()
        cpuPreparedKeys.clear()
        for (set in gpuTextures.values) {
            set.destroy(engine)
        }
        gpuTextures.clear()
    }

    private fun createTexture2D(
        engine: Engine,
        width: Int,
        height: Int,
        internalFormat: Texture.InternalFormat,
        rgbaData: java.nio.ByteBuffer
    ): Texture {
        val tex = Texture.Builder()
            .width(width)
            .height(height)
            .levels(0xFF)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(internalFormat)
            .build(engine)

        rgbaData.rewind()
        val pbd = Texture.PixelBufferDescriptor(
            rgbaData,
            Texture.Format.RGBA,
            Texture.Type.UBYTE
        )
        tex.setImage(engine, 0, pbd)
        tex.generateMipmaps(engine)
        return tex
    }

    private fun tryLoadFromAssets(key: String): BakedTextureCpuData? {
        val albedoBmp = openAssetBitmap("${key}_albedo") ?: return null
        val normalBmp = openAssetBitmap("${key}_normal") ?: run { albedoBmp.recycle(); return null }
        val ormBmp = openAssetBitmap("${key}_orm") ?: run {
            albedoBmp.recycle(); normalBmp.recycle(); return null
        }

        return try {
            val w = albedoBmp.width
            val h = albedoBmp.height
            BakedTextureCpuData(
                key = key,
                width = w,
                height = h,
                albedoRgba = ProceduralTextureBaker.bitmapToRgbaBuffer(albedoBmp),
                normalRgba = ProceduralTextureBaker.bitmapToRgbaBuffer(normalBmp),
                ormRgba = ProceduralTextureBaker.bitmapToRgbaBuffer(ormBmp)
            )
        } finally {
            albedoBmp.recycle()
            normalBmp.recycle()
            ormBmp.recycle()
        }
    }

    private fun openAssetBitmap(stem: String): android.graphics.Bitmap? {
        for (ext in arrayOf("webp", "png")) {
            val path = "textures/$stem.$ext"
            try {
                appContext.assets.open(path).use { stream: InputStream ->
                    return BitmapFactory.decodeStream(stream)
                }
            } catch (_: Throwable) {
                // Asset missing, continue checking fallback extension or procedural baker
            }
        }
        return null
    }
}
