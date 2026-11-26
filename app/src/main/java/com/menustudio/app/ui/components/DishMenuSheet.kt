package com.menustudio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menustudio.app.bridge.CustomDish
import com.menustudio.app.bridge.DishArchetype
import com.menustudio.app.ui.theme.StudioColors
import java.util.Locale

/**
 * Clean Menu Sheet opened from the Zone 1 Dish Switcher Pill.
 * Supports switching saved dishes, + Creating New Custom Dish, Rename, Duplicate, and Delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishMenuSheet(
    dishes: List<CustomDish>,
    activeDishId: String,
    onDismiss: () -> Unit,
    onSelectDish: (String) -> Unit,
    onCreateDish: (name: String, archetype: DishArchetype) -> Unit,
    onRenameDish: (dishId: String, newName: String) -> Unit,
    onDuplicateDish: (dishId: String) -> Unit,
    onDeleteDish: (dishId: String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<CustomDish?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161310),
        contentColor = StudioColors.WarmCream,
        scrimColor = Color(0xD90A0806)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Custom Menu Dishes",
                    color = StudioColors.WarmCream,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(CircleShape)
                        .background(StudioColors.WarmAmber)
                        .clickable(role = Role.Button) { showCreateDialog = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = StudioColors.StageVoid,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "New Dish",
                        color = StudioColors.StageVoid,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dishes, key = { it.id }) { dish ->
                    val isActive = dish.id == activeDishId
                    val priceText = String.format(Locale.US, "$%.2f", dish.totalPrice)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isActive) Color(0x2EF5A623)
                                else StudioColors.GlassSurfaceMuted
                            )
                            .border(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) StudioColors.WarmAmber else StudioColors.GlassBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(role = Role.Button) { onSelectDish(dish.id) }
                            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = dish.name,
                                    color = if (isActive) StudioColors.WarmAmber else StudioColors.WarmCream,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Active",
                                        tint = StudioColors.WarmAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${dish.archetype.displayLabel} · ${dish.layers.size} layers · $priceText · ${dish.totalKcal} kcal",
                                color = StudioColors.TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        SheetActionButton(
                            contentDescription = "Rename ${dish.name}",
                            tint = StudioColors.WarmCream,
                            onClick = { renameTarget = dish }
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, tint = StudioColors.WarmCream, modifier = Modifier.size(18.dp))
                        }

                        SheetActionButton(
                            contentDescription = "Duplicate ${dish.name}",
                            tint = StudioColors.WarmCream,
                            onClick = { onDuplicateDish(dish.id) }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = StudioColors.WarmCream, modifier = Modifier.size(18.dp))
                        }

                        if (dishes.size > 1) {
                            SheetActionButton(
                                contentDescription = "Delete ${dish.name}",
                                tint = StudioColors.DangerRed,
                                onClick = { onDeleteDish(dish.id) }
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = StudioColors.DangerRed, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateDishDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, archetype ->
                showCreateDialog = false
                onCreateDish(name, archetype)
            }
        )
    }

    renameTarget?.let { target ->
        RenameDishDialog(
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { updatedName ->
                renameTarget = null
                onRenameDish(target.id, updatedName)
            }
        )
    }
}

@Composable
private fun SheetActionButton(
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun CreateDishDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, DishArchetype) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var archetype by remember { mutableStateOf(DishArchetype.STACK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.GlassCharcoalElevated,
        title = {
            Text("Create Custom Dish", color = StudioColors.WarmCream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("e.g., Double Truffle Melt", color = StudioColors.TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StudioColors.WarmCream,
                        unfocusedTextColor = StudioColors.WarmCream,
                        focusedBorderColor = StudioColors.WarmAmber,
                        unfocusedBorderColor = StudioColors.GlassBorderStrong
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Layout Archetype", color = StudioColors.TextSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in DishArchetype.entries) {
                        val selected = option == archetype
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(CircleShape)
                                .background(if (selected) StudioColors.WarmAmber else StudioColors.GlassSurfaceMuted)
                                .border(1.dp, if (selected) StudioColors.WarmAmber else StudioColors.GlassBorder, CircleShape)
                                .clickable(role = Role.Button) { archetype = option }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.displayLabel,
                                color = if (selected) StudioColors.StageVoid else StudioColors.WarmCream,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, archetype) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioColors.WarmAmber,
                    contentColor = StudioColors.StageVoid
                )
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = StudioColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun RenameDishDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.GlassCharcoalElevated,
        title = {
            Text("Rename Dish", color = StudioColors.WarmCream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = StudioColors.WarmCream,
                    unfocusedTextColor = StudioColors.WarmCream,
                    focusedBorderColor = StudioColors.WarmAmber,
                    unfocusedBorderColor = StudioColors.GlassBorderStrong
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioColors.WarmAmber,
                    contentColor = StudioColors.StageVoid
                )
            ) {
                Text("Rename", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = StudioColors.TextSecondary)
            }
        }
    )
}
