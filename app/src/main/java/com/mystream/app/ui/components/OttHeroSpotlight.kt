package com.mystream.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mystream.app.data.model.StremioMetaPreview
import com.mystream.app.ui.theme.BgDark
import com.mystream.app.ui.theme.FocusRing
import com.mystream.app.ui.theme.GlassBorder
import com.mystream.app.ui.theme.HotstarBg
import com.mystream.app.ui.theme.HotstarHeroBottomVignette
import com.mystream.app.ui.theme.HotstarHeroSideVignette
import com.mystream.app.ui.theme.ImdbGold
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary

import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun OttHeroSpotlight(
    item: StremioMetaPreview?,
    trailerYtId: String?,
    isTrailerPlaybackEnabled: Boolean,
    isAudioMuted: Boolean,
    isWatchlisted: Boolean,
    onToggleTrailerPlayback: () -> Unit,
    onToggleAudioMute: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onNavigateDownToContent: () -> Unit,
    onNavigateLeftToSidebar: () -> Unit,
    modifier: Modifier = Modifier,
    watchlistFocusRequester: FocusRequester? = null
) {
    LaunchedEffect(trailerYtId, isTrailerPlaybackEnabled, isAudioMuted) {
        if (!trailerYtId.isNullOrBlank() && isTrailerPlaybackEnabled) {
            TrailerPlaybackManager.play(trailerYtId, isAudioMuted)
        } else {
            TrailerPlaybackManager.stop()
        }
    }

    val isVideoPlaying = TrailerPlaybackManager.isVideoPlaying && !TrailerPlaybackManager.isStopped
    val backdropAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVideoPlaying) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "HeroBackdropAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        if (item != null) {
            // Layer 1: Static Backdrop (Fallback & Base while video loads)
            val backdropUrl = item.background ?: item.poster
            if (!backdropUrl.isNullOrBlank()) {
                val context = LocalContext.current
                val imageRequest = remember(backdropUrl) {
                    ImageRequest.Builder(context)
                        .data(backdropUrl)
                        .size(coil3.size.Size(1280, 720))
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(backdropAlpha)
                )
            }

            // Layer 2: Cinematic Left Side Vignette (Protects Text Readability)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HotstarHeroSideVignette)
            )

            // Layer 3: Bottom Vignette (Blends into bottom category row seamlessly)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HotstarHeroBottomVignette)
            )

            // Layer 4: Hero Content & Metadata Overlays (Firmly on the left 46% of screen)
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.46f)
                    .padding(start = 24.dp, top = 20.dp, end = 12.dp)
            ) {
                // Title + Type Badge + Rating Badge all in a single line!
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Type + Rating Badge (e.g. MOVIE • ★ 8.4) - Placed to the LEFT of the Title
                    val typeText = if (item.type.equals("series", ignoreCase = true)) "SERIES" else "MOVIE"
                    val typeAndRatingText = if (!item.imdbRating.isNullOrBlank()) {
                        "$typeText • ★ ${item.imdbRating}"
                    } else {
                        typeText
                    }

                    Box(
                        modifier = Modifier
                            .background(FocusRing.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                            .border(1.dp, FocusRing.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = typeAndRatingText,
                            color = FocusRing,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Text(
                        text = item.name,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Line 2: Year & Genres (No duplicate or hardcoded specs)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val year = item.year ?: item.releaseInfo
                    if (!year.isNullOrBlank()) {
                        Text(
                            text = year,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val genreText = item.genres.take(3).joinToString(" • ")
                    if (genreText.isNotBlank()) {
                        if (!year.isNullOrBlank()) {
                            Text(text = "•", color = TextMuted, fontSize = 10.sp)
                        }
                        Text(
                            text = genreText,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Synopsis (left-aligned, 4 lines max, constrained to 46% width)
                val desc = item.description
                if (!desc.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = desc,
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 16.sp,
                        maxLines = 4,
                        textAlign = TextAlign.Start,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons Row: Watchlist, Trailer Play/Pause (left of volume), Audio Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Watchlist Toggle Button
                    OttHeroIconButton(
                        icon = if (isWatchlisted) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Watchlist",
                        tint = if (isWatchlisted) FocusRing else TextPrimary,
                        focusRequester = watchlistFocusRequester,
                        onClick = onToggleWatchlist,
                        onNavigateDown = onNavigateDownToContent,
                        onNavigateLeft = onNavigateLeftToSidebar
                    )

                    // Trailer Play/Pause Toggle Button (Left of volume button)
                    if (!trailerYtId.isNullOrBlank()) {
                        val isPlaying = isTrailerPlaybackEnabled && !TrailerPlaybackManager.isStopped
                        OttHeroIconButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause Trailer" else "Play Trailer",
                            tint = if (isPlaying) FocusRing else TextSecondary,
                            onClick = {
                                val nextPlaying = !isPlaying
                                TrailerPlaybackManager.setPlaybackEnabled(nextPlaying)
                                if (nextPlaying) {
                                    TrailerPlaybackManager.resume()
                                } else {
                                    TrailerPlaybackManager.stop()
                                }
                                onToggleTrailerPlayback()
                            },
                            onNavigateDown = onNavigateDownToContent,
                            onNavigateLeft = null
                        )
                    }

                    // Audio Mute/Unmute Toggle Button for Background Trailer
                    if (!trailerYtId.isNullOrBlank()) {
                        OttHeroIconButton(
                            icon = if (TrailerPlaybackManager.isAudioMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (TrailerPlaybackManager.isAudioMuted) "Unmute Trailer" else "Mute Trailer",
                            tint = if (TrailerPlaybackManager.isAudioMuted) TextSecondary else FocusRing,
                            onClick = {
                                TrailerPlaybackManager.setMuted(!TrailerPlaybackManager.isAudioMuted)
                                onToggleAudioMute()
                            },
                            onNavigateDown = onNavigateDownToContent,
                            onNavigateLeft = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OttHeroActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onNavigateDown: () -> Unit,
    onNavigateLeft: (() -> Unit)?
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> if (isPrimary) FocusRing else Color(0x33FFFFFF)
        isPrimary -> Color.White
        else -> Color(0x331E293B)
    }

    val contentColor = when {
        isFocused -> if (isPrimary) Color.Black else Color.White
        isPrimary -> Color.Black
        else -> Color.White
    }

    val borderStroke = when {
        isFocused -> 2.dp
        else -> 1.dp
    }

    val borderColor = when {
        isFocused -> FocusRing
        else -> GlassBorder
    }

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(borderStroke, borderColor, RoundedCornerShape(24.dp))
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionDown -> {
                            onNavigateDown()
                            true
                        }
                        Key.DirectionLeft -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft()
                                true
                            } else false
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = contentColor,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OttHeroIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onNavigateDown: () -> Unit,
    onNavigateLeft: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = if (isFocused) FocusRing.copy(alpha = 0.25f) else Color(0x331E293B)
    val borderColor = if (isFocused) FocusRing else GlassBorder

    Box(
        modifier = Modifier
            .size(34.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(CircleShape)
            .background(bg)
            .border(if (isFocused) 2.dp else 1.dp, borderColor, CircleShape)
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionDown -> {
                            onNavigateDown()
                            true
                        }
                        Key.DirectionLeft -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft()
                                true
                            } else false
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
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) FocusRing else tint,
            modifier = Modifier.size(16.dp)
        )
    }
}
