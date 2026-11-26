package com.menustudio.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menustudio.app.bridge.CustomDish
import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.IngredientCategory
import com.menustudio.app.bridge.MenuEngineBridge
import com.menustudio.app.bridge.MenuIngredientInstance
import com.menustudio.app.bridge.ReactiveMenuEngineBridge
import com.menustudio.app.catalog.IngredientCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class PendingLayerUndo(
    val dishId: String,
    val originalIndex: Int,
    val layer: MenuIngredientInstance
)

data class StudioToast(
    val id: Long,
    val message: String,
    val canUndoDelete: Boolean = false
)

data class MenuStudioUiState(
    val dishes: List<CustomDish> = emptyList(),
    val activeDishId: String = "",
    val selectedLayerUid: String? = null,
    val activeCategoryFilter: IngredientCategory? = null, // null = All
    val isBeautyShotMode: Boolean = false,
    val isExploded: Boolean = false,
    val isStackRailExpandedOnMobile: Boolean = true,
    val isMenuSheetOpen: Boolean = false,
    val pendingUndo: PendingLayerUndo? = null,
    val toast: StudioToast? = null
) {
    val activeDish: CustomDish?
        get() = dishes.firstOrNull { it.id == activeDishId } ?: dishes.firstOrNull()

    /**
     * Physical top-to-bottom presentation for Zone 2 Stack Rail.
     */
    val topToBottomLayers: List<MenuIngredientInstance>
        get() = activeDish?.layers?.asReversed() ?: emptyList()

    val selectedLayer: MenuIngredientInstance?
        get() = activeDish?.layers?.firstOrNull { it.uid == selectedLayerUid }

    val selectedLayerBottomIndex: Int
        get() {
            val dish = activeDish ?: return -1
            return dish.layers.indexOfFirst { it.uid == selectedLayerUid }
        }

    val canMoveSelectedUp: Boolean
        get() {
            val dish = activeDish ?: return false
            val idx = selectedLayerBottomIndex
            return idx in 0 until (dish.layers.size - 1)
        }

    val canMoveSelectedDown: Boolean
        get() {
            val idx = selectedLayerBottomIndex
            return idx > 0
        }
}

/**
 * Pure Kotlin state manager coordinating CRUD, stack ordering, undo, and HUD states
 * while keeping the 3D Filament viewport decoupled behind [MenuEngineBridge].
 */
