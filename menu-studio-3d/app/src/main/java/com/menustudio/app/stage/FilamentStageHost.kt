package com.menustudio.app.stage

import android.annotation.SuppressLint
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.Utils
import com.menustudio.app.bridge.EngineBridgeSnapshot
import com.menustudio.app.bridge.MenuEngineBridge
import com.menustudio.app.bridge.ReactiveMenuEngineBridge
import kotlin.math.abs

/**
 * Pluggable contract for procedural food geometry modules that attach directly to Filament's
 * [Engine] and [Scene] via [MenuEngineBridge], keeping all 3D food geometry decoupled from UI.
 */
interface ProceduralStagePlugin {
    fun onAttach(engine: Engine, scene: Scene, view: View)
    fun onBridgeStateChanged(snapshot: EngineBridgeSnapshot)
    fun onFrameUpdate(engine: Engine, scene: Scene, frameTimeNanos: Long) {}
    fun resolveEntityToLayerUid(renderableEntity: Int): String? = null
    fun onDetach(engine: Engine, scene: Scene)
}

/**
 * Clean full-bleed Filament SurfaceView mount hook (`StageHost`) connected to [MenuEngineBridge].
 * Supports native touch orbit, pinch-zoom, and tap-on-ingredient / tap-empty-space hit-testing
 * without embedding hardcoded 3D food geometry inside Compose UI components.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun StageHost(
    engineBridge: MenuEngineBridge,
    onEmptySpaceTapped: () -> Unit,
    modifier: Modifier = Modifier,
    stagePlugin: ProceduralStagePlugin? = null,
    showMountOverlayWhenEmpty: Boolean = true
) {
    val reactiveBridge = engineBridge as? ReactiveMenuEngineBridge
    val bridgeSnapshot by (reactiveBridge?.snapshot?.collectAsState()
        ?: remember { mutableStateOf(EngineBridgeSnapshot()) })

    var isSurfaceReady by remember { mutableStateOf(false) }
    var hostError by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    val controller = remember(engineBridge, stagePlugin) {
        FilamentStageController(
            bridge = reactiveBridge,
            plugin = stagePlugin,
            onEmptySpaceTapped = onEmptySpaceTapped,
            onReadyChanged = { ready -> isSurfaceReady = ready },
            onInitError = { err -> hostError = err }
        )
    }

    // Push reactive bridge commands only when bridgeSnapshot changes (avoids running on unrelated recompositions).
    LaunchedEffect(controller, bridgeSnapshot) {
        controller.updateBridgeSnapshot(bridgeSnapshot)
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.resume()
                Lifecycle.Event.ON_PAUSE -> controller.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF221B16),
                        Color(0xFF14110E),
                        Color(0xFF0B0908)
                    )
                )
            )
    ) {
        if (hostError == null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    SurfaceView(context).apply {
                        controller.attachSurfaceView(this)
                    }
                }
            )
        }

        // Clean studio mount indicator while running with empty placeholder hook (no external food plugin attached)
        AnimatedVisibility(
            visible = stagePlugin == null && showMountOverlayWhenEmpty,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            StagePlaceholderBadge(
                snapshot = bridgeSnapshot,
                filamentReady = isSurfaceReady,
                hostError = hostError
            )
        }
    }
}

@Composable
private fun StagePlaceholderBadge(
    snapshot: EngineBridgeSnapshot,
    filamentReady: Boolean,
    hostError: String?
) {
    val dish = snapshot.dish
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(Color(0x99100E0C), RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x33F5E9DC), RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (hostError != null) Color(0xFFE5534B)
                        else if (filamentReady) Color(0xFF56C271)
                        else Color(0xFFF5A623),
                        CircleShape
                    )
            )
            Icon(
                imageVector = Icons.Outlined.ViewInAr,
                contentDescription = null,
                tint = Color(0xFFF3C677),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = dish?.name ?: "Filament StageHost",
                color = Color(0xFFF7F1EA),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = buildString {
                if (dish != null) {
                    append("${dish.archetype.displayLabel} · ${dish.layers.size} layers")
                    if (snapshot.explodeAmount > 0.01f) append(" · Exploded")
                    if (snapshot.selectedLayerUid != null) {
                        val selected = dish.layers.firstOrNull { it.uid == snapshot.selectedLayerUid }
                        if (selected != null) append(" · Focus: ${selected.label}")
                    }
                } else {
                    append("StageHost Ready · Orbit & Pinch Active")
                }
            },
            color = Color(0xFFBBAE9E),
            fontSize = 12.sp
        )
    }
}

/**
 * Encapsulates native Filament Engine, SwapChain, Camera, Manipulator orbit/pinch controller,
 * and View.pick hit-testing lifecycle cleanly away from Compose UI code.
 */
