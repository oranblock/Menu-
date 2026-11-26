package com.menustudio.app.food

import com.google.android.filament.Box
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.menustudio.app.food.textures.FoodTextureSet
import com.menustudio.app.food.textures.FoodTextureStore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * Prevents Filament native double-free crashes (Assertion failed: Invalid handle / SIGSEGV).
 * Must ONLY be called on the Filament render thread.
 */
class FilamentSafeGuard {
    private val destroyedMaterials: MutableSet<MaterialInstance> =
        Collections.newSetFromMap(IdentityHashMap())

    fun safeDestroyMaterialInstance(engine: Engine, mi: MaterialInstance?) {
        if (mi == null) return
        if (destroyedMaterials.add(mi)) {
            engine.destroyMaterialInstance(mi)
        }
    }

    fun clear() = destroyedMaterials.clear()
}

/**
 * Reusable direct ByteBuffer quad (zero steady-state DirectByteBuffer / GC churn).
 * Grows monotonically only if a larger mesh arrives.
 */
class ReusableDirectBuffers {
    var posBuf: ByteBuffer = alloc(1024 * 12)
        private set
    var quatBuf: ByteBuffer = alloc(1024 * 8)
        private set
    var colBuf: ByteBuffer = alloc(1024 * 16)
        private set
    var uvBuf: ByteBuffer = alloc(1024 * 8)
        private set
    var idxBuf: ByteBuffer = alloc(2048 * 4)
        private set
    var normScratch: ByteBuffer = alloc(1024 * 12)
        private set

    fun ensureCapacity(vertexCount: Int, indexCount: Int) {
        if (posBuf.capacity() < vertexCount * 12) posBuf = alloc(nextPow2(vertexCount * 12))
        if (normScratch.capacity() < vertexCount * 12) normScratch = alloc(nextPow2(vertexCount * 12))
        if (quatBuf.capacity() < vertexCount * 8) quatBuf = alloc(nextPow2(vertexCount * 8))
        if (colBuf.capacity() < vertexCount * 16) colBuf = alloc(nextPow2(vertexCount * 16))
        if (uvBuf.capacity() < vertexCount * 8) uvBuf = alloc(nextPow2(vertexCount * 8))
        if (idxBuf.capacity() < indexCount * 4) idxBuf = alloc(nextPow2(indexCount * 4))
    }

    private fun alloc(bytes: Int): ByteBuffer =
        ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())

    private fun nextPow2(v: Int): Int {
        var n = v.coerceAtLeast(256) - 1
        n = n or (n ushr 1); n = n or (n ushr 2); n = n or (n ushr 4)
        n = n or (n ushr 8); n = n or (n ushr 16)
        return n + 1
    }
}

/**
 * Owns one Filament renderable entity with 4-slot VB (POS, TANGENTS, COLOR, UV0).
 * Supports ZERO-RECREATION in-place GPU uploads during slider drags, lazy PBR texture binding,
 * and strict 6-step teardown when a layer is deleted or detached.
 */
class SafeFilamentLayer(val uid: String) {
    var entity: Int = 0
        private set
    private var vb: VertexBuffer? = null
    private var ib: IndexBuffer? = null
    private var mi: MaterialInstance? = null
    private var allocatedVerts: Int = 0
    private var allocatedIndices: Int = 0
    private var isInScene: Boolean = false
    private var desiredTextureKey: String = ""
    private var boundTextureKey: String? = null

    fun uploadOrUpdate(
        engine: Engine,
        scene: Scene,
        baseMaterial: Material,
        guard: FilamentSafeGuard,
        textureStore: FoodTextureStore,
        payload: BakedLayerPayload
    ) {
        desiredTextureKey = payload.material.textureKey
        val topologyChanged = (entity == 0) ||
            (allocatedVerts != payload.vertexCount) ||
            (allocatedIndices != payload.indexCount)

        if (topologyChanged) {
            destroy(engine, scene, guard)
            createHandles(engine, scene, baseMaterial, textureStore, payload)
        } else {
            // FAST PATH (0 entity churn, 0 MaterialInstance churn, 0 GC!)
            val vertexBuffer = vb ?: return
            val indexBuffer = ib ?: return
            payload.posBuf.position(0).limit(payload.vertexCount * 12)
            payload.quatBuf.position(0).limit(payload.vertexCount * 8)
            payload.colBuf.position(0).limit(payload.vertexCount * 16)
            payload.uvBuf.position(0).limit(payload.vertexCount * 8)
            payload.idxBuf.position(0).limit(payload.indexCount * 4)

            vertexBuffer.setBufferAt(engine, 0, payload.posBuf)
            vertexBuffer.setBufferAt(engine, 1, payload.quatBuf)
            vertexBuffer.setBufferAt(engine, 2, payload.colBuf)
            vertexBuffer.setBufferAt(engine, 3, payload.uvBuf)
            indexBuffer.setBuffer(engine, payload.idxBuf)
            mi?.let { inst ->
                applyMaterialParams(inst, payload.material)
                bindTexturesIfNeeded(textureStore, inst)
            }
        }
    }

