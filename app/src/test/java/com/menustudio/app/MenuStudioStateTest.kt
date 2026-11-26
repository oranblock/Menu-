package com.menustudio.app

import com.menustudio.app.bridge.CustomDish
import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.bridge.MenuEngineBridge
import com.menustudio.app.bridge.ReactiveMenuEngineBridge
import com.menustudio.app.catalog.IngredientCatalog
import com.menustudio.app.food.primitives.BriocheCrownPrimitive
import com.menustudio.app.food.primitives.CrispyChickenPrimitive
import com.menustudio.app.food.primitives.FoodPrimitiveRegistry
import com.menustudio.app.food.primitives.MeshAppender
import com.menustudio.app.food.primitives.MozzarellaPuddlesPrimitive
import com.menustudio.app.food.primitives.PortobelloPrimitive
import com.menustudio.app.food.primitives.SlateBoardPrimitive
import com.menustudio.app.food.primitives.SweetBasilPrimitive
import com.menustudio.app.food.textures.ProceduralTextureBaker
import com.menustudio.app.state.MenuStudioStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingEngineBridge : MenuEngineBridge {
    val syncedDishes = mutableListOf<CustomDish>()
    val selectedLayers = mutableListOf<String?>()
    val explodeValues = mutableListOf<Float>()
    var bitePressCount = 0
    private var tapCallback: ((String) -> Unit)? = null

    override fun syncDish(dish: CustomDish) {
        syncedDishes.add(dish)
    }

    override fun selectLayer(uid: String?) {
        selectedLayers.add(uid)
    }

    override fun setExplode(amount: Float) {
        explodeValues.add(amount)
    }

    override fun triggerBitePress() {
        bitePressCount++
    }

    override fun onLayerTapped(callback: (String) -> Unit) {
        tapCallback = callback
    }

    fun simulateTap(uid: String) {
        tapCallback?.invoke(uid)
    }
}

class MenuStudioStateTest {

    @Test
    fun initialDishHasBottomToTopLayersAndReversedRailOrder() {
        val bridge = RecordingEngineBridge()
        val holder = MenuStudioStateHolder(engineBridge = bridge)
        val state = holder.uiState.value
        val active = state.activeDish!!

        assertEquals("Signature Smash", active.name)
        assertEquals(DishArchetype.STACK, active.archetype)
        assertEquals("brioche_base", active.layers.first().typeId)
        assertEquals("brioche_top", active.layers.last().typeId)

        // Rail displays physical top-to-bottom order
        assertEquals("brioche_top", state.topToBottomLayers.first().typeId)
        assertEquals("brioche_base", state.topToBottomLayers.last().typeId)
        assertTrue(active.totalPrice > 0.0)
        assertTrue(active.totalKcal > 0)
        assertEquals(1, bridge.syncedDishes.size)
    }

    @Test
    fun addDuplicateIngredientsGetsUniqueUidsAndAutoSelects() {
        val bridge = RecordingEngineBridge()
        val holder = MenuStudioStateHolder(engineBridge = bridge)

        holder.addIngredient("patty")
        val firstAddedUid = holder.uiState.value.selectedLayerUid!!
        holder.addIngredient("patty")
        val secondAddedUid = holder.uiState.value.selectedLayerUid!!

        assertNotEquals(firstAddedUid, secondAddedUid)
        val layers = holder.uiState.value.activeDish!!.layers
        val pattyCount = layers.count { it.typeId == "patty" }
        assertEquals(3, pattyCount) // 1 initial + 2 added

        // Top bun remains the cap on a STACK burger when adding non-top-cap ingredients
        assertEquals("brioche_top", layers.last().typeId)
        assertEquals(secondAddedUid, bridge.selectedLayers.last())
    }

    @Test
    fun variantAndThicknessAdjustUpdateBridgeAndTotals() {
        val bridge = RecordingEngineBridge()
        val holder = MenuStudioStateHolder(engineBridge = bridge)
        val initialDish = holder.uiState.value.activeDish!!
        val patty = initialDish.layers.first { it.typeId == "patty" }
        val baseTotal = initialDish.totalPrice
        val baseKcal = initialDish.totalKcal

        holder.selectLayer(patty.uid)
        holder.updateLayerVariant(patty.uid, "Thick Pub")
        holder.updateLayerThickness(patty.uid, 1.5f)

        val updated = holder.uiState.value.activeDish!!
        val updatedPatty = updated.layers.first { it.uid == patty.uid }
        assertEquals("Thick Pub", updatedPatty.variant)
        assertEquals(1.5f, updatedPatty.thickness, 0.001f)
        assertTrue(updated.totalPrice > baseTotal)
        assertTrue(updated.totalKcal > baseKcal)

        // Clamping beyond bounds
        holder.updateLayerThickness(patty.uid, 3.0f)
        assertEquals(1.5f, holder.uiState.value.selectedLayer!!.thickness, 0.001f)
        holder.updateLayerThickness(patty.uid, 0.1f)
        assertEquals(0.5f, holder.uiState.value.selectedLayer!!.thickness, 0.001f)
    }