class MenuStudioStateHolder(
    val engineBridge: MenuEngineBridge = ReactiveMenuEngineBridge(),
    private val scheduleAutoDismiss: (delayMs: Long, block: () -> Unit) -> Job? = { _, _ -> null }
) {
    private val idCounter = AtomicLong(2000L)
    private var toastDismissJob: Job? = null

    private val initialDishes = IngredientCatalog.defaultDishes()
    private val _uiState = MutableStateFlow(
        MenuStudioUiState(
            dishes = initialDishes,
            activeDishId = initialDishes.first().id
        )
    )
    val uiState: StateFlow<MenuStudioUiState> = _uiState.asStateFlow()

    init {
        engineBridge.onLayerTapped { uid ->
            selectLayer(uid)
        }
        _uiState.value.activeDish?.let { engineBridge.syncDish(it) }
    }

    fun selectDish(dishId: String) {
        val target = _uiState.value.dishes.firstOrNull { it.id == dishId } ?: return
        _uiState.update {
            it.copy(
                activeDishId = target.id,
                selectedLayerUid = null,
                isMenuSheetOpen = false,
                pendingUndo = null,
                toast = null
            )
        }
        engineBridge.syncDish(target)
        engineBridge.selectLayer(null)
    }

    fun setMenuSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isMenuSheetOpen = open) }
    }

    fun createDish(rawName: String, archetype: DishArchetype) {
        val cleanName = rawName.trim().ifEmpty { "Custom ${archetype.displayLabel}" }
        val starterTypeId = when (archetype) {
            DishArchetype.STACK -> "brioche_base"
            DishArchetype.FLAT -> "sourdough_crust"
            DishArchetype.BOWL -> "rice_base"
        }
        val newDish = CustomDish(
            id = "dish_${idCounter.incrementAndGet()}",
            name = cleanName,
            archetype = archetype,
            layers = listOf(IngredientCatalog.createInstance(starterTypeId))
        )
        _uiState.update {
            it.copy(
                dishes = it.dishes + newDish,
                activeDishId = newDish.id,
                selectedLayerUid = newDish.layers.firstOrNull()?.uid,
                isMenuSheetOpen = false,
                pendingUndo = null
            )
        }
        engineBridge.syncDish(newDish)
        engineBridge.selectLayer(newDish.layers.firstOrNull()?.uid)
    }

    fun renameDish(dishId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        updateDishById(dishId) { dish -> dish.copy(name = trimmed) }
    }

    fun duplicateDish(dishId: String) {
        val source = _uiState.value.dishes.firstOrNull { it.id == dishId } ?: return
        val clonedLayers = source.layers.map { layer ->
            layer.copy(uid = IngredientCatalog.nextUid(layer.typeId))
        }
        val copyDish = source.copy(
            id = "dish_${idCounter.incrementAndGet()}",
            name = "${source.name} Copy",
            layers = clonedLayers
        )
        _uiState.update {
            it.copy(
                dishes = it.dishes + copyDish,
                activeDishId = copyDish.id,
                selectedLayerUid = null,
                isMenuSheetOpen = false
            )
        }
        engineBridge.syncDish(copyDish)
        engineBridge.selectLayer(null)
    }

    fun deleteDish(dishId: String) {
        val current = _uiState.value
        if (current.dishes.size <= 1) {
            showToast("Keep at least one custom dish in studio", canUndoDelete = false)
            return
        }
        val remaining = current.dishes.filterNot { it.id == dishId }
        val nextActive = if (current.activeDishId == dishId) remaining.first() else current.activeDish ?: remaining.first()
        _uiState.update {
            it.copy(
                dishes = remaining,
                activeDishId = nextActive.id,
                selectedLayerUid = null,
                pendingUndo = null
            )
        }
        engineBridge.syncDish(nextActive)
        engineBridge.selectLayer(null)
    }

    fun selectCategoryFilter(category: IngredientCategory?) {
        _uiState.update { it.copy(activeCategoryFilter = category) }
    }

    fun addIngredient(typeId: String) {
        val active = _uiState.value.activeDish ?: return
        val entry = IngredientCatalog.findByTypeId(typeId) ?: return
        val instance = IngredientCatalog.createInstance(typeId)

        val updatedLayers = active.layers.toMutableList()
        val hasTrailingTopCap = active.archetype == DishArchetype.STACK &&
            updatedLayers.lastOrNull()?.let { IngredientCatalog.findByTypeId(it.typeId)?.isTopCap == true } == true

        if (hasTrailingTopCap && !entry.isTopCap) {
            updatedLayers.add(updatedLayers.size - 1, instance)
        } else {
            updatedLayers.add(instance)
        }

        val updatedDish = active.copy(layers = updatedLayers)
        commitActiveDish(updatedDish, selectedUid = instance.uid)
    }

    fun selectLayer(uid: String?) {
        val validUid = if (uid != null && _uiState.value.activeDish?.layers?.any { it.uid == uid } == true) {
            uid
        } else {
            null
        }
        _uiState.update { it.copy(selectedLayerUid = validUid) }
        engineBridge.selectLayer(validUid)
    }

    fun updateLayerVariant(uid: String, variant: String) {
        val active = _uiState.value.activeDish ?: return
        val updatedLayers = active.layers.map { layer ->
            if (layer.uid == uid) layer.copy(variant = variant) else layer
        }
        commitActiveDish(active.copy(layers = updatedLayers), selectedUid = _uiState.value.selectedLayerUid)
    }

    fun updateLayerThickness(uid: String, thickness: Float) {
        val active = _uiState.value.activeDish ?: return
        val clamped = thickness.coerceIn(0.5f, 1.5f)
        val updatedLayers = active.layers.map { layer ->
            if (layer.uid == uid) layer.copy(thickness = clamped) else layer
        }
        commitActiveDish(active.copy(layers = updatedLayers), selectedUid = _uiState.value.selectedLayerUid)
    }

    /**
     * Moves layer upward toward the physical top of the dish (higher index in bottom -> top list).
     */
    fun moveLayerUp(uid: String) {
        val active = _uiState.value.activeDish ?: return
        val idx = active.layers.indexOfFirst { it.uid == uid }
        if (idx < 0 || idx >= active.layers.lastIndex) return

        val reordered = active.layers.toMutableList()
        val item = reordered.removeAt(idx)
        reordered.add(idx + 1, item)
        commitActiveDish(active.copy(layers = reordered), selectedUid = uid)
    }

    /**
     * Moves layer downward toward the physical base of the dish (lower index in bottom -> top list).
     */
    fun moveLayerDown(uid: String) {
        val active = _uiState.value.activeDish ?: return
        val idx = active.layers.indexOfFirst { it.uid == uid }
        if (idx <= 0) return

        val reordered = active.layers.toMutableList()
        val item = reordered.removeAt(idx)
        reordered.add(idx - 1, item)
        commitActiveDish(active.copy(layers = reordered), selectedUid = uid)
    }

    fun deleteLayer(uid: String) {
        val active = _uiState.value.activeDish ?: return
        val idx = active.layers.indexOfFirst { it.uid == uid }
        if (idx < 0) return

        val removed = active.layers[idx]
        val updatedLayers = active.layers.toMutableList().apply { removeAt(idx) }
        val updatedDish = active.copy(layers = updatedLayers)
        val nextSelected = if (_uiState.value.selectedLayerUid == uid) null else _uiState.value.selectedLayerUid

        _uiState.update { state ->
            state.copy(
                dishes = state.dishes.map { if (it.id == updatedDish.id) updatedDish else it },
                selectedLayerUid = nextSelected,
                pendingUndo = PendingLayerUndo(updatedDish.id, idx, removed)
            )
        }
        engineBridge.syncDish(updatedDish)
        engineBridge.selectLayer(nextSelected)
        showToast("Removed ${removed.label}", canUndoDelete = true, timeoutMs = 3000L)
    }

    fun undoDeleteLayer() {
        val pending = _uiState.value.pendingUndo ?: return
        val targetDish = _uiState.value.dishes.firstOrNull { it.id == pending.dishId } ?: return
        val updatedLayers = targetDish.layers.toMutableList()
        val safeIndex = pending.originalIndex.coerceIn(0, updatedLayers.size)
        updatedLayers.add(safeIndex, pending.layer)
        val updatedDish = targetDish.copy(layers = updatedLayers)

        toastDismissJob?.cancel()
        _uiState.update { state ->
            state.copy(
                dishes = state.dishes.map { if (it.id == updatedDish.id) updatedDish else it },
                activeDishId = updatedDish.id,
                selectedLayerUid = pending.layer.uid,
                pendingUndo = null,
                toast = null
            )
        }
        engineBridge.syncDish(updatedDish)
        engineBridge.selectLayer(pending.layer.uid)
    }

    fun dismissToast() {
        toastDismissJob?.cancel()
        _uiState.update { it.copy(pendingUndo = null, toast = null) }
    }

    fun toggleBeautyShotMode() {
        _uiState.update { state ->
            val next = !state.isBeautyShotMode
            state.copy(
                isBeautyShotMode = next,
                selectedLayerUid = if (next) null else state.selectedLayerUid
            )
        }
        if (_uiState.value.isBeautyShotMode) {
            engineBridge.selectLayer(null)
        }
    }

    fun toggleExplode() {
        val nextExploded = !_uiState.value.isExploded
        _uiState.update { it.copy(isExploded = nextExploded) }
        engineBridge.setExplode(if (nextExploded) 1f else 0f)
    }

    fun toggleMobileStackRail() {
        _uiState.update { it.copy(isStackRailExpandedOnMobile = !it.isStackRailExpandedOnMobile) }
    }

    fun triggerBitePress() {
        engineBridge.triggerBitePress()
        showToast("Bite press synced to 3D stage", canUndoDelete = false, timeoutMs = 1800L)
    }

    fun saveMenuOrder() {
        val dish = _uiState.value.activeDish ?: return
        engineBridge.syncDish(dish)
        val formattedPrice = String.format(java.util.Locale.US, "%.2f", dish.totalPrice)
        showToast(
            "Saved ${dish.name} (${dish.layers.size} layers · $$formattedPrice)",
            canUndoDelete = false,
            timeoutMs = 2400L
        )
    }

    private fun updateDishById(dishId: String, transform: (CustomDish) -> CustomDish) {
        var updatedTarget: CustomDish? = null
        _uiState.update { state ->
            val mapped = state.dishes.map { dish ->
                if (dish.id == dishId) {
                    transform(dish).also { updatedTarget = it }
                } else {
                    dish
                }
            }
            state.copy(dishes = mapped)
        }
        val active = _uiState.value.activeDish
        if (updatedTarget != null && active?.id == updatedTarget?.id) {
            engineBridge.syncDish(updatedTarget!!)
        }
    }

    private fun commitActiveDish(updatedDish: CustomDish, selectedUid: String?) {
        _uiState.update { state ->
            state.copy(
                dishes = state.dishes.map { if (it.id == updatedDish.id) updatedDish else it },
                selectedLayerUid = selectedUid
            )
        }
        engineBridge.syncDish(updatedDish)
        engineBridge.selectLayer(selectedUid)
    }

    private fun showToast(message: String, canUndoDelete: Boolean, timeoutMs: Long = 3000L) {
        toastDismissJob?.cancel()
        val toastId = idCounter.incrementAndGet()
        _uiState.update {
            it.copy(
                toast = StudioToast(id = toastId, message = message, canUndoDelete = canUndoDelete)
            )
        }
        toastDismissJob = scheduleAutoDismiss(timeoutMs) {
            _uiState.update { current ->
                if (current.toast?.id == toastId) {
                    current.copy(toast = null, pendingUndo = if (canUndoDelete) null else current.pendingUndo)
                } else {
                    current
                }
            }
        }
    }
}

class MenuStudioViewModel : ViewModel() {
    val engineBridge = ReactiveMenuEngineBridge()

    val holder = MenuStudioStateHolder(
        engineBridge = engineBridge,
        scheduleAutoDismiss = { delayMs, block ->
            viewModelScope.launch {
                delay(delayMs)
                block()
            }
        }
    )

    val uiState: StateFlow<MenuStudioUiState> = holder.uiState
}
