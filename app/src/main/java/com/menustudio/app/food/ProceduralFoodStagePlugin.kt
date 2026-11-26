package com.menustudio.app.food

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.Scene
import com.google.android.filament.SurfaceOrientation
import com.google.android.filament.View
import com.menustudio.app.bridge.CustomDish
import com.menustudio.app.bridge.EngineBridgeSnapshot
import com.menustudio.app.food.primitives.FoodPrimitiveRegistry
import com.menustudio.app.food.primitives.MeshAppender
import com.menustudio.app.food.textures.FoodTextureStore
import com.menustudio.app.stage.ProceduralStagePlugin
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.exp

data class FoodMaterialSpec(
    val textureKey: String,
    val tintR: Float = 1f, val tintG: Float = 1f, val tintB: Float = 1f,
    val sssR: Float = 0.9f, val sssG: Float = 0.45f, val sssB: Float = 0.15f,
    val sssPower: Float = 8.5f,
    val sssThickness: Float = 0.65f,
    val normalScale: Float = 1.15f,
    val uvScaleX: Float = 1.0f,
    val uvScaleY: Float = 1.0f,
    val triplanarBlend: Float = 0.0f
)

data class BakedLayerPayload(
    val uid: String,
    val orderIndex: Int,
    val vertexCount: Int,
    val indexCount: Int,
    val posBuf: ByteBuffer,
    val quatBuf: ByteBuffer,
    val colBuf: ByteBuffer,
    val uvBuf: ByteBuffer,
    val idxBuf: ByteBuffer,
    val material: FoodMaterialSpec
)

data class BakedDishFrame(
    val syncVersion: Long,
    val bakedDishHeight: Float,
    val slateBoard: BakedLayerPayload,
    val layers: List<BakedLayerPayload>
)

