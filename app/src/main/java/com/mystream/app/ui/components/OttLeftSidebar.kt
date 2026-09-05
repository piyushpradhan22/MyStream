package com.mystream.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mystream.app.ui.theme.FocusRing
import com.mystream.app.ui.theme.GlassBorder
import com.mystream.app.ui.theme.HotstarSidebarGlass
import com.mystream.app.ui.theme.HotstarSidebarGlassExpanded
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary

enum class OttNavDestination {
    SEARCH,
    HOME,
    MOVIES,
    SERIES,
    WATCHLIST,
    CUSTOM_URL,
    SETTINGS
}

data class OttNavItem(
    val destination: OttNavDestination,
    val title: String,
    val icon: ImageVector
)

val OTT_NAV_ITEMS = listOf(
    OttNavItem(OttNavDestination.SEARCH, "Search", Icons.Default.Search),
    OttNavItem(OttNavDestination.HOME, "Home", Icons.Default.Home),
    OttNavItem(OttNavDestination.MOVIES, "Movies", Icons.Default.Movie),
    OttNavItem(OttNavDestination.SERIES, "TV Shows", Icons.Default.Tv),
    OttNavItem(OttNavDestination.WATCHLIST, "Watchlist", Icons.Default.Bookmark),
    OttNavItem(OttNavDestination.CUSTOM_URL, "Stream Link", Icons.Default.AddLink),
    OttNavItem(OttNavDestination.SETTINGS, "Settings", Icons.Default.Settings)
)

@Composable
fun OttLeftSidebar(
    selectedDestination: OttNavDestination,
    onSelectDestination: (OttNavDestination) -> Unit,
    onNavigateRight: () -> Unit,
    modifier: Modifier = Modifier,
    searchFocusRequester: FocusRequester? = null,
    homeFocusRequester: FocusRequester? = null,
    onHomeFocusChanged: ((Boolean) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(40.dp)
            .zIndex(10f)
            .background(HotstarSidebarGlass)
            .border(
                width = 1.dp,
                color = GlassBorder,
                shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Brand Logo / Icon Header (Small & Subtle)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(FocusRing.copy(alpha = 0.2f))
                    .border(1.dp, FocusRing, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    color = FocusRing,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Navigation Items
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OTT_NAV_ITEMS.forEach { item ->
                    val isSelected = selectedDestination == item.destination
                    val fr = when (item.destination) {
                        OttNavDestination.SEARCH -> searchFocusRequester
                        OttNavDestination.HOME -> homeFocusRequester
                        else -> null
                    }
                    OttSidebarButton(
                        item = item,
                        isSelected = isSelected,
                        focusRequester = fr,
                        onFocusChanged = { focused ->
                            if (item.destination == OttNavDestination.HOME) {
                                onHomeFocusChanged?.invoke(focused)
                            }
                        },
                        onClick = { onSelectDestination(item.destination) },
                        onNavigateRight = onNavigateRight
                    )
                }
            }
        }
    }
}

@Composable
private fun OttSidebarButton(
    item: OttNavItem,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
    onNavigateRight: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }

    val itemBg = when {
        isFocused -> FocusRing.copy(alpha = 0.25f)
        isSelected -> Color(0x2238BDF8)
        else -> Color.Transparent
    }

    val iconColor = when {
        isFocused -> FocusRing
        isSelected -> FocusRing
        else -> TextMuted
    }

    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(itemBg)
            .then(
                if (isFocused) Modifier.border(1.5.dp, FocusRing, RoundedCornerShape(8.dp))
                else if (isSelected) Modifier.border(1.dp, FocusRing.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                else Modifier
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                onFocusChanged?.invoke(state.isFocused)
            }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionRight -> {
                            onNavigateRight()
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Left active pill indicator line
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(2.dp)
                    .height(12.dp)
                    .background(FocusRing, CircleShape)
            )
        }

        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
    }
}
