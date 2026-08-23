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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
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
import com.mystream.app.ui.theme.FocusRingOrange
import com.mystream.app.ui.theme.ImdbGold
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SecondaryCyan
import com.mystream.app.ui.theme.SurfaceCard
import com.mystream.app.ui.theme.SurfaceDark
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

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
    val watchlist by repository.watchlistFlow.collectAsState(initial = emptyList())
    var isDetailLoading by remember { mutableStateOf(true) }
    var isStreamsLoading by remember { mutableStateOf(false) }
    var isTorrentsLoading by remember { mutableStateOf(false) }
    var isResolvingMoreStreams by remember { mutableStateOf(false) }
    var resolvingStreamKey by remember { mutableStateOf<String?>(null) }

    val availableTabFocusRequester = remember { FocusRequester() }
    val allTorrentsTabFocusRequester = remember { FocusRequester() }
    val firstStreamFocusRequester = remember { FocusRequester() }
    val firstTorrentFocusRequester = remember { FocusRequester() }

    val tab0InteractionSource = remember { MutableInteractionSource() }
    val isTab0Focused by tab0InteractionSource.collectIsFocusedAsState()

    val tab1InteractionSource = remember { MutableInteractionSource() }
    val isTab1Focused by tab1InteractionSource.collectIsFocusedAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val torrentEngine = remember { com.mystream.app.torrent.TorrentStreamEngine.getInstance(context) }
    val torrentStatus by torrentEngine.statusFlow.collectAsState()

    var isP2PBufferingDialogVisible by remember { mutableStateOf(false) }
    var p2pStreamingTorrent by remember { mutableStateOf<StremioStreamSource?>(null) }
    var p2pRestartFromBeginning by remember { mutableStateOf(false) }

    var selectedStreamTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Available Streams, 1 = All Torrents
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    var selectedEpisode by remember { mutableStateOf<StremioVideoEpisode?>(null) }

    val listState = rememberLazyListState()
    val backButtonFocusRequester = remember { FocusRequester() }

    val scope = rememberCoroutineScope()

    LaunchedEffect(metaDetail, isDetailLoading) {
        if (!isDetailLoading && metaDetail != null) {
            kotlinx.coroutines.delay(100)
            try {
                backButtonFocusRequester.requestFocus()
                listState.scrollToItem(0, 0)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun loadStreams(queryId: String, forceRefresh: Boolean = false) {
        scope.launch {
            isStreamsLoading = true
            isResolvingMoreStreams = true
            streams = emptyList()
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
            allTorrents = emptyList()
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
                // Do not auto-fetch on initial load for series; wait for user to click an episode
                selectedEpisode = null
                streams = emptyList()
                allTorrents = emptyList()
            }
        } catch (e: Exception) {
            // handle error
        } finally {
            isDetailLoading = false
        }
    }

    var lastLoadedTorrentsQueryId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedStreamTab, selectedEpisode?.id, metaDetail?.id) {
        val detail = metaDetail ?: return@LaunchedEffect
        if (selectedStreamTab != 1) return@LaunchedEffect

        val queryId = if (detail.type.equals("series", ignoreCase = true)) {
            selectedEpisode?.id
        } else {
            detail.id
        }

        if (queryId != null && (queryId != lastLoadedTorrentsQueryId || allTorrents.isEmpty())) {
            lastLoadedTorrentsQueryId = queryId
            loadAllTorrents(queryId)
        }
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
                detail.videos.map { it.season }.distinct().sortedWith(compareBy { if (it == 0) Int.MAX_VALUE else it })
            } else emptyList()

            val currentSeasonEpisodes = if (isSeries && seasons.isNotEmpty()) {
                val currentSeason = seasons.getOrElse(selectedSeasonIndex) { 1 }
                detail.videos.filter { it.season == currentSeason }.sortedBy { it.episode }
            } else emptyList()

            LazyColumn(
                state = listState,
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
                        val backInteraction = remember { MutableInteractionSource() }
                        val isBackFocused by backInteraction.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(top = 18.dp, start = 16.dp)
                                .align(Alignment.TopStart)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isBackFocused) FocusRingOrange.copy(alpha = 0.2f) else Color(0x66000000))
                                .border(
                                    if (isBackFocused) 2.5.dp else 1.dp,
                                    if (isBackFocused) FocusRingOrange else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .focusRequester(backButtonFocusRequester)
                                .focusable(interactionSource = backInteraction)
                                .clickable(interactionSource = backInteraction, indication = null, onClick = onBack)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isBackFocused) FocusRingOrange else Color.White
                            )
                        }

                        // Top Watchlist Button
                        val isMediaInWatchlist = metaDetail?.id?.let { mId -> watchlist.any { it.imdbId == mId } } ?: false
                        IconButton(
                            onClick = {
                                metaDetail?.let { dt ->
                                    scope.launch {
                                        if (isMediaInWatchlist) {
                                            repository.removeFromWatchlist(dt.id)
                                            android.widget.Toast.makeText(context, "Removed from Watchlist", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            val watchItem = com.mystream.app.data.model.WatchlistItem(
                                                id = dt.id,
                                                imdbId = dt.id,
                                                title = dt.name,
                                                subtitle = dt.genres.firstOrNull() ?: dt.year,
                                                posterUrl = dt.poster,
                                                backdropUrl = dt.background,
                                                type = dt.type,
                                                dateAddedMs = System.currentTimeMillis()
                                            )
                                            repository.addToWatchlist(watchItem)
                                            android.widget.Toast.makeText(context, "Added to Watchlist!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(top = 18.dp, end = 16.dp)
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x66000000))
                        ) {
                            Icon(
                                imageVector = if (isMediaInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Watchlist",
                                tint = if (isMediaInWatchlist) SecondaryCyan else Color.White
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

                // Series Season Tabs & Episodes Carousel
                if (isSeries && seasons.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            // Season Selector Tabs
                            ScrollableTabRow(
                                selectedTabIndex = selectedSeasonIndex,
                                containerColor = Color.Transparent,
                                contentColor = PrimaryNeon,
                                edgePadding = 20.dp,
                                indicator = { tabPositions ->
                                    if (selectedSeasonIndex < tabPositions.size) {
                                        TabRowDefaults.SecondaryIndicator(
                                            Modifier.tabIndicatorOffset(tabPositions[selectedSeasonIndex]),
                                            color = PrimaryNeon
                                        )
                                    }
                                }
                            ) {
                                seasons.forEachIndexed { index, seasonNum ->
                                    Tab(
                                        selected = selectedSeasonIndex == index,
                                        onClick = {
                                            if (selectedSeasonIndex != index) {
                                                selectedSeasonIndex = index
                                                selectedEpisode = null
                                                streams = emptyList()
                                                allTorrents = emptyList()
                                            }
                                        },
                                        text = {
                                            Text(
                                                text = if (seasonNum == 0) "Season 0 (Specials)" else "Season $seasonNum",
                                                fontWeight = if (selectedSeasonIndex == index) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedSeasonIndex == index) PrimaryNeon else TextSecondary
                                            )
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Horizontal Episodes List
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(currentSeasonEpisodes) { episode ->
                                    val isSelected = selectedEpisode?.id == episode.id
                                    EpisodeHorizontalCardItem(
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
                            }

                            // Selected Episode Info Header Card
                            selectedEpisode?.let { ep ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SurfaceDark.copy(alpha = 0.6f))
                                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                                        .padding(12.dp)
                                ) {
                                    val epTitle = ep.name?.takeIf { it.isNotBlank() } ?: "Episode ${ep.episode}"
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(PrimaryNeon.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "S${ep.season} E${ep.episode}",
                                                color = PrimaryNeon,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = epTitle,
                                            color = TextPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (!ep.overview.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = ep.overview,
                                            color = TextMuted,
                                            fontSize = 11.5.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Stream Section Tabs (Available Streams vs All Torrents)
                item {
                    val streamHeaderTitle = if (isSeries) {
                        if (selectedEpisode != null) {
                            "Streams for S${selectedEpisode?.season}E${selectedEpisode?.episode}"
                        } else {
                            "Episode Streams"
                        }
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
                                .focusRequester(availableTabFocusRequester)
                                .focusable(interactionSource = tab0InteractionSource)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionRight -> {
                                            allTorrentsTabFocusRequester.requestFocus()
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            firstStreamFocusRequester.requestFocus()
                                            true
                                        }
                                        Key.DirectionCenter,
                                        Key.Enter,
                                        Key.NumPadEnter,
                                        Key.Spacebar -> {
                                            selectedStreamTab = 0
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isTab0Focused) FocusRingOrange.copy(alpha = 0.35f) else if (isTab0Selected) PrimaryNeon.copy(alpha = 0.2f) else SurfaceCard)
                                .border(
                                    if (isTab0Focused) 2.5.dp else 1.5.dp,
                                    if (isTab0Focused) FocusRingOrange else if (isTab0Selected) PrimaryNeon else Color(0x22FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(interactionSource = tab0InteractionSource, indication = null) { selectedStreamTab = 0 }
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
                                    tint = if (isTab0Focused) FocusRingOrange else if (isTab0Selected) PrimaryNeon else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (streams.isNotEmpty()) "Available (${streams.size})" else "Available",
                                    color = if (isTab0Focused) FocusRingOrange else if (isTab0Selected) Color.White else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isTab0Focused || isTab0Selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }

                        // Tab 1: All Torrents / Magnets (Advanced)
                        val isTab1Selected = selectedStreamTab == 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(allTorrentsTabFocusRequester)
                                .focusable(interactionSource = tab1InteractionSource)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            availableTabFocusRequester.requestFocus()
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            firstTorrentFocusRequester.requestFocus()
                                            true
                                        }
                                        Key.DirectionCenter,
                                        Key.Enter,
                                        Key.NumPadEnter,
                                        Key.Spacebar -> {
                                            selectedStreamTab = 1
                                            if (allTorrents.isEmpty()) {
                                                val queryId = if (isSeries) selectedEpisode?.id else id
                                                queryId?.let { loadAllTorrents(it) }
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isTab1Focused) FocusRingOrange.copy(alpha = 0.35f) else if (isTab1Selected) SecondaryCyan.copy(alpha = 0.2f) else SurfaceCard)
                                .border(
                                    if (isTab1Focused) 2.5.dp else 1.5.dp,
                                    if (isTab1Focused) FocusRingOrange else if (isTab1Selected) SecondaryCyan else Color(0x22FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(interactionSource = tab1InteractionSource, indication = null) {
                                    selectedStreamTab = 1
                                    if (allTorrents.isEmpty()) {
                                        val queryId = if (isSeries) selectedEpisode?.id else id
                                        queryId?.let { loadAllTorrents(it) }
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
                                    tint = if (isTab1Focused) FocusRingOrange else if (isTab1Selected) SecondaryCyan else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (allTorrents.isNotEmpty()) "All Torrents (${allTorrents.size})" else "All Torrents (⚡)",
                                    color = if (isTab1Focused) FocusRingOrange else if (isTab1Selected) Color.White else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isTab1Focused || isTab1Selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // TAB 0: Available Resolved Streams List (Default)
                if (selectedStreamTab == 0) {
                    if (isSeries && selectedEpisode == null) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Text(
                                        text = "Select an episode above to fetch streams",
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else if (isStreamsLoading) {
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
                        itemsIndexed(streams) { index, stream ->
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

                            val isSavedInWatchlist = watchlist.any { it.id == streamKey || (stream.infoHash != null && it.infoHash == stream.infoHash) }
                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                                StreamCard(
                                    stream = stream,
                                    isResolving = isResolvingThis,
                                    externalFocusRequester = if (index == 0) firstStreamFocusRequester else null,
                                    onClick = { launchStream(restartFromBeginning = false) },
                                    onRestart = { launchStream(restartFromBeginning = true) },
                                    onWatchlistToggle = {
                                        scope.launch {
                                            if (isSavedInWatchlist) {
                                                repository.removeFromWatchlist(streamKey)
                                                stream.infoHash?.let { repository.removeFromWatchlist(it) }
                                                android.widget.Toast.makeText(context, "Removed from Watchlist", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                val watchItem = com.mystream.app.data.model.WatchlistItem(
                                                    id = streamKey,
                                                    imdbId = detail.id,
                                                    title = if (isSeries && selectedEpisode != null) "${detail.name} (S${selectedEpisode?.season}E${selectedEpisode?.episode})" else detail.name,
                                                    subtitle = "${stream.quality} • ${stream.providerName ?: "Stream"}",
                                                    posterUrl = selectedEpisode?.thumbnail ?: detail.poster,
                                                    backdropUrl = detail.background,
                                                    type = detail.type,
                                                    seasonNumber = selectedEpisode?.season ?: 0,
                                                    episodeNumber = selectedEpisode?.episode ?: 0,
                                                    infoHash = stream.infoHash,
                                                    torrentTitle = stream.title ?: stream.name,
                                                    torrentQuality = stream.quality
                                                )
                                                repository.addToWatchlist(watchItem)
                                                android.widget.Toast.makeText(context, "Added to Watchlist!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    isSavedToWatchlist = isSavedInWatchlist
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
                    if (isSeries && selectedEpisode == null) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddLink,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Text(
                                        text = "Select an episode above to fetch torrents",
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else if (isTorrentsLoading) {
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
                        itemsIndexed(allTorrents) { index, torr ->
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
                                        } else {
                                            val err = res.exceptionOrNull()?.message ?: "Torrent not instant-cached on PikPak. Tap card for P2P."
                                            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    } finally {
                                        resolvingStreamKey = null
                                    }
                                }
                            }

                            val isUnder6Gb = torr.fileSizeMb <= 6000.0
                            val isSavedInWatchlist = watchlist.any { it.id == streamKey || (torr.infoHash != null && it.infoHash == torr.infoHash) }

                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                                StreamCard(
                                    stream = torr,
                                    isResolving = isResolvingThis,
                                    externalFocusRequester = if (index == 0) firstTorrentFocusRequester else null,
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
                                    onMagnetStream = if (isUnder6Gb) {
                                        { resolveAndPlay(restartFromBeginning = false) }
                                    } else null,
                                    onWatchlistToggle = {
                                        scope.launch {
                                            if (isSavedInWatchlist) {
                                                repository.removeFromWatchlist(streamKey)
                                                torr.infoHash?.let { repository.removeFromWatchlist(it) }
                                                android.widget.Toast.makeText(context, "Removed from Watchlist", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                val watchItem = com.mystream.app.data.model.WatchlistItem(
                                                    id = streamKey,
                                                    imdbId = detail.id,
                                                    title = if (isSeries && selectedEpisode != null) "${detail.name} (S${selectedEpisode?.season}E${selectedEpisode?.episode})" else detail.name,
                                                    subtitle = "${torr.quality} • ${torr.fileSize ?: ""}".trim().removeSuffix("•").trim(),
                                                    posterUrl = selectedEpisode?.thumbnail ?: detail.poster,
                                                    backdropUrl = detail.background,
                                                    type = detail.type,
                                                    seasonNumber = selectedEpisode?.season ?: 0,
                                                    episodeNumber = selectedEpisode?.episode ?: 0,
                                                    infoHash = torr.infoHash,
                                                    torrentTitle = torr.title ?: torr.name,
                                                    torrentQuality = torr.quality
                                                )
                                                repository.addToWatchlist(watchItem)
                                                android.widget.Toast.makeText(context, "Added to Watchlist!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    isSavedToWatchlist = isSavedInWatchlist,
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
private fun EpisodeHorizontalCardItem(
    episode: StremioVideoEpisode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgColor = if (isFocused) FocusRingOrange.copy(alpha = 0.2f) else if (isSelected) PrimaryNeon.copy(alpha = 0.2f) else SurfaceCard
    val borderColor = if (isFocused) FocusRingOrange else if (isSelected) PrimaryNeon else Color(0x22FFFFFF)

    Column(
        modifier = Modifier
            .width(145.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(if (isFocused) 2.5.dp else if (isSelected) 1.8.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(7.dp))
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

            // Episode Number Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isFocused) FocusRingOrange else if (isSelected) PrimaryNeon else Color(0xCC000000))
                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
            ) {
                Text(
                    text = "EP ${episode.episode}",
                    color = if (isFocused || isSelected) Color.Black else Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isFocused) FocusRingOrange else PrimaryNeon.copy(alpha = 0.85f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "▶ READY",
                        color = Color.Black,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val epTitle = episode.name?.takeIf { it.isNotBlank() } ?: "Episode ${episode.episode}"
        Text(
            text = epTitle,
            color = if (isFocused) FocusRingOrange else if (isSelected) PrimaryNeon else TextPrimary,
            fontSize = 11.5.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
