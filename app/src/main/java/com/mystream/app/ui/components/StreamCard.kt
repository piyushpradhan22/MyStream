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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.mystream.app.ui.theme.EmeraldNeon
import com.mystream.app.ui.theme.FocusRingOrange
import com.mystream.app.ui.theme.FocusRingOrangeGlow
import com.mystream.app.ui.theme.GlassBorder
import com.mystream.app.ui.theme.GlassBorderStrong
import com.mystream.app.ui.theme.GlassSurface
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
    onExternalPlayer: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    actionButtonText: String = "⚡ Resolve",
    modifier: Modifier = Modifier
) {
    val cardFocusRequester = externalFocusRequester ?: remember { FocusRequester() }
    val magnetFocusRequester = remember { FocusRequester() }
    val restartFocusRequester = remember { FocusRequester() }
    val extPlayerFocusRequester = remember { FocusRequester() }

    val cardInteractionSource = remember { MutableInteractionSource() }
    val restartInteractionSource = remember { MutableInteractionSource() }
    val magnetInteractionSource = remember { MutableInteractionSource() }
    val extPlayerInteractionSource = remember { MutableInteractionSource() }

    val isCardFocused by cardInteractionSource.collectIsFocusedAsState()
    val isRestartFocused by restartInteractionSource.collectIsFocusedAsState()
    val isMagnetFocused by magnetInteractionSource.collectIsFocusedAsState()
    val isExtPlayerFocused by extPlayerInteractionSource.collectIsFocusedAsState()

    val isAnyFocused = isCardFocused || isMagnetFocused || isRestartFocused || isExtPlayerFocused
    val borderColor = if (isAnyFocused) FocusRingOrange else GlassBorder
    val bgColor = if (isAnyFocused) Color(0xFF151C2C) else GlassSurface

    val shouldMarquee = isAnyFocused

    // Extract Torrent Name and File Name properly from title lines
    val titleLines = stream.title?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    val torrentName = titleLines.firstOrNull()?.takeIf { !it.contains("💾") && !it.contains("👤") }
        ?: stream.name?.takeIf { it.isNotBlank() }
        ?: "Direct Stream"

    val fileName = stream.behaviorHints?.bingeGroup?.takeIf { it.isNotBlank() && it != torrentName }
        ?: titleLines.getOrNull(1)?.takeIf { !it.contains("💾") && !it.contains("👤") && it != torrentName }

    // Outer Card Container
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(if (isAnyFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Main Clickable Card Body
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
                            } else if (onExternalPlayer != null && !isResolving) {
                                extPlayerFocusRequester.safeRequestFocus()
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
            // Badges (Quality, HDR, Hindi, Provider, Cloud Status)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Quality Spec Pill
                val is4k = stream.quality.contains("4K", ignoreCase = true)
                val is1080 = stream.quality.contains("1080", ignoreCase = true)
                val badgeBg = when {
                    is4k -> EmeraldNeon
                    is1080 -> PrimaryNeon
                    else -> SecondaryCyan
                }
                val badgeText = when {
                    is4k -> Color.Black
                    else -> Color.White
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stream.quality,
                        color = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                // HDR / Dolby Vision Badge if available
                stream.hdrType?.let { hdr ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(SecondaryCyan.copy(alpha = 0.2f))
                            .border(0.5.dp, SecondaryCyan.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
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
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFE65100).copy(alpha = 0.35f))
                            .border(0.5.dp, Color(0xFFFF9800), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🇮🇳",
                            fontSize = 10.5.sp
                        )
                    }
                }

                // Provider Name (e.g. PP, HF)
                stream.providerName?.let { provider ->
                    val isHf = provider.equals("HF", ignoreCase = true) || provider.contains("HuggingFace", ignoreCase = true)
                    val isPp = provider.equals("PP", ignoreCase = true) || provider.contains("PikPak", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isHf) Color(0x33FFD54F) else Color(0x2AFFFFFF))
                            .border(0.5.dp, if (isHf) Color(0xFFFFD54F) else GlassBorder, RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isHf) "⚡ HF DIRECT" else if (isPp) "PP" else provider,
                            color = if (isHf) Color(0xFFFFE082) else EmeraldNeon,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Cloud Archive Tier Badge (ARC)
                val isArchive = stream.isArchive ||
                        stream.name?.contains("ARC", ignoreCase = true) == true ||
                        stream.quality.contains("ARC", ignoreCase = true) ||
                        stream.title?.contains("ARC", ignoreCase = true) == true
                if (isArchive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0D47A1).copy(alpha = 0.65f))
                            .border(0.5.dp, Color(0xFF42A5F5), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "❄️ ARC",
                            color = Color(0xFF90CAF9),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Line 1: Torrent / Release Name
            Text(
                text = torrentName,
                color = if (isCardFocused) FocusRingOrange else TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = if (shouldMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (shouldMarquee) Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE) else Modifier.fillMaxWidth()
            )

            // Line 2: File Name (if different)
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

        // 2. Action Buttons
        if (isResolving || onMagnetStream != null || onRestart != null || onExternalPlayer != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Direct P2P Magnet Stream button
                if (onMagnetStream != null && !isResolving) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMagnetFocused) AccentAmber.copy(alpha = 0.35f) else AccentAmber.copy(alpha = 0.18f))
                            .border(
                                if (isMagnetFocused) 2.dp else 1.dp,
                                if (isMagnetFocused) FocusRingOrange else AccentAmber.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
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
                                        } else if (onExternalPlayer != null) {
                                            extPlayerFocusRequester.safeRequestFocus()
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
                            .padding(horizontal = 11.dp, vertical = 7.dp),
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
                } else {
                    if (onRestart != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isRestartFocused) FocusRingOrange.copy(alpha = 0.25f) else SurfaceDark)
                                .border(
                                    if (isRestartFocused) 2.dp else 1.dp,
                                    if (isRestartFocused) FocusRingOrange else GlassBorderStrong,
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

                                        Key.DirectionRight -> {
                                            if (onExternalPlayer != null) {
                                                extPlayerFocusRequester.safeRequestFocus()
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

                    if (onExternalPlayer != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isExtPlayerFocused) FocusRingOrange.copy(alpha = 0.25f) else SurfaceDark)
                                .border(
                                    if (isExtPlayerFocused) 2.dp else 1.dp,
                                    if (isExtPlayerFocused) FocusRingOrange else GlassBorderStrong,
                                    CircleShape
                                )
                                .focusRequester(extPlayerFocusRequester)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionCenter,
                                        Key.Enter,
                                        Key.NumPadEnter,
                                        Key.Spacebar -> {
                                            onExternalPlayer()
                                            true
                                        }

                                        Key.DirectionLeft -> {
                                            if (onRestart != null) {
                                                restartFocusRequester.safeRequestFocus()
                                            } else if (onMagnetStream != null && !isResolving) {
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
                                    interactionSource = extPlayerInteractionSource,
                                    indication = null,
                                    onClick = onExternalPlayer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Play in External Player",
                                tint = if (isExtPlayerFocused) FocusRingOrange else com.mystream.app.ui.theme.SecondaryCyan,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
