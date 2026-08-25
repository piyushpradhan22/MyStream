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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mystream.app.data.model.PlayerTrackInfo
import com.mystream.app.ui.theme.EmeraldNeon
import com.mystream.app.ui.theme.FocusRingOrange
import com.mystream.app.ui.theme.FocusRingOrangeGlow
import com.mystream.app.ui.theme.GlassBorder
import com.mystream.app.ui.theme.GlassBorderStrong
import com.mystream.app.ui.theme.GlassSurface
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SurfaceDark
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary

@Composable
fun AudioTrackSelectorDialog(
    tracks: List<PlayerTrackInfo>,
    onSelectTrack: (PlayerTrackInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(24.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black)
                .clip(RoundedCornerShape(18.dp))
                .background(GlassSurface)
                .border(1.5.dp, GlassBorderStrong, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryNeon.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = PrimaryNeon,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Select Audio Track",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (tracks.isEmpty()) {
                    Text(
                        text = "No additional audio tracks found for this stream.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(tracks) { track ->
                            val itemInteraction = remember { MutableInteractionSource() }
                            val isFocused by itemInteraction.collectIsFocusedAsState()

                            val itemBg = if (isFocused) FocusRingOrange.copy(alpha = 0.25f)
                            else if (track.isSelected) PrimaryNeon.copy(alpha = 0.2f)
                            else Color(0x1AFFFFFF)

                            val itemBorder = if (isFocused) FocusRingOrange
                            else if (track.isSelected) PrimaryNeon
                            else GlassBorder

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(itemBg)
                                    .border(if (isFocused) 2.dp else 1.dp, itemBorder, RoundedCornerShape(12.dp))
                                    .focusable(interactionSource = itemInteraction)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter || keyEvent.key == Key.Spacebar)) {
                                            onSelectTrack(track)
                                            onDismiss()
                                            true
                                        } else false
                                    }
                                    .clickable(interactionSource = itemInteraction, indication = null) {
                                        onSelectTrack(track)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.label,
                                        color = if (isFocused) FocusRingOrange else TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = if (track.isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                                    )
                                    val subParts = mutableListOf<String>()
                                    if (!track.language.isNullOrBlank()) subParts.add(track.language.uppercase())
                                    if (track.channels > 0) subParts.add("${track.channels}ch")
                                    if (!track.mimeType.isNullOrBlank()) subParts.add(track.mimeType.substringAfter("/"))
                                    if (subParts.isNotEmpty()) {
                                        Text(
                                            text = subParts.joinToString(" • "),
                                            color = TextMuted,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }

                                if (track.isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (isFocused) FocusRingOrange else EmeraldNeon,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
