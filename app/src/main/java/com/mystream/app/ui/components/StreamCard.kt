package com.mystream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
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
    onPikPakStream: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardFocusRequester = externalFocusRequester ?: remember { FocusRequester() }
    val pikpakFocusRequester = remember { FocusRequester() }
    val restartFocusRequester = remember { FocusRequester() }

    val cardInteractionSource = remember { MutableInteractionSource() }
    val restartInteractionSource = remember { MutableInteractionSource() }
    val pikpakInteractionSource = remember { MutableInteractionSource() }

    val isCardFocused by cardInteractionSource.collectIsFocusedAsState()
    val isRestartFocused by restartInteractionSource.collectIsFocusedAsState()
    val isPikPakFocused by pikpakInteractionSource.collectIsFocusedAsState()

    val isAnyFocused = isCardFocused || isPikPakFocused || isRestartFocused
    val borderColor = if (isAnyFocused) FocusRingOrange else GlassBorder
    // Semi-transparent styling so the trailer shines through
    val bgColor = if (isAnyFocused) Color(0xAA151C2C) else Color(0x660B0E17)

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
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(if (isAnyFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
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
                            if (onPikPakStream != null && !isResolving) {
                                pikpakFocusRequester.safeRequestFocus()
                                true
                            } else if (onRestart != null && !isResolving) {
                                restartFocusRequester.safeRequestFocus()
                                true
                            } else if (onRight != null) {
                                onRight()
                                true
                            } else true
                        }

                        Key.DirectionLeft -> {
                            if (onLeft != null) {
                                onLeft()
                                true
                            } else false
                        }

                        Key.DirectionUp -> {
                            if (onUp != null) {
                                onUp()
                                true
                            } else false
                        }

                        Key.DirectionDown -> {
                            if (onDown != null) {
                                onDown()
                                true
                            } else false
                        }

                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.Spacebar -> {
                            onClick()
                            true
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
                horizontalArrangement = Arrangement.spacedBy(5.dp)
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
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = stream.quality,
                        color = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                // HDR / Dolby Vision Badge if available
                stream.hdrType?.let { hdr ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SecondaryCyan.copy(alpha = 0.2f))
                            .border(0.5.dp, SecondaryCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = hdr,
                            color = SecondaryCyan,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Hindi Audio Badge if available
                if (stream.hasHindiAudio) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE65100).copy(alpha = 0.35f))
                            .border(0.5.dp, Color(0xFFFF9800), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "🇮🇳",
                            fontSize = 9.5.sp
                        )
                    }
                }

                // Provider Name (e.g. PP, HF)
                stream.providerName?.let { provider ->
                    val isHf = provider.equals("HF", ignoreCase = true) || provider.contains("HuggingFace", ignoreCase = true)
                    val isPp = provider.equals("PP", ignoreCase = true) || provider.contains("PikPak", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isHf) Color(0x33FFD54F) else Color(0x2AFFFFFF))
                            .border(0.5.dp, if (isHf) Color(0xFFFFD54F) else GlassBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = if (isHf) "⚡ HF DIRECT" else if (isPp) "PP" else provider,
                            color = if (isHf) Color(0xFFFFE082) else EmeraldNeon,
                            fontSize = 8.5.sp,
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
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0D47A1).copy(alpha = 0.65f))
                            .border(0.5.dp, Color(0xFF42A5F5), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "❄️ ARC",
                            color = Color(0xFF90CAF9),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Line 1: Torrent / Release Name
            Text(
                text = torrentName,
                color = if (isCardFocused) FocusRingOrange else TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = if (shouldMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (shouldMarquee) Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE) else Modifier.fillMaxWidth()
            )

            // Line 2: Subtitle metadata details (Audio, File Size, Seeders)
            val detailItems = mutableListOf<String>()
            stream.audioDetails?.let { detailItems.add(it) }
            stream.fileSize?.let { detailItems.add("💾 $it") }
            stream.seeders?.let { detailItems.add("👤 $it") }

            if (detailItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = detailItems.joinToString("  •  "),
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 2. Action Buttons (Stacked VERTICALLY on right side of card)
        if (isResolving || onPikPakStream != null || onRestart != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Direct PikPak Stream button: "PP" + Cloud icon
                if (onPikPakStream != null && !isResolving) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isPikPakFocused) FocusRingOrange.copy(alpha = 0.35f) else AccentAmber.copy(alpha = 0.18f))
                            .border(
                                if (isPikPakFocused) 2.dp else 1.dp,
                                if (isPikPakFocused) FocusRingOrange else AccentAmber.copy(alpha = 0.5f),
                                RoundedCornerShape(6.dp)
                            )
                            .focusRequester(pikpakFocusRequester)
                            .focusable(interactionSource = pikpakInteractionSource)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (keyEvent.key) {
                                    Key.DirectionCenter,
                                    Key.Enter,
                                    Key.NumPadEnter,
                                    Key.Spacebar -> {
                                        onPikPakStream()
                                        true
                                    }

                                    Key.DirectionLeft -> {
                                        cardFocusRequester.safeRequestFocus()
                                        true
                                    }

                                    Key.DirectionRight -> {
                                        if (onRight != null) {
                                            onRight()
                                            true
                                        } else false
                                    }

                                    Key.DirectionUp -> {
                                        if (onUp != null) {
                                            onUp()
                                            true
                                        } else false
                                    }

                                    Key.DirectionDown -> {
                                        if (onRestart != null) {
                                            restartFocusRequester.safeRequestFocus()
                                            true
                                        } else if (onDown != null) {
                                            onDown()
                                            true
                                        } else false
                                    }

                                    else -> false
                                }
                            }
                            .clickable(
                                interactionSource = pikpakInteractionSource,
                                indication = null,
                                onClick = onPikPakStream
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "PP",
                                color = if (isPikPakFocused) FocusRingOrange else AccentAmber,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = "PikPak Direct Stream",
                                tint = if (isPikPakFocused) FocusRingOrange else AccentAmber,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }

                if (isResolving) {
                    CircularProgressIndicator(
                        color = FocusRingOrange,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else if (onRestart != null) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (isRestartFocused) FocusRingOrange.copy(alpha = 0.25f) else SurfaceDark.copy(alpha = 0.6f))
                            .border(
                                if (isRestartFocused) 2.dp else 1.dp,
                                if (isRestartFocused) FocusRingOrange else GlassBorderStrong,
                                CircleShape
                            )
                            .focusRequester(restartFocusRequester)
                            .focusable(interactionSource = restartInteractionSource)
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
                                        cardFocusRequester.safeRequestFocus()
                                        true
                                    }

                                    Key.DirectionRight -> {
                                        if (onRight != null) {
                                            onRight()
                                            true
                                        } else false
                                    }

                                    Key.DirectionUp -> {
                                        if (onPikPakStream != null && !isResolving) {
                                            pikpakFocusRequester.safeRequestFocus()
                                        } else if (onUp != null) {
                                            onUp()
                                        } else {
                                            cardFocusRequester.safeRequestFocus()
                                        }
                                        true
                                    }

                                    Key.DirectionDown -> {
                                        if (onDown != null) {
                                            onDown()
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
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
