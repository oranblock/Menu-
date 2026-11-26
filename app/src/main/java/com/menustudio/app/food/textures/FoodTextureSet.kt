package com.menustudio.app.food.textures

import com.google.android.filament.Engine
import com.google.android.filament.Texture
import java.nio.ByteBuffer

/**
 * Off-main-thread CPU texture payload ready for render-thread Filament upload.
 */
data class BakedTextureCpuData(
    val key: String,
    val width: Int,
    val height: Int,
    val albedoRgba: ByteBuffer,
    val normalRgba: ByteBuffer,
    val ormRgba: ByteBuffer
)

/**
 * Render-thread owned Filament PBR texture triple (Albedo sRGB, Normal linear, ORM linear).
 * ORM packing: R = AO, G = Roughness, B = Wetness.
 */
class FoodTextureSet(
    val key: String,
    var albedo: Texture?,
    var normal: Texture?,
    var orm: Texture?
) {
    fun isValid(): Boolean = albedo != null && normal != null && orm != null

    fun destroy(engine: Engine) {
        albedo?.let { engine.destroyTexture(it) }
        normal?.let { engine.destroyTexture(it) }
        orm?.let { engine.destroyTexture(it) }
        albedo = null
        normal = null
        orm = null
    }
}
