package com.menustudio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menustudio.app.ui.theme.StudioColors
import java.util.Locale

/**
 * Zone 1 — Minimal Floating Top Bar (56.dp height, dark warm-charcoal glass pills).
 */
@Composable
fun TopFloatingBar(
    dishName: String,
    totalPrice: Double,
    totalKcal: Int,
    isExploded: Boolean,
    onOpenDishMenu: () -> Unit,
    onToggleBeautyShot: () -> Unit,
    onToggleExplode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedPrice = String.format(Locale.US, "$%.2f", totalPrice)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        // Left: Custom Menu Dish Switcher Pill
        Row(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 220.dp)
                .defaultMinSize(minHeight = 44.dp)
                .clip(CircleShape)
                .background(StudioColors.GlassCharcoal)
                .border(1.dp, StudioColors.GlassBorder, CircleShape)
                .clickable(role = Role.Button, onClick = onOpenDishMenu)
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = dishName,
                color = StudioColors.WarmCream,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Switch Dish",
                tint = StudioColors.WarmAmber,
                modifier = Modifier.size(20.dp)
            )
        }

        // Right: Live Summary Pill ($Price + kcal + Eye Toggle + Explode Toggle)
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 44.dp)
                .clip(CircleShape)
                .background(StudioColors.GlassCharcoal)
                .border(1.dp, StudioColors.GlassBorder, CircleShape)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = formattedPrice,
                color = StudioColors.WarmAmber,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                maxLines = 1
            )
            Text(
                text = "·",
                color = StudioColors.TextSecondary,
                fontSize = 14.sp
            )
            Text(
                text = "$totalKcal kcal",
                color = StudioColors.WarmCream,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                ),
                maxLines = 1
            )

            // Eye Toggle (Beauty Shot)
            GlassIconToggle(
                active = false,
                contentDescription = "Hide HUD for Beauty Shot",
                onClick = onToggleBeautyShot
            ) {
                Icon(
                    imageVector = Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    tint = StudioColors.WarmCream,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Explode Toggle (1-tap expand/collapse 3D layers)
            GlassIconToggle(
                active = isExploded,
                contentDescription = if (isExploded) "Collapse Layers" else "Explode Layers",
                onClick = onToggleExplode
            ) {
                Icon(
                    imageVector = Icons.Rounded.Layers,
                    contentDescription = null,
                    tint = if (isExploded) StudioColors.StageVoid else StudioColors.WarmCream,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

/**
 * Discrete single pill shown when Beauty Shot (Eye Toggle) hides all HUD overlays.
 */
@Composable
fun BeautyShotRestorePill(
    onRestoreHud: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(CircleShape)
            .background(StudioColors.GlassCharcoal)
            .border(1.dp, StudioColors.GlassBorderStrong, CircleShape)
            .clickable(role = Role.Button, onClick = onRestoreHud)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Visibility,
            contentDescription = "Show Controls",
            tint = StudioColors.WarmAmber,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "Show Studio HUD",
            color = StudioColors.WarmCream,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GlassIconToggle(
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (active) StudioColors.WarmAmber
                else StudioColors.GlassSurfaceMuted
            )
            .clickable(
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
