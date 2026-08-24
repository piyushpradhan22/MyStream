package com.mystream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mystream.app.data.model.StremioStreamSource
import com.mystream.app.ui.theme.AccentAmber
import com.mystream.app.ui.theme.FocusRingOrange
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SecondaryCyan
import com.mystream.app.ui.theme.SurfaceCard
import com.mystream.app.ui.theme.SurfaceDark
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary
import com.mystream.app.ui.utils.safeRequestFocus

@Composable
fun StreamCard(
    stream: StremioStreamSource,
    isResolving: Boolean = false,
    externalFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onRestart: (() -> Unit)? = null,
    onMagnetStream: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    actionButtonText: String = "⚡ Resolve",
    modifier: Modifier = Modifier
) {
    val cardFocusRequester = externalFocusRequester ?: remember { FocusRequester() }
    val magnetFocusRequester = remember { FocusRequester() }
    val restartFocusRequester = remember { FocusRequester() }

    val cardInteractionSource = remember { MutableInteractionSource() }
    val restartInteractionSource = remember { MutableInteractionSource() }
    val magnetInteractionSource = remember { MutableInteractionSource() }

    val isCardFocused by cardInteractionSource.collectIsFocusedAsState()
    val isRestartFocused by restartInteractionSource.collectIsFocusedAsState()
    val isMagnetFocused by magnetInteractionSource.collectIsFocusedAsState()

    val isAnyFocused = isCardFocused || isMagnetFocused || isRestartFocused
    val borderColor = if (isAnyFocused) FocusRingOrange else Color(0x22FFFFFF)
    val bgColor = if (isAnyFocused) FocusRingOrange.copy(alpha = 0.12f) else SurfaceCard

    val shouldMarquee = isAnyFocused

    // Extract Torrent Name and File Name properly from title lines
    val titleLines = stream.title?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    val torrentName = titleLines.firstOrNull()?.takeIf { !it.contains("💾") && !it.contains("👤") }
        ?: stream.name?.takeIf { it.isNotBlank() }
        ?: "Direct Stream"

    val fileName = stream.behaviorHints?.bingeGroup?.takeIf { it.isNotBlank() && it != torrentName }
        ?: titleLines.getOrNull(1)?.takeIf { !it.contains("💾") && !it.contains("👤") && it != torrentName }

    // Outer Card Container: clean non-clickable Row containing Card Body & Action Buttons as direct siblings
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (isAnyFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Main Clickable Card Body (takes full remaining width)
        Column(
            modifier = Modifier
                .weight(1f)
                .focusRequester(cardFocusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (keyEvent.key) {
                        Key.DirectionRight -> {
                            if (onMagnetStream != null && !isResolving) {
                                magnetFocusRequester.safeRequestFocus()
                                true
                            } else if (onRestart != null && !isResolving) {
                                restartFocusRequester.safeRequestFocus()
                                true
                            } else false
                        }

                        Key.DirectionUp -> {
                            if (onUp != null) {
                                onUp()
                                true
                            } else false
                        }

                        else -> false
                    }
                }
                .clickable(
                    interactionSource = cardInteractionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
            // Badges (Quality, HDR, Hindi, Provider)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Quality Badge
                val is4k = stream.quality.contains("4K")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (is4k) AccentAmber else PrimaryNeon)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stream.quality,
                        color = if (is4k) Color.Black else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // HDR Badge if available
                stream.hdrType?.let { hdr ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SecondaryCyan.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = hdr,
                            color = SecondaryCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Hindi Audio Badge if available
                if (stream.hasHindiAudio) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE65100))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🇮🇳 HINDI",
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Provider Name (e.g. PP)
                stream.providerName?.let { provider ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33FFFFFF))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = provider,
                            color = TextSecondary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Line 1: Torrent / Release Name (Marquee only when focused/selected)
            Text(
                text = torrentName,
                color = if (isCardFocused) FocusRingOrange else TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = if (shouldMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (shouldMarquee) Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE) else Modifier.fillMaxWidth()
            )

            // Line 2: File Name (Marquee only when focused/selected)
            if (!fileName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "📄 $fileName",
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = if (shouldMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = if (shouldMarquee) Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE) else Modifier.fillMaxWidth()
                )
            }

            // Line 3: Subtitle metadata details (Audio, File Size, Seeders)
            val detailItems = mutableListOf<String>()
            stream.audioDetails?.let { detailItems.add(it) }
            stream.fileSize?.let { detailItems.add("💾 $it") }
            stream.seeders?.let { detailItems.add("👤 $it") }

            if (detailItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detailItems.joinToString("  •  "),
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 2. Action Buttons (Direct Sibling to Card Body -> Seamless D-pad Focus)
        if (isResolving || onMagnetStream != null || onRestart != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Direct P2P Magnet Stream button
                if (onMagnetStream != null && !isResolving) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isMagnetFocused) AccentAmber.copy(alpha = 0.35f) else AccentAmber.copy(alpha = 0.2f))
                            .border(
                                if (isMagnetFocused) 2.dp else 1.dp,
                                if (isMagnetFocused) FocusRingOrange else AccentAmber.copy(alpha = 0.5f),
                                RoundedCornerShape(6.dp)
                            )
                            .focusRequester(magnetFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (keyEvent.key) {
                                    Key.DirectionCenter,
                                    Key.Enter,
                                    Key.NumPadEnter,
                                    Key.Spacebar -> {
                                        onMagnetStream()
                                        true
                                    }

                                    Key.DirectionLeft -> {
                                        cardFocusRequester.safeRequestFocus()
                                        true
                                    }

                                    Key.DirectionRight -> {
                                        if (onRestart != null) {
                                            restartFocusRequester.safeRequestFocus()
                                            true
                                        } else false
                                    }

                                    Key.DirectionUp -> {
                                        if (onUp != null) {
                                            onUp()
                                            true
                                        } else false
                                    }

                                    else -> false
                                }
                            }
                            .clickable(
                                interactionSource = magnetInteractionSource,
                                indication = null,
                                onClick = onMagnetStream
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = actionButtonText,
                            color = if (isMagnetFocused) FocusRingOrange else AccentAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isResolving) {
                    CircularProgressIndicator(
                        color = FocusRingOrange,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else if (onRestart != null) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isRestartFocused) FocusRingOrange.copy(alpha = 0.25f) else SurfaceDark)
                            .border(
                                if (isRestartFocused) 2.dp else 1.dp,
                                if (isRestartFocused) FocusRingOrange else Color(0x33FFFFFF),
                                CircleShape
                            )
                            .focusRequester(restartFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (keyEvent.key) {
                                    Key.DirectionCenter,
                                    Key.Enter,
                                    Key.NumPadEnter,
                                    Key.Spacebar -> {
                                        onRestart()
                                        true
                                    }

                                    Key.DirectionLeft -> {
                                        if (onMagnetStream != null && !isResolving) {
                                            magnetFocusRequester.safeRequestFocus()
                                        } else {
                                            cardFocusRequester.safeRequestFocus()
                                        }
                                        true
                                    }

                                    Key.DirectionUp -> {
                                        if (onUp != null) {
                                            onUp()
                                            true
                                        } else false
                                    }

                                    else -> false
                                }
                            }
                            .clickable(
                                interactionSource = restartInteractionSource,
                                indication = null,
                                onClick = onRestart
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Restart from Beginning",
                            tint = if (isRestartFocused) FocusRingOrange else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
