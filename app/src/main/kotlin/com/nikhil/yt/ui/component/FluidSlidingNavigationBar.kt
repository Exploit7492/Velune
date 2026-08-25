package com.nikhil.yt.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import com.nikhil.yt.ui.screens.Screens

@Composable
fun FluidSlidingNavigationBar(
    modifier: Modifier = Modifier,
    items: List<Screens>,
    currentRoute: String,
    pureBlack: Boolean,
    badgeCounts: Map<String, Int> = emptyMap(),
    onTabSelected: (Screens) -> Unit
) {
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassSupported()

    val barColor = if (useGlass) Color.Transparent else if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val adaptiveTextColor = if (glassConfig.textColor.isSpecified) {
        glassConfig.textColor
    } else if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
    }
    val selectedContentColor = if (useGlass) adaptiveTextColor else MaterialTheme.colorScheme.onSecondaryContainer
    val unselectedContentColor = if (useGlass) adaptiveTextColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurfaceVariant

    val glassShape = RoundedCornerShape(28.dp)
    val barModifier = if (useGlass) {
        modifier
            .clip(glassShape)
            .fillMaxWidth()
            .height(80.dp)
            .liquidGlass(config = glassConfig, shape = glassShape)
    } else {
        modifier
            .clip(glassShape)
            .fillMaxWidth()
            .height(80.dp)
            .background(barColor)
    }

    BoxWithConstraints(modifier = barModifier) {
        val tabWidth = maxWidth / items.size

        val pillWidth = 48.dp
        val pillHeight = 32.dp

        val indicatorOffset by animateDpAsState(
            targetValue = (tabWidth * selectedIndex) + ((tabWidth - pillWidth) / 2),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "PillSlider"
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset, y = 14.dp)
                .width(pillWidth)
                .height(pillHeight)
                .background(
                    color = if (useGlass) selectedContentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape
                )
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(item) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(18.dp))

                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            painter = painterResource(id = if (isSelected) item.iconIdActive else item.iconIdInactive),
                            contentDescription = stringResource(id = item.titleId),
                            tint = if (isSelected) selectedContentColor else unselectedContentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        val badgeCount = badgeCounts[item.route] ?: 0
                        if (badgeCount > 0) {
                            Badge(modifier = Modifier.offset(x = 8.dp, y = (-4).dp), containerColor = MaterialTheme.colorScheme.error) {
                                Text(if (badgeCount > 99) "99+" else badgeCount.toString(), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(id = item.titleId),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected) selectedContentColor else unselectedContentColor
                    )
                }
            }
        }
    }
}