package com.mystream.app.ui.screens

import androidx.compose.foundation.background
import com.mystream.app.ui.utils.safeRequestFocus
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
import com.mystream.app.ui.theme.FocusRingOrangeGlow
import com.mystream.app.ui.theme.GlassBorder
import com.mystream.app.ui.theme.GlassSurface
import com.mystream.app.ui.theme.ImdbGold
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SecondaryCyan
import com.mystream.app.ui.theme.SurfaceCard
import com.mystream.app.ui.theme.SurfaceDark
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
    val streamRefreshButtonFocusRequester = remember { FocusRequester() }
    val firstStreamFocusRequester = remember { FocusRequester() }
    val firstTorrentFocusRequester = remember { FocusRequester() }
    val firstSeasonTabFocusRequester = remember { FocusRequester() }
    val firstEpisodeFocusRequester = remember { FocusRequester() }

    val tab0InteractionSource = remember { MutableInteractionSource() }
    val isTab0Focused by tab0InteractionSource.collectIsFocusedAsState()

    val tab1InteractionSource = remember { MutableInteractionSource() }
    val isTab1Focused by tab1InteractionSource.collectIsFocusedAsState()

    val streamRefreshInteractionSource = remember { MutableInteractionSource() }
    val isStreamRefreshFocused by streamRefreshInteractionSource.collectIsFocusedAsState()

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
    val episodeLazyListState = rememberLazyListState()
    val backButtonFocusRequester = remember { FocusRequester() }
    val watchlistButtonFocusRequester = remember { FocusRequester() }

    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedSeasonIndex) {
        episodeLazyListState.scrollToItem(0)
    }

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

    var streamLoadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var torrentsLoadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun loadStreams(queryId: String, forceRefresh: Boolean = false) {
        streamLoadJob?.cancel()
        streamLoadJob = scope.launch {
            if (forceRefresh || streams.isEmpty()) {
                isStreamsLoading = true
                isResolvingMoreStreams = true
                if (forceRefresh) streams = emptyList()
            }
            try {
                repository.streamStreamsForMedia(type, queryId, forceRefresh = forceRefresh).collect { newStreams ->
                    streams = newStreams
                    isStreamsLoading = false
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                isStreamsLoading = false
                isResolvingMoreStreams = false
            }
        }
    }

    fun loadAllTorrents(queryId: String) {
        torrentsLoadJob?.cancel()
        torrentsLoadJob = scope.launch {
            isTorrentsLoading = true
            allTorrents = emptyList()
            try {
                allTorrents = repository.fetchAllTorrentsForMedia(type, queryId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                if (streams.isEmpty()) {
                    loadStreams(id, forceRefresh = false)
                }
            } else {
                // Do not auto-fetch on initial load for series; wait for user to click an episode
                if (selectedEpisode == null) {
                    streams = emptyList()
                    allTorrents = emptyList()
                }
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
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionRight -> {
                                            watchlistButtonFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            if (isSeries && seasons.isNotEmpty()) {
                                                firstSeasonTabFocusRequester.safeRequestFocus()
                                            } else if (isSeries && currentSeasonEpisodes.isNotEmpty()) {
                                                firstEpisodeFocusRequester.safeRequestFocus()
                                            } else if (selectedStreamTab == 1) {
                                                allTorrentsTabFocusRequester.safeRequestFocus()
                                            } else {
                                                availableTabFocusRequester.safeRequestFocus()
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                }
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
                        val watchlistInteraction = remember { MutableInteractionSource() }
                        val isWatchlistFocused by watchlistInteraction.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(top = 18.dp, end = 16.dp)
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isWatchlistFocused) FocusRingOrange.copy(alpha = 0.2f) else Color(0x66000000))
                                .border(
                                    if (isWatchlistFocused) 2.5.dp else 1.dp,
                                    if (isWatchlistFocused) FocusRingOrange else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .focusRequester(watchlistButtonFocusRequester)
                                .focusable(interactionSource = watchlistInteraction)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            backButtonFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            if (isSeries && seasons.isNotEmpty()) {
                                                firstSeasonTabFocusRequester.safeRequestFocus()
                                            } else if (isSeries && currentSeasonEpisodes.isNotEmpty()) {
                                                firstEpisodeFocusRequester.safeRequestFocus()
                                            } else if (selectedStreamTab == 1) {
                                                allTorrentsTabFocusRequester.safeRequestFocus()
                                            } else {
                                                availableTabFocusRequester.safeRequestFocus()
                                            }
                                            true
                                        }
                                        Key.DirectionCenter,
                                        Key.Enter,
                                        Key.NumPadEnter,
                                        Key.Spacebar -> {
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
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .clickable(interactionSource = watchlistInteraction, indication = null) {
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
                                }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isMediaInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Watchlist",
                                tint = if (isWatchlistFocused) FocusRingOrange else if (isMediaInWatchlist) SecondaryCyan else Color.White
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
                                    val seasonTabInteraction = remember { MutableInteractionSource() }
                                    val isSeasonTabFocused by seasonTabInteraction.collectIsFocusedAsState()

                                    Tab(
                                        selected = selectedSeasonIndex == index,
                                        interactionSource = seasonTabInteraction,
                                        modifier = Modifier
                                            .then(if (index == 0) Modifier.focusRequester(firstSeasonTabFocusRequester) else Modifier)
                                            .focusable(interactionSource = seasonTabInteraction)
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                                when (keyEvent.key) {
                                                    Key.DirectionUp -> {
                                                        backButtonFocusRequester.safeRequestFocus()
                                                        true
                                                    }
                                                    Key.DirectionDown -> {
                                                        scope.launch {
                                                            if (episodeLazyListState.firstVisibleItemIndex > 0) {
                                                                episodeLazyListState.scrollToItem(0)
                                                                kotlinx.coroutines.delay(50)
                                                            }
                                                            firstEpisodeFocusRequester.safeRequestFocus()
                                                        }
                                                        true
                                                    }
                                                    Key.DirectionCenter,
                                                    Key.Enter,
                                                    Key.NumPadEnter,
                                                    Key.Spacebar -> {
                                                        if (selectedSeasonIndex != index) {
                                                            selectedSeasonIndex = index
                                                            selectedEpisode = null
                                                            streams = emptyList()
                                                            allTorrents = emptyList()
                                                        }
                                                        scope.launch {
                                                            episodeLazyListState.scrollToItem(0)
                                                            kotlinx.coroutines.delay(50)
                                                            firstEpisodeFocusRequester.safeRequestFocus()
                                                        }
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            },
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
                                                fontWeight = if (isSeasonTabFocused || selectedSeasonIndex == index) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSeasonTabFocused) FocusRingOrange else if (selectedSeasonIndex == index) PrimaryNeon else TextSecondary
                                            )
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Horizontal Episodes List
                            androidx.compose.foundation.lazy.LazyRow(
                                state = episodeLazyListState,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(currentSeasonEpisodes) { index, episode ->
                                    val isSelected = selectedEpisode?.id == episode.id
                                    EpisodeHorizontalCardItem(
                                        episode = episode,
                                        isSelected = isSelected,
                                        modifier = if (index == 0) Modifier.focusRequester(firstEpisodeFocusRequester) else Modifier,
                                        onUp = {
                                            firstSeasonTabFocusRequester.safeRequestFocus()
                                        },
                                        onDown = {
                                            if (selectedStreamTab == 1) {
                                                allTorrentsTabFocusRequester.safeRequestFocus()
                                            } else {
                                                availableTabFocusRequester.safeRequestFocus()
                                            }
                                        },
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
                                text = "Streams",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Global Refresh Button
                        val isStreamRefreshFocused by streamRefreshInteractionSource.collectIsFocusedAsState()
                        Box(
                            modifier = Modifier
                                .focusRequester(streamRefreshButtonFocusRequester)
                                .focusable(interactionSource = streamRefreshInteractionSource)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            if (selectedStreamTab == 1) {
                                                allTorrentsTabFocusRequester.safeRequestFocus()
                                            } else {
                                                availableTabFocusRequester.safeRequestFocus()
                                            }
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            if (selectedStreamTab == 0 && streams.isNotEmpty()) {
                                                firstStreamFocusRequester.safeRequestFocus()
                                            } else if (selectedStreamTab == 1 && allTorrents.isNotEmpty()) {
                                                firstTorrentFocusRequester.safeRequestFocus()
                                            } else {
                                                availableTabFocusRequester.safeRequestFocus()
                                            }
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (isSeries && currentSeasonEpisodes.isNotEmpty()) {
                                                firstEpisodeFocusRequester.safeRequestFocus()
                                            } else if (isSeries && seasons.isNotEmpty()) {
                                                firstSeasonTabFocusRequester.safeRequestFocus()
                                            } else {
                                                watchlistButtonFocusRequester.safeRequestFocus()
                                            }
                                            true
                                        }
                                        Key.DirectionCenter,
                                        Key.Enter,
                                        Key.NumPadEnter,
                                        Key.Spacebar -> {
                                            val queryId = if (isSeries) selectedEpisode?.id ?: id else id
                                            loadStreams(queryId, forceRefresh = true)
                                            loadAllTorrents(queryId)
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isStreamRefreshFocused) FocusRingOrange.copy(alpha = 0.35f) else Color(0x1FFFFFFF))
                                .border(
                                    if (isStreamRefreshFocused) 2.dp else 1.dp,
                                    if (isStreamRefreshFocused) FocusRingOrange else GlassBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(interactionSource = streamRefreshInteractionSource, indication = null) {
                                    val queryId = if (isSeries) selectedEpisode?.id ?: id else id
                                    loadStreams(queryId, forceRefresh = true)
                                    loadAllTorrents(queryId)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Streams",
                                    tint = if (isStreamRefreshFocused) FocusRingOrange else if (isResolvingMoreStreams || isStreamsLoading || isTorrentsLoading) PrimaryNeon else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Refresh",
                                    color = if (isStreamRefreshFocused) FocusRingOrange else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Stream Tabs (Matches Series Season Tabs exactly)
                    ScrollableTabRow(
                        selectedTabIndex = selectedStreamTab,
                        containerColor = Color.Transparent,
                        contentColor = PrimaryNeon,
                        edgePadding = 20.dp,
                        indicator = { tabPositions ->
                            if (selectedStreamTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedStreamTab]),
                                    color = if (selectedStreamTab == 1) SecondaryCyan else PrimaryNeon
                                )
                            }
                        }
                    ) {
                        // Tab 0: Available Fast Streams
                        Tab(
                            selected = selectedStreamTab == 0,
                            interactionSource = tab0InteractionSource,
                            modifier = Modifier
                                .focusRequester(availableTabFocusRequester)
                                .focusable(interactionSource = tab0InteractionSource)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionRight -> {
                                            allTorrentsTabFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionLeft -> true
                                        Key.DirectionDown -> {
                                            if (streams.isNotEmpty()) {
                                                firstStreamFocusRequester.safeRequestFocus()
                                            } else if (allTorrents.isNotEmpty()) {
                                                selectedStreamTab = 1
                                                firstTorrentFocusRequester.safeRequestFocus()
                                            }
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            streamRefreshButtonFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionCenter,
                                        Key.Enter,
                                        Key.NumPadEnter,
                                        Key.Spacebar -> {
                                            selectedStreamTab = 0
                                            if (streams.isNotEmpty()) {
                                                firstStreamFocusRequester.safeRequestFocus()
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                },
                            onClick = { selectedStreamTab = 0 },
                            text = {
                                Text(
                                    text = if (streams.isNotEmpty()) "Available Streams (${streams.size})" else "Available Streams",
                                    fontWeight = if (isTab0Focused || selectedStreamTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isTab0Focused) FocusRingOrange else if (selectedStreamTab == 0) PrimaryNeon else TextSecondary
                                )
                            }
                        )

                        // Tab 1: All Torrents
                        Tab(
                            selected = selectedStreamTab == 1,
                            interactionSource = tab1InteractionSource,
                            modifier = Modifier
                                .focusRequester(allTorrentsTabFocusRequester)
                                .focusable(interactionSource = tab1InteractionSource)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            availableTabFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            streamRefreshButtonFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            if (allTorrents.isNotEmpty()) {
                                                firstTorrentFocusRequester.safeRequestFocus()
                                            } else if (streams.isNotEmpty()) {
                                                selectedStreamTab = 0
                                                firstStreamFocusRequester.safeRequestFocus()
                                            }
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            streamRefreshButtonFocusRequester.safeRequestFocus()
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
                                            } else {
                                                firstTorrentFocusRequester.safeRequestFocus()
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                },
                            onClick = {
                                selectedStreamTab = 1
                                if (allTorrents.isEmpty()) {
                                    val queryId = if (isSeries) selectedEpisode?.id else id
                                    queryId?.let { loadAllTorrents(it) }
                                }
                            },
                            text = {
                                Text(
                                    text = if (allTorrents.isNotEmpty()) "All Torrents (${allTorrents.size})" else "All Torrents",
                                    fontWeight = if (isTab1Focused || selectedStreamTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isTab1Focused) FocusRingOrange else if (selectedStreamTab == 1) SecondaryCyan else TextSecondary
                                )
                            }
                        )
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
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(color = PrimaryNeon, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                                    Text(
                                        text = "Loading streams...",
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
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
                                            } else {
                                                val err = res.exceptionOrNull()?.message ?: "Stream resolution failed"
                                                android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
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
                                    externalFocusRequester = if (index == 0) firstStreamFocusRequester else null,
                                    onUp = if (index == 0) {
                                        { availableTabFocusRequester.safeRequestFocus() }
                                    } else null,
                                    onClick = { launchStream(restartFromBeginning = false) },
                                    onRestart = { launchStream(restartFromBeginning = true) }
                                )
                            }
                        }

                        if (isResolvingMoreStreams || isStreamsLoading) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp, horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = PrimaryNeon,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Finding and checking more high-speed streams...",
                                        color = PrimaryNeon,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
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
                    } else if (isTorrentsLoading && allTorrents.isEmpty()) {
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

                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                                StreamCard(
                                    stream = torr,
                                    isResolving = isResolvingThis,
                                    externalFocusRequester = if (index == 0) firstTorrentFocusRequester else null,
                                    onUp = if (index == 0) {
                                        { allTorrentsTabFocusRequester.safeRequestFocus() }
                                    } else null,
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
                                    actionButtonText = "☁ Direct"
                                )
                            }
                        }

                        if (isTorrentsLoading) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp, horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = SecondaryCyan,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Loading more candidate torrents...",
                                        color = SecondaryCyan,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
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

            // Cloud Stream Resolving / Recovering Dialog
            if (resolvingStreamKey != null) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = {
                        resolvingStreamKey = null
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceDark)
                            .border(1.5.dp, PrimaryNeon, RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = PrimaryNeon,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "Preparing Stream...",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Getting playback ready. Please wait...",
                                color = TextMuted,
                                fontSize = 12.5.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
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
    modifier: Modifier = Modifier,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "epScale"
    )

    val bgColor = if (isFocused) FocusRingOrange.copy(alpha = 0.22f) else if (isSelected) PrimaryNeon.copy(alpha = 0.22f) else GlassSurface
    val borderColor = if (isFocused) FocusRingOrange else if (isSelected) PrimaryNeon else GlassBorder

    Column(
        modifier = modifier
            .width(160.dp)
            .scale(scale)
            .shadow(
                elevation = if (isFocused) 12.dp else 4.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = if (isFocused) FocusRingOrangeGlow else Color.Black,
                spotColor = if (isFocused) FocusRingOrange else Color.Black
            )
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (isFocused) 2.5.dp else if (isSelected) 1.8.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionUp -> {
                        onUp?.invoke()
                        true
                    }
                    Key.DirectionDown -> {
                        onDown?.invoke()
                        true
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
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(9.dp))
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
                    .padding(5.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isFocused) FocusRingOrange else if (isSelected) PrimaryNeon else Color(0xD907090E))
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "EP ${episode.episode}",
                    color = if (isFocused || isSelected) Color.Black else Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isFocused) FocusRingOrange else PrimaryNeon)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "▶ READY",
                        color = if (isFocused) Color.Black else Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        val epTitle = episode.name?.takeIf { it.isNotBlank() } ?: "Episode ${episode.episode}"
        Text(
            text = epTitle,
            color = if (isFocused) FocusRingOrange else if (isSelected) PrimaryNeon else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
