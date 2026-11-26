package com.menustudio.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menustudio.app.bridge.MenuIngredientInstance
import com.menustudio.app.catalog.IngredientCatalog
import com.menustudio.app.ui.theme.StudioColors
import java.util.Locale
import kotlin.math.abs

/**
 * Zone 2 — Floating Live Stack Rail.
 * Shows active layers in physical top-to-bottom order.
 * - Mobile (< 768dp): slim collapsible right-edge chip rail.
 * - Tablet / Desktop (>= 768dp): clean 240.dp glass panel on the left edge.
 */
@Composable
fun LiveStackRail(
    topToBottomLayers: List<MenuIngredientInstance>,
    selectedLayerUid: String?,
    isTabletOrDesktop: Boolean,
    isExpandedOnMobile: Boolean,
    onToggleMobileRail: () -> Unit,
    onLayerSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isTabletOrDesktop) {
        DesktopStackPanel(
            topToBottomLayers = topToBottomLayers,
            selectedLayerUid = selectedLayerUid,
            onLayerSelected = onLayerSelected,
            modifier = modifier
        )
    } else {
        MobileCompactStackRail(
            topToBottomLayers = topToBottomLayers,
            selectedLayerUid = selectedLayerUid,
            isExpanded = isExpandedOnMobile,
            onToggleExpanded = onToggleMobileRail,
            onLayerSelected = onLayerSelected,
            modifier = modifier
        )
    }
}

@Composable
private fun MobileCompactStackRail(
    topToBottomLayers: List<MenuIngredientInstance>,
    selectedLayerUid: String?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onLayerSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(end = 10.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Collapsible rail toggle chip
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 44.dp)
                .clip(CircleShape)
                .background(StudioColors.GlassCharcoal)
                .border(1.dp, StudioColors.GlassBorder, CircleShape)
                .clickable(role = Role.Button, onClick = onToggleExpanded)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,
                contentDescription = if (isExpanded) "Collapse Stack Rail" else "Expand Stack Rail",
                tint = StudioColors.WarmAmber,
                modifier = Modifier.size(18.dp)
            )
            Icon(
                imageVector = Icons.Rounded.Layers,
                contentDescription = null,
                tint = StudioColors.WarmCream,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "${topToBottomLayers.size}",
                color = StudioColors.WarmCream,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(min = 108.dp, max = 148.dp)
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End,
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(topToBottomLayers, key = { it.uid }) { layer ->
                    val isSelected = layer.uid == selectedLayerUid
                    val dotColor = Color(IngredientCatalog.findByTypeId(layer.typeId)?.accentHex ?: 0xFFF5A623)

                    Row(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) StudioColors.GlassCharcoalElevated
                                else StudioColors.GlassCharcoal
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) StudioColors.WarmAmber else StudioColors.GlassBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable(role = Role.Button) { onLayerSelected(layer.uid) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(dotColor, CircleShape)
                        )
                        Text(
                            text = layer.label,
                            color = if (isSelected) StudioColors.WarmAmber else StudioColors.WarmCream,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopStackPanel(
    topToBottomLayers: List<MenuIngredientInstance>,
    selectedLayerUid: String?,
    onLayerSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(start = 16.dp)
            .width(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(StudioColors.GlassCharcoal)
            .border(1.dp, StudioColors.GlassBorder, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stack Layers",
                color = StudioColors.WarmCream,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${topToBottomLayers.size} active",
                color = StudioColors.TextSecondary,
                fontSize = 13.sp
            )
        }

        LazyColumn(
            modifier = Modifier.heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(topToBottomLayers, key = { it.uid }) { layer ->
                val isSelected = layer.uid == selectedLayerUid
                val dotColor = Color(IngredientCatalog.findByTypeId(layer.typeId)?.accentHex ?: 0xFFF5A623)
                val thicknessTag = if (abs(layer.clampedThickness - 1.0f) >= 0.05f) {
                    String.format(Locale.US, " · %.1fx", layer.clampedThickness)
                } else ""

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) Color(0x33F5A623)
                            else StudioColors.GlassSurfaceMuted
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) StudioColors.WarmAmber else StudioColors.GlassBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable(role = Role.Button) { onLayerSelected(layer.uid) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(dotColor, CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = layer.label,
                            color = if (isSelected) StudioColors.WarmAmber else StudioColors.WarmCream,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${layer.variant}$thicknessTag",
                            color = StudioColors.TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "$%.2f", layer.effectivePrice),
                        color = StudioColors.WarmCream,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
