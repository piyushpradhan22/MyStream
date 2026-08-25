package com.mystream.app.ui.components

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.mystream.app.ui.theme.BgDark
import com.mystream.app.ui.theme.EmeraldNeon
import com.mystream.app.ui.theme.FocusRingOrange
import com.mystream.app.ui.theme.GlassBorder
import com.mystream.app.ui.theme.GlassSurface
import com.mystream.app.ui.theme.ImdbGold
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.PrimaryNeonGlow
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary

@Composable
fun HeroBanner(
    item: StremioMetaPreview,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    playFocusRequester: FocusRequester? = null,
    onNavigateDown: (() -> Unit)? = null,
    onDetailsClick: (() -> Unit)? = null,
    height: Int = 360
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(BgDark)
    ) {
        // Backdrop Image
        val imageUrl = item.background ?: item.poster
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Cinematic Multi-Stop Ambient Vignette Overlays
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.35f to Color(0x4D07090E),
                        0.70f to Color(0xD907090E),
                        1.0f to BgDark
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xE607090E),
                        0.45f to Color(0x9907090E),
                        0.85f to Color.Transparent
                    )
                )
        )

        // Banner Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Badges Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!item.imdbRating.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(GlassSurface)
                            .border(0.5.dp, Color(0x4DFFC000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.5.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = ImdbGold,
                            modifier = Modifier.size(12.5.dp)
                        )
                        Text(
                            text = item.imdbRating,
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // 4K UHD Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(GlassSurface)
                        .border(0.5.dp, GlassBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "4K UHD",
                        color = EmeraldNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                if (item.year != null || item.releaseInfo != null) {
                    Text(
                        text = item.year ?: item.releaseInfo ?: "",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (item.genres.isNotEmpty()) {
                    Text(
                        text = "•  " + item.genres.take(3).joinToString(", "),
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = item.name,
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.description,
                    color = TextSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            val playInteractionSource = remember { MutableInteractionSource() }
            val isPlayFocused by playInteractionSource.collectIsFocusedAsState()

            val detailsInteractionSource = remember { MutableInteractionSource() }
            val isDetailsFocused by detailsInteractionSource.collectIsFocusedAsState()

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlayClick,
                    interactionSource = playInteractionSource,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayFocused) PrimaryNeon else PrimaryNeon.copy(alpha = 0.95f),
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .then(if (playFocusRequester != null) Modifier.focusRequester(playFocusRequester) else Modifier)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown && onNavigateDown != null) {
                                onNavigateDown()
                                true
                            } else false
                        }
                        .shadow(
                            elevation = if (isPlayFocused) 16.dp else 6.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = PrimaryNeonGlow,
                            spotColor = PrimaryNeon
                        )
                        .then(
                            if (isPlayFocused) Modifier.border(2.5.dp, FocusRingOrange, RoundedCornerShape(12.dp))
                            else Modifier.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Play Now",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    )
                }

                if (onDetailsClick != null) {
                    OutlinedButton(
                        onClick = onDetailsClick,
                        interactionSource = detailsInteractionSource,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .background(GlassSurface, RoundedCornerShape(12.dp))
                            .then(
                                if (isDetailsFocused) Modifier.border(2.5.dp, FocusRingOrange, RoundedCornerShape(12.dp))
                                else Modifier.border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Details",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Details",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