    @Test
    fun moveUpAndDownReordersStackWithinBounds() {
        val holder = MenuStudioStateHolder()
        val initialLayers = holder.uiState.value.activeDish!!.layers
        val target = initialLayers[2] // index 2 (Patty)

        holder.selectLayer(target.uid)
        holder.moveLayerUp(target.uid)
        val afterUp = holder.uiState.value.activeDish!!.layers
        assertEquals(3, afterUp.indexOfFirst { it.uid == target.uid })

        holder.moveLayerDown(target.uid)
        holder.moveLayerDown(target.uid)
        val afterDown = holder.uiState.value.activeDish!!.layers
        assertEquals(1, afterDown.indexOfFirst { it.uid == target.uid })

        // Topmost layer cannot move further up
        val topLayer = afterDown.last()
        holder.selectLayer(topLayer.uid)
        assertFalse(holder.uiState.value.canMoveSelectedUp)
        assertTrue(holder.uiState.value.canMoveSelectedDown)
    }

    @Test
    fun deleteLayerShowsUndoAndRestoresExactPosition() {
        val bridge = RecordingEngineBridge()
        val holder = MenuStudioStateHolder(engineBridge = bridge)
        val beforeLayers = holder.uiState.value.activeDish!!.layers
        val tomatoIndex = beforeLayers.indexOfFirst { it.typeId == "tomato" }
        val tomato = beforeLayers[tomatoIndex]

        holder.selectLayer(tomato.uid)
        holder.deleteLayer(tomato.uid)

        val afterDelete = holder.uiState.value
        assertNull(afterDelete.selectedLayerUid)
        assertFalse(afterDelete.activeDish!!.layers.any { it.uid == tomato.uid })
        assertNotNull(afterDelete.toast)
        assertTrue(afterDelete.toast!!.canUndoDelete)

        holder.undoDeleteLayer()
        val afterUndo = holder.uiState.value
        assertEquals(tomato.uid, afterUndo.selectedLayerUid)
        assertEquals(tomato.uid, afterUndo.activeDish!!.layers[tomatoIndex].uid)
        assertNull(afterUndo.toast)
    }

    @Test
    fun dishCrudCreateRenameDuplicateDeleteWorksCleanly() {
        val holder = MenuStudioStateHolder()
        val initialCount = holder.uiState.value.dishes.size

        holder.createDish("Truffle Bowl", DishArchetype.BOWL)
        assertEquals(initialCount + 1, holder.uiState.value.dishes.size)
        val created = holder.uiState.value.activeDish!!
        assertEquals("Truffle Bowl", created.name)
        assertEquals(DishArchetype.BOWL, created.archetype)
        assertEquals("rice_base", created.layers.single().typeId)

        holder.renameDish(created.id, "Chef Truffle Bowl")
        assertEquals("Chef Truffle Bowl", holder.uiState.value.activeDish!!.name)

        holder.duplicateDish(created.id)
        val duplicated = holder.uiState.value.activeDish!!
        assertEquals("Chef Truffle Bowl Copy", duplicated.name)
        assertNotEquals(created.id, duplicated.id)
        assertNotEquals(created.layers.first().uid, duplicated.layers.first().uid)

        holder.deleteDish(duplicated.id)
        assertEquals(initialCount + 1, holder.uiState.value.dishes.size)
    }

    @Test
    fun engineBridgeTapExplodeBiteAndBeautyShotSync() {
        val bridge = ReactiveMenuEngineBridge()
        val holder = MenuStudioStateHolder(engineBridge = bridge)
        val layer = holder.uiState.value.activeDish!!.layers[2]

        // Tap from 3D stage triggers selection in UI
        bridge.dispatchLayerTapped(layer.uid)
        assertEquals(layer.uid, holder.uiState.value.selectedLayerUid)
        assertEquals(layer.uid, bridge.snapshot.value.selectedLayerUid)

        // Tap empty space dismisses adjust bar
        holder.selectLayer(null)
        assertNull(holder.uiState.value.selectedLayerUid)
        assertNull(bridge.snapshot.value.selectedLayerUid)

        // Explode toggle & bite press
        holder.toggleExplode()
        assertTrue(holder.uiState.value.isExploded)
        assertEquals(1.0f, bridge.snapshot.value.explodeAmount, 0.001f)
        holder.toggleExplode()
        assertFalse(holder.uiState.value.isExploded)
        assertEquals(0.0f, bridge.snapshot.value.explodeAmount, 0.001f)

        val prevNonce = bridge.snapshot.value.bitePressNonce
        holder.triggerBitePress()
        assertEquals(prevNonce + 1L, bridge.snapshot.value.bitePressNonce)

        // Beauty shot clears adjust bar and hides HUD
        bridge.dispatchLayerTapped(layer.uid)
        holder.toggleBeautyShotMode()
        assertTrue(holder.uiState.value.isBeautyShotMode)
        assertNull(holder.uiState.value.selectedLayerUid)
    }

