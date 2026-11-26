package com.menustudio.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menustudio.app.food.ProceduralFoodStagePlugin
import com.menustudio.app.stage.ProceduralStagePlugin
import com.menustudio.app.stage.StageHost
import com.menustudio.app.state.MenuStudioStateHolder
import com.menustudio.app.state.StudioToast
import com.menustudio.app.ui.components.BeautyShotRestorePill
import com.menustudio.app.ui.components.BottomAddDock
import com.menustudio.app.ui.components.ContextualAdjustBar
import com.menustudio.app.ui.components.DishMenuSheet
import com.menustudio.app.ui.components.LiveStackRail
import com.menustudio.app.ui.components.TopFloatingBar
import com.menustudio.app.ui.theme.StudioColors

/**
 * Single-screen, zero-scroll, app-native 3D Custom Menu Studio.
 * 3 floating HUD zones over a full-bleed Filament 3D viewport (`fillMaxSize()`).
 */
@Composable
fun MenuStudioScreen(
    stateHolder: MenuStudioStateHolder,
    stagePlugin: ProceduralStagePlugin? = null
) {
    val state by stateHolder.uiState.collectAsState()
    val activeDish = state.activeDish
    val appContext = LocalContext.current.applicationContext
    val resolvedPlugin = stagePlugin ?: remember(appContext) { ProceduralFoodStagePlugin(appContext) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.StageVoid)
    ) {
        val isTabletOrDesktop = maxWidth >= 768.dp

        // Background Layer — Full-Bleed 3D Viewport (always live & unobstructed)
        StageHost(
            engineBridge = stateHolder.engineBridge,
            onEmptySpaceTapped = { stateHolder.selectLayer(null) },
            stagePlugin = resolvedPlugin,
            modifier = Modifier.fillMaxSize()
        )

        // Beauty Shot mode: keep only a single discrete exit pill
        AnimatedVisibility(
            visible = state.isBeautyShotMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 10.dp, end = 12.dp)
        ) {
            BeautyShotRestorePill(
                onRestoreHud = { stateHolder.toggleBeautyShotMode() }
            )
        }

        // Standard Studio HUD (3 Floating Zones)
        AnimatedVisibility(
            visible = !state.isBeautyShotMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Zone 1 — Minimal Floating Top Bar
                TopFloatingBar(
                    dishName = activeDish?.name ?: "Menu Studio",
                    totalPrice = activeDish?.totalPrice ?: 0.0,
                    totalKcal = activeDish?.totalKcal ?: 0,
                    isExploded = state.isExploded,
                    onOpenDishMenu = { stateHolder.setMenuSheetOpen(true) },
                    onToggleBeautyShot = { stateHolder.toggleBeautyShotMode() },
                    onToggleExplode = { stateHolder.toggleExplode() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp)
                )

                // Zone 2 — Floating Live Stack Rail
                // Mobile (< 768dp): Right edge; Tablet/Desktop (>= 768dp): Left 240dp panel
                LiveStackRail(
                    topToBottomLayers = state.topToBottomLayers,
                    selectedLayerUid = state.selectedLayerUid,
                    isTabletOrDesktop = isTabletOrDesktop,
                    isExpandedOnMobile = state.isStackRailExpandedOnMobile,
                    onToggleMobileRail = { stateHolder.toggleMobileStackRail() },
                    onLayerSelected = { uid -> stateHolder.selectLayer(uid) },
                    modifier = Modifier
                        .align(if (isTabletOrDesktop) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(
                            top = 72.dp,
                            bottom = if (state.selectedLayer != null) 256.dp else 132.dp
                        )
                )

                // Bottom Stack: Toast + Contextual Adjust Bar + Zone 3 Bottom Add Dock
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(
                            if (isTabletOrDesktop) Modifier.widthIn(max = 672.dp)
                            else Modifier.fillMaxWidth()
                        )
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 3-second Undo Toast / Save Toast
                    AnimatedVisibility(
                        visible = state.toast != null,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        state.toast?.let { toast ->
                            StudioToastPill(
                                toast = toast,
                                onUndo = { stateHolder.undoDeleteLayer() },
                                onDismiss = { stateHolder.dismissToast() }
                            )
                        }
                    }

                    // Contextual Adjust Bar (reveals right above Zone 3 when a layer is selected)
                    AnimatedVisibility(
                        visible = state.selectedLayer != null,
                        enter = fadeIn() + slideInVertically { it / 3 },
                        exit = fadeOut() + slideOutVertically { it / 3 }
                    ) {
                        state.selectedLayer?.let { selected ->
                            ContextualAdjustBar(
                                layer = selected,
                                canMoveUp = state.canMoveSelectedUp,
                                canMoveDown = state.canMoveSelectedDown,
                                onSelectVariant = { variant ->
                                    stateHolder.updateLayerVariant(selected.uid, variant)
                                },
                                onChangeThickness = { thickness ->
                                    stateHolder.updateLayerThickness(selected.uid, thickness)
                                },
                                onMoveUp = { stateHolder.moveLayerUp(selected.uid) },
                                onMoveDown = { stateHolder.moveLayerDown(selected.uid) },
                                onDelete = { stateHolder.deleteLayer(selected.uid) },
                                onDismiss = { stateHolder.selectLayer(null) }
                            )
                        }
                    }

                    // Zone 3 — Thumb-Zone Bottom Add Dock
                    BottomAddDock(
                        activeCategoryFilter = state.activeCategoryFilter,
                        onSelectCategoryFilter = { stateHolder.selectCategoryFilter(it) },
                        onAddIngredient = { stateHolder.addIngredient(it) },
                        onSaveMenu = { stateHolder.saveMenuOrder() }
                    )
                }
            }
        }
    }

    if (state.isMenuSheetOpen) {
        DishMenuSheet(
            dishes = state.dishes,
            activeDishId = activeDish?.id ?: "",
            onDismiss = { stateHolder.setMenuSheetOpen(false) },
            onSelectDish = { stateHolder.selectDish(it) },
            onCreateDish = { name, archetype -> stateHolder.createDish(name, archetype) },
            onRenameDish = { id, newName -> stateHolder.renameDish(id, newName) },
            onDuplicateDish = { stateHolder.duplicateDish(it) },
            onDeleteDish = { stateHolder.deleteDish(it) }
        )
    }
}

@Composable
private fun StudioToastPill(
    toast: StudioToast,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .defaultMinSize(minHeight = 44.dp)
            .clip(CircleShape)
            .background(StudioColors.GlassCharcoalElevated)
            .border(1.dp, StudioColors.GlassBorderStrong, CircleShape)
            .clickable(role = Role.Button, onClick = onDismiss)
            .padding(start = 16.dp, end = if (toast.canUndoDelete) 6.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = toast.message,
            color = StudioColors.WarmCream,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (toast.canUndoDelete) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 36.dp)
                    .clip(CircleShape)
                    .background(StudioColors.WarmAmber)
                    .clickable(role = Role.Button, onClick = onUndo)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Undo",
                    color = StudioColors.StageVoid,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
