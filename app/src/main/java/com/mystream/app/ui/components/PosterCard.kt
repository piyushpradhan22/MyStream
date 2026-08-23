package com.mystream.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mystream.app.data.model.StremioMetaPreview
import com.mystream.app.ui.theme.FocusRingOrange
import com.mystream.app.ui.theme.ImdbGold
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SurfaceCard
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary

@Composable
fun PosterCard(
    item: StremioMetaPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 140,
    progressFraction: Float? = null,
    onClearClick: (() -> Unit)? = null
) {
    val cardFocusRequester = remember { FocusRequester() }
    val clearFocusRequester = remember { FocusRequester() }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val clearInteractionSource = remember { MutableInteractionSource() }
    val isClearFocused by clearInteractionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused || isHovered) 1.08f else 1.0f,
        label = "cardScale"
    )

    val borderModifier = if (isFocused) {
        Modifier.border(2.5.dp, FocusRingOrange, RoundedCornerShape(12.dp))
    } else {
        Modifier.border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
    }

    val widthModifier = if (width > 0) Modifier.width(width.dp) else Modifier.fillMaxWidth()

    Column(
        modifier = modifier
            .then(widthModifier)
            .scale(scale)
            .focusRequester(cardFocusRequester)
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionRight -> {
                        if (onClearClick != null) {
                            clearFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(if (isFocused) 12.dp else 4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .then(borderModifier)
        ) {
            if (!item.poster.isNullOrBlank()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2C3E50), Color(0xFF000000))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.name.take(2).uppercase(),
                        color = TextSecondary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Clear from Continue Watching Button
            if (onClearClick != null) {
                Box(
                    modifier = Modifier
                        .focusRequester(clearFocusRequester)
                        .focusable(interactionSource = clearInteractionSource)
                        .onPreviewKeyEvent { keyEvent ->
                            val isSelectKey = keyEvent.key == Key.DirectionCenter ||
                                keyEvent.key == Key.Enter ||
                                keyEvent.key == Key.NumPadEnter ||
                                keyEvent.key == Key.Spacebar

                            if (isSelectKey) {
                                if (keyEvent.type == KeyEventType.KeyUp) {
                                    onClearClick()
                                }
                                return@onPreviewKeyEvent true
                            }

                            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    cardFocusRequester.requestFocus()
                                    true
                                }

                                else -> false
                            }
                        }
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isClearFocused) PrimaryNeon.copy(alpha = 0.35f) else Color(0xD9000000))
                        .border(
                            if (isClearFocused) 2.dp else 1.dp,
                            if (isClearFocused) PrimaryNeon else Color(0x66FFFFFF),
                            CircleShape
                        )
                        .clickable(interactionSource = clearInteractionSource, indication = null) { onClearClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove from Continue Watching",
                        tint = if (isClearFocused) Color.White else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Rating badge if available
            if (!item.imdbRating.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(if (onClearClick != null) Alignment.TopStart else Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = ImdbGold,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = item.imdbRating,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Type badge (Series / Movie)
            if (item.type.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 6.dp, vertical = if (progressFraction != null && progressFraction > 0f) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = item.type.uppercase(),
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Progress Bar if watched
            if (progressFraction != null && progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.5.dp)
                        .background(Color(0x88000000))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progressFraction.coerceIn(0.03f, 1f))
                            .fillMaxSize()
                            .background(PrimaryNeon)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.name,
            color = if (isFocused) FocusRingOrange else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val subtext = listOfNotNull(item.year ?: item.releaseInfo, item.genres.firstOrNull()).joinToString(" • ")
        if (subtext.isNotBlank()) {
            Text(
                text = subtext,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
