package com.mystream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mystream.app.data.model.MediaPlaybackItem
import com.mystream.app.data.model.StremioMetaDetail
import com.mystream.app.data.model.StremioStreamSource
import com.mystream.app.data.model.StremioVideoEpisode
import com.mystream.app.data.repository.SourcesRepository
import com.mystream.app.ui.components.StreamCard
import com.mystream.app.ui.theme.BgDark
import com.mystream.app.ui.theme.ImdbGold
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SecondaryCyan
import com.mystream.app.ui.theme.SurfaceCard
import com.mystream.app.ui.theme.SurfaceDark
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private fun formatTransferRate(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1_000_000L -> String.format("%.1f MB/s", bytesPerSec / 1_000_000.0)
        bytesPerSec >= 1_000L -> "${bytesPerSec / 1_000} KB/s"
        else -> "$bytesPerSec B/s"
    }
}

@Composable
fun DetailScreen(
    type: String,
    id: String,
    repository: SourcesRepository,
    onBack: () -> Unit,
    onPlay: (MediaPlaybackItem) -> Unit
) {
    var metaDetail by remember { mutableStateOf<StremioMetaDetail?>(null) }
    var streams by remember { mutableStateOf<List<StremioStreamSource>>(emptyList()) }
    var allTorrents by remember { mutableStateOf<List<StremioStreamSource>>(emptyList()) }
    var isDetailLoading by remember { mutableStateOf(true) }
    var isStreamsLoading by remember { mutableStateOf(false) }
    var isTorrentsLoading by remember { mutableStateOf(false) }
    var isResolvingMoreStreams by remember { mutableStateOf(false) }
    var resolvingStreamKey by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val torrentEngine = remember { com.mystream.app.torrent.TorrentStreamEngine.getInstance(context) }
    val torrentStatus by torrentEngine.statusFlow.collectAsState()

    var isP2PBufferingDialogVisible by remember { mutableStateOf(false) }
    var p2pStreamingTorrent by remember { mutableStateOf<StremioStreamSource?>(null) }
    var p2pRestartFromBeginning by remember { mutableStateOf(false) }

    var selectedStreamTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Available Streams, 1 = All Torrents
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    var selectedEpisode by remember { mutableStateOf<StremioVideoEpisode?>(null) }

    val scope = rememberCoroutineScope()

    fun loadStreams(queryId: String, forceRefresh: Boolean = false) {
        scope.launch {
            isStreamsLoading = streams.isEmpty() || forceRefresh
            isResolvingMoreStreams = true
            if (forceRefresh) {
                streams = emptyList()
            }
            try {
                repository.streamStreamsForMedia(type, queryId, forceRefresh = forceRefresh).collect { newStreams ->
                    streams = newStreams
                    isStreamsLoading = false
                }
            } catch (e: Exception) {
                // keep current
            } finally {
                isStreamsLoading = false
                isResolvingMoreStreams = false
            }
        }
    }

    fun loadAllTorrents(queryId: String) {
        scope.launch {
            isTorrentsLoading = true
            try {
                allTorrents = repository.fetchAllTorrentsForMedia(type, queryId)
            } catch (e: Exception) {
                // ignore
            } finally {
                isTorrentsLoading = false
            }
        }
    }

    LaunchedEffect(id) {
        isDetailLoading = true
        try {
            val detail = repository.fetchMetaDetail(type, id)
            metaDetail = detail

            if (!detail.type.equals("series", ignoreCase = true)) {
                loadStreams(id)
            } else {
                val firstEp = detail.videos.firstOrNull()
                selectedEpisode = firstEp
                firstEp?.id?.let { epId -> loadStreams(epId) }
            }
        } catch (e: Exception) {
            // handle error
        } finally {
            isDetailLoading = false
        }
    }

    LaunchedEffect(selectedStreamTab, selectedEpisode?.id, metaDetail?.id) {
        val detail = metaDetail ?: return@LaunchedEffect
        if (selectedStreamTab != 1 || isTorrentsLoading || allTorrents.isNotEmpty()) return@LaunchedEffect

        val queryId = if (detail.type.equals("series", ignoreCase = true)) {
            selectedEpisode?.id
        } else {
            detail.id
        }

        queryId?.let { loadAllTorrents(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        if (isDetailLoading || metaDetail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryNeon)
            }
        } else {
            val detail = metaDetail!!
            val isSeries = detail.type.equals("series", ignoreCase = true)
            val seasons = if (isSeries) {
                detail.videos.map { it.season }.distinct().sorted()
            } else emptyList()

            val currentSeasonEpisodes = if (isSeries && seasons.isNotEmpty()) {
                val currentSeason = seasons.getOrElse(selectedSeasonIndex) { 1 }
                detail.videos.filter { it.season == currentSeason }.sortedBy { it.episode }
            } else emptyList()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                // Header Backdrop Box
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        val backdrop = detail.background ?: detail.poster
                        if (!backdrop.isNullOrBlank()) {
                            AsyncImage(
                                model = backdrop,
                                contentDescription = detail.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Gradient fade
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            BgDark.copy(alpha = 0.5f),
                                            BgDark
                                        )
                                    )
                                )
                        )

                        // Top Back Button
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(top = 18.dp, start = 16.dp)
                                .align(Alignment.TopStart)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x66000000))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Title and Metadata Info
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = detail.name,
                            color = TextPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            detail.year?.let { y ->
                                Text(text = y, color = TextSecondary, fontSize = 13.sp)
                            }
                            detail.imdbRating?.let { rating ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Rating",
                                        tint = ImdbGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = rating,
                                        color = ImdbGold,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            detail.runtime?.let { r ->
                                Text(text = r, color = TextMuted, fontSize = 13.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceCard)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = detail.type.uppercase(),
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (detail.genres.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = detail.genres.joinToString(" • "),
                                color = SecondaryCyan,
                                fontSize = 12.sp
                            )
                        }

                        detail.description?.let { desc ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = desc,
                                color = TextMuted,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Series Season Tabs & Episodes
                if (isSeries && seasons.isNotEmpty()) {
                    item {
                        ScrollableTabRow(
                            selectedTabIndex = selectedSeasonIndex,
                            containerColor = Color.Transparent,
                            contentColor = PrimaryNeon,
                            edgePadding = 20.dp,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedSeasonIndex]),
                                    color = PrimaryNeon
                                )
                            }
                        ) {
                            seasons.forEachIndexed { index, seasonNum ->
                                Tab(
                                    selected = selectedSeasonIndex == index,
                                    onClick = { selectedSeasonIndex = index },
                                    text = {
                                        Text(
                                            text = "Season $seasonNum",
                                            fontWeight = if (selectedSeasonIndex == index) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedSeasonIndex == index) PrimaryNeon else TextSecondary
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Episodes Horizontal / Vertical List
                    items(currentSeasonEpisodes) { episode ->
                        val isSelected = selectedEpisode?.id == episode.id
                        EpisodeCardItem(
                            episode = episode,
                            isSelected = isSelected,
                            onClick = {
                                selectedEpisode = episode
                                loadStreams(episode.id)
                                if (selectedStreamTab == 1) {
                                    loadAllTorrents(episode.id)
                                }
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Stream Section Tabs (Available Streams vs All Torrents)
                item {
                    val streamHeaderTitle = if (isSeries && selectedEpisode != null) {
                        "Streams for S${selectedEpisode?.season}E${selectedEpisode?.episode}"
                    } else {
                        "Available Streams"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = SecondaryCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = streamHeaderTitle,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Loading badge in header when resolving additional streams in background
                            if (isResolvingMoreStreams && streams.isNotEmpty() && selectedStreamTab == 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PrimaryNeon.copy(alpha = 0.15f))
                                        .border(1.dp, PrimaryNeon.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = PrimaryNeon,
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 1.8.dp
                                        )
                                        Text(
                                            text = "Finding more...",
                                            color = PrimaryNeon,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                val queryId = if (isSeries) selectedEpisode?.id ?: id else id
                                if (selectedStreamTab == 0) {
                                    loadStreams(queryId, forceRefresh = true)
                                } else {
                                    loadAllTorrents(queryId)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Streams",
                                tint = if (isResolvingMoreStreams) PrimaryNeon else TextSecondary
                            )
                        }
                    }

                    // Tab Selector: Available Streams vs All Torrents
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tab 0: Available Fast Streams (Default)
                        val isTab0Selected = selectedStreamTab == 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isTab0Selected) PrimaryNeon.copy(alpha = 0.2f) else SurfaceCard)
                                .border(
                                    1.5.dp,
                                    if (isTab0Selected) PrimaryNeon else Color(0x22FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedStreamTab = 0 }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = if (isTab0Selected) PrimaryNeon else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (streams.isNotEmpty()) "Available (${streams.size})" else "Available",
                                    color = if (isTab0Selected) Color.White else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isTab0Selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }

                        // Tab 1: All Torrents / Magnets (Advanced)
                        val isTab1Selected = selectedStreamTab == 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isTab1Selected) SecondaryCyan.copy(alpha = 0.2f) else SurfaceCard)
                                .border(
                                    1.5.dp,
                                    if (isTab1Selected) SecondaryCyan else Color(0x22FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedStreamTab = 1
                                    if (allTorrents.isEmpty()) {
                                        val queryId = if (isSeries) selectedEpisode?.id ?: id else id
                                        loadAllTorrents(queryId)
                                    }
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddLink,
                                    contentDescription = null,
                                    tint = if (isTab1Selected) SecondaryCyan else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (allTorrents.isNotEmpty()) "All Torrents (${allTorrents.size})" else "All Torrents (⚡)",
                                    color = if (isTab1Selected) Color.White else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isTab1Selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // TAB 0: Available Resolved Streams List (Default)
                if (selectedStreamTab == 0) {
                    if (isStreamsLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(30.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = PrimaryNeon)
                            }
                        }
                    } else if (streams.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No cached streams found. Check 'All Torrents' tab to pick a specific release.",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(streams) { stream ->
                            val streamKey = stream.infoHash ?: stream.url ?: stream.name.orEmpty()
                            val isResolvingThis = resolvingStreamKey == streamKey
                            val currentQueryId = if (isSeries && selectedEpisode != null) {
                                "${detail.id}:${selectedEpisode?.season}:${selectedEpisode?.episode}"
                            } else {
                                detail.id
                            }

                            fun launchStream(restartFromBeginning: Boolean) {
                                if (isResolvingThis) return
                                scope.launch {
                                    val savedPos = if (restartFromBeginning) 0L else repository.getSavedPosition(currentQueryId)
                                    val existingUrl = stream.url
                                    if (!existingUrl.isNullOrBlank() && (existingUrl.startsWith("http://") || existingUrl.startsWith("https://"))) {
                                        val playbackItem = MediaPlaybackItem(
                                            id = detail.id,
                                            title = if (isSeries && selectedEpisode != null) {
                                                "${detail.name} - S${selectedEpisode?.season}E${selectedEpisode?.episode}: ${selectedEpisode?.name ?: ""}"
                                            } else {
                                                detail.name
                                            },
                                            subtitle = "${stream.quality} • ${stream.providerName ?: "Stream"}",
                                            mediaUrl = existingUrl,
                                            posterUrl = selectedEpisode?.thumbnail ?: detail.poster,
                                            backdropUrl = detail.background,
                                            isSeries = isSeries,
                                            seasonNumber = selectedEpisode?.season ?: 0,
                                            episodeNumber = selectedEpisode?.episode ?: 0,
                                            startPositionMs = savedPos,
                                            headers = stream.behaviorHints?.proxyHeaders
                                        )
                                        onPlay(playbackItem)
                                    } else {
                                        resolvingStreamKey = streamKey
                                        try {
                                            val res = repository.resolveSpecificStream(stream, currentQueryId)
                                            val resolvedUrl = res.getOrNull()
                                            if (!resolvedUrl.isNullOrBlank()) {
                                                val playbackItem = MediaPlaybackItem(
                                                    id = detail.id,
                                                    title = if (isSeries && selectedEpisode != null) {
                                                        "${detail.name} - S${selectedEpisode?.season}E${selectedEpisode?.episode}: ${selectedEpisode?.name ?: ""}"
                                                    } else {
                                                        detail.name
                                                    },
                                                    subtitle = "${stream.quality} • PikPak Cloud Direct",
                                                    mediaUrl = resolvedUrl,
                                                    posterUrl = selectedEpisode?.thumbnail ?: detail.poster,
                                                    backdropUrl = detail.background,
                                                    isSeries = isSeries,
                                                    seasonNumber = selectedEpisode?.season ?: 0,
                                                    episodeNumber = selectedEpisode?.episode ?: 0,
                                                    startPositionMs = savedPos,
                                                    headers = stream.behaviorHints?.proxyHeaders
                                                )
                                                onPlay(playbackItem)
                                            }
                                        } finally {
                                            resolvingStreamKey = null
                                        }
                                    }
                                }
                            }

                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                                StreamCard(
                                    stream = stream,
                                    isResolving = isResolvingThis,
                                    onClick = { launchStream(restartFromBeginning = false) },
                                    onRestart = { launchStream(restartFromBeginning = true) }
                                )
                            }
                        }

                        // Background stream resolution indicator at the bottom of the list
                        if (isResolvingMoreStreams && streams.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SurfaceDark.copy(alpha = 0.7f))
                                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            color = PrimaryNeon,
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Searching and resolving remaining stream qualities...",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TAB 1: All Candidate Torrents & Magnets (Advanced)
                    if (isTorrentsLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(30.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = SecondaryCyan)
                            }
                        }
                    } else if (allTorrents.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No candidate torrents found from Torrentio.",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(allTorrents) { torr ->
                            val streamKey = torr.infoHash ?: torr.url ?: torr.name.orEmpty()
                            val isResolvingThis = resolvingStreamKey == streamKey
                            val currentQueryId = if (isSeries && selectedEpisode != null) {
                                "${detail.id}:${selectedEpisode?.season}:${selectedEpisode?.episode}"
                            } else {
                                detail.id
                            }

                            fun resolveAndPlay(restartFromBeginning: Boolean) {
                                if (isResolvingThis) return
                                scope.launch {
                                    resolvingStreamKey = streamKey
                                    try {
                                        val res = repository.resolveAndSaveSingleTorrent(torr, detail.type, currentQueryId)
                                        val freshUrl = res.getOrNull()
                                        if (!freshUrl.isNullOrBlank()) {
                                            val savedPos = if (restartFromBeginning) 0L else repository.getSavedPosition(currentQueryId)
                                            val playbackItem = MediaPlaybackItem(
                                                id = detail.id,
                                                title = if (isSeries && selectedEpisode != null) {
                                                    "${detail.name} - S${selectedEpisode?.season}E${selectedEpisode?.episode}: ${selectedEpisode?.name ?: ""}"
                                                } else {
                                                    detail.name
                                                },
                                                subtitle = "${torr.quality} • PikPak Direct",
                                                mediaUrl = freshUrl,
                                                posterUrl = selectedEpisode?.thumbnail ?: detail.poster,
                                                backdropUrl = detail.background,
                                                isSeries = isSeries,
                                                seasonNumber = selectedEpisode?.season ?: 0,
                                                episodeNumber = selectedEpisode?.episode ?: 0,
                                                startPositionMs = savedPos,
                                                headers = torr.behaviorHints?.proxyHeaders
                                            )
                                            onPlay(playbackItem)
                                        }
                                    } finally {
                                        resolvingStreamKey = null
                                    }
                                }
                            }

                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                                StreamCard(
                                    stream = torr,
                                    isResolving = isResolvingThis,
                                    onClick = {
                                        torr.infoHash?.let { hash ->
                                            p2pRestartFromBeginning = false
                                            p2pStreamingTorrent = torr
                                            isP2PBufferingDialogVisible = true
                                            torrentEngine.startStreaming(hash, torr.title ?: torr.name ?: "Torrent Stream")
                                        }
                                    },
                                    onRestart = {
                                        torr.infoHash?.let { hash ->
                                            p2pRestartFromBeginning = true
                                            p2pStreamingTorrent = torr
                                            isP2PBufferingDialogVisible = true
                                            torrentEngine.startStreaming(hash, torr.title ?: torr.name ?: "Torrent Stream")
                                        }
                                    },
                                    onMagnetStream = {
                                        resolveAndPlay(restartFromBeginning = false)
                                    },
                                    actionButtonText = "☁ Direct"
                                )
                            }
                        }
                    }
                }
            }

            // P2P Streaming auto-launch when initial buffer is ready
            LaunchedEffect(torrentStatus.isReadyForStreaming, torrentStatus.streamUrl) {
                if (isP2PBufferingDialogVisible && torrentStatus.isReadyForStreaming && torrentStatus.streamUrl != null && p2pStreamingTorrent != null) {
                    val torr = p2pStreamingTorrent!!
                    val currentQueryId = if (isSeries && selectedEpisode != null) {
                        "${detail.id}:${selectedEpisode?.season}:${selectedEpisode?.episode}"
                    } else {
                        detail.id
                    }
                    val savedPos = if (p2pRestartFromBeginning) 0L else repository.getSavedPosition(currentQueryId)
                    p2pRestartFromBeginning = false
                    isP2PBufferingDialogVisible = false
                    val playbackItem = MediaPlaybackItem(
                        id = detail.id,
                        title = if (isSeries && selectedEpisode != null) {
                            "${detail.name} - S${selectedEpisode?.season}E${selectedEpisode?.episode}: ${selectedEpisode?.name ?: ""}"
                        } else {
                            detail.name
                        },
                        subtitle = "${torr.quality} • 🧲 P2P Direct Stream",
                        mediaUrl = torrentStatus.streamUrl!!,
                        posterUrl = selectedEpisode?.thumbnail ?: detail.poster,
                        backdropUrl = detail.background,
                        isSeries = isSeries,
                        seasonNumber = selectedEpisode?.season ?: 0,
                        episodeNumber = selectedEpisode?.episode ?: 0,
                        startPositionMs = savedPos,
                        headers = null
                    )
                    onPlay(playbackItem)
                }
            }

            // P2P Buffering HUD Dialog
            if (isP2PBufferingDialogVisible) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = {
                        isP2PBufferingDialogVisible = false
                        torrentEngine.stopStreaming()
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceDark)
                            .border(1.5.dp, com.mystream.app.ui.theme.AccentAmber, RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🧲 Direct P2P Torrent Streaming",
                                color = com.mystream.app.ui.theme.AccentAmber,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = p2pStreamingTorrent?.title?.lines()?.firstOrNull() ?: "Connecting...",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(
                                color = com.mystream.app.ui.theme.AccentAmber,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = torrentStatus.state,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "👤 Seeds: ${torrentStatus.seeds}",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "⚡ Speed: ${formatTransferRate(torrentStatus.downloadRateBytes)}",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    isP2PBufferingDialogVisible = false
                                    torrentEngine.stopStreaming()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCardItem(
    episode: StremioVideoEpisode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) PrimaryNeon.copy(alpha = 0.2f) else SurfaceCard
    val borderModifier = if (isSelected) {
        Modifier.background(PrimaryNeon.copy(alpha = 0.15f))
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
        ) {
            if (!episode.thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = episode.thumbnail,
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            val epTitle = episode.name?.takeIf { it.isNotBlank() } ?: "Episode ${episode.episode}"
            Text(
                text = "E${episode.episode}: $epTitle",
                color = if (isSelected) PrimaryNeon else TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!episode.overview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episode.overview,
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
