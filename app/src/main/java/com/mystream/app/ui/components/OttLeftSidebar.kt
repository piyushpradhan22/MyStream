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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mystream.app.ui.screens.OttCategory
import com.mystream.app.ui.theme.FocusRing
import com.mystream.app.ui.theme.GlassBorder
import com.mystream.app.ui.theme.HotstarSidebarGlass
import com.mystream.app.ui.theme.HotstarSidebarGlassExpanded
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary

/** Fixed (non-category) navigation destinations pinned in the sidebar. */
enum class OttNavDestination {
    EXIT,
    SEARCH,
    CUSTOM_URL,
    SETTINGS
}

data class OttNavItem(
    val destination: OttNavDestination,
    val title: String,
    val icon: ImageVector
)

private val OTT_TOP_NAV_ITEMS = listOf(
    OttNavItem(OttNavDestination.EXIT, "Exit App", Icons.AutoMirrored.Filled.ExitToApp)
)

private val OTT_BOTTOM_NAV_ITEMS = listOf(
    OttNavItem(OttNavDestination.SEARCH, "Search", Icons.Default.Search),
    OttNavItem(OttNavDestination.CUSTOM_URL, "Custom URL", Icons.Default.AddLink),
    OttNavItem(OttNavDestination.SETTINGS, "Settings", Icons.Default.Settings)
)

private const val SIDEBAR_COLLAPSED_WIDTH = 40
private const val SIDEBAR_EXPANDED_WIDTH = 190
private val SidebarIconSlot = Modifier.size(18.dp)

/**
 * Compact, uniformly-sized icon per category. Recognized streaming platforms keep their distinct
 * mono badge (Netflix "N", Prime, Disney+, Jio, Zee5, Sony LIV); everything else falls back to a
 * generic movie/series/etc. icon so every row always has a relevant, single-glyph icon.
 */
