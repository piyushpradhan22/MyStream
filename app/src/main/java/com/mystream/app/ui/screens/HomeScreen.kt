@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.mystream.app.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.distinctUntilChanged
import com.mystream.app.ui.utils.safeRequestFocus
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mystream.app.data.model.MediaPlaybackItem
import com.mystream.app.data.model.PlaybackProgressRecord
import com.mystream.app.data.model.StremioMetaPreview
import com.mystream.app.data.model.WatchlistItem
import com.mystream.app.data.repository.SourcesRepository
import com.mystream.app.ui.components.CustomUrlDialog
import com.mystream.app.ui.components.ExitConfirmationDialog
import com.mystream.app.ui.components.OttHeroSpotlight
import com.mystream.app.ui.components.OttLeftSidebar
import com.mystream.app.ui.components.OttNavDestination
import com.mystream.app.ui.components.PosterCard
import com.mystream.app.ui.theme.FocusRing
import com.mystream.app.ui.theme.FocusRingOrange
import com.mystream.app.ui.theme.GlassBorder
import com.mystream.app.ui.theme.HotstarBg
import com.mystream.app.ui.theme.HotstarPillActive
import com.mystream.app.ui.theme.HotstarPillActiveBg
import com.mystream.app.ui.theme.HotstarPillInactiveBg
import com.mystream.app.ui.theme.HotstarPillInactiveText
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SecondaryCyan
import com.mystream.app.ui.theme.SurfaceCard
import com.mystream.app.ui.theme.SurfaceCardFocused
import com.mystream.app.ui.theme.SurfaceDark
import com.mystream.app.ui.theme.SurfaceElevated
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged

data class OttCategory(
    val id: String,
    val title: String,
    val type: String = "movie",
    val catalogId: String = "top",
    val genre: String? = null
)

