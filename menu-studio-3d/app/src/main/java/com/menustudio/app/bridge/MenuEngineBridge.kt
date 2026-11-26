package com.menustudio.app.bridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Mandatory decoupling contract between the Compose Studio UI and the 3D Filament host.
 * Procedural food modules observe commands through this bridge without leaking 3D engine internals into UI.
 */
interface MenuEngineBridge {
    // Commands UI sends to 3D Engine
    fun syncDish(dish: CustomDish)
    fun selectLayer(uid: String?)
    fun setExplode(amount: Float) // 0.0 .. 1.0
    fun triggerBitePress()

    // Events 3D Engine sends back to UI
    fun onLayerTapped(callback: (String) -> Unit)
}

/**
 * Immutable command state consumed by the Filament StageHost or any pluggable procedural food module.
 */
data class EngineBridgeSnapshot(
    val dish: CustomDish? = null,
    val selectedLayerUid: String? = null,
    val explodeAmount: Float = 0f,
    val bitePressNonce: Long = 0L,
    val syncVersion: Long = 0L
)

/**
 * Thread-safe reactive implementation of [MenuEngineBridge] connecting UI state to the Filament StageHost.
 */
class ReactiveMenuEngineBridge : MenuEngineBridge {

    private val _snapshot = MutableStateFlow(EngineBridgeSnapshot())
    val snapshot: StateFlow<EngineBridgeSnapshot> = _snapshot.asStateFlow()

    private val tapCallbacks = CopyOnWriteArrayList<(String) -> Unit>()

    override fun syncDish(dish: CustomDish) {
        _snapshot.update { current ->
            current.copy(
                dish = dish,
                syncVersion = current.syncVersion + 1L
            )
        }
    }

    override fun selectLayer(uid: String?) {
        _snapshot.update { current ->
            current.copy(selectedLayerUid = uid)
        }
    }

    override fun setExplode(amount: Float) {
        val clamped = amount.coerceIn(0f, 1f)
        _snapshot.update { current ->
            current.copy(explodeAmount = clamped)
        }
    }

    override fun triggerBitePress() {
        _snapshot.update { current ->
            current.copy(bitePressNonce = current.bitePressNonce + 1L)
        }
    }

    override fun onLayerTapped(callback: (String) -> Unit) {
        tapCallbacks.add(callback)
    }

    /**
     * Called by the 3D StageHost when a hit-test / Filament pick selects an ingredient layer.
     */
    fun dispatchLayerTapped(uid: String) {
        for (callback in tapCallbacks) {
            callback(uid)
        }
    }
}
