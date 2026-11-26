package com.menustudio.app.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menustudio.app.bridge.MenuIngredientInstance
import com.menustudio.app.catalog.IngredientCatalog
import com.menustudio.app.ui.theme.StudioColors
import java.util.Locale

/**
 * Compact Contextual Adjust Bar floating directly above Zone 3 when a layer is selected.
 * Contains: Variant chips, Portion/Thickness slider (0.5x - 1.5x), Move Up/Down, and Delete.
 */
@Composable
fun ContextualAdjustBar(
    layer: MenuIngredientInstance,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelectVariant: (String) -> Unit,
    onChangeThickness: (Float) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val catalogEntry = IngredientCatalog.findByTypeId(layer.typeId)
    val variants = catalogEntry?.variants ?: listOf(layer.variant)
    val dotColor = Color(catalogEntry?.accentHex ?: 0xFFF5A623)
    val priceFormatted = String.format(Locale.US, "$%.2f", layer.effectivePrice)
    val multiplierFormatted = String.format(Locale.US, "%.1fx", layer.clampedThickness)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(StudioColors.GlassCharcoalElevated)
            .border(1.dp, StudioColors.GlassBorderStrong, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header row: Selected item identity + stats + dismiss
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(dotColor, CircleShape)
                )
                Text(
                    text = catalogEntry?.label ?: layer.label,
                    color = StudioColors.WarmCream,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$priceFormatted · ${layer.effectiveKcal} kcal",
                    color = StudioColors.WarmAmber,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Adjust Bar",
                    tint = StudioColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Variant chips row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(variants) { variant ->
                val isSelected = variant == layer.variant
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) StudioColors.WarmAmber
                            else StudioColors.GlassSurfaceMuted
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) StudioColors.WarmAmber else StudioColors.GlassBorder,
                            shape = CircleShape
                        )
                        .clickable(role = Role.Button) { onSelectVariant(variant) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = variant,
                        color = if (isSelected) StudioColors.StageVoid else StudioColors.WarmCream,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        // Portion/Thickness slider (0.5x - 1.5x) + Reorder (Up/Down) + Delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = multiplierFormatted,
                color = StudioColors.WarmCream,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                )
            )

            Slider(
                value = layer.clampedThickness,
                onValueChange = onChangeThickness,
                valueRange = 0.5f..1.5f,
                colors = SliderDefaults.colors(
                    thumbColor = StudioColors.WarmAmber,
                    activeTrackColor = StudioColors.WarmAmber,
                    inactiveTrackColor = StudioColors.GlassBorder
                ),
                modifier = Modifier.weight(1f)
            )

            AdjustActionButton(
                enabled = canMoveUp,
                tint = StudioColors.WarmCream,
                backgroundColor = StudioColors.GlassSurfaceMuted,
                contentDescription = "Move Layer Up",
                onClick = onMoveUp
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = null,
                    tint = StudioColors.WarmCream,
                    modifier = Modifier.size(19.dp)
                )
            }

            AdjustActionButton(
                enabled = canMoveDown,
                tint = StudioColors.WarmCream,
                backgroundColor = StudioColors.GlassSurfaceMuted,
                contentDescription = "Move Layer Down",
                onClick = onMoveDown
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = StudioColors.WarmCream,
                    modifier = Modifier.size(19.dp)
                )
            }

            AdjustActionButton(
                enabled = true,
                tint = StudioColors.DangerRed,
                backgroundColor = Color(0x2BE5534B),
                contentDescription = "Delete Layer",
                onClick = onDelete
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = StudioColors.DangerRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AdjustActionButton(
    enabled: Boolean,
    tint: Color,
    backgroundColor: Color,
    contentDescription: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, tint.copy(alpha = 0.25f), CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