@Composable
fun HomeScreen(
    repository: SourcesRepository,
    onNavigateToDetail: (type: String, id: String) -> Unit,
    onNavigateToCatalog: (title: String, type: String, catalogId: String, genre: String?) -> Unit,
    onPlayDirect: (MediaPlaybackItem) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSources: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Dialog States
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var showExitConfirmationDialog by remember { mutableStateOf(false) }
    var showSetupCredentialsDialog by remember { mutableStateOf(false) }



    if (showExitConfirmationDialog) {
        ExitConfirmationDialog(
            onConfirmExit = {
                (context as? Activity)?.let { act ->
                    act.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(0)
                }
            },
            onDismiss = { showExitConfirmationDialog = false }
        )
    }

    if (showCustomUrlDialog) {
        CustomUrlDialog(
            onDismiss = { showCustomUrlDialog = false },
            onPlay = { item -> onPlayDirect(item) }
        )
    }

    LaunchedEffect(Unit) {
        val cfg = repository.getJsonConfig()
        if (cfg.postgresUrl.isNullOrBlank() && cfg.pikpakAccounts.isEmpty()) {
            showSetupCredentialsDialog = true
        }
    }

    if (showSetupCredentialsDialog) {
        FirstStartupSetupDialog(
            onNavigateToSettings = onNavigateToSources,
            onDismiss = { showSetupCredentialsDialog = false }
        )
    }

    // Data sources
    val continueWatchingList: List<PlaybackProgressRecord> by repository.continueWatchingFlow.collectAsState(initial = emptyList())
    val watchlist: List<WatchlistItem> by repository.watchlistFlow.collectAsState(initial = emptyList())
    val appSettings by repository.appSettingsFlow.collectAsState(initial = com.mystream.app.data.model.AppSettingsConfig())

    var topMovies by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var topSeries by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var hfCatalogItems by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var indianCategories by remember { mutableStateOf<List<Pair<String, List<StremioMetaPreview>>>>(emptyList()) }
    var actionMovies by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var scifiMovies by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var comedySeries by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Navigation & Category Selection State
    var selectedNavDestination by remember { mutableStateOf(OttNavDestination.HOME) }
    var selectedCategoryId by rememberSaveable { mutableStateOf("trending") }
    val sidebarHomeFocusRequester = remember { FocusRequester() }
    var isHomeSidebarFocused by remember { mutableStateOf(false) }

    // Intercept back button: highlight Home button on sidebar first, then show exit dialog
    BackHandler(enabled = true) {
        android.util.Log.d("HomeScreenBack", "BackHandler triggered! isHomeSidebarFocused=$isHomeSidebarFocused")
        if (!isHomeSidebarFocused) {
            try {
                selectedNavDestination = OttNavDestination.HOME
                val ok = sidebarHomeFocusRequester.requestFocus()
                android.util.Log.d("HomeScreenBack", "sidebarHomeFocusRequester.requestFocus() returned: $ok")
            } catch (e: Exception) {
                android.util.Log.e("HomeScreenBack", "sidebarHomeFocusRequester.requestFocus() threw exception", e)
                showExitConfirmationDialog = true
            }
        } else {
            android.util.Log.d("HomeScreenBack", "Already focused on home sidebar, showing exit dialog")
            showExitConfirmationDialog = true
        }
    }

    // Hero Spotlight State
    var focusedItem by remember { mutableStateOf<StremioMetaPreview?>(null) }
    var currentTrailerYtId by remember { mutableStateOf<String?>(null) }
    // User requested: "Make sure background trailer audio is playing"
    var isTrailerAudioMuted by remember { mutableStateOf(false) }

    // Focus Management
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    val playHeroFocusRequester = remember { FocusRequester() }
    val categoryPillFirstItemFR = remember { FocusRequester() }
    var focusedCardIndex by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val cardFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val categoryPillFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val carouselListState = rememberLazyListState(initialFirstVisibleItemIndex = focusedCardIndex)
    var isLoadingMoreCategoryItems by remember { mutableStateOf(false) }
    var isRestoringFocus by remember { mutableStateOf(false) }

    var lastActiveCategoryId by remember { mutableStateOf(selectedCategoryId) }
    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != lastActiveCategoryId) {
            lastActiveCategoryId = selectedCategoryId
            cardFocusRequesters.clear()
            focusedCardIndex = 0
            try {
                carouselListState.scrollToItem(0)
            } catch (_: Exception) {}
        }
    }

    // Fetch Catalogs in Parallel
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            coroutineScope {
                launch(Dispatchers.IO) {
                    try {
                        val moviesRes = repository.fetchCatalog("movie", "top", skip = 0)
                        topMovies = moviesRes.metas
                        if (focusedItem == null && moviesRes.metas.isNotEmpty()) {
                            focusedItem = moviesRes.metas.first()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Error loading top movies", e)
                    }
                }
                launch(Dispatchers.IO) {
                    try {
                        val seriesRes = repository.fetchCatalog("series", "top", skip = 0)
                        topSeries = seriesRes.metas
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Error loading series", e)
                    }
                }
                launch(Dispatchers.IO) {
                    try {
                        val hfRes = repository.fetchHfCatalog(skip = 0, limit = 30)
                        hfCatalogItems = hfRes.metas
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Error loading HF catalog", e)
                    }
                }
                launch(Dispatchers.IO) {
                    try {
                        val indian = repository.getAllIndianCategories()
                        indianCategories = indian
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Error loading Indian categories", e)
                    }
                }
                launch(Dispatchers.IO) {
                    try {
                        val actionRes = repository.fetchCatalog("movie", "top", genre = "Action", skip = 0)
                        actionMovies = actionRes.metas
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Error loading action movies", e)
                    }
                }
                launch(Dispatchers.IO) {
                    try {
                        val scifiRes = repository.fetchCatalog("movie", "top", genre = "Sci-Fi", skip = 0)
                        scifiMovies = scifiRes.metas
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Error loading sci-fi movies", e)
                    }
                }
                launch(Dispatchers.IO) {
                    try {
                        val comedyRes = repository.fetchCatalog("series", "top", genre = "Comedy", skip = 0)
                        comedySeries = comedyRes.metas
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Error loading comedy series", e)
                    }
                }
            }
        } finally {
            isLoading = false
        }
    }

    // Set initial focused item if continue watching is available
    LaunchedEffect(continueWatchingList, topMovies) {
        if (focusedItem == null) {
            val firstRecord = continueWatchingList.firstOrNull()
            if (firstRecord != null) {
                focusedItem = StremioMetaPreview(
                    id = firstRecord.imdbId,
                    type = firstRecord.type,
                    name = firstRecord.title,
                    poster = firstRecord.posterUrl,
                    genres = listOfNotNull(firstRecord.subtitle)
                )
                selectedCategoryId = "continue_watching"
            } else if (topMovies.isNotEmpty()) {
                focusedItem = topMovies.first()
            }
        }
    }

    // Enrich Continue Watching items with IMDB metadata in background
    var enrichedCwMeta by remember { mutableStateOf<Map<String, StremioMetaPreview>>(emptyMap()) }
    LaunchedEffect(continueWatchingList) {
        if (continueWatchingList.isEmpty()) return@LaunchedEffect
        val enriched = mutableMapOf<String, StremioMetaPreview>()
        continueWatchingList.forEach { record ->
            try {
                val meta = repository.fetchMetaDetail(record.type, record.imdbId)
                enriched[record.imdbId] = StremioMetaPreview(
                    id = record.imdbId,
                    type = record.type,
                    name = record.title,
                    poster = record.posterUrl ?: meta.poster,
                    background = record.backdropUrl ?: meta.background,
                    imdbRating = meta.imdbRating,
                    year = meta.year ?: meta.releaseInfo,
                    releaseInfo = meta.releaseInfo ?: meta.year,
                    genres = if (meta.genres.isNotEmpty()) meta.genres else listOfNotNull(record.subtitle),
                    description = meta.description
                )
            } catch (_: Exception) { /* graceful degradation */ }
        }
        enrichedCwMeta = enriched
    }



    // Debounced Background Trailer Loading with Audio & Metadata Enrichment
    LaunchedEffect(focusedItem?.id) {
        currentTrailerYtId = null
        val item = focusedItem ?: return@LaunchedEffect
        // 900ms debounce ensures rapid D-pad scrolling is silky smooth
        delay(900)
        try {
            val meta = repository.fetchMetaDetail(item.type, item.id)
            var trailerId = meta.effectiveTrailerYtId
            if (trailerId.isNullOrBlank()) {
                // Autoplay Hindi trailer fallback by default if official is unavailable
                trailerId = repository.searchYouTubeTrailer(meta.name, meta.year, "Hindi")
            }
            currentTrailerYtId = trailerId
            focusedItem = focusedItem?.copy(
                imdbRating = if (!meta.imdbRating.isNullOrBlank()) meta.imdbRating else focusedItem?.imdbRating,
                year = meta.year ?: meta.releaseInfo ?: focusedItem?.year,
                releaseInfo = meta.releaseInfo ?: meta.year ?: focusedItem?.releaseInfo,
                description = if (!meta.description.isNullOrBlank()) meta.description else focusedItem?.description,
                genres = if (meta.genres.isNotEmpty()) meta.genres else focusedItem?.genres ?: emptyList(),
                background = meta.background ?: focusedItem?.background
            )
        } catch (e: Exception) {
            android.util.Log.d("HomeScreen", "Trailer not available for ${item.name}: ${e.message}")
        }
    }

    // Dynamic categories list (Preserving exact original labels)
    val categories = remember(continueWatchingList.size, watchlist.size, hfCatalogItems.size, indianCategories.size) {
        val list = mutableListOf<OttCategory>()
        if (continueWatchingList.isNotEmpty()) {
            list.add(OttCategory("continue_watching", "Continue Watching"))
        }
        if (hfCatalogItems.isNotEmpty()) {
            list.add(OttCategory("hf_direct", "⚡ HF Direct", type = "movie", catalogId = "hftor"))
        }
        indianCategories.forEachIndexed { index, pair ->
            list.add(OttCategory("indian_$index", pair.first))
        }
        list.add(OttCategory("trending", "Popular Movies", type = "movie", catalogId = "top"))
        list.add(OttCategory("series", "Popular Series", type = "series", catalogId = "top"))
        list.add(OttCategory("action", "Action & Adventure", type = "movie", catalogId = "top", genre = "Action"))
        list.add(OttCategory("scifi", "Sci-Fi & Thriller", type = "movie", catalogId = "top", genre = "Sci-Fi"))
        list.add(OttCategory("comedy", "Binge-Worthy Comedies", type = "series", catalogId = "top", genre = "Comedy"))
        if (watchlist.isNotEmpty()) {
            list.add(OttCategory("watchlist", "My Watchlist"))
        }
        list
    }

    // Get items for currently selected category
    val currentCategoryItems: List<StremioMetaPreview> = remember(
        selectedCategoryId,
        continueWatchingList,
        enrichedCwMeta,
        topMovies,
        topSeries,
        hfCatalogItems,
        indianCategories,
        actionMovies,
        scifiMovies,
        comedySeries,
        watchlist
    ) {
        when {
            selectedCategoryId == "continue_watching" -> continueWatchingList.map { record: PlaybackProgressRecord ->
                // Use enriched metadata if available, else fallback to raw record data
                enrichedCwMeta[record.imdbId] ?: StremioMetaPreview(
                    id = record.imdbId,
                    type = record.type,
                    name = record.title,
                    poster = record.posterUrl,
                    background = record.backdropUrl,
                    genres = listOfNotNull(record.subtitle)
                )
            }
            selectedCategoryId == "trending" -> topMovies
            selectedCategoryId == "series" -> topSeries
            selectedCategoryId == "hf_direct" -> hfCatalogItems
            selectedCategoryId.startsWith("indian_") -> {
                val index = selectedCategoryId.substringAfter("indian_").toIntOrNull() ?: 0
                indianCategories.getOrNull(index)?.second ?: emptyList()
            }
            selectedCategoryId == "action" -> actionMovies
            selectedCategoryId == "scifi" -> scifiMovies
            selectedCategoryId == "comedy" -> comedySeries
            selectedCategoryId == "watchlist" -> watchlist.map { item: WatchlistItem ->
                StremioMetaPreview(
                    id = item.imdbId,
                    type = item.type,
                    name = item.title,
                    poster = item.posterUrl,
                    background = item.backdropUrl,
                    genres = listOfNotNull(item.subtitle ?: item.torrentQuality)
                )
            }
            else -> topMovies
        }
    }

    val isCurrentItemWatchlisted = remember(focusedItem?.id, watchlist) {
        focusedItem?.let { item -> watchlist.any { it.imdbId == item.id } } ?: false
    }

    // Robust card focus helper: scrolls to card and requests focus with retry
    suspend fun focusCardAtIndex(index: Int) {
        if (currentCategoryItems.isEmpty()) return
        val targetIdx = index.coerceIn(0, (currentCategoryItems.size - 1).coerceAtLeast(0))
        focusedCardIndex = targetIdx
        currentCategoryItems.getOrNull(targetIdx)?.let { focusedItem = it }
        try {
            carouselListState.scrollToItem(targetIdx)
        } catch (_: Exception) {}
        for (retry in 0..6) {
            delay(50)
            val fr = cardFocusRequesters[targetIdx]
            if (fr != null && fr.safeRequestFocus()) {
                break
            }
        }
    }

    // Auto-focus first card in bottom carousel on initial load (defaults to first card of Continue Watching)
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(currentCategoryItems.isNotEmpty()) {
        if (!hasRequestedInitialFocus && currentCategoryItems.isNotEmpty()) {
            hasRequestedInitialFocus = true
            focusCardAtIndex(focusedCardIndex)
        }
    }

    // Restore focus and preserve highlighted card when returning from DetailScreen
    var isFirstResume by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isFirstResume) {
                    isFirstResume = false
                } else {
                    isRestoringFocus = true
                    scope.launch {
                        focusCardAtIndex(focusedCardIndex)
                        delay(200)
                        isRestoringFocus = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val endOfCatalogReached = remember { mutableStateMapOf<String, Boolean>() }

    // Resilient infinite scroll pagination using snapshotFlow (prevents D-pad scroll cancellation)
    LaunchedEffect(selectedCategoryId) {
        snapshotFlow {
            val cat = categories.find { it.id == selectedCategoryId }
            val items = currentCategoryItems
            val threshold = (items.size - 6).coerceAtLeast(0)
            val nearEnd = items.size >= 10 && (
                focusedCardIndex >= threshold || 
                carouselListState.firstVisibleItemIndex >= (threshold - 2).coerceAtLeast(0)
            )
            Triple(nearEnd, cat, items.size)
        }
        .distinctUntilChanged()
        .collect { (nearEnd, cat, currentSize) ->
            if (nearEnd && cat != null && 
                cat.id != "continue_watching" && cat.id != "watchlist" && 
                !isLoadingMoreCategoryItems && 
                endOfCatalogReached[cat.id] != true
            ) {
                isLoadingMoreCategoryItems = true
                try {
                    val nextSkip = currentSize
                    android.util.Log.d("HomeScreen", "Pagination triggering for ${cat.id} at skip=$nextSkip, currentCount=$currentSize")
                    if (cat.id.startsWith("indian_")) {
                        val idx = cat.id.substringAfter("indian_").toIntOrNull() ?: 0
                        val catTitle = indianCategories.getOrNull(idx)?.first
                        if (catTitle != null) {
                            val res = repository.fetchIndianCatalog(category = catTitle, skip = nextSkip, limit = 20)
                            if (res.metas.isNotEmpty()) {
                                val updated = indianCategories.toMutableList()
                                val (cName, existing) = updated[idx]
                                val newItems = (existing + res.metas).distinctBy { it.id }
                                if (newItems.size == existing.size) {
                                    endOfCatalogReached[cat.id] = true
                                } else {
                                    updated[idx] = cName to newItems
                                    indianCategories = updated
                                }
                            } else {
                                endOfCatalogReached[cat.id] = true
                            }
                        }
                    } else if (cat.id == "hf_direct") {
                        val res = repository.fetchHfCatalog(skip = nextSkip, limit = 30)
                        if (res.metas.isNotEmpty()) {
                            val beforeSize = hfCatalogItems.size
                            val newItems = (hfCatalogItems + res.metas).distinctBy { it.id }
                            if (newItems.size == beforeSize) {
                                endOfCatalogReached[cat.id] = true
                            } else {
                                hfCatalogItems = newItems
                            }
                        } else {
                            endOfCatalogReached[cat.id] = true
                        }
                    } else {
                        val res = repository.fetchCatalog(
                            type = cat.type,
                            catalogId = cat.catalogId,
                            genre = cat.genre,
                            skip = nextSkip
                        )
                        if (res.metas.isNotEmpty()) {
                            when (selectedCategoryId) {
                                "trending" -> {
                                    val beforeSize = topMovies.size
                                    val newItems = (topMovies + res.metas).distinctBy { it.id }
                                    if (newItems.size == beforeSize) endOfCatalogReached[cat.id] = true
                                    else topMovies = newItems
                                }
                                "series" -> {
                                    val beforeSize = topSeries.size
                                    val newItems = (topSeries + res.metas).distinctBy { it.id }
                                    if (newItems.size == beforeSize) endOfCatalogReached[cat.id] = true
                                    else topSeries = newItems
                                }
                                "action" -> {
                                    val beforeSize = actionMovies.size
                                    val newItems = (actionMovies + res.metas).distinctBy { it.id }
                                    if (newItems.size == beforeSize) endOfCatalogReached[cat.id] = true
                                    else actionMovies = newItems
                                }
                                "scifi" -> {
                                    val beforeSize = scifiMovies.size
                                    val newItems = (scifiMovies + res.metas).distinctBy { it.id }
                                    if (newItems.size == beforeSize) endOfCatalogReached[cat.id] = true
                                    else scifiMovies = newItems
                                }
                                "comedy" -> {
                                    val beforeSize = comedySeries.size
                                    val newItems = (comedySeries + res.metas).distinctBy { it.id }
                                    if (newItems.size == beforeSize) endOfCatalogReached[cat.id] = true
                                    else comedySeries = newItems
                                }
                            }
                        } else {
                            endOfCatalogReached[cat.id] = true
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("HomeScreen", "Pagination error for $selectedCategoryId", e)
                } finally {
                    isLoadingMoreCategoryItems = false
                }
            }
        }
    }

    // Main JioHotstar OTT Layout: Left Sidebar + Main Content (Spotlight + Middle Carousel + Very Bottom Category Pills)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // 1. Left Navigation Rail (Narrow 52dp Glass Sidebar)
        OttLeftSidebar(
            selectedDestination = selectedNavDestination,
            onSelectDestination = { dest ->
                selectedNavDestination = dest
                when (dest) {
                    OttNavDestination.SEARCH -> onNavigateToSearch()
                    OttNavDestination.SETTINGS -> onNavigateToSources()
                    OttNavDestination.CUSTOM_URL -> showCustomUrlDialog = true
                    OttNavDestination.MOVIES -> {
                        selectedCategoryId = "trending"
                        if (topMovies.isNotEmpty()) focusedItem = topMovies.first()
                    }
                    OttNavDestination.SERIES -> {
                        selectedCategoryId = "series"
                        if (topSeries.isNotEmpty()) focusedItem = topSeries.first()
                    }
                    OttNavDestination.WATCHLIST -> {
                        selectedCategoryId = "watchlist"
                        val first = watchlist.firstOrNull()
                        if (first != null) {
                            focusedItem = StremioMetaPreview(
                                id = first.imdbId,
                                type = first.type,
                                name = first.title,
                                poster = first.posterUrl
                            )
                        }
                    }
                    OttNavDestination.HOME -> {
                        selectedCategoryId = if (continueWatchingList.isNotEmpty()) "continue_watching" else "trending"
                    }
                }
            },
            onNavigateRight = {
                try {
                    val targetFR = cardFocusRequesters[focusedCardIndex] ?: cardFocusRequesters[0] ?: playHeroFocusRequester
                    targetFR.requestFocus()
                } catch (_: Exception) {
                    try { playHeroFocusRequester.requestFocus() } catch (_: Exception) {}
                }
            },
            searchFocusRequester = searchFocusRequester,
            homeFocusRequester = sidebarHomeFocusRequester,
            onHomeFocusChanged = { isHomeSidebarFocused = it }
        )

        // 2. Main OTT Content Showcase
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        ) {
            // Top Section (53% Height): Hero Spotlight with Ambient Video Trailer & Audio
            OttHeroSpotlight(
                item = focusedItem,
                trailerYtId = currentTrailerYtId,
                isTrailerPlaybackEnabled = appSettings.trailerPlaybackEnabled,
                isAudioMuted = appSettings.trailerAudioMuted,
                isWatchlisted = isCurrentItemWatchlisted,
                onToggleTrailerPlayback = {
                    scope.launch {
                        repository.updateAppSettings(
                            appSettings.copy(trailerPlaybackEnabled = !appSettings.trailerPlaybackEnabled)
                        )
                    }
                },
                onToggleAudioMute = {
                    scope.launch {
                        repository.updateAppSettings(
                            appSettings.copy(trailerAudioMuted = !appSettings.trailerAudioMuted)
                        )
                    }
                },
                onToggleWatchlist = {
                    focusedItem?.let { item ->
                        scope.launch {
                            val existing = watchlist.firstOrNull { it.imdbId == item.id }
                            if (existing != null) {
                                repository.removeFromWatchlist(existing.id)
                            } else {
                                repository.addToWatchlist(
                                    WatchlistItem(
                                        id = item.id,
                                        imdbId = item.id,
                                        title = item.name,
                                        type = item.type,
                                        posterUrl = item.poster,
                                        subtitle = item.genres.firstOrNull()
                                    )
                                )
                            }
                        }
                    }
                },
                onNavigateDownToContent = {
                    val targetFR = cardFocusRequesters[focusedCardIndex] ?: cardFocusRequesters[0]
                    if (targetFR != null) {
                        try { targetFR.requestFocus() } catch (_: Exception) { focusManager.moveFocus(FocusDirection.Down) }
                    } else {
                        focusManager.moveFocus(FocusDirection.Down)
                    }
                },
                onNavigateLeftToSidebar = {
                    try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
                },
                watchlistFocusRequester = playHeroFocusRequester,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.53f)
            )

            // Bottom Section (47% Height): Middle Carousel + Very Bottom Category Pills
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.47f)
                    .background(HotstarBg)
                    .padding(bottom = 4.dp)
            ) {
                // Middle: Single Category Cards Carousel Row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isLoading && currentCategoryItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = FocusRing,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        LazyRow(
                            state = carouselListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 2.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(
                                items = currentCategoryItems,
                                key = { _, item -> "${selectedCategoryId}_${item.id}" },
                                contentType = { _, _ -> "poster" }
                            ) { index, meta ->
                                val isFirstCard = index == 0
                                val progressFraction = if (selectedCategoryId == "continue_watching") {
                                    continueWatchingList.firstOrNull { it.imdbId == meta.id }?.progressFraction
                                } else null
                                val cardFR = cardFocusRequesters.getOrPut(index) { FocusRequester() }

                                PosterCard(
                                    item = meta,
                                    width = 110,
                                    progressFraction = progressFraction,
                                    modifier = Modifier
                                        .focusRequester(cardFR)
                                        .onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                if (!isRestoringFocus || index == focusedCardIndex) {
                                                    focusedItem = meta
                                                    focusedCardIndex = index
                                                }
                                            }
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.key) {
                                                    Key.DirectionLeft -> {
                                                        if (isFirstCard) {
                                                            try { sidebarHomeFocusRequester.requestFocus(); true } catch (_: Exception) { false }
                                                        } else {
                                                            false // Let Compose LazyRow handle natural left scrolling & focus
                                                        }
                                                    }
                                                    Key.DirectionRight -> {
                                                        false // Let Compose LazyRow handle natural right scrolling & focus
                                                    }
                                                    Key.DirectionUp -> {
                                                        try {
                                                            playHeroFocusRequester.requestFocus()
                                                            true
                                                        } catch (_: Exception) {
                                                            focusManager.moveFocus(FocusDirection.Up)
                                                        }
                                                    }
                                                    Key.DirectionDown -> {
                                                        val activePillIndex = categories.indexOfFirst { it.id == selectedCategoryId }.coerceAtLeast(0)
                                                        val pillFR = categoryPillFocusRequesters[activePillIndex] ?: categoryPillFirstItemFR
                                                        try {
                                                             pillFR.requestFocus()
                                                             true
                                                        } catch (_: Exception) {
                                                            focusManager.moveFocus(FocusDirection.Down)
                                                        }
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        },
                                    onClick = {
                                        focusedItem = meta
                                        onNavigateToDetail(meta.type, meta.id)
                                    }
                                )
                            }

                            if (isLoadingMoreCategoryItems) {
                                item(key = "${selectedCategoryId}_loading_more") {
                                    Box(
                                        modifier = Modifier
                                            .height(160.dp)
                                            .width(70.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = FocusRing,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // VERY BOTTOM: Category Switcher Pills Row (Hotstar Style)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 4.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(categories) { index, category ->
                        val isSelected = category.id == selectedCategoryId
                        val pillFR = categoryPillFocusRequesters.getOrPut(index) { FocusRequester() }

                        CategoryPill(
                            title = category.title,
                            isSelected = isSelected,
                            focusRequester = pillFR,
                            onClick = {
                                selectedCategoryId = category.id
                                val firstItem = currentCategoryItems.firstOrNull()
                                if (firstItem != null) {
                                    focusedItem = firstItem
                                }
                            },
                            onNavigateDown = {
                                // Already at bottom edge
                            },
                            onNavigateUp = {
                                val targetIndex = focusedCardIndex.coerceIn(0, (currentCategoryItems.size - 1).coerceAtLeast(0))
                                val targetFR = cardFocusRequesters[targetIndex] ?: cardFocusRequesters[0]
                                if (targetFR != null) {
                                    try {
                                        targetFR.requestFocus()
                                    } catch (_: Exception) {
                                        focusManager.moveFocus(FocusDirection.Up)
                                    }
                                } else {
                                    focusManager.moveFocus(FocusDirection.Up)
                                }
                            },
                            onNavigateLeft = if (index == 0) {
                                {
                                    try { sidebarHomeFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(
    title: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onNavigateDown: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateLeft: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> FocusRing.copy(alpha = 0.25f)
        isSelected -> HotstarPillActiveBg
        else -> HotstarPillInactiveBg
    }

    val textColor = when {
        isFocused -> TextPrimary
        isSelected -> HotstarPillActive
        else -> HotstarPillInactiveText
    }

    val border = when {
        isFocused -> androidx.compose.foundation.BorderStroke(1.5.dp, FocusRing)
        isSelected -> androidx.compose.foundation.BorderStroke(1.dp, HotstarPillActive.copy(alpha = 0.6f))
        else -> androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
    }

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(border.width, border.brush, RoundedCornerShape(16.dp))
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionDown -> {
                            onNavigateDown()
                            true
                        }
                        Key.DirectionUp -> {
                            onNavigateUp()
                            true
                        }
                        Key.DirectionLeft -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft()
                                true
                            } else false
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryBadgeIcon(categoryName = title)
            Text(
                text = title,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CategoryBadgeIcon(categoryName: String) {
    val clean = categoryName.trim()
    when {
        clean.contains("Netflix", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE50914))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "N",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
        clean.contains("Prime", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF00A8E1))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "prime",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
        }
        clean.contains("Disney", ignoreCase = true) || clean.contains("Hotstar", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0F1035))
                    .border(0.8.dp, Color(0xFF1E88E5), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Disney+",
                    color = Color(0xFF90CAF9),
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
        }
        clean.contains("Jio", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE50055))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Jio",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
        }
        clean.contains("Zee5", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF8224E3))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ZEE5",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 8.5.sp
                )
            }
        }
        clean.contains("Sony", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFF6900))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LIV",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp
                )
            }
        }
        clean.contains("HF", ignoreCase = true) || clean.contains("HuggingFace", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFFB300).copy(alpha = 0.2f))
                    .border(0.8.dp, Color(0xFFFFB300), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚡ HF",
                    color = Color(0xFFFFD54F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp
                )
            }
        }
        clean.equals("Top Rated", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFF5C518))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "★ TOP",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp
                )
            }
        }
        clean.contains("Hindi", ignoreCase = true) -> {
            Text(
                text = "🇮🇳",
                fontSize = 12.sp
            )
        }
        clean.contains("Series", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x3300E5FF))
                    .border(0.8.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SERIES",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp
                )
            }
        }
        clean.contains("Movie", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x336C5CE7))
                    .border(0.8.dp, Color(0xFF6C5CE7), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MOVIE",
                    color = Color(0xFFA29BFE),
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp
                )
            }
        }
    }
}