private class FilamentStageController(
    private val bridge: ReactiveMenuEngineBridge?,
    private val plugin: ProceduralStagePlugin?,
    private val onEmptySpaceTapped: () -> Unit,
    private val onReadyChanged: (Boolean) -> Unit,
    private val onInitError: (String) -> Unit
) {
    private var uiHelper: UiHelper? = null
    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var swapChain: SwapChain? = null
    private var manipulator: Manipulator? = null
    private var cameraEntity: Int = 0

    private var latestSnapshot: EngineBridgeSnapshot = EngineBridgeSnapshot()
    private var isResumed = false
    private var isDestroyed = false

    private val choreographer = Choreographer.getInstance()
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isResumed || isDestroyed) return
            choreographer.postFrameCallback(this)
            renderFrame(frameTimeNanos)
        }
    }

    fun updateBridgeSnapshot(snapshot: EngineBridgeSnapshot) {
        latestSnapshot = snapshot
        plugin?.onBridgeStateChanged(snapshot)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attachSurfaceView(surfaceView: SurfaceView) {
        try {
            Utils.init()
            val createdEngine = Engine.create()
            val createdRenderer = createdEngine.createRenderer()
            val createdScene = createdEngine.createScene()
            val createdView = createdEngine.createView()
            val entity = EntityManager.get().create()
            val createdCamera = createdEngine.createCamera(entity)

            createdView.scene = createdScene
            createdView.camera = createdCamera

            // Deep linear espresso studio backdrop (matches #141210 after ACES tone mapping)
            createdRenderer.clearOptions = createdRenderer.clearOptions.apply {
                clear = true
                clearColor = floatArrayOf(0.0045f, 0.0038f, 0.0032f, 1.0f)
            }

            // Portrait-friendly orbit home distance so even stacked towers stay framed in view
            val orbitManipulator = Manipulator.Builder()
                .targetPosition(0f, 0.15f, 0f)
                .orbitHomePosition(0f, 3.4f, 9.0f)
                .orbitSpeed(0.008f, 0.008f)
                .zoomSpeed(0.06f)
                .build(Manipulator.Mode.ORBIT)

            engine = createdEngine
            renderer = createdRenderer
            scene = createdScene
            view = createdView
            camera = createdCamera
            cameraEntity = entity
            manipulator = orbitManipulator

            plugin?.onAttach(createdEngine, createdScene, createdView)
            plugin?.onBridgeStateChanged(latestSnapshot)

            val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
            helper.renderCallback = object : UiHelper.RendererCallback {
                override fun onNativeWindowChanged(surface: Surface) {
                    val eng = engine ?: return
                    swapChain?.let { eng.destroySwapChain(it) }
                    swapChain = eng.createSwapChain(surface)
                }

                override fun onDetachedFromSurface() {
                    val eng = engine ?: return
                    swapChain?.let {
                        eng.destroySwapChain(it)
                        eng.flushAndWait()
                    }
                    swapChain = null
                }

                override fun onResized(width: Int, height: Int) {
                    val v = view ?: return
                    val cam = camera ?: return
                    val aspect = width.toDouble() / height.coerceAtLeast(1).toDouble()
                    v.viewport = Viewport(0, 0, width, height)
                    cam.setProjection(42.0, aspect, 0.1, 100.0, Camera.Fov.VERTICAL)
                    manipulator?.setViewport(width, height)
                }
            }
            helper.attachTo(surfaceView)
            uiHelper = helper

            wireTouchGestures(surfaceView)
            onReadyChanged(true)
        } catch (t: Throwable) {
            onInitError(t.message ?: "Filament init unavailable")
            // Still allow pure tap-empty-space UX fallback if Filament native lib cannot load on headless test device
            wireFallbackTapOnly(surfaceView)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireFallbackTapOnly(surfaceView: SurfaceView) {
        val detector = GestureDetector(
            surfaceView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    onEmptySpaceTapped()
                    return true
                }
            }
        )
        surfaceView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireTouchGestures(surfaceView: SurfaceView) {
        var activePointers = 0
        var isGrabbing = false
        var downX = 0f
        var downY = 0f
        var movedTooMuch = false

        val scaleDetector = ScaleGestureDetector(
            surfaceView.context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val delta = (1.0f - detector.scaleFactor) * 8.0f
                    manipulator?.scroll(detector.focusX.toInt(), detector.focusY.toInt(), delta)
                    return true
                }
            }
        )

        val tapDetector = GestureDetector(
            surfaceView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    handleStageTap(surfaceView, e.x, e.y)
                    return true
                }
            }
        )

        surfaceView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            tapDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointers = 1
                    downX = event.x
                    downY = event.y
                    movedTooMuch = false
                    if (!scaleDetector.isInProgress) {
                        manipulator?.grabBegin(event.x.toInt(), event.y.toInt(), false)
                        isGrabbing = true
                    }
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    activePointers = event.pointerCount
                    if (isGrabbing) {
                        manipulator?.grabEnd()
                        isGrabbing = false
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.x - downX) > 14f || abs(event.y - downY) > 14f) {
                        movedTooMuch = true
                    }
                    if (activePointers == 1 && !scaleDetector.isInProgress && isGrabbing) {
                        manipulator?.grabUpdate(event.x.toInt(), event.y.toInt())
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isGrabbing) {
                        manipulator?.grabEnd()
                        isGrabbing = false
                    }
                    activePointers = 0
                    if (!movedTooMuch && event.actionMasked == MotionEvent.ACTION_UP) {
                        // SingleTapUp already dispatched via GestureDetector
                    }
                }
            }
            true
        }
    }

    private fun handleStageTap(surfaceView: SurfaceView, x: Float, y: Float) {
        val v = view
        val activePlugin = plugin
        if (v != null && activePlugin != null) {
            val invertY = (surfaceView.height - 1 - y.toInt()).coerceAtLeast(0)
            v.pick(x.toInt(), invertY, surfaceView.handler) { result ->
                val uid = activePlugin.resolveEntityToLayerUid(result.renderable)
                if (uid != null) {
                    bridge?.dispatchLayerTapped(uid)
                } else {
                    onEmptySpaceTapped()
                }
            }
        } else {
            onEmptySpaceTapped()
        }
    }

    private fun renderFrame(frameTimeNanos: Long) {
        val helper = uiHelper ?: return
        if (!helper.isReadyToRender) return
        val r = renderer ?: return
        val v = view ?: return
        val sc = swapChain ?: return
        val cam = camera ?: return
        val manip = manipulator ?: return
        val eng = engine ?: return
        val activeScene = scene ?: return

        // Render-thread commit of background-baked buffers + 60fps explode/bite transforms
        plugin?.onFrameUpdate(eng, activeScene, frameTimeNanos)

        val eye = DoubleArray(3)
        val target = DoubleArray(3)
        val upward = DoubleArray(3)
        manip.getLookAt(eye, target, upward)
        cam.lookAt(
            eye[0], eye[1], eye[2],
            target[0], target[1], target[2],
            upward[0], upward[1], upward[2]
        )

        if (r.beginFrame(sc, frameTimeNanos)) {
            r.render(v)
            r.endFrame()
        }
    }

    fun resume() {
        if (isDestroyed) return
        isResumed = true
        choreographer.postFrameCallback(frameCallback)
    }

    fun pause() {
        isResumed = false
        choreographer.removeFrameCallback(frameCallback)
    }

    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        pause()

        val eng = engine
        val sc = scene
        if (eng != null && sc != null) {
            plugin?.onDetach(eng, sc)
        }
        uiHelper?.detach()

        if (eng != null) {
            swapChain?.let { eng.destroySwapChain(it) }
            view?.let { eng.destroyView(it) }
            sc?.let { eng.destroyScene(it) }
            renderer?.let { eng.destroyRenderer(it) }
            camera?.let { eng.destroyCameraComponent(cameraEntity) }
            EntityManager.get().destroy(cameraEntity)
            eng.flushAndWait()
            eng.destroy()
        }
        engine = null
        renderer = null
        scene = null
        view = null
        camera = null
        swapChain = null
        manipulator = null
        onReadyChanged(false)
    }
}
