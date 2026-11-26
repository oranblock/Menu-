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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menustudio.app.bridge.IngredientCategory
import com.menustudio.app.catalog.IngredientCatalog
import com.menustudio.app.catalog.IngredientCatalogEntry
import com.menustudio.app.ui.theme.StudioColors
import java.util.Locale

/**
 * Zone 3 — Thumb-Zone Bottom Add Dock.
 * Clean category filter pills + horizontal scrollable Ingredient Cards + primary Save Menu pill button.
 */
@Composable
fun BottomAddDock(
    activeCategoryFilter: IngredientCategory?,
    onSelectCategoryFilter: (IngredientCategory?) -> Unit,
    onAddIngredient: (String) -> Unit,
    onSaveMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleEntries = if (activeCategoryFilter == null) {
        IngredientCatalog.entries
    } else {
        IngredientCatalog.entries.filter { it.category == activeCategoryFilter }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(StudioColors.GlassCharcoal)
            .border(1.dp, StudioColors.GlassBorder, RoundedCornerShape(24.dp))
            .padding(top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Category Filter Pills Row: All, Base, Mains, Cheese, Fresh, Sauces, Extras
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                CategoryFilterPill(
                    label = "All",
                    selected = activeCategoryFilter == null,
                    onClick = { onSelectCategoryFilter(null) }
                )
            }
            items(IngredientCategory.entries) { category ->
                CategoryFilterPill(
                    label = category.displayLabel,
                    selected = activeCategoryFilter == category,
                    onClick = { onSelectCategoryFilter(category) }
                )
            }
        }

        // Single horizontal scrollable row of Ingredient Cards + pinned Save Menu primary pill button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleEntries, key = { it.typeId }) { entry ->
                    IngredientAddCard(
                        entry = entry,
                        onClick = { onAddIngredient(entry.typeId) }
                    )
                }
            }

            // Compact Save Menu / Order primary pill button on the right
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(CircleShape)
                    .background(StudioColors.WarmAmber)
                    .clickable(role = Role.Button, onClick = onSaveMenu)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = StudioColors.StageVoid,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Save",
                    color = StudioColors.StageVoid,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(CircleShape)
            .background(
                if (selected) StudioColors.WarmCream
                else StudioColors.GlassSurfaceMuted
            )
            .border(
                width = 1.dp,
                color = if (selected) StudioColors.WarmCream else StudioColors.GlassBorder,
                shape = CircleShape
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) StudioColors.StageVoid else StudioColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun IngredientAddCard(
    entry: IngredientCatalogEntry,
    onClick: () -> Unit
) {
    val formattedPrice = if (entry.basePrice % 1.0 == 0.0) {
        String.format(Locale.US, "$%.0f", entry.basePrice)
    } else {
        String.format(Locale.US, "$%.2f", entry.basePrice)
    }

    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(StudioColors.GlassCharcoalElevated)
            .border(1.dp, StudioColors.GlassBorderStrong, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(Color(entry.accentHex), CircleShape)
        )
        Text(
            text = "+ ${entry.shortChipLabel}",
            color = StudioColors.WarmCream,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = "· $formattedPrice",
            color = StudioColors.WarmAmber,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            maxLines = 1
        )
    }
}
