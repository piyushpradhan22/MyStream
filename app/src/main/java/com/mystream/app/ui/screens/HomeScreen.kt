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
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    val homeCache = remember { repository.homeCatalog }
    var topMovies by remember { mutableStateOf(homeCache.topMovies) }
    var topSeries by remember { mutableStateOf(homeCache.topSeries) }
    var hfCatalogItems by remember { mutableStateOf(homeCache.hfCatalogItems) }
    var indianCategories by remember { mutableStateOf(homeCache.indianCategories) }
    var actionMovies by remember { mutableStateOf(homeCache.actionMovies) }
    var scifiMovies by remember { mutableStateOf(homeCache.scifiMovies) }
    var comedySeries by remember { mutableStateOf(homeCache.comedySeries) }
    var isLoading by remember { mutableStateOf(!homeCache.loaded) }

    // Category Selection State
    var selectedCategoryId by rememberSaveable {
        mutableStateOf(
            if (continueWatchingList.isNotEmpty()) {
                "continue_watching"
            } else {
                val moviesIdx = homeCache.indianCategories.indexOfFirst { it.first.equals("Movies", ignoreCase = true) }
                if (moviesIdx >= 0) "indian_$moviesIdx" else "trending"
            }
        )
    }
    val sidebarExitFocusRequester = remember { FocusRequester() }
    var sidebarFocusable by remember { mutableStateOf(false) }
    // True whenever focus is anywhere inside the left sidebar (not just Exit App).
    var isSidebarFocused by remember { mutableStateOf(false) }

    // Hero Spotlight State
    var focusedItem by remember { mutableStateOf<StremioMetaPreview?>(null) }
    var currentTrailerYtId by remember { mutableStateOf<String?>(null) }
    // User requested: "Make sure background trailer audio is playing"
    var isTrailerAudioMuted by remember { mutableStateOf(false) }

    // Focus Management
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    val playHeroFocusRequester = remember { FocusRequester() }
    var focusedCardIndex by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val cardFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val categorySidebarFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val carouselListState = rememberLazyListState(initialFirstVisibleItemIndex = focusedCardIndex)
    var isLoadingMoreCategoryItems by remember { mutableStateOf(false) }
    var isRestoringFocus by remember { mutableStateOf(false) }

    var lastActiveCategoryId by remember { mutableStateOf(selectedCategoryId) }
    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != lastActiveCategoryId) {
            lastActiveCategoryId = selectedCategoryId
            // NOTE: do NOT clear cardFocusRequesters here — the new category's cards compose (and
            // register their requesters) BEFORE this effect runs, so clearing would wipe them and
            // leave DOWN-from-hero unable to focus any card. Requesters are index-keyed and reusable.
            focusedCardIndex = 0
            try {
                carouselListState.scrollToItem(0)
            } catch (_: Exception) {}
        }
    }

    // Fetch Catalogs in Parallel (skipped if already cached from a previous visit)
    LaunchedEffect(Unit) {
        if (homeCache.loaded) {
            isLoading = false
            return@LaunchedEffect
        }
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
            homeCache.loaded = true
        }
    }

    // Persist loaded catalog lists to the app-scoped cache so they survive navigation.
    LaunchedEffect(topMovies, topSeries, hfCatalogItems, indianCategories, actionMovies, scifiMovies, comedySeries) {
        homeCache.topMovies = topMovies
        homeCache.topSeries = topSeries
        homeCache.hfCatalogItems = hfCatalogItems
        homeCache.indianCategories = indianCategories
        homeCache.actionMovies = actionMovies
        homeCache.scifiMovies = scifiMovies
        homeCache.comedySeries = comedySeries
    }

    // Initial category (once per app run): Continue Watching if items exist, else the Indian "Movies" category.
    LaunchedEffect(continueWatchingList, indianCategories) {
        if (homeCache.initialCategorySelected) return@LaunchedEffect
        if (continueWatchingList.isNotEmpty()) {
            selectedCategoryId = "continue_watching"
            homeCache.initialCategorySelected = true
        } else {
            val moviesIdx = indianCategories.indexOfFirst { it.first.equals("Movies", ignoreCase = true) }
            if (moviesIdx >= 0) {
                selectedCategoryId = "indian_$moviesIdx"
                homeCache.initialCategorySelected = true
            }
        }
    }

    // Enrich Continue Watching items with IMDB metadata in background
    var enrichedCwMeta by remember { mutableStateOf<Map<String, StremioMetaPreview>>(emptyMap()) }
    LaunchedEffect(continueWatchingList) {
        if (continueWatchingList.isEmpty()) return@LaunchedEffect
        // Fetch all titles concurrently (each call is repository-cached) so the row
        // populates in one round-trip's time instead of serially per title.
        val enriched = coroutineScope {
            continueWatchingList.map { record ->
                async(Dispatchers.IO) {
                    try {
                        val meta = repository.fetchMetaDetail(record.type, record.imdbId)
                        record.imdbId to StremioMetaPreview(
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
                    } catch (_: Exception) { null }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        enrichedCwMeta = enriched
    }



    // Debounced Background Trailer Loading with Audio & Metadata Enrichment
    LaunchedEffect(focusedItem?.id) {
        currentTrailerYtId = null
        val item = focusedItem ?: return@LaunchedEffect
        // Skip trailer autoplay for the first card shown after returning from Detail; enrich metadata only.
        val suppressAutoplay = homeCache.suppressNextAutoplay
        if (suppressAutoplay) homeCache.suppressNextAutoplay = false
        // 900ms debounce ensures rapid D-pad scrolling is silky smooth
        delay(900)
        try {
            val meta = repository.fetchMetaDetail(item.type, item.id)
            focusedItem = focusedItem?.copy(
                imdbRating = if (!meta.imdbRating.isNullOrBlank()) meta.imdbRating else focusedItem?.imdbRating,
                year = meta.year ?: meta.releaseInfo ?: focusedItem?.year,
                releaseInfo = meta.releaseInfo ?: meta.year ?: focusedItem?.releaseInfo,
                description = if (!meta.description.isNullOrBlank()) meta.description else focusedItem?.description,
                genres = if (meta.genres.isNotEmpty()) meta.genres else focusedItem?.genres ?: emptyList(),
                background = meta.background ?: focusedItem?.background
            )
            if (!suppressAutoplay) {
                var trailerId = meta.effectiveTrailerYtId
                if (trailerId.isNullOrBlank()) {
                    // Autoplay Hindi trailer fallback by default if official is unavailable
                    trailerId = repository.searchYouTubeTrailer(meta.name, meta.year, "Hindi")
                }
                currentTrailerYtId = trailerId
            }
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
            list.add(OttCategory("hf_direct", "HF Direct", type = "movie", catalogId = "hftor"))
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

    // Intercept back button: move focus to the sidebar's currently active category first (never Exit
    // App directly), then show the exit dialog only if focus is already inside the sidebar.
    BackHandler(enabled = true) {
        if (!isSidebarFocused) {
            val activeCategoryIndex = categories.indexOfFirst { it.id == selectedCategoryId }.coerceAtLeast(0)
            val targetFR = categorySidebarFocusRequesters[activeCategoryIndex] ?: sidebarExitFocusRequester
            try {
                sidebarFocusable = true
                targetFR.requestFocus()
            } catch (e: Exception) {
                android.util.Log.e("HomeScreenBack", "sidebar focus request threw exception", e)
                showExitConfirmationDialog = true
            }
        } else {
            showExitConfirmationDialog = true
        }
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

    // Live snapshot of the current row's items so background coroutines (e.g. D-pad
    // boundary focus advance) always observe freshly paginated items, not a stale capture.
    val currentItemsState = rememberUpdatedState(currentCategoryItems)

    // On category switch, preview the FIRST card of the new category (hero + trailer) even before
    // it is focused, so it never keeps showing the previously highlighted card. Handles async loads.
    var previewedCategoryId by remember { mutableStateOf(selectedCategoryId) }
    LaunchedEffect(selectedCategoryId, currentCategoryItems) {
        if (selectedCategoryId != previewedCategoryId) {
            val first = currentCategoryItems.firstOrNull()
            if (first != null) {
                previewedCategoryId = selectedCategoryId
                focusedItem = first
            }
        }
    }

    // Robust card focus helper: scrolls to card and requests focus with retry
    suspend fun focusCardAtIndex(index: Int) {
        if (currentCategoryItems.isEmpty()) {
            android.util.Log.d("HomeFocus", "focusCardAtIndex: no items in category, cannot move down")
            return
        }
        val targetIdx = index.coerceIn(0, (currentCategoryItems.size - 1).coerceAtLeast(0))
        focusedCardIndex = targetIdx
        currentCategoryItems.getOrNull(targetIdx)?.let { focusedItem = it }
        // Only scroll if the target card isn't already on-screen, to avoid a jarring re-scroll.
        val alreadyVisible = carouselListState.layoutInfo.visibleItemsInfo.any { it.index == targetIdx }
        if (!alreadyVisible) {
            try {
                carouselListState.scrollToItem(targetIdx)
            } catch (_: Exception) {}
        }
        for (retry in 0..10) {
            delay(50)
            cardFocusRequesters[targetIdx]?.let { if (it.safeRequestFocus()) return }
        }
        // Fallback so DOWN never dead-ends: focus the first on-screen card (or any registered card).
        for (retry in 0..6) {
            delay(50)
            val firstVisible = carouselListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
            val fr = firstVisible?.let { cardFocusRequesters[it] } ?: cardFocusRequesters.entries.minByOrNull { it.key }?.value
            if (fr != null && fr.safeRequestFocus()) {
                android.util.Log.d("HomeFocus", "focusCardAtIndex: target $targetIdx unavailable, focused fallback card")
                return
            }
        }
        android.util.Log.w("HomeFocus", "focusCardAtIndex FAILED (target=$targetIdx, items=${currentCategoryItems.size}, visible=${carouselListState.layoutInfo.visibleItemsInfo.map { it.index }}, requesters=${cardFocusRequesters.keys})")
    }

    // Auto-focus the first card on initial load and explicitly clear any lingering sidebar focus,
    // so a fresh app start never leaves the Exit App button highlighted.
    // The effect must re-run when card requesters become available, otherwise a late composition
    // can leave the app with no focused action at all.
    LaunchedEffect(currentCategoryItems.isNotEmpty(), selectedCategoryId, cardFocusRequesters.size) {
        if (currentCategoryItems.isEmpty()) return@LaunchedEffect

        // Clear any default focus that landed on the left sidebar before the content cards are ready.
        try {
            focusManager.clearFocus(force = true)
        } catch (_: Exception) {}

        if (cardFocusRequesters.containsKey(0)) {
            focusCardAtIndex(0)
        }
    }

    // When the selected category changes, explicitly move focus onto that category's first card.
    LaunchedEffect(selectedCategoryId, currentCategoryItems.size, cardFocusRequesters.size) {
        if (currentCategoryItems.isEmpty()) return@LaunchedEffect

        if (cardFocusRequesters.containsKey(0)) {
            focusCardAtIndex(0)
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

    val endOfCatalogReached = remember { mutableStateMapOf<String, Boolean>().apply { putAll(homeCache.endOfCatalogReached) } }

    // Persist pagination end-flags to the cache so infinite-scroll state survives navigation.
    LaunchedEffect(Unit) {
        snapshotFlow { endOfCatalogReached.toMap() }.collect { m ->
            homeCache.endOfCatalogReached.clear()
            homeCache.endOfCatalogReached.putAll(m)
        }
    }

    // Resilient infinite scroll pagination using snapshotFlow (prevents D-pad scroll cancellation)
    LaunchedEffect(selectedCategoryId) {
        val activeCategoryId = selectedCategoryId
        snapshotFlow {
            // Read the live list (currentItemsState), not the captured `currentCategoryItems`,
            // otherwise items.size stays frozen at capture time and paging stops after one page.
            val items = currentItemsState.value
            val lastVisible = carouselListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // Prefetch well before the end so slow sources (HF/Indian) load in time for smooth continuity.
            val threshold = (items.size - 12).coerceAtLeast(0)
            val nearEnd = items.size >= 8 && (focusedCardIndex >= threshold || lastVisible >= threshold)
            nearEnd to items.size
        }
        .distinctUntilChanged()
        .collect { (nearEnd, snapshotSize) ->
            // Only page the category this effect was launched for; ignore transient state during a switch.
            if (selectedCategoryId != activeCategoryId) return@collect
            val cat = categories.find { it.id == activeCategoryId }
            if (nearEnd && cat != null &&
                cat.id != "continue_watching" && cat.id != "watchlist" &&
                !isLoadingMoreCategoryItems &&
                endOfCatalogReached[cat.id] != true
            ) {
                isLoadingMoreCategoryItems = true
                try {
                    if (cat.id.startsWith("indian_")) {
                        val idx = cat.id.substringAfter("indian_").toIntOrNull() ?: 0
                        val entry = indianCategories.getOrNull(idx)
                        if (entry != null) {
                            val (cName, existing) = entry
                            // Skip is derived from the live list size to guarantee contiguous, gapless paging.
                            val nextSkip = existing.size
                            android.util.Log.d("HomeScreen", "Pagination triggering for ${cat.id} at skip=$nextSkip")
                            val res = repository.fetchIndianCatalog(category = cName, skip = nextSkip, limit = 20)
                            if (res.metas.isNotEmpty()) {
                                val newItems = (existing + res.metas).distinctBy { it.id }
                                if (newItems.size == existing.size) {
                                    endOfCatalogReached[cat.id] = true
                                } else {
                                    val updated = indianCategories.toMutableList()
                                    updated[idx] = cName to newItems
                                    indianCategories = updated
                                }
                            } else {
                                endOfCatalogReached[cat.id] = true
                            }
                        }
                    } else if (cat.id == "hf_direct") {
                        val nextSkip = hfCatalogItems.size
                        android.util.Log.d("HomeScreen", "Pagination triggering for ${cat.id} at skip=$nextSkip")
                        val res = repository.fetchHfCatalog(skip = nextSkip, limit = 30)
                        if (res.metas.isNotEmpty()) {
                            val newItems = (hfCatalogItems + res.metas).distinctBy { it.id }
                            if (newItems.size == hfCatalogItems.size) {
                                endOfCatalogReached[cat.id] = true
                            } else {
                                hfCatalogItems = newItems
                            }
                        } else {
                            endOfCatalogReached[cat.id] = true
                        }
                    } else {
                        val currentList = when (cat.id) {
                            "trending" -> topMovies
                            "series" -> topSeries
                            "action" -> actionMovies
                            "scifi" -> scifiMovies
                            "comedy" -> comedySeries
                            else -> return@collect
                        }
                        val nextSkip = currentList.size
                        android.util.Log.d("HomeScreen", "Pagination triggering for ${cat.id} at skip=$nextSkip")
                        val res = repository.fetchCatalog(
                            type = cat.type,
                            catalogId = cat.catalogId,
                            genre = cat.genre,
                            skip = nextSkip
                        )
                        if (res.metas.isNotEmpty()) {
                            val newItems = (currentList + res.metas).distinctBy { it.id }
                            if (newItems.size == currentList.size) {
                                endOfCatalogReached[cat.id] = true
                            } else {
                                when (cat.id) {
                                    "trending" -> topMovies = newItems
                                    "series" -> topSeries = newItems
                                    "action" -> actionMovies = newItems
                                    "scifi" -> scifiMovies = newItems
                                    "comedy" -> comedySeries = newItems
                                }
                            }
                        } else {
                            endOfCatalogReached[cat.id] = true
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("HomeScreen", "Pagination error for $activeCategoryId", e)
                } finally {
                    isLoadingMoreCategoryItems = false
                }
            }
        }
    }

    // Main OTT Layout: Left Sidebar (nav + categories) + Full-Screen Hero with Carousel Overlaid at the Bottom
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // 1. Left Navigation Rail: Exit App (top) + Search/Custom URL/Settings + all categories (bottom).
        // Collapsed = icons only; expands into a full label bar while any item inside it has focus.
        OttLeftSidebar(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            onSelectCategory = { id ->
                // Switch category and immediately hand focus to that category's first card,
                // so the highlighted item on the right matches the selected left sidebar section.
                selectedCategoryId = id
                scope.launch { focusCardAtIndex(0) }
            },
            onExit = { showExitConfirmationDialog = true },
            onSearch = onNavigateToSearch,
            onCustomUrl = { showCustomUrlDialog = true },
            onSettings = onNavigateToSources,
            onNavigateRight = {
                scope.launch {
                    focusCardAtIndex(focusedCardIndex)
                }
            },
            searchFocusRequester = searchFocusRequester,
            exitFocusRequester = sidebarExitFocusRequester,
            sidebarFocusable = sidebarFocusable,
            onSidebarFocusChanged = { isSidebarFocused = it },
            categoryFocusRequesters = categorySidebarFocusRequesters
        )

        // 2. Main OTT Content: Full-screen Hero Spotlight (trailer plays full screen, same as DetailScreen)
        // with the category carousel overlaid at the very bottom for a seamless, uninterrupted video.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        ) {
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
                    // Return focus to the current carousel card (robust scroll+retry), never the sidebar.
                    scope.launch { focusCardAtIndex(focusedCardIndex) }
                },
                onNavigateLeftToSidebar = {
                    sidebarFocusable = true
                    try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
                },
                watchlistFocusRequester = playHeroFocusRequester,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom overlay: category cards carousel, pinned at the extreme bottom of the full-screen hero
            // (small overscan-safe gap so poster labels stay fully visible).
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                if (isLoading && currentCategoryItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
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
                            .fillMaxWidth()
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
                                                        val activeCategoryIndex = categories.indexOfFirst { it.id == selectedCategoryId }.coerceAtLeast(0)
                                                        val sidebarFR = categorySidebarFocusRequesters[activeCategoryIndex] ?: sidebarExitFocusRequester
                                                        sidebarFocusable = true
                                                        try { sidebarFR.requestFocus(); true } catch (_: Exception) { false }
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
                                                else -> false
                                            }
                                        } else false
                                    },
                                onClick = {
                                    focusedItem = meta
                                    homeCache.suppressNextAutoplay = true
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
