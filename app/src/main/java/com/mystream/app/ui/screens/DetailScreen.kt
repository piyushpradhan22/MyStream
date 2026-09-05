package com.mystream.app.ui.screens

import android.content.Context
import android.widget.Toast
import com.mystream.app.data.repository.SourcesRepository
import kotlinx.coroutines.CoroutineScope
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.statusBarsPadding
import com.mystream.app.ui.utils.appTopBarPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.bitmapConfig
import com.mystream.app.data.model.MediaPlaybackItem
import com.mystream.app.data.model.StremioMetaDetail
import com.mystream.app.data.model.StremioStreamSource
import com.mystream.app.data.model.StremioVideoEpisode
import com.mystream.app.ui.components.TrailerPlaybackManager
import com.mystream.app.ui.components.StreamCard
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import com.mystream.app.ui.theme.BgDark
import com.mystream.app.ui.theme.FocusRing
import com.mystream.app.ui.theme.FocusRingOrange
import com.mystream.app.ui.theme.FocusRingOrangeGlow
import com.mystream.app.ui.theme.GlassBorder
import com.mystream.app.ui.theme.GlassSurface
import com.mystream.app.ui.theme.HotstarBg
import com.mystream.app.ui.theme.HotstarHeroBottomVignette
import com.mystream.app.ui.theme.HotstarHeroSideVignette
import com.mystream.app.ui.theme.ImdbGold
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SecondaryCyan
import com.mystream.app.ui.theme.SurfaceCard
import com.mystream.app.ui.theme.SurfaceDark
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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