    fun syncTexturesIfNeeded(textureStore: FoodTextureStore) {
        val inst = mi ?: return
        bindTexturesIfNeeded(textureStore, inst)
    }

    private fun bindTexturesIfNeeded(textureStore: FoodTextureStore, inst: MaterialInstance) {
        if (desiredTextureKey.isEmpty() || boundTextureKey == desiredTextureKey) return
        val texSet: FoodTextureSet = textureStore.getTextureSet(desiredTextureKey) ?: return
        val albedo = texSet.albedo ?: return
        val normal = texSet.normal ?: return
        val orm = texSet.orm ?: return
        val sampler: TextureSampler = textureStore.sampler

        inst.setParameter("albedoMap", albedo, sampler)
        inst.setParameter("normalMap", normal, sampler)
        inst.setParameter("ormMap", orm, sampler)
        boundTextureKey = desiredTextureKey
    }

    private fun createHandles(
        engine: Engine,
        scene: Scene,
        baseMaterial: Material,
        textureStore: FoodTextureStore,
        payload: BakedLayerPayload
    ) {
        val vCount = payload.vertexCount
        val iCount = payload.indexCount

        val newVb = VertexBuffer.Builder()
            .vertexCount(vCount)
            .bufferCount(4)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.HALF4, 0, 8)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 2, VertexBuffer.AttributeType.FLOAT4, 0, 16)
            .attribute(VertexBuffer.VertexAttribute.UV0, 3, VertexBuffer.AttributeType.FLOAT2, 0, 8)
            .build(engine)

        payload.posBuf.position(0).limit(vCount * 12)
        payload.quatBuf.position(0).limit(vCount * 8)
        payload.colBuf.position(0).limit(vCount * 16)
        payload.uvBuf.position(0).limit(vCount * 8)
        newVb.setBufferAt(engine, 0, payload.posBuf)
        newVb.setBufferAt(engine, 1, payload.quatBuf)
        newVb.setBufferAt(engine, 2, payload.colBuf)
        newVb.setBufferAt(engine, 3, payload.uvBuf)

        val newIb = IndexBuffer.Builder()
            .indexCount(iCount)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        payload.idxBuf.position(0).limit(iCount * 4)
        newIb.setBuffer(engine, payload.idxBuf)

        val newMi = baseMaterial.createInstance().also { inst ->
            applyMaterialParams(inst, payload.material)
            boundTextureKey = null
            bindTexturesIfNeeded(textureStore, inst)
        }
        val newEntity = EntityManager.get().create()

        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0.5f, 0f, 2.8f, 2.8f, 2.8f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, newVb, newIb)
            .material(0, newMi)
            .castShadows(true)
            .receiveShadows(true)
            .build(engine, newEntity)

        engine.transformManager.create(newEntity)
        scene.addEntity(newEntity)

        entity = newEntity
        vb = newVb
        ib = newIb
        mi = newMi
        allocatedVerts = vCount
        allocatedIndices = iCount
        isInScene = true
    }

    private fun applyMaterialParams(inst: MaterialInstance, p: FoodMaterialSpec) {
        inst.setParameter("tintColor", Colors.RgbType.LINEAR, p.tintR, p.tintG, p.tintB)
        inst.setParameter("sssTint", Colors.RgbType.LINEAR, p.sssR, p.sssG, p.sssB)
        inst.setParameter("sssPower", p.sssPower)
        inst.setParameter("sssThickness", p.sssThickness)
        inst.setParameter("normalScale", p.normalScale)
        inst.setParameter("uvScale", p.uvScaleX, p.uvScaleY)
        inst.setParameter("triplanarBlend", p.triplanarBlend)
    }

    fun setTransformY(engine: Engine, yOffset: Float, scaleY: Float, rotYRad: Float) {
        if (entity == 0) return
        val tm = engine.transformManager
        val inst = tm.getInstance(entity)
        if (inst == 0) return
        val c = cos(rotYRad)
        val s = sin(rotYRad)
        val mat = floatArrayOf(
            c, 0f, -s, 0f,
            0f, scaleY, 0f, 0f,
            s, 0f, c, 0f,
            0f, yOffset, 0f, 1f
        )
        tm.setTransform(inst, mat)
    }

    /**
     * Strict 6-step Filament teardown order — zero handle leaks, zero SIGSEGV!
     */
    fun destroy(engine: Engine, scene: Scene, guard: FilamentSafeGuard) {
        if (entity != 0) {
            if (isInScene) {
                scene.removeEntity(entity)
                isInScene = false
            }
            engine.renderableManager.destroy(entity)
            engine.transformManager.destroy(entity)
            engine.destroyEntity(entity)
            EntityManager.get().destroy(entity)
            entity = 0
        }
        vb?.let { engine.destroyVertexBuffer(it) }; vb = null
        ib?.let { engine.destroyIndexBuffer(it) }; ib = null
        guard.safeDestroyMaterialInstance(engine, mi); mi = null
        allocatedVerts = 0
        allocatedIndices = 0
        boundTextureKey = null
    }
}