class ProceduralFoodStagePlugin(
    private val appContext: Context,
    private val materialAssetPath: String = "materials/food_textured_pbr.filamat"
) : ProceduralStagePlugin {

    companion object {
        private const val SLATE_UID = "__studio_slate_board__"
    }

    // Conflated single-thread background baker (ZERO main-thread ANR!)
    private val bakeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FoodBakeWorker").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val latestSnapshot = AtomicReference(EngineBridgeSnapshot())
    private val bakeScheduled = AtomicBoolean(false)
    private val pendingFrame = AtomicReference<BakedDishFrame?>(null)
    private val isDetached = AtomicBoolean(false)

    // Worker-owned reusable buffers & appender (never touched by render thread during bake)
    private val workerBufferPool = HashMap<String, ReusableDirectBuffers>(24)
    private val workerAppender = MeshAppender()
    private var workerNormScratch = FloatArray(4096 * 3)
    private var lastBakedSyncVersion = -1L

    // Render-thread-owned Filament state
    private val textureStore = FoodTextureStore(appContext)
    private val safeGuard = FilamentSafeGuard()
    private val slateLayer = SafeFilamentLayer(SLATE_UID)
    private val activeLayers = LinkedHashMap<String, SafeFilamentLayer>(24)
    private val entityToUid = HashMap<Int, String>(24)
    private val layerOrderMap = HashMap<String, Int>(24)
    private var foodMaterial: Material? = null
    private var indirectLight: IndirectLight? = null
    private val lightEntities = IntArray(3)

    // Smooth 60Hz render-thread animation state (zero per-frame heap allocations)
    private var bakedDishHeight = 2.0f
    private var currentDishHeight = 2.0f
    private var currentExplode = 0f
    private var currentSquish = 0f
    private var targetSquish = 0f
    private var lastBiteNonce = 0L
    private var turntableRad = 0f
    private var lastFrameNanos = 0L

    override fun onAttach(engine: Engine, scene: Scene, view: View) {
        isDetached.set(false)
        setupStudioFoodLighting(engine, scene)
        foodMaterial = loadMaterialFromAssets(engine, materialAssetPath)
        scheduleBakeIfNeeded()
    }

    /** Called on Main Thread by Compose — O(1), non-blocking, zero allocations! */
    override fun onBridgeStateChanged(snapshot: EngineBridgeSnapshot) {
        val prev = latestSnapshot.getAndSet(snapshot)
        if (snapshot.bitePressNonce != lastBiteNonce) {
            lastBiteNonce = snapshot.bitePressNonce
            targetSquish = 0.28f
        }
        if (snapshot.syncVersion != prev.syncVersion || snapshot.dish?.id != prev.dish?.id) {
            scheduleBakeIfNeeded()
        }
    }

    /** Called strictly on Filament Choreographer Render Thread before beginFrame() — ZERO allocations! */
    override fun onFrameUpdate(engine: Engine, scene: Scene, frameTimeNanos: Long) {
        if (isDetached.get()) return
        val dt = if (lastFrameNanos == 0L) 0.016f
        else ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNanos = frameTimeNanos

        // 1. Upload newly baked 512x512 PBR textures on render thread (once per key)
        val texturesUploaded = textureStore.uploadPendingOnRenderThread(engine)

        // 2. Atomic handoff from background geometry baker -> Filament GPU buffers
        val ready = pendingFrame.getAndSet(null)
        val mat = foodMaterial
        if (ready != null && mat != null) {
            commitBakedFrameOnRenderThread(engine, scene, mat, ready)
        } else if (texturesUploaded) {
            slateLayer.syncTexturesIfNeeded(textureStore)
            for (layer in activeLayers.values) {
                layer.syncTexturesIfNeeded(textureStore)
            }
        }

        // 3. Smooth 60 FPS Explode + Auto-Center + Bite Spring + Selection Lift + Turntable
        val snap = latestSnapshot.get()
        currentDishHeight += (bakedDishHeight - currentDishHeight) * (1f - exp(-10f * dt))
        currentExplode += (snap.explodeAmount - currentExplode) * (1f - exp(-10f * dt))
        currentSquish += (targetSquish - currentSquish) * (1f - exp(-16f * dt))
        if (targetSquish > 0.01f && currentSquish > 0.22f) targetSquish = 0f
        turntableRad = (turntableRad + dt * 0.28f) % (2f * PI.toFloat())

        val verticalCenter = -currentDishHeight * 0.5f - (activeLayers.size * currentExplode * 0.16f)
        slateLayer.setTransformY(engine, verticalCenter, 1f, turntableRad)

        val selectedUid = snap.selectedLayerUid
        for ((uid, layer) in activeLayers) {
            val idx = layerOrderMap[uid] ?: 0
            val explodeY = idx * currentExplode * 0.36f
            val focusLift = if (uid == selectedUid) 0.09f else 0f
            val scaleY = (1f - currentSquish * 0.22f).coerceIn(0.65f, 1.05f)
            layer.setTransformY(engine, verticalCenter + explodeY + focusLift, scaleY, turntableRad)
        }
    }

    override fun resolveEntityToLayerUid(renderableEntity: Int): String? =
        entityToUid[renderableEntity]

    /**
     * Strict teardown order:
     *   1. Entities + VBs/IBs + MaterialInstances
     *   2. Textures (FoodTextureStore)
     *   3. Material
     *   4. Lights & IndirectLight
     */
    override fun onDetach(engine: Engine, scene: Scene) {
        isDetached.set(true)
        pendingFrame.set(null)

        slateLayer.destroy(engine, scene, safeGuard)
        for (layer in activeLayers.values) {
            layer.destroy(engine, scene, safeGuard)
        }
        activeLayers.clear()
        entityToUid.clear()
        layerOrderMap.clear()

        textureStore.destroyAll(engine)

        foodMaterial?.let { engine.destroyMaterial(it) }
        foodMaterial = null

        for (i in lightEntities.indices) {
            val le = lightEntities[i]
            if (le != 0) {
                scene.removeEntity(le)
                engine.destroyEntity(le)
                EntityManager.get().destroy(le)
                lightEntities[i] = 0
            }
        }
        indirectLight?.let {
            scene.indirectLight = null
            engine.destroyIndirectLight(it)
        }
        indirectLight = null

        safeGuard.clear()
        bakeExecutor.shutdownNow()
    }

    // ------------------------------------------------------------------
    // CONFLATED BACKGROUND BAKE WORKER (ZERO ANR)
    // ------------------------------------------------------------------
    private fun scheduleBakeIfNeeded() {
        if (isDetached.get()) return
        if (!bakeScheduled.compareAndSet(false, true)) return
        bakeExecutor.execute {
            try {
                while (!isDetached.get()) {
                    bakeScheduled.set(false)
                    val snap = latestSnapshot.get()
                    val dish = snap.dish ?: break
                    if (snap.syncVersion == lastBakedSyncVersion) break
                    lastBakedSyncVersion = snap.syncVersion

                    val frame = bakeDishOffMainThread(dish, snap.syncVersion)
                    pendingFrame.set(frame)

                    if (latestSnapshot.get().syncVersion == lastBakedSyncVersion) break
                }
            } catch (_: Throwable) {
                // Swallow cleanly on shutdown/interrupt
            }
        }
    }

    private fun commitBakedFrameOnRenderThread(
        engine: Engine,
        scene: Scene,
        baseMaterial: Material,
        frame: BakedDishFrame
    ) {
        bakedDishHeight = frame.bakedDishHeight
        slateLayer.uploadOrUpdate(engine, scene, baseMaterial, safeGuard, textureStore, frame.slateBoard)

        val incomingUids = HashSet<String>(frame.layers.size * 2)
        for (p in frame.layers) incomingUids.add(p.uid)

        val iter = activeLayers.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (!incomingUids.contains(entry.key)) {
                entityToUid.remove(entry.value.entity)
                layerOrderMap.remove(entry.key)
                entry.value.destroy(engine, scene, safeGuard)
                iter.remove()
            }
        }

        entityToUid.clear()
        for (payload in frame.layers) {
            val slot = activeLayers.getOrPut(payload.uid) { SafeFilamentLayer(payload.uid) }
            slot.uploadOrUpdate(engine, scene, baseMaterial, safeGuard, textureStore, payload)
            entityToUid[slot.entity] = payload.uid
            layerOrderMap[payload.uid] = payload.orderIndex
        }
    }

    private fun bakeDishOffMainThread(dish: CustomDish, syncVersion: Long): BakedDishFrame {
        // 1. Grounded dark slate serving plate beneath dish
        textureStore.ensureBakedOffMainThread("slate")
        workerAppender.reset()
        FoodPrimitiveRegistry.slateBoard.bakeBoard(workerAppender)
        val slateSpec = FoodMaterialSpec(
            textureKey = "slate",
            sssR = 0.08f, sssG = 0.08f, sssB = 0.08f,
            sssPower = 14f,
            sssThickness = 0.90f,
            normalScale = 0.48f,
            uvScaleX = 1.4f,
            uvScaleY = 1.4f,
            triplanarBlend = 1.0f
        )
        val slatePayload = finalizeAppenderPayload(
            uid = SLATE_UID,
            orderIndex = -1,
            buffers = workerBufferPool.getOrPut(SLATE_UID) { ReusableDirectBuffers() },
            material = slateSpec
        )

        // 2. Sculpted UV-mapped food layers ordered bottom -> top
        val visibleLayers = dish.layers.filter { it.visible }
        val results = ArrayList<BakedLayerPayload>(visibleLayers.size)
        var cursorY = 0f

        visibleLayers.forEachIndexed { orderIdx, inst ->
            val primitive = FoodPrimitiveRegistry.resolvePrimitive(inst.typeId)
            val texKey = FoodPrimitiveRegistry.resolveTextureKey(inst.typeId)
            textureStore.ensureBakedOffMainThread(texKey)

            workerAppender.reset()
            cursorY = primitive.bake(dish.archetype, inst, orderIdx, cursorY, workerAppender)
            val buffers = workerBufferPool.getOrPut(inst.uid) { ReusableDirectBuffers() }
            val spec = resolveMaterialForCatalogItem(inst.typeId, inst.variant, texKey)
            results.add(finalizeAppenderPayload(inst.uid, orderIdx, buffers, spec))
        }

        return BakedDishFrame(syncVersion, cursorY.coerceAtLeast(0.4f), slatePayload, results)
    }

    private fun finalizeAppenderPayload(
        uid: String,
        orderIndex: Int,
        buffers: ReusableDirectBuffers,
        material: FoodMaterialSpec
    ): BakedLayerPayload {
        val vc = workerAppender.vCount
        val ic = workerAppender.iCount
        if (workerNormScratch.size < vc * 3) workerNormScratch = FloatArray(vc * 3 * 2)
        workerAppender.computeNormals(workerNormScratch)

        buffers.ensureCapacity(vc, ic)
        buffers.posBuf.apply { clear() }.asFloatBuffer().put(workerAppender.pos, 0, vc * 3)
        buffers.normScratch.apply { clear() }.asFloatBuffer().put(workerNormScratch, 0, vc * 3)
        buffers.colBuf.apply { clear() }.asFloatBuffer().put(workerAppender.col, 0, vc * 4)
        buffers.uvBuf.apply { clear() }.asFloatBuffer().put(workerAppender.uv, 0, vc * 2)
        buffers.idxBuf.apply { clear() }.asIntBuffer().put(workerAppender.idx, 0, ic)

        buffers.posBuf.position(0).limit(vc * 12)
        buffers.normScratch.position(0).limit(vc * 12)
        buffers.uvBuf.position(0).limit(vc * 8)
        buffers.idxBuf.position(0).limit(ic * 4)

        val orientation = SurfaceOrientation.Builder()
            .vertexCount(vc)
            .normals(buffers.normScratch)
            .uvs(buffers.uvBuf)
            .positions(buffers.posBuf)
            .triangleCount(ic / 3)
            .triangles_uint32(buffers.idxBuf)
            .build()
        buffers.quatBuf.clear()
        orientation.getQuatsAsHalf(buffers.quatBuf)
        orientation.destroy()

        return BakedLayerPayload(
            uid = uid,
            orderIndex = orderIndex,
            vertexCount = vc,
            indexCount = ic,
            posBuf = buffers.posBuf,
            quatBuf = buffers.quatBuf,
            colBuf = buffers.colBuf,
            uvBuf = buffers.uvBuf,
            idxBuf = buffers.idxBuf,
            material = material
        )
    }

    // ------------------------------------------------------------------
    // WARM FOOD-PHOTOGRAPHY 3-POINT LIGHTING + 3-BAND SH STUDIO IBL + SHADOW BIAS
    // ------------------------------------------------------------------
    private fun setupStudioFoodLighting(engine: Engine, scene: Scene) {
        val warmStudioSH = floatArrayOf(
            0.78f, 0.70f, 0.60f,
            0.22f, 0.19f, 0.15f,
            0.34f, 0.30f, 0.24f,
            0.14f, 0.12f, 0.10f,
            0.05f, 0.04f, 0.03f,
            0.06f, 0.05f, 0.04f,
            0.12f, 0.10f, 0.08f,
            0.04f, 0.03f, 0.02f,
            0.06f, 0.05f, 0.04f
        )
        indirectLight = IndirectLight.Builder()
            .irradiance(3, warmStudioSH)
            .intensity(28_000f)
            .build(engine).also { scene.indirectLight = it }

        val shadowOpts = LightManager.ShadowOptions().apply {
            mapSize = 1024
            shadowCascades = 1
            normalBias = 0.045f
            constantBias = 0.0025f
            stable = true
        }

        // Key Warm Softbox (Front-Right-Top, Casts Shadows)
        lightEntities[0] = EntityManager.get().create().also { e ->
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 0.93f, 0.84f)
                .intensity(98_000f)
                .direction(-0.42f, -0.76f, -0.48f)
                .castShadows(true)
                .shadowOptions(shadowOpts)
                .build(engine, e)
            scene.addEntity(e)
        }
        // Back Warm Rim Light (Makes Tomato/Lettuce Subsurface Edges Glow)
        lightEntities[1] = EntityManager.get().create().also { e ->
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 0.86f, 0.68f)
                .intensity(55_000f)
                .direction(0.55f, -0.35f, 0.75f)
                .castShadows(false)
                .build(engine, e)
            scene.addEntity(e)
        }
        // Cool Subtle Fill (Prevents Crushed Shadows)
        lightEntities[2] = EntityManager.get().create().also { e ->
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(0.84f, 0.90f, 1.0f)
                .intensity(26_000f)
                .direction(0.75f, -0.40f, -0.50f)
                .castShadows(false)
                .build(engine, e)
            scene.addEntity(e)
        }
    }

    private fun loadMaterialFromAssets(engine: Engine, assetPath: String): Material {
        val bytes = appContext.assets.open(assetPath).use { it.readBytes() }
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buf.put(bytes)
        buf.flip()
        return Material.Builder().payload(buf, buf.remaining()).build(engine)
    }

    private fun resolveMaterialForCatalogItem(typeId: String, variant: String, textureKey: String): FoodMaterialSpec =
        when (typeId) {
            "brioche_top" ->
                if (variant.contains("Pretzel", ignoreCase = true)) {
                    FoodMaterialSpec(textureKey, 0.56f, 0.36f, 0.24f, 0.72f, 0.34f, 0.12f, 10f, 0.72f, 0.24f, 1.0f, 1.0f, 0.0f)
                } else {
                    FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 0.95f, 0.54f, 0.16f, 9f, 0.68f, 0.24f, 1.0f, 1.0f, 0.0f)
                }
            "brioche_base" ->
                FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 0.90f, 0.50f, 0.14f, 9f, 0.70f, 0.24f, 1.2f, 1.2f, 1.0f)
            "sourdough_crust" ->
                FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 0.85f, 0.48f, 0.16f, 10f, 0.72f, 0.34f, 1.2f, 1.2f, 1.0f)
            "patty" ->
                FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 0.32f, 0.10f, 0.04f, 14f, 0.84f, 0.68f, 1.5f, 1.5f, 1.0f)
            "crispy_chicken" ->
                FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 0.80f, 0.44f, 0.14f, 10f, 0.72f, 0.58f, 1.4f, 1.4f, 1.0f)
            "portobello" ->
                FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 0.34f, 0.18f, 0.08f, 11f, 0.74f, 0.32f, 1.2f, 1.2f, 1.0f)
            "cheddar" ->
                if (variant.contains("White", ignoreCase = true)) {
                    FoodMaterialSpec(textureKey, 1.08f, 1.12f, 1.38f, 0.96f, 0.86f, 0.62f, 8.5f, 0.42f, 0.26f, 1.2f, 1.2f, 1.0f)
                } else {
                    FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 1.0f, 0.62f, 0.12f, 8.5f, 0.38f, 0.26f, 1.2f, 1.2f, 1.0f)
                }
            "mozzarella" ->
                FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 0.98f, 0.92f, 0.78f, 9f, 0.40f, 0.22f, 1.3f, 1.3f, 1.0f)
            "gruyere" ->
                FoodMaterialSpec(textureKey, 1.05f, 1.10f, 1.35f, 0.96f, 0.86f, 0.64f, 9f, 0.40f, 0.26f, 1.2f, 1.2f, 1.0f)
            "rice_base" ->
                FoodMaterialSpec(textureKey, 1.06f, 1.10f, 1.38f, 0.96f, 0.92f, 0.78f, 8f, 0.50f, 0.42f, 2.0f, 2.0f, 1.0f)
            "tomato" -> when (variant) {
                "Cherokee" -> FoodMaterialSpec(textureKey, 0.74f, 0.58f, 0.88f, 0.82f, 0.14f, 0.22f, 6.5f, 0.12f, 0.25f, 1.0f, 1.0f, 0.0f)
                "Green Zebra" -> FoodMaterialSpec(textureKey, 0.56f, 1.34f, 0.62f, 0.52f, 0.90f, 0.18f, 6f, 0.12f, 0.25f, 1.0f, 1.0f, 0.0f)
                else -> FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 1.0f, 0.18f, 0.08f, 6.5f, 0.10f, 0.25f, 1.0f, 1.0f, 0.0f)
            }
            "lettuce" ->
                FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 0.45f, 0.90f, 0.14f, 5.5f, 0.10f, 0.42f, 1.0f, 1.0f, 0.0f)
            "basil" ->
                FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 0.36f, 0.82f, 0.12f, 6.0f, 0.12f, 0.35f, 1.0f, 1.0f, 0.0f)
            "bacon" ->
                FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 0.55f, 0.14f, 0.08f, 10f, 0.45f, 0.65f, 1.0f, 1.0f, 0.0f)
            "pickles" ->
                FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 0.48f, 0.85f, 0.18f, 6f, 0.16f, 0.55f, 1.0f, 1.0f, 0.0f)
            "avocado" ->
                FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 0.58f, 0.84f, 0.22f, 7f, 0.34f, 0.28f, 1.0f, 1.0f, 0.0f)
            "san_marzano" ->
                FoodMaterialSpec(textureKey, 0.95f, 0.78f, 0.72f, 0.90f, 0.16f, 0.08f, 7f, 0.30f, 0.32f, 1.0f, 1.0f, 0.0f)
            "crispy_onions" ->
                FoodMaterialSpec(textureKey, 0.92f, 0.76f, 0.54f, 0.76f, 0.42f, 0.14f, 10f, 0.62f, 0.52f, 1.0f, 1.0f, 0.0f)
            else ->
                FoodMaterialSpec(textureKey, 1.0f, 1.0f, 1.0f, 0.88f, 0.36f, 0.12f, 9f, 0.36f, 0.35f, 1.0f, 1.0f, 0.0f)
        }
}