@Composable
private fun CategorySidebarIcon(category: OttCategory, tint: Color) {
    val clean = category.title.trim()
    Box(modifier = SidebarIconSlot, contentAlignment = Alignment.Center) {
        when {
            category.id == "continue_watching" -> Icon(Icons.Default.History, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            category.id == "watchlist" -> Icon(Icons.Default.Bookmark, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            category.id == "hf_direct" -> Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
            clean.contains("Netflix", ignoreCase = true) -> PlatformBadge("N", Color(0xFFE50914), Color.White)
            clean.contains("Prime", ignoreCase = true) -> PlatformBadge("P", Color(0xFF00A8E1), Color.White)
            clean.contains("Disney", ignoreCase = true) || clean.contains("Hotstar", ignoreCase = true) -> PlatformBadge("D+", Color(0xFF0F1035), Color(0xFF90CAF9), Color(0xFF1E88E5))
            clean.contains("Jio", ignoreCase = true) -> PlatformBadge("J", Color(0xFFE50055), Color.White)
            clean.contains("Zee5", ignoreCase = true) -> PlatformBadge("Z5", Color(0xFF8224E3), Color.White)
            clean.contains("Sony", ignoreCase = true) -> PlatformBadge("LIV", Color(0xFFFF6900), Color.White)
            clean.equals("Top Rated", ignoreCase = true) -> Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF5C518), modifier = Modifier.size(16.dp))
            clean.contains("Hindi", ignoreCase = true) -> Text(text = "\uD83C\uDDEE\uD83C\uDDF3", fontSize = 12.sp)
            category.type.equals("series", ignoreCase = true) -> Icon(Icons.Default.Tv, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            else -> Icon(Icons.Default.Movie, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PlatformBadge(label: String, bg: Color, textColor: Color, borderColor: Color? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .then(if (borderColor != null) Modifier.border(0.8.dp, borderColor, RoundedCornerShape(4.dp)) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontWeight = FontWeight.Black,
            fontSize = if (label.length > 1) 7.sp else 9.sp,
            maxLines = 1
        )
    }
}

@Composable
fun OttLeftSidebar(
    categories: List<OttCategory>,
    selectedCategoryId: String,
    onSelectCategory: (String) -> Unit,
    onExit: () -> Unit,
    onSearch: () -> Unit,
    onCustomUrl: () -> Unit,
    onSettings: () -> Unit,
    onNavigateRight: () -> Unit,
    modifier: Modifier = Modifier,
    searchFocusRequester: FocusRequester? = null,
    exitFocusRequester: FocusRequester? = null,
    sidebarFocusable: Boolean = true,
    onSidebarFocusChanged: ((Boolean) -> Unit)? = null,
    categoryFocusRequesters: MutableMap<Int, FocusRequester>? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    val handleRightNavigation = {
        isExpanded = false
        onNavigateRight()
    }
    val categoryListState = rememberLazyListState()
    val sidebarWidth by animateDpAsState(
        targetValue = if (isExpanded) SIDEBAR_EXPANDED_WIDTH.dp else SIDEBAR_COLLAPSED_WIDTH.dp,
        animationSpec = tween(220),
        label = "SidebarWidth"
    )
    val sidebarBg = if (isExpanded) HotstarSidebarGlassExpanded else HotstarSidebarGlass

    LaunchedEffect(categories) {
        categoryListState.scrollToItem(0)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(sidebarWidth)
            .zIndex(10f)
            .background(sidebarBg)
            .border(
                width = 1.dp,
                color = GlassBorder,
                shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
            )
            .onFocusChanged { state ->
                onSidebarFocusChanged?.invoke(state.hasFocus)
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // App Brand Logo / Icon Header (Small & Subtle)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                AnimatedVisibility(visible = isExpanded, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = "  MyStream",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Exit App pinned at the very top
            OTT_TOP_NAV_ITEMS.forEach { item ->
                OttSidebarButton(
                    title = item.title,
                    icon = { tint -> Icon(imageVector = item.icon, contentDescription = item.title, tint = tint, modifier = Modifier.size(16.dp)) },
                    isSelected = false,
                    isExpanded = isExpanded,
                    focusRequester = exitFocusRequester,
                    isFocusable = sidebarFocusable,
                    onClick = onExit,
                    onNavigateLeft = { isExpanded = !isExpanded },
                    onNavigateRight = handleRightNavigation
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            SidebarDivider()
            Spacer(modifier = Modifier.height(10.dp))

            // Search / Custom URL / Settings
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OTT_BOTTOM_NAV_ITEMS.forEach { item ->
                    val fr = if (item.destination == OttNavDestination.SEARCH) searchFocusRequester else null
                    OttSidebarButton(
                        title = item.title,
                        icon = { tint -> Icon(imageVector = item.icon, contentDescription = item.title, tint = tint, modifier = Modifier.size(16.dp)) },
                        isSelected = false,
                        isExpanded = isExpanded,
                        focusRequester = fr,
                        isFocusable = sidebarFocusable,
                        onClick = {
                            when (item.destination) {
                                OttNavDestination.SEARCH -> onSearch()
                                OttNavDestination.CUSTOM_URL -> onCustomUrl()
                                OttNavDestination.SETTINGS -> onSettings()
                                else -> {}
                            }
                        },
                        onNavigateLeft = { isExpanded = !isExpanded },
                        onNavigateRight = handleRightNavigation
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            SidebarDivider()
            Spacer(modifier = Modifier.height(6.dp))

            // Categories (scrollable, fills remaining space)
            LazyColumn(
                state = categoryListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(categories, key = { _, cat -> cat.id }) { index, category ->
                    val fr = categoryFocusRequesters?.getOrPut(index) { FocusRequester() }
                    OttSidebarButton(
                        title = category.title,
                        icon = { tint -> CategorySidebarIcon(category = category, tint = tint) },
                        isSelected = category.id == selectedCategoryId,
                        isExpanded = isExpanded,
                        focusRequester = fr,
                        isFocusable = sidebarFocusable,
                        onClick = { onSelectCategory(category.id) },
                        onNavigateLeft = { isExpanded = !isExpanded },
                        onNavigateRight = handleRightNavigation
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 7.dp)
            .height(1.dp)
            .background(GlassBorder)
    )
}

@Composable
private fun OttSidebarButton(
    title: String,
    icon: @Composable (tint: Color) -> Unit,
    isSelected: Boolean,
    isExpanded: Boolean,
    focusRequester: FocusRequester? = null,
    isFocusable: Boolean = true,
    onClick: () -> Unit,
    onNavigateLeft: (() -> Unit)? = null,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(itemBg)
            .then(
                if (isFocused) Modifier.border(1.5.dp, FocusRing, RoundedCornerShape(8.dp))
                else if (isSelected) Modifier.border(1.dp, FocusRing.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                else Modifier
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state -> isFocused = state.isFocused }
            .focusable(interactionSource = interactionSource, enabled = isFocusable)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            onNavigateLeft?.invoke()
                            true
                        }
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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isFocusable,
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon(iconColor)

        AnimatedVisibility(visible = isExpanded, enter = fadeIn(), exit = fadeOut()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = if (isFocused || isSelected) TextPrimary else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
