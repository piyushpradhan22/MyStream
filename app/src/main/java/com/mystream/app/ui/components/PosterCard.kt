package com.mystream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.bitmapConfig
import com.mystream.app.data.model.StremioMetaPreview
import com.mystream.app.ui.theme.FocusRing
import com.mystream.app.ui.theme.FocusRingOrange
import com.mystream.app.ui.theme.GlassBorder
import com.mystream.app.ui.theme.GlassSurface
import com.mystream.app.ui.theme.ImdbGold
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SecondaryCyan
import com.mystream.app.ui.theme.SurfaceCard
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary

// Pre-cached static brushes and shapes to eliminate allocation overhead
private val CardCornerShape = RoundedCornerShape(12.dp)
private val VignetteGradient = Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9090B10)))
private val DefaultPlaceholderBrush = Brush.verticalGradient(listOf(Color(0xFF161B26), Color(0xFF0F141E)))
private val ProgressBarBrush = Brush.horizontalGradient(listOf(SecondaryCyan, PrimaryNeon))

@Composable
fun PosterCard(
    item: StremioMetaPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 110,
    progressFraction: Float? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocusedState = interactionSource.collectIsFocusedAsState()

    val sizeModifier = if (width > 0) Modifier.width(width.dp).height(172.dp) else Modifier.fillMaxWidth().height(172.dp)
    val posterBoxModifier = if (width > 0) Modifier.width((width - 6).coerceAtLeast(80).dp).height(134.dp) else Modifier.fillMaxWidth().height(134.dp)

    Column(
        modifier = modifier
            .then(sizeModifier)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
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
                onClick = onClick
            )
            .padding(horizontal = 3.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = posterBoxModifier
                .clip(CardCornerShape)
                .background(SurfaceCard)
                .drawWithContent {
                    drawContent()
                    // Focus read is scoped strictly to the Skia draw phase (ZERO recomposition, ZERO layout pass)
                    val isFocused = isFocusedState.value
                    val borderColor = if (isFocused) FocusRing else GlassBorder
                    val strokeWidth = if (isFocused) 2.5.dp.toPx() else 1.dp.toPx()
                    drawRoundRect(
                        color = borderColor,
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(width = strokeWidth)
                    )
                }
        ) {
            PosterThumbnail(
                posterUrl = item.poster,
                name = item.name,
                imdbRating = item.imdbRating,
                type = item.type,
                progressFraction = progressFraction
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.name,
            color = TextPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val subtext = remember(item.year, item.releaseInfo, item.genres) {
            listOfNotNull(item.year ?: item.releaseInfo, item.genres.firstOrNull()).joinToString(" • ")
        }
        if (subtext.isNotBlank()) {
            Text(
                text = subtext,
                color = TextMuted,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Completely immutable & 100% skippable thumbnail composable.
 * Takes ONLY primitives and immutable types.
 * Will NEVER recompose during D-pad focus navigation changes.
 */
@Composable
private fun PosterThumbnail(
    posterUrl: String?,
    name: String,
    imdbRating: String?,
    type: String,
    progressFraction: Float?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (!posterUrl.isNullOrBlank()) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val imageRequest = remember(posterUrl) {
                coil3.request.ImageRequest.Builder(context)
                    .data(posterUrl)
                    .size(280, 420)
                    .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                    .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DefaultPlaceholderBrush),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(2).uppercase(),
                    color = TextSecondary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Rating badge if available (Single node: zero clipping / icon overhead)
        if (!imdbRating.isNullOrBlank()) {
            Text(
                text = "★ $imdbRating",
                color = ImdbGold,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color(0xD90A0E17), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.5.dp)
            )
        }

        // Type badge (Series / Movie - High contrast, crisp badge)
        if (type.isNotBlank()) {
            Text(
                text = type.uppercase(),
                color = Color.White,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = if (progressFraction != null && progressFraction > 0f) 8.dp else 6.dp)
                    .background(Color(0xE60F172A), RoundedCornerShape(3.dp))
                    .border(0.5.dp, Color(0x66FFFFFF), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.5.dp, vertical = 1.5.dp)
            )
        }

        // Progress Bar if watched
        if (progressFraction != null && progressFraction > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0x88000000))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progressFraction.coerceIn(0.03f, 1f))
                        .fillMaxSize()
                        .background(ProgressBarBrush)
                )
            }
        }
    }
}