    @Test
    fun everyCatalogIngredientResolvesToValidPrimitiveAndTextureKey() {
        val validKeys = setOf(
            "brioche", "sourdough_crust", "patty", "chicken", "mushroom",
            "cheese", "mozzarella", "tomato", "lettuce", "basil",
            "bacon", "pickle", "avocado", "sauce", "slate"
        )
        val appender = MeshAppender()

        for (entry in IngredientCatalog.entries) {
            val primitive = FoodPrimitiveRegistry.resolvePrimitive(entry.typeId)
            val texKey = FoodPrimitiveRegistry.resolveTextureKey(entry.typeId)
            assertTrue("Unexpected textureKey '$texKey' for ${entry.typeId}", texKey in validKeys)

            for (archetype in DishArchetype.entries) {
                val instance = IngredientCatalog.createInstance(entry.typeId)
                appender.reset()
                val topY = primitive.bake(archetype, instance, orderIdx = 1, baseY = 0.25f, appender = appender)
                assertTrue("Expected positive vertexCount for ${entry.typeId}", appender.vCount > 0)
                assertTrue("Expected positive indexCount for ${entry.typeId}", appender.iCount > 0)
                assertEquals("Triangles must be multiples of 3 for ${entry.typeId}", 0, appender.iCount % 3)
                assertTrue("topY must advance above baseY for ${entry.typeId}", topY > 0.25f)

                for (i in 0 until appender.iCount) {
                    assertTrue("Index out of bounds for ${entry.typeId}", appender.idx[i] in 0 until appender.vCount)
                }
            }
        }
    }

    @Test
    fun briocheCrownMaintainsRealVerticalDomeHeightAcrossThicknesses() {
        val crown = BriocheCrownPrimitive()
        val appender = MeshAppender()
        for (thickness in listOf(0.5f, 1.0f, 1.5f)) {
            appender.reset()
            val inst = IngredientCatalog.createInstance("brioche_top", thickness = thickness)
            val topY = crown.bake(DishArchetype.STACK, inst, orderIdx = 6, baseY = 1.0f, appender = appender)
            var minY = Float.POSITIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (v in 0 until appender.vCount) {
                val y = appender.pos[v * 3 + 1]
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            val height = maxY - minY
            assertTrue("Expected dome height >= ${0.80f * thickness}, got $height", height >= 0.80f * thickness)
            assertTrue("topY should advance cleanly with dome height", topY >= 1.0f + 0.80f * thickness)
        }
    }

    @Test
    fun primitiveRegistryRoutesVisualPassV2PrimitivesAndTextures() {
        assertTrue(FoodPrimitiveRegistry.resolvePrimitive("basil") is SweetBasilPrimitive)
        assertEquals("basil", FoodPrimitiveRegistry.resolveTextureKey("basil"))

        assertTrue(FoodPrimitiveRegistry.resolvePrimitive("mozzarella") is MozzarellaPuddlesPrimitive)
        assertEquals("mozzarella", FoodPrimitiveRegistry.resolveTextureKey("mozzarella"))

        assertTrue(FoodPrimitiveRegistry.resolvePrimitive("crispy_chicken") is CrispyChickenPrimitive)
        assertEquals("chicken", FoodPrimitiveRegistry.resolveTextureKey("crispy_chicken"))

        assertTrue(FoodPrimitiveRegistry.resolvePrimitive("portobello") is PortobelloPrimitive)
        assertEquals("mushroom", FoodPrimitiveRegistry.resolveTextureKey("portobello"))

        assertEquals("sourdough_crust", FoodPrimitiveRegistry.resolveTextureKey("sourdough_crust"))

        // Tileable noise wraps seamlessly between u=0 and u=1
        val n0 = ProceduralTextureBaker.tileableFbm(0.0f, 0.37f, freq = 6, octaves = 3, seed = 42)
        val n1 = ProceduralTextureBaker.tileableFbm(1.0f, 0.37f, freq = 6, octaves = 3, seed = 42)
        assertEquals(n0, n1, 1e-5f)
    }

    @Test
    fun slateBoardPrimitiveBakesGroundedPlateGeometry() {
        val appender = MeshAppender()
        val topY = FoodPrimitiveRegistry.slateBoard.bakeBoard(appender)
        assertTrue(appender.vCount > 0)
        assertTrue(appender.iCount > 0)
        assertEquals(0, appender.iCount % 3)
        assertEquals(0f, topY, 0.001f)
        assertTrue(SlateBoardPrimitive.BOARD_BASE_Y < 0f)
    }
}