@Composable
private fun SeeMoreGridCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = if (isFocused) FocusRing else GlassBorder
    val bgColor = if (isFocused) SurfaceCardFocused else SurfaceCard

    Box(
        modifier = modifier
            .width(104.dp)
            .height(154.dp)
            .background(bgColor, RoundedCornerShape(10.dp))
            .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .focusable(interactionSource = interactionSource)
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
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(if (isFocused) FocusRing.copy(alpha = 0.2f) else SurfaceElevated, CircleShape)
                .border(1.dp, if (isFocused) FocusRing else GlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = null,
                    tint = if (isFocused) FocusRing else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "See All",
                color = if (isFocused) FocusRing else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Explore all",
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun FirstStartupSetupDialog(
    onNavigateToSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val configureFR = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        try {
            configureFR.requestFocus()
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, SecondaryCyan.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(SecondaryCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = SecondaryCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "Welcome to MyStream",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "To start streaming high-speed 4K/1080p movies and series from your cloud storage, please configure your PostgreSQL database & PikPak credentials.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val laterInteraction = remember { MutableInteractionSource() }
                        val isLaterFocused by laterInteraction.collectIsFocusedAsState()

                        OutlinedButton(
                            onClick = onDismiss,
                            interactionSource = laterInteraction,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isLaterFocused) FocusRingOrange.copy(alpha = 0.2f) else Color.Transparent,
                                contentColor = if (isLaterFocused) FocusRingOrange else TextSecondary
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isLaterFocused) 2.5.dp else 1.dp,
                                if (isLaterFocused) FocusRingOrange else Color(0x33FFFFFF)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Later", color = if (isLaterFocused) FocusRingOrange else TextSecondary)
                        }

                        val configInteraction = remember { MutableInteractionSource() }
                        val isConfigFocused by configInteraction.collectIsFocusedAsState()

                        Button(
                            onClick = {
                                onDismiss()
                                onNavigateToSettings()
                            },
                            interactionSource = configInteraction,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(configureFR),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConfigFocused) FocusRingOrange else PrimaryNeon,
                                contentColor = if (isConfigFocused) Color.Black else Color.White
                            ),
                            border = if (isConfigFocused) androidx.compose.foundation.BorderStroke(2.5.dp, FocusRingOrange) else null,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Set Up Credentials",
                                fontWeight = FontWeight.Bold,
                                color = if (isConfigFocused) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