@Composable
fun DetailScreen(
    type: String,
    id: String,
    repository: SourcesRepository,
    onBack: () -> Unit,
    onPlay: (MediaPlaybackItem) -> Unit
) {
    BackHandler { onBack() }

    var metaDetail by remember { mutableStateOf<StremioMetaDetail?>(null) }
    var streams by remember { mutableStateOf<List<StremioStreamSource>>(emptyList()) }
    var allTorrents by remember { mutableStateOf<List<StremioStreamSource>>(emptyList()) }
    val watchlist by repository.watchlistFlow.collectAsState(initial = emptyList())
    val continueWatchingList by repository.continueWatchingFlow.collectAsState(initial = emptyList())
    var isDetailLoading by remember { mutableStateOf(true) }
    var isStreamsLoading by remember { mutableStateOf(false) }
    var isTorrentsLoading by remember { mutableStateOf(false) }
    var isResolvingMoreStreams by remember { mutableStateOf(false) }
    var resolvingStreamKey by remember { mutableStateOf<String?>(null) }

    val availableTabFocusRequester = remember { FocusRequester() }
    val allTorrentsTabFocusRequester = remember { FocusRequester() }
    val streamRefreshButtonFocusRequester = remember { FocusRequester() }
    val firstSeasonTabFocusRequester = remember { FocusRequester() }
    val firstEpisodeFocusRequester = remember { FocusRequester() }

    val streamCardFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    fun getStreamCardFR(index: Int): FocusRequester =
        streamCardFocusRequesters.getOrPut(index) { FocusRequester() }

    val torrentCardFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    fun getTorrentCardFR(index: Int): FocusRequester =
        torrentCardFocusRequesters.getOrPut(index) { FocusRequester() }

    val tab0InteractionSource = remember { MutableInteractionSource() }
    val isTab0Focused by tab0InteractionSource.collectIsFocusedAsState()

    val tab1InteractionSource = remember { MutableInteractionSource() }
    val isTab1Focused by tab1InteractionSource.collectIsFocusedAsState()

    val streamRefreshInteractionSource = remember { MutableInteractionSource() }
    val isStreamRefreshFocused by streamRefreshInteractionSource.collectIsFocusedAsState()

    var lastFocusedStreamIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var lastFocusedTorrentIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val streamRowState = rememberLazyListState()
    val torrentRowState = rememberLazyListState()

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
    val removeContinueWatchingFocusRequester = remember { FocusRequester() }
    val watchlistButtonFocusRequester = remember { FocusRequester() }
    val trailerPlayPauseFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val cinemetaTrailerFocusRequester = remember { FocusRequester() }
    val customTrailerFocusRequester = remember { FocusRequester() }
    var customTrailerYtId by remember { mutableStateOf<String?>(null) }
    var isSearchingCustomTrailer by remember { mutableStateOf(false) }
    var playCustomTrailerWhenReady by remember { mutableStateOf(false) }
    var isDetailTrailerReady by remember { mutableStateOf(false) }
    var userSelectedTrailerYtId by remember { mutableStateOf<String?>(null) }

    val activeBackgroundTrailerYtId = userSelectedTrailerYtId
        ?: metaDetail?.effectiveTrailerYtId?.takeIf { it.isNotBlank() }
        ?: customTrailerYtId
        ?: TrailerPlaybackManager.activeTrailerYtId

    val currentQueryId = if (metaDetail?.type.equals("series", ignoreCase = true)) {
        selectedEpisode?.id ?: id
    } else {
        id
    }

    fun launchTorrentP2P(torrent: StremioStreamSource, fromBeginning: Boolean) {
        val hash = torrent.infoHash ?: torrent.url?.substringAfter("btih:")?.substringBefore("&")
        if (!hash.isNullOrBlank()) {
            p2pRestartFromBeginning = fromBeginning
            p2pStreamingTorrent = torrent
            isP2PBufferingDialogVisible = true
            torrentEngine.startStreaming(hash, torrent.title ?: torrent.name ?: "Torrent Stream")
        } else if (!torrent.url.isNullOrBlank() && torrent.url.startsWith("magnet:", ignoreCase = true)) {
            p2pRestartFromBeginning = fromBeginning
            p2pStreamingTorrent = torrent
            isP2PBufferingDialogVisible = true
            torrentEngine.startStreaming(torrent.url, torrent.title ?: torrent.name ?: "Torrent Stream")
        } else {
            Toast.makeText(context, "Invalid magnet link or hash", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(torrentStatus.isReadyForStreaming, torrentStatus.streamUrl, isP2PBufferingDialogVisible) {
        if (torrentStatus.isReadyForStreaming && isP2PBufferingDialogVisible && !torrentStatus.streamUrl.isNullOrBlank()) {
            isP2PBufferingDialogVisible = false
            val savedPos = if (p2pRestartFromBeginning) 0L else repository.getSavedPosition(currentQueryId)
            val dt = metaDetail
            val isSeriesType = dt?.type.equals("series", ignoreCase = true)
            val playbackItem = MediaPlaybackItem(
                id = dt?.id ?: id,
                title = if (isSeriesType && selectedEpisode != null) {
                    "${dt?.name ?: ""} - S${selectedEpisode?.season}E${selectedEpisode?.episode}: ${selectedEpisode?.name ?: ""}"
                } else {
                    dt?.name ?: "Stream"
                },
                subtitle = "${p2pStreamingTorrent?.quality ?: ""} • 🧲 P2P Direct Stream",
                mediaUrl = torrentStatus.streamUrl!!,
                posterUrl = selectedEpisode?.thumbnail ?: dt?.poster,
                backdropUrl = dt?.background,
                isSeries = isSeriesType,
                seasonNumber = selectedEpisode?.season ?: 0,
                episodeNumber = selectedEpisode?.episode ?: 0,
                startPositionMs = savedPos,
                headers = null
            )
            onPlay(playbackItem)
        }
    }

    val appSettings by repository.appSettingsFlow.collectAsState(initial = com.mystream.app.data.model.AppSettingsConfig())
    val isDetailTrailerPlaying = appSettings.trailerPlaybackEnabled && !TrailerPlaybackManager.isStopped

    LaunchedEffect(activeBackgroundTrailerYtId) {
        isDetailTrailerReady = false
        if (!activeBackgroundTrailerYtId.isNullOrBlank()) {
            kotlinx.coroutines.delay(600)
            isDetailTrailerReady = true
        }
    }

    LaunchedEffect(metaDetail?.id, appSettings.preferredAudioLanguage) {
        val dt = metaDetail ?: return@LaunchedEffect
        val lang = appSettings.preferredAudioLanguage.ifBlank { "Hindi" }
        isSearchingCustomTrailer = true
        val ytId = repository.searchYouTubeTrailer(dt.name, dt.year, lang)
        customTrailerYtId = ytId
        isSearchingCustomTrailer = false
        if (playCustomTrailerWhenReady && !ytId.isNullOrBlank()) {
            userSelectedTrailerYtId = ytId
            TrailerPlaybackManager.restartOrLoad(ytId, appSettings.trailerAudioMuted)
            isDetailTrailerReady = true
            playCustomTrailerWhenReady = false
        }
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedSeasonIndex) {
        episodeLazyListState.scrollToItem(0)
    }

    LaunchedEffect(metaDetail, isDetailLoading) {
        val detail = metaDetail
        if (!isDetailLoading && detail != null) {
            kotlinx.coroutines.delay(200)
            try {
                listState.scrollToItem(0, 0)
                backButtonFocusRequester.safeRequestFocus() // focuses the Watchlist button (Back button removed)
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
                if (forceRefresh) streams = emptyList()
            }
            isResolvingMoreStreams = true
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

    fun loadAllTorrents(queryId: String, forceRefresh: Boolean = false) {
        torrentsLoadJob?.cancel()
        torrentsLoadJob = scope.launch {
            isTorrentsLoading = true
            if (forceRefresh) allTorrents = emptyList()
            try {
                allTorrents = repository.fetchAllTorrentsForMedia(type, queryId, forceRefresh = forceRefresh)
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

    LaunchedEffect(activeBackgroundTrailerYtId, appSettings.trailerPlaybackEnabled, appSettings.trailerAudioMuted) {
        if (!activeBackgroundTrailerYtId.isNullOrBlank() && appSettings.trailerPlaybackEnabled) {
            TrailerPlaybackManager.play(activeBackgroundTrailerYtId, appSettings.trailerAudioMuted, forceReplay = false)
        }
    }

    val isVideoPlaying = TrailerPlaybackManager.isVideoPlaying && !TrailerPlaybackManager.isStopped
    val backdropAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVideoPlaying) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "DetailBackdropAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Fullscreen Fixed Backdrop Poster (Visible while video loads/buffers)
        val backdrop = metaDetail?.background ?: metaDetail?.poster
        if (!backdrop.isNullOrBlank()) {
            val backdropRequest = remember(backdrop) {
                coil3.request.ImageRequest.Builder(context)
                    .data(backdrop)
                    .size(1280, 720)
                    .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                    .build()
            }
            AsyncImage(
                model = backdropRequest,
                contentDescription = metaDetail?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(backdropAlpha)
            )
        }

        // Layered cinematic gradient overlay (JioHotstar cinema vignettes)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HotstarHeroSideVignette)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HotstarHeroBottomVignette)
        )

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

            // Top Information Section (Anchored to TopStart)
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .appTopBarPadding(additionalTop = 10.dp)
                    .padding(horizontal = 24.dp)
            ) {
                DetailTopInfoSection(
                    detail = detail,
                    isMediaInWatchlist = watchlist.any { it.imdbId == detail.id },
                    onToggleWatchlist = {
                        scope.launch {
                            val inWl = watchlist.any { it.imdbId == detail.id }
                            if (inWl) {
                                repository.removeFromWatchlist(detail.id)
                            } else {
                                repository.addToWatchlist(
                                    com.mystream.app.data.model.WatchlistItem(
                                        id = detail.id,
                                        imdbId = detail.id,
                                        title = detail.name,
                                        subtitle = detail.genres.firstOrNull() ?: detail.year ?: "",
                                        posterUrl = detail.poster,
                                        backdropUrl = detail.background,
                                        type = detail.type,
                                        dateAddedMs = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    },
                    hasPlaybackProgress = continueWatchingList.any { it.imdbId == detail.id || it.mediaId == detail.id },
                    onClearPlaybackProgress = {
                        scope.launch {
                            val rec = continueWatchingList.firstOrNull { it.imdbId == detail.id || it.mediaId == detail.id }
                            if (rec != null) {
                                repository.removePlaybackProgress(rec.mediaId)
                            }
                        }
                    },
                    removeContinueWatchingFocusRequester = removeContinueWatchingFocusRequester,
                    isDetailTrailerPlaying = isDetailTrailerPlaying,
                    onToggleTrailerPlayback = {
                        val nextPlaying = !(appSettings.trailerPlaybackEnabled && !TrailerPlaybackManager.isStopped)
                        TrailerPlaybackManager.setPlaybackEnabled(nextPlaying)
                        if (nextPlaying) {
                            TrailerPlaybackManager.resume()
                        } else {
                            TrailerPlaybackManager.stop()
                        }
                        scope.launch {
                            repository.updateAppSettings(appSettings.copy(trailerPlaybackEnabled = nextPlaying))
                        }
                    },
                    isAudioMuted = appSettings.trailerAudioMuted,
                    onToggleAudioMute = {
                        val newMuted = !appSettings.trailerAudioMuted
                        scope.launch {
                            repository.updateAppSettings(appSettings.copy(trailerAudioMuted = newMuted))
                        }
                    },
                    cinemetaTrailerYtId = detail.effectiveTrailerYtId,
                    customTrailerYtId = customTrailerYtId,
                    preferredLanguage = appSettings.preferredAudioLanguage,
                    onPlayTrailer = { ytId: String, _: String ->
                        userSelectedTrailerYtId = ytId
                        TrailerPlaybackManager.restartOrLoad(ytId, appSettings.trailerAudioMuted)
                        isDetailTrailerReady = true
                    },
                    onPlayCustomTrailerWhenReady = {
                        scope.launch {
                            isSearchingCustomTrailer = true
                            val ytId = if (!customTrailerYtId.isNullOrBlank() && customTrailerYtId != detail.effectiveTrailerYtId) {
                                customTrailerYtId
                            } else {
                                repository.searchYouTubeTrailer(detail.name, detail.year, "Hindi")
                            }
                            if (!ytId.isNullOrBlank()) {
                                customTrailerYtId = ytId
                                userSelectedTrailerYtId = ytId
                                TrailerPlaybackManager.restartOrLoad(ytId, appSettings.trailerAudioMuted)
                                isDetailTrailerReady = true
                            } else {
                                Toast.makeText(context, "Hindi trailer not found", Toast.LENGTH_SHORT).show()
                            }
                            isSearchingCustomTrailer = false
                        }
                    },
                    watchlistButtonFocusRequester = watchlistButtonFocusRequester,
                    trailerPlayPauseFocusRequester = trailerPlayPauseFocusRequester,
                    audioFocusRequester = audioFocusRequester,
                    cinemetaTrailerFocusRequester = cinemetaTrailerFocusRequester,
                    customTrailerFocusRequester = customTrailerFocusRequester,
                    onNavigateDown = {
                        if (isSeries && seasons.isNotEmpty()) firstSeasonTabFocusRequester.safeRequestFocus()
                        else if (isSeries && currentSeasonEpisodes.isNotEmpty()) firstEpisodeFocusRequester.safeRequestFocus()
                        else if (selectedStreamTab == 1) allTorrentsTabFocusRequester.safeRequestFocus()
                        else availableTabFocusRequester.safeRequestFocus()
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .wrapContentHeight()
                )
            }

            // PINNED BOTTOM SECTION: Season Tabs + Episodes (for Series) + Stream/Torrent Tabs + Single Horizontal LazyRow
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                // Series Season Tabs & Episodes Carousel (Anchored to bottom directly above streams)
                if (isSeries && seasons.isNotEmpty()) {
                    // Season Selector Tabs
                    ScrollableTabRow(
                        selectedTabIndex = selectedSeasonIndex,
                        containerColor = Color.Transparent,
                        contentColor = PrimaryNeon,
                        edgePadding = 4.dp,
                        modifier = Modifier.wrapContentWidth(),
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
                                                watchlistButtonFocusRequester.safeRequestFocus()
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

                    Spacer(modifier = Modifier.height(4.dp))

                    // Horizontal Episodes List
                    androidx.compose.foundation.lazy.LazyRow(
                        state = episodeLazyListState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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

                    Spacer(modifier = Modifier.height(6.dp))
                }

                // TABS & REFRESH ROW (Positioned directly above single row cards)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tab 0: Streams
                    val isTab0Selected = selectedStreamTab == 0
                    Box(
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
                                            val targetIdx = lastFocusedStreamIndex.coerceIn(0, streams.size - 1)
                                            scope.launch {
                                                streamRowState.scrollToItem(targetIdx)
                                                getStreamCardFR(targetIdx).safeRequestFocus()
                                            }
                                        } else if (allTorrents.isNotEmpty()) {
                                            selectedStreamTab = 1
                                            val targetIdx = lastFocusedTorrentIndex.coerceIn(0, allTorrents.size - 1)
                                            scope.launch {
                                                torrentRowState.scrollToItem(targetIdx)
                                                getTorrentCardFR(targetIdx).safeRequestFocus()
                                            }
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
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                        selectedStreamTab = 0
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isTab0Focused) FocusRing
                                else if (isTab0Selected) PrimaryNeon.copy(alpha = 0.22f)
                                else SurfaceCard
                            )
                            .border(
                                if (isTab0Focused) 2.dp else 1.dp,
                                if (isTab0Focused) FocusRing else if (isTab0Selected) PrimaryNeon else GlassBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(interactionSource = tab0InteractionSource, indication = null) {
                                selectedStreamTab = 0
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Streams (${streams.size})",
                                fontSize = 12.5.sp,
                                fontWeight = if (isTab0Focused || isTab0Selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isTab0Focused) Color.Black else if (isTab0Selected) PrimaryNeon else TextSecondary
                            )
                            if (isResolvingMoreStreams) {
                                Spacer(modifier = Modifier.width(6.dp))
                                CircularProgressIndicator(
                                    color = if (isTab0Focused) Color.Black else PrimaryNeon,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    // Tab 1: Torrents
                    val isTab1Selected = selectedStreamTab == 1
                    Box(
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
                                            val targetIdx = lastFocusedTorrentIndex.coerceIn(0, allTorrents.size - 1)
                                            scope.launch {
                                                torrentRowState.scrollToItem(targetIdx)
                                                getTorrentCardFR(targetIdx).safeRequestFocus()
                                            }
                                        } else if (streams.isNotEmpty()) {
                                            selectedStreamTab = 0
                                            val targetIdx = lastFocusedStreamIndex.coerceIn(0, streams.size - 1)
                                            scope.launch {
                                                streamRowState.scrollToItem(targetIdx)
                                                getStreamCardFR(targetIdx).safeRequestFocus()
                                            }
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
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                        selectedStreamTab = 1
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isTab1Focused) FocusRing
                                else if (isTab1Selected) SecondaryCyan.copy(alpha = 0.22f)
                                else SurfaceCard
                            )
                            .border(
                                if (isTab1Focused) 2.dp else 1.dp,
                                if (isTab1Focused) FocusRing else if (isTab1Selected) SecondaryCyan else GlassBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(interactionSource = tab1InteractionSource, indication = null) {
                                selectedStreamTab = 1
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Torrents (${allTorrents.size})",
                                fontSize = 12.5.sp,
                                fontWeight = if (isTab1Focused || isTab1Selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isTab1Focused) Color.Black else if (isTab1Selected) SecondaryCyan else TextSecondary
                            )
                            if (isTorrentsLoading) {
                                Spacer(modifier = Modifier.width(6.dp))
                                CircularProgressIndicator(
                                    color = if (isTab1Focused) Color.Black else SecondaryCyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    // 3. Refresh button placed directly after "Torrents"
                    Box(
                        modifier = Modifier
                            .focusRequester(streamRefreshButtonFocusRequester)
                            .focusable(interactionSource = streamRefreshInteractionSource)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> {
                                        allTorrentsTabFocusRequester.safeRequestFocus()
                                        true
                                    }
                                    Key.DirectionRight -> true
                                    Key.DirectionDown -> {
                                        if (selectedStreamTab == 0 && streams.isNotEmpty()) {
                                            val targetIdx = lastFocusedStreamIndex.coerceIn(0, streams.size - 1)
                                            scope.launch {
                                                streamRowState.scrollToItem(targetIdx)
                                                getStreamCardFR(targetIdx).safeRequestFocus()
                                            }
                                        } else if (selectedStreamTab == 1 && allTorrents.isNotEmpty()) {
                                            val targetIdx = lastFocusedTorrentIndex.coerceIn(0, allTorrents.size - 1)
                                            scope.launch {
                                                torrentRowState.scrollToItem(targetIdx)
                                                getTorrentCardFR(targetIdx).safeRequestFocus()
                                            }
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
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                        val queryId = if (isSeries) selectedEpisode?.id ?: id else id
                                        loadStreams(queryId, forceRefresh = true)
                                        loadAllTorrents(queryId, forceRefresh = true)
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isStreamRefreshFocused) FocusRing else SurfaceCard)
                            .border(
                                if (isStreamRefreshFocused) 2.dp else 1.dp,
                                if (isStreamRefreshFocused) FocusRing else GlassBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(interactionSource = streamRefreshInteractionSource, indication = null) {
                                val queryId = if (isSeries) selectedEpisode?.id ?: id else id
                                loadStreams(queryId, forceRefresh = true)
                                loadAllTorrents(queryId, forceRefresh = true)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Streams",
                                tint = if (isStreamRefreshFocused) Color.Black else PrimaryNeon,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Refresh",
                                color = if (isStreamRefreshFocused) Color.Black else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                    // Single Row Stream/Torrent Cards Container with Right-Edge Indicator
                    val currentQueryId = if (isSeries && selectedEpisode != null) {
                        "${detail.id}:${selectedEpisode?.season}:${selectedEpisode?.episode}"
                    } else {
                        detail.id
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(105.dp)
                    ) {
                        if (selectedStreamTab == 0) {
                            if (isSeries && selectedEpisode == null) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Select an episode above to fetch streams",
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            } else if (isStreamsLoading && streams.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(color = PrimaryNeon, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Text("Loading streams...", color = TextSecondary, fontSize = 12.sp)
                                    }
                                }
                            } else if (streams.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No cached streams found. Check 'Torrents' tab to pick a specific release.",
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                androidx.compose.foundation.lazy.LazyRow(
                                    state = streamRowState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    itemsIndexed(
                                        items = streams,
                                        key = { idx, s -> "s_${s.infoHash ?: s.url ?: idx}" }
                                    ) { index, stream ->
                                        val streamKey = stream.infoHash ?: stream.url ?: stream.name.orEmpty()
                                        val isResolving = resolvingStreamKey == streamKey
                                        val isMagnet = stream.url?.startsWith("magnet:", ignoreCase = true) == true || (stream.url.isNullOrBlank() && !stream.infoHash.isNullOrBlank())
                                        val isUnder58Gb = stream.fileSizeMb <= (5.8 * 1024.0)

                                        Box(modifier = Modifier.width(310.dp)) {
                                            StreamCard(
                                                stream = stream,
                                                isResolving = isResolving,
                                                externalFocusRequester = getStreamCardFR(index),
                                                onUp = {
                                                    lastFocusedStreamIndex = index
                                                    availableTabFocusRequester.safeRequestFocus()
                                                },
                                                onDown = null,
                                                onLeft = if (index > 0) { { getStreamCardFR(index - 1).safeRequestFocus() } } else null,
                                                onRight = if (index < streams.lastIndex) { { getStreamCardFR(index + 1).safeRequestFocus() } } else null,
                                                onClick = {
                                                    if (isMagnet) {
                                                        launchTorrentP2P(stream, fromBeginning = false)
                                                    } else if (!isResolving) {
                                                        launchResolvedStreamHelper(
                                                            context = context,
                                                            scope = scope,
                                                            repository = repository,
                                                            stream = stream,
                                                            detail = detail,
                                                            isSeries = isSeries,
                                                            selectedEpisode = selectedEpisode,
                                                            currentQueryId = currentQueryId,
                                                            streamKey = streamKey,
                                                            restartFromBeginning = false,
                                                            inExternalPlayer = false,
                                                            onStartResolving = { resolvingStreamKey = it },
                                                            onEndResolving = { resolvingStreamKey = null },
                                                            onPlay = onPlay
                                                        )
                                                    }
                                                },
                                                onRestart = {
                                                    if (isMagnet) {
                                                        launchTorrentP2P(stream, fromBeginning = true)
                                                    } else if (!isResolving) {
                                                        launchResolvedStreamHelper(
                                                            context = context,
                                                            scope = scope,
                                                            repository = repository,
                                                            stream = stream,
                                                            detail = detail,
                                                            isSeries = isSeries,
                                                            selectedEpisode = selectedEpisode,
                                                            currentQueryId = currentQueryId,
                                                            streamKey = streamKey,
                                                            restartFromBeginning = true,
                                                            inExternalPlayer = false,
                                                            onStartResolving = { resolvingStreamKey = it },
                                                            onEndResolving = { resolvingStreamKey = null },
                                                            onPlay = onPlay
                                                        )
                                                    }
                                                },
                                                onPikPakStream = if (isMagnet && isUnder58Gb) {
                                                    {
                                                        if (!isResolving) {
                                                            resolveAndPlayTorrentHelper(
                                                                context = context,
                                                                scope = scope,
                                                                repository = repository,
                                                                torr = stream,
                                                                displayTorr = stream,
                                                                matchingCached = null,
                                                                detail = detail,
                                                                isSeries = isSeries,
                                                                selectedEpisode = selectedEpisode,
                                                                currentQueryId = currentQueryId,
                                                                streamKey = streamKey,
                                                                restartFromBeginning = false,
                                                                inExternalPlayer = false,
                                                                onRefreshStreams = { loadStreams(currentQueryId) },
                                                                onStartResolving = { resolvingStreamKey = it },
                                                                onEndResolving = { resolvingStreamKey = null },
                                                                onPlay = onPlay
                                                            )
                                                        }
                                                    }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // TAB 1: Torrents
                            if (isSeries && selectedEpisode == null) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Select an episode above to fetch torrents",
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            } else if (isTorrentsLoading && allTorrents.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(color = SecondaryCyan, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Text("Scraping torrent sources...", color = SecondaryCyan, fontSize = 12.sp)
                                    }
                                }
                            } else if (allTorrents.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No torrents found for this title.",
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                androidx.compose.foundation.lazy.LazyRow(
                                    state = torrentRowState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    itemsIndexed(
                                        items = allTorrents,
                                        key = { idx, t -> "t_${t.infoHash ?: t.url ?: idx}" }
                                    ) { index, torrent ->
                                        val torrStreamKey = torrent.infoHash ?: torrent.url ?: torrent.name.orEmpty()
                                        val isTorrResolving = resolvingStreamKey == torrStreamKey
                                        val matchingCached = streams.firstOrNull { it.infoHash != null && it.infoHash.equals(torrent.infoHash, ignoreCase = true) }
                                        val displayTorr = if (matchingCached?.isArchive == true && !torrent.isArchive) {
                                            torrent.copy(name = (torrent.name ?: "") + " ARC")
                                        } else {
                                            torrent
                                        }

                                        Box(modifier = Modifier.width(310.dp)) {
                                            val isUnder58Gb = torrent.fileSizeMb <= (5.8 * 1024.0)
                                            StreamCard(
                                                stream = displayTorr,
                                                isResolving = isTorrResolving,
                                                externalFocusRequester = getTorrentCardFR(index),
                                                onUp = {
                                                    lastFocusedTorrentIndex = index
                                                    allTorrentsTabFocusRequester.safeRequestFocus()
                                                },
                                                onDown = null,
                                                onLeft = if (index > 0) { { getTorrentCardFR(index - 1).safeRequestFocus() } } else null,
                                                onRight = if (index < allTorrents.lastIndex) { { getTorrentCardFR(index + 1).safeRequestFocus() } } else null,
                                                onClick = {
                                                    launchTorrentP2P(torrent, fromBeginning = false)
                                                },
                                                onRestart = {
                                                    launchTorrentP2P(torrent, fromBeginning = true)
                                                },
                                                onPikPakStream = if (isUnder58Gb) {
                                                    {
                                                        if (!isTorrResolving) {
                                                            resolveAndPlayTorrentHelper(
                                                                context = context,
                                                                scope = scope,
                                                                repository = repository,
                                                                torr = torrent,
                                                                displayTorr = displayTorr,
                                                                matchingCached = matchingCached,
                                                                detail = detail,
                                                                isSeries = isSeries,
                                                                selectedEpisode = selectedEpisode,
                                                                currentQueryId = currentQueryId,
                                                                streamKey = torrStreamKey,
                                                                restartFromBeginning = false,
                                                                inExternalPlayer = false,
                                                                onRefreshStreams = { loadStreams(currentQueryId) },
                                                                onStartResolving = { resolvingStreamKey = it },
                                                                onEndResolving = { resolvingStreamKey = null },
                                                                onPlay = onPlay
                                                            )
                                                        }
                                                    }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Right-edge Indicator showing more cards exist
                        val canScrollMore = if (selectedStreamTab == 0) streamRowState.canScrollForward else torrentRowState.canScrollForward
                        if (canScrollMore) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(44.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color.Transparent, HotstarBg.copy(alpha = 0.85f), HotstarBg)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xCC07090E))
                                        .border(1.dp, FocusRing.copy(alpha = 0.7f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "›",
                                        color = FocusRing,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isP2PBufferingDialogVisible) {
            BackHandler(enabled = true) {
                isP2PBufferingDialogVisible = false
                torrentEngine.stopStreaming()
            }

            val cancelP2PFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(100)
                cancelP2PFocusRequester.safeRequestFocus()
            }

            androidx.compose.ui.window.Dialog(
                onDismissRequest = {
                    isP2PBufferingDialogVisible = false
                    torrentEngine.stopStreaming()
                }
            ) {
                val speedMb = torrentStatus.downloadRateBytes / (1024.0 * 1024.0)
                val speedText = if (speedMb >= 0.1) String.format(java.util.Locale.US, "%.1f MB/s", speedMb) else "${torrentStatus.downloadRateBytes / 1024} KB/s"

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceCard.copy(alpha = 0.95f))
                        .border(1.5.dp, FocusRing.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 28.dp, vertical = 24.dp)
                        .width(360.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = PrimaryNeon,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "P2P Direct Streaming",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = torrentStatus.state,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { torrentStatus.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = PrimaryNeon,
                            trackColor = Color(0x33FFFFFF)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Peers: ${torrentStatus.peers} | Seeds: ${torrentStatus.seeds}",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = speedText,
                                color = FocusRing,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        androidx.compose.material3.Button(
                            onClick = {
                                isP2PBufferingDialogVisible = false
                                torrentEngine.stopStreaming()
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0x33FFFFFF),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .focusRequester(cancelP2PFocusRequester)
                                .padding(top = 4.dp)
                        ) {
                            Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
        targetValue = if (isFocused) 1.04f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "epScale"
    )

    val bgColor = if (isFocused) FocusRing.copy(alpha = 0.15f) else if (isSelected) PrimaryNeon.copy(alpha = 0.15f) else SurfaceCard
    val borderColor = if (isFocused) FocusRing else if (isSelected) PrimaryNeon else GlassBorder

    Column(
        modifier = modifier
            .width(160.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (isFocused) 2.dp else if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
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
                val context = androidx.compose.ui.platform.LocalContext.current
                val epImageRequest = remember(episode.thumbnail) {
                    coil3.request.ImageRequest.Builder(context)
                        .data(episode.thumbnail)
                        .size(coil3.size.Size(320, 180))
                        .build()
                }
                AsyncImage(
                    model = epImageRequest,
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
                    .background(if (isFocused) FocusRing else if (isSelected) PrimaryNeon else Color(0xD907090E))
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
                        .background(if (isFocused) FocusRing else PrimaryNeon)
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
            color = if (isFocused) FocusRing else if (isSelected) PrimaryNeon else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailTopInfoSection(
    detail: StremioMetaDetail,
    isMediaInWatchlist: Boolean,
    onToggleWatchlist: () -> Unit,
    hasPlaybackProgress: Boolean = false,
    onClearPlaybackProgress: () -> Unit = {},
    removeContinueWatchingFocusRequester: FocusRequester,
    isDetailTrailerPlaying: Boolean,
    onToggleTrailerPlayback: () -> Unit,
    isAudioMuted: Boolean,
    onToggleAudioMute: () -> Unit,
    cinemetaTrailerYtId: String?,
    customTrailerYtId: String?,
    preferredLanguage: String,
    onPlayTrailer: (ytId: String, title: String) -> Unit,
    onPlayCustomTrailerWhenReady: () -> Unit,
    watchlistButtonFocusRequester: FocusRequester,
    trailerPlayPauseFocusRequester: FocusRequester,
    audioFocusRequester: FocusRequester,
    cinemetaTrailerFocusRequester: FocusRequester,
    customTrailerFocusRequester: FocusRequester,
    onNavigateDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefLang = preferredLanguage.ifBlank { "Hindi" }
    val (flagEmoji, langTrailerLabel) = when (prefLang.lowercase()) {
        "hindi" -> Pair("🇮🇳", "Hindi Trailer")
        "tamil" -> Pair("🇮🇳", "Tamil Trailer")
        "telugu" -> Pair("🇮🇳", "Telugu Trailer")
        "malayalam" -> Pair("🇮🇳", "Malayalam Trailer")
        "kannada" -> Pair("🇮🇳", "Kannada Trailer")
        "spanish" -> Pair("🇪🇸", "Spanish Trailer")
        "french" -> Pair("🇫🇷", "French Trailer")
        "german" -> Pair("🇩🇪", "German Trailer")
        "japanese" -> Pair("🇯🇵", "Japanese Trailer")
        "korean" -> Pair("🇰🇷", "Korean Trailer")
        else -> Pair("🇮🇳", "$prefLang Trailer")
    }

    val showCustomTrailer = true

    Column(modifier = modifier) {
        // Title + Type & Rating Badge all in a single line!
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Type + Rating Badge (e.g. MOVIE • ★ 8.4) - Placed to the LEFT of the Title
            val typeText = if (detail.type.equals("series", ignoreCase = true)) "SERIES" else "MOVIE"
            val typeAndRatingText = if (!detail.imdbRating.isNullOrBlank()) {
                "$typeText • ★ ${detail.imdbRating}"
            } else {
                typeText
            }

            Box(
                modifier = Modifier
                    .background(FocusRing.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                    .border(1.dp, FocusRing.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = typeAndRatingText,
                    color = FocusRing,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = detail.name,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black,
                        offset = Offset(2f, 2f),
                        blurRadius = 8f
                    )
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Line 2: Year, Runtime, Genres
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            detail.year?.let { y ->
                Text(text = y, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(text = "•", color = TextMuted, fontSize = 10.sp)
            }
            detail.runtime?.let { rt ->
                Text(text = rt, color = TextSecondary, fontSize = 11.sp)
                Text(text = "•", color = TextMuted, fontSize = 10.sp)
            }
            if (detail.genres.isNotEmpty()) {
                Text(
                    text = detail.genres.take(3).joinToString(" • "),
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Synopsis (left-aligned, 4 lines max, constrained to 48% width)
        detail.description?.let { desc ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = desc,
                color = Color(0xFFCBD5E1),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 16.sp,
                maxLines = 4,
                textAlign = TextAlign.Start,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.48f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action buttons row (Watchlist left of volume, Trailer Play/Pause, Volume, Official Trailer, Hindi Trailer icon-only)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 0. Remove from Continue Watching Button (X) - Icon Only, no labels
            if (hasPlaybackProgress) {
                val removeInteraction = remember { MutableInteractionSource() }
                val isRemoveFocused by removeInteraction.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isRemoveFocused) FocusRing else SurfaceCard)
                        .border(
                            width = if (isRemoveFocused) 2.dp else 1.dp,
                            color = if (isRemoveFocused) FocusRing else GlassBorder,
                            shape = CircleShape
                        )
                        .focusRequester(removeContinueWatchingFocusRequester)
                        .focusable(interactionSource = removeInteraction)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (keyEvent.key) {
                                Key.DirectionLeft -> true
                                Key.DirectionRight -> {
                                    watchlistButtonFocusRequester.safeRequestFocus()
                                    true
                                }
                                Key.DirectionDown -> {
                                    onNavigateDown()
                                    true
                                }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    onClearPlaybackProgress()
                                    true
                                }
                                else -> false
                            }
                        }
                        .clickable(interactionSource = removeInteraction, indication = null) {
                            onClearPlaybackProgress()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove from Continue Watching",
                        tint = if (isRemoveFocused) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 1. Watchlist Button (LEFT of volume button)
            val watchlistInteraction = remember { MutableInteractionSource() }
            val isWatchlistFocused by watchlistInteraction.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isWatchlistFocused) FocusRing else if (isMediaInWatchlist) PrimaryNeon.copy(alpha = 0.22f) else SurfaceCard)
                    .border(
                        width = if (isWatchlistFocused) 2.dp else 1.dp,
                        color = if (isWatchlistFocused) FocusRing else if (isMediaInWatchlist) PrimaryNeon else GlassBorder,
                        shape = CircleShape
                    )
                    .focusRequester(watchlistButtonFocusRequester)
                    .focusable(interactionSource = watchlistInteraction)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                if (hasPlaybackProgress) {
                                    removeContinueWatchingFocusRequester.safeRequestFocus()
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                trailerPlayPauseFocusRequester.safeRequestFocus()
                                true
                            }
                            Key.DirectionDown -> {
                                onNavigateDown()
                                true
                            }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                onToggleWatchlist()
                                true
                            }
                            else -> false
                        }
                    }
                    .clickable(interactionSource = watchlistInteraction, indication = null) {
                        onToggleWatchlist()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMediaInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isMediaInWatchlist) "In Watchlist" else "Add to Watchlist",
                    tint = if (isWatchlistFocused) Color.Black else if (isMediaInWatchlist) PrimaryNeon else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // 2. Trailer Play / Pause Button (Icon-only, directly left of volume button)
            val trailerPlayInteraction = remember { MutableInteractionSource() }
            val isTrailerPlayFocused by trailerPlayInteraction.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isTrailerPlayFocused) FocusRing else SurfaceCard)
                    .border(
                        width = if (isTrailerPlayFocused) 2.dp else 1.dp,
                        color = if (isTrailerPlayFocused) FocusRing else GlassBorder,
                        shape = CircleShape
                    )
                    .focusRequester(trailerPlayPauseFocusRequester)
                    .focusable(interactionSource = trailerPlayInteraction)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                watchlistButtonFocusRequester.safeRequestFocus()
                                true
                            }
                            Key.DirectionRight -> {
                                audioFocusRequester.safeRequestFocus()
                                true
                            }
                            Key.DirectionDown -> {
                                onNavigateDown()
                                true
                            }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                onToggleTrailerPlayback()
                                true
                            }
                            else -> false
                        }
                    }
                    .clickable(interactionSource = trailerPlayInteraction, indication = null) {
                        onToggleTrailerPlayback()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDetailTrailerPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isDetailTrailerPlaying) "Pause Trailer" else "Play Trailer",
                    tint = if (isTrailerPlayFocused) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // 3. Audio Mute / Unmute Button (Icon-only)
            val audioInteraction = remember { MutableInteractionSource() }
            val isAudioFocused by audioInteraction.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isAudioFocused) FocusRing else SurfaceCard)
                    .border(
                        width = if (isAudioFocused) 2.dp else 1.dp,
                        color = if (isAudioFocused) FocusRing else GlassBorder,
                        shape = CircleShape
                    )
                    .focusRequester(audioFocusRequester)
                    .focusable(interactionSource = audioInteraction)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                trailerPlayPauseFocusRequester.safeRequestFocus()
                                true
                            }
                            Key.DirectionRight -> {
                                if (!cinemetaTrailerYtId.isNullOrBlank()) {
                                    cinemetaTrailerFocusRequester.safeRequestFocus()
                                } else if (showCustomTrailer) {
                                    customTrailerFocusRequester.safeRequestFocus()
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                onNavigateDown()
                                true
                            }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                onToggleAudioMute()
                                true
                            }
                            else -> false
                        }
                    }
                    .clickable(interactionSource = audioInteraction, indication = null) {
                        onToggleAudioMute()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAudioMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isAudioMuted) "Unmute Trailer" else "Mute Trailer",
                    tint = if (isAudioFocused) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // 4. Official Trailer Button (Rounded 34.dp circle icon button)
            if (!cinemetaTrailerYtId.isNullOrBlank()) {
                val cinemetaInteraction = remember { MutableInteractionSource() }
                val isCinemetaFocused by cinemetaInteraction.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isCinemetaFocused) FocusRing else SurfaceCard)
                        .border(
                            width = if (isCinemetaFocused) 2.dp else 1.dp,
                            color = if (isCinemetaFocused) FocusRing else GlassBorder,
                            shape = CircleShape
                        )
                        .focusRequester(cinemetaTrailerFocusRequester)
                        .focusable(interactionSource = cinemetaInteraction)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    audioFocusRequester.safeRequestFocus()
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (showCustomTrailer) customTrailerFocusRequester.safeRequestFocus()
                                    true
                                }
                                Key.DirectionDown -> {
                                    onNavigateDown()
                                    true
                                }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    onPlayTrailer(cinemetaTrailerYtId, "${detail.name} • Official Trailer")
                                    true
                                }
                                else -> false
                            }
                        }
                        .clickable(interactionSource = cinemetaInteraction, indication = null) {
                            onPlayTrailer(cinemetaTrailerYtId, "${detail.name} • Official Trailer")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Official Trailer",
                        tint = if (isCinemetaFocused) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 5. Localized / Hindi Trailer Button (With India flag icon 🇮🇳)
            if (showCustomTrailer) {
                val customInteraction = remember { MutableInteractionSource() }
                val isCustomFocused by customInteraction.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isCustomFocused) FocusRing else SurfaceCard)
                        .border(
                            width = if (isCustomFocused) 2.dp else 1.dp,
                            color = if (isCustomFocused) FocusRing else GlassBorder,
                            shape = CircleShape
                        )
                        .focusRequester(customTrailerFocusRequester)
                        .focusable(interactionSource = customInteraction)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    if (!cinemetaTrailerYtId.isNullOrBlank()) cinemetaTrailerFocusRequester.safeRequestFocus()
                                    else audioFocusRequester.safeRequestFocus()
                                    true
                                }
                                Key.DirectionDown -> {
                                    onNavigateDown()
                                    true
                                }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    onPlayCustomTrailerWhenReady()
                                    true
                                }
                                else -> false
                            }
                        }
                        .clickable(interactionSource = customInteraction, indication = null) {
                            onPlayCustomTrailerWhenReady()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🇮🇳",
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

private fun launchResolvedStreamHelper(
    context: Context,
    scope: CoroutineScope,
    repository: SourcesRepository,
    stream: StremioStreamSource,
    detail: StremioMetaDetail,
    isSeries: Boolean,
    selectedEpisode: StremioVideoEpisode?,
    currentQueryId: String,
    streamKey: String,
    restartFromBeginning: Boolean,
    inExternalPlayer: Boolean,
    onStartResolving: (String) -> Unit,
    onEndResolving: () -> Unit,
    onPlay: (MediaPlaybackItem) -> Unit
) {
    scope.launch {
        val savedPos = if (restartFromBeginning) 0L else repository.getSavedPosition(currentQueryId)
        val existingUrl = stream.url
        val isArc = stream.isArchive || stream.name?.contains("ARC", ignoreCase = true) == true
        if (!isArc && !existingUrl.isNullOrBlank() && (existingUrl.startsWith("http://") || existingUrl.startsWith("https://"))) {
            val playbackItem = MediaPlaybackItem(
                id = detail.id,
                title = if (isSeries && selectedEpisode != null) {
                    "${detail.name} - S${selectedEpisode.season}E${selectedEpisode.episode}: ${selectedEpisode.name ?: ""}"
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
            if (inExternalPlayer) {
                com.mystream.app.ui.utils.ExternalPlayerHelper.launchExternalPlayer(context, playbackItem, savedPos)
            } else {
                onPlay(playbackItem)
            }
        } else {
            onStartResolving(streamKey)
            try {
                val res = repository.resolveSpecificStream(stream, currentQueryId)
                val resolvedUrl = res.getOrNull()
                if (!resolvedUrl.isNullOrBlank()) {
                    val playbackItem = MediaPlaybackItem(
                        id = detail.id,
                        title = if (isSeries && selectedEpisode != null) {
                            "${detail.name} - S${selectedEpisode.season}E${selectedEpisode.episode}: ${selectedEpisode.name ?: ""}"
                        } else {
                            detail.name
                        },
                        subtitle = "${stream.quality}${if (isArc) " ARC" else ""} • PikPak Cloud Direct",
                        mediaUrl = resolvedUrl,
                        posterUrl = selectedEpisode?.thumbnail ?: detail.poster,
                        backdropUrl = detail.background,
                        isSeries = isSeries,
                        seasonNumber = selectedEpisode?.season ?: 0,
                        episodeNumber = selectedEpisode?.episode ?: 0,
                        startPositionMs = savedPos,
                        headers = stream.behaviorHints?.proxyHeaders
                    )
                    if (inExternalPlayer) {
                        com.mystream.app.ui.utils.ExternalPlayerHelper.launchExternalPlayer(context, playbackItem, savedPos)
                    } else {
                        onPlay(playbackItem)
                    }
                } else {
                    val err = res.exceptionOrNull()?.message ?: "Stream resolution failed"
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                onEndResolving()
            }
        }
    }
}

private fun resolveAndPlayTorrentHelper(
    context: Context,
    scope: CoroutineScope,
    repository: SourcesRepository,
    torr: StremioStreamSource,
    displayTorr: StremioStreamSource,
    matchingCached: StremioStreamSource?,
    detail: StremioMetaDetail,
    isSeries: Boolean,
    selectedEpisode: StremioVideoEpisode?,
    currentQueryId: String,
    streamKey: String,
    restartFromBeginning: Boolean,
    inExternalPlayer: Boolean,
    onRefreshStreams: () -> Unit,
    onStartResolving: (String) -> Unit,
    onEndResolving: () -> Unit,
    onPlay: (MediaPlaybackItem) -> Unit
) {
    scope.launch {
        onStartResolving(streamKey)
        try {
            val res = repository.resolveAndSaveSingleTorrent(torr, detail.type, currentQueryId)
            val freshUrl = res.getOrNull()
            if (!freshUrl.isNullOrBlank()) {
                onRefreshStreams()
                val savedPos = if (restartFromBeginning) 0L else repository.getSavedPosition(currentQueryId)
                val isArc = displayTorr.isArchive || matchingCached?.isArchive == true
                val arcTag = if (isArc) " ARC" else ""
                val playbackItem = MediaPlaybackItem(
                    id = detail.id,
                    title = if (isSeries && selectedEpisode != null) {
                        "${detail.name} - S${selectedEpisode.season}E${selectedEpisode.episode}: ${selectedEpisode.name ?: ""}"
                    } else {
                        detail.name
                    },
                    subtitle = "${torr.quality}$arcTag • PikPak Direct",
                    mediaUrl = freshUrl,
                    posterUrl = selectedEpisode?.thumbnail ?: detail.poster,
                    backdropUrl = detail.background,
                    isSeries = isSeries,
                    seasonNumber = selectedEpisode?.season ?: 0,
                    episodeNumber = selectedEpisode?.episode ?: 0,
                    startPositionMs = savedPos,
                    headers = torr.behaviorHints?.proxyHeaders
                )
                if (inExternalPlayer) {
                    com.mystream.app.ui.utils.ExternalPlayerHelper.launchExternalPlayer(context, playbackItem, savedPos)
                } else {
                    onPlay(playbackItem)
                }
            } else {
                val err = res.exceptionOrNull()?.message ?: "Torrent not instant-cached on PikPak. Tap card for P2P."
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            onEndResolving()
        }
    }
}
