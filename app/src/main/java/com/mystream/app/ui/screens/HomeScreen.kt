package com.mystream.app.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import com.mystream.app.ui.utils.appTopBarPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mystream.app.data.model.MediaPlaybackItem
import com.mystream.app.data.model.StremioMetaPreview
import com.mystream.app.data.repository.SourcesRepository
import com.mystream.app.ui.components.CustomUrlDialog
import com.mystream.app.ui.components.HeroBanner
import com.mystream.app.ui.components.PosterCard
import com.mystream.app.ui.theme.BgDark
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SecondaryCyan
import com.mystream.app.ui.theme.SurfaceCard
import com.mystream.app.ui.theme.SurfaceDark
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import com.mystream.app.ui.theme.TextSecondary

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Mic
import com.mystream.app.ui.components.ExitConfirmationDialog
import com.mystream.app.ui.theme.FocusRingOrange
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material.icons.filled.Refresh
import kotlinx.coroutines.withContext

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
    val continueWatchingList by repository.continueWatchingFlow.collectAsState(initial = emptyList())
    val watchlist by repository.watchlistFlow.collectAsState(initial = emptyList())

    val heroPlayFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val continueWatchingFirstItemFR = remember { FocusRequester() }
    val watchlistFirstItemFR = remember { FocusRequester() }

    val dynamicRowFirstItemFRs = remember { mutableMapOf<Int, FocusRequester>() }
    fun getDynamicRowFirstItemFR(index: Int): FocusRequester = dynamicRowFirstItemFRs.getOrPut(index) { FocusRequester() }

    val row1FirstItemFR = remember { FocusRequester() }
    val row2FirstItemFR = remember { FocusRequester() }
    val row3FirstItemFR = remember { FocusRequester() }
    val row4FirstItemFR = remember { FocusRequester() }
    val row5FirstItemFR = remember { FocusRequester() }
    val rowHfFirstItemFR = remember { FocusRequester() }

    val rowScrollStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }
    val continueWatchingListState = rememberLazyListState()
    val watchlistListState = rememberLazyListState()

    var hfCatalogItems by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var indianCategories by remember { mutableStateOf<List<Pair<String, List<StremioMetaPreview>>>>(emptyList()) }

    var topMovies by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var topSeries by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var actionMovies by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var scifiMovies by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var comedySeries by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }

    var featuredItem by remember { mutableStateOf<StremioMetaPreview?>(null) }
    var featuredPool by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var featuredIndex by remember { mutableIntStateOf(0) }
    var heroAutoAdvanceTrigger by remember { mutableLongStateOf(0L) }

    // Automatic smooth rotation of suggestion cards every 6.5s
    LaunchedEffect(featuredPool, heroAutoAdvanceTrigger) {
        if (featuredPool.size > 1) {
            while (true) {
                delay(6500)
                featuredIndex = (featuredIndex + 1) % featuredPool.size
                featuredItem = featuredPool[featuredIndex]
            }
        }
    }
    var isLoading by remember { mutableStateOf(true) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var showExitConfirmationDialog by remember { mutableStateOf(false) }
    var showSetupCredentialsDialog by remember { mutableStateOf(false) }

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

    // Intercept back button on Home to ask for exit confirmation
    BackHandler(enabled = true) {
        showExitConfirmationDialog = true
    }

    if (showExitConfirmationDialog) {
        ExitConfirmationDialog(
            onConfirmExit = {
                (context as? Activity)?.let { act ->
                    act.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(0)
                }
            },
            onDismiss = {
                showExitConfirmationDialog = false
            }
        )
    }

    val listState = rememberLazyListState()

    LaunchedEffect(isLoading, featuredItem) {
        if (!isLoading && featuredItem != null) {
            kotlinx.coroutines.delay(100)
            try {
                heroPlayFocusRequester.requestFocus()
                kotlinx.coroutines.delay(50)
                listState.scrollToItem(0, 0)
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    LaunchedEffect(reloadTrigger) {
        if (topMovies.isEmpty() && indianCategories.isEmpty()) {
            isLoading = true
        }
        loadError = null
        try {
            coroutineScope {
                launch(Dispatchers.IO) {
                    try {
                        val allIndian = repository.getAllIndianCategories()
                        indianCategories = allIndian
                        val firstCatItems = allIndian.firstOrNull()?.second
                        if (featuredItem == null && !firstCatItems.isNullOrEmpty()) {
                            featuredItem = firstCatItems.first()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Error loading all Indian categories", e)
                    }
                }

                launch(Dispatchers.IO) {
                    try {
                        val moviesRes = repository.fetchCatalog("movie", "top", skip = 0)
                        topMovies = moviesRes.metas
                        if (featuredItem == null && moviesRes.metas.isNotEmpty()) {
                            featuredItem = moviesRes.metas.firstOrNull()
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
                        android.util.Log.e("HomeScreen", "Error loading top series", e)
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
                        android.util.Log.e("HomeScreen", "Error loading scifi movies", e)
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

                launch(Dispatchers.IO) {
                    try {
                        val hfRes = repository.fetchHfCatalog(skip = 0, limit = 30)
                        hfCatalogItems = hfRes.metas
                        if (featuredItem == null && hfRes.metas.isNotEmpty()) {
                            featuredItem = hfRes.metas.firstOrNull()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Error loading HuggingFace catalog", e)
                    }
                }
            }

            // Build randomized pool of featured cards once at boot and keep fixed throughout the session
            val existingBootPool = repository.bootFeaturedPool
            if (!existingBootPool.isNullOrEmpty()) {
                featuredPool = existingBootPool
                featuredItem = existingBootPool.firstOrNull()
            } else {
                val allMetas = (hfCatalogItems + indianCategories.flatMap { it.second } + topMovies + topSeries + actionMovies + scifiMovies)
                    .filter { !it.background.isNullOrBlank() || !it.poster.isNullOrBlank() }
                    .distinctBy { it.id }
                    .shuffled()

                if (allMetas.isNotEmpty()) {
                    repository.bootFeaturedPool = allMetas
                    featuredPool = allMetas
                    featuredItem = allMetas.first()
                }
            }
        } finally {
            isLoading = false
        }
    }

    if (showCustomUrlDialog) {
        CustomUrlDialog(
            onDismiss = { showCustomUrlDialog = false },
            onPlay = { item ->
                onPlayDirect(item)
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // Combined Cinematic Top Section (Header + Hero Banner in item 0)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    // Hero Banner content
                    featuredItem?.let { hero ->
                        HeroBanner(
                            item = hero,
                            playFocusRequester = heroPlayFocusRequester,
                            onNavigateUp = {
                                try {
                                    searchFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                            },
                            onNavigateDown = {
                                try {
                                    if (continueWatchingList.isNotEmpty()) {
                                        continueWatchingFirstItemFR.requestFocus()
                                    } else if (watchlist.isNotEmpty()) {
                                        watchlistFirstItemFR.requestFocus()
                                    } else if (hfCatalogItems.isNotEmpty()) {
                                        rowHfFirstItemFR.requestFocus()
                                    } else if (indianCategories.isNotEmpty()) {
                                        getDynamicRowFirstItemFR(0).requestFocus()
                                    } else {
                                        row1FirstItemFR.requestFocus()
                                    }
                                } catch (_: Exception) {}
                            },
                            onNavigateLeft = {
                                if (featuredPool.size > 1) {
                                    featuredIndex = (featuredIndex - 1 + featuredPool.size) % featuredPool.size
                                    featuredItem = featuredPool[featuredIndex]
                                    heroAutoAdvanceTrigger++
                                }
                            },
                            onNavigateRight = {
                                if (featuredPool.size > 1) {
                                    featuredIndex = (featuredIndex + 1) % featuredPool.size
                                    featuredItem = featuredPool[featuredIndex]
                                    heroAutoAdvanceTrigger++
                                }
                            },
                            onSelectIndex = { targetIdx ->
                                if (targetIdx in featuredPool.indices) {
                                    featuredIndex = targetIdx
                                    featuredItem = featuredPool[targetIdx]
                                    heroAutoAdvanceTrigger++
                                }
                            },
                            itemCount = featuredPool.size,
                            currentIndex = featuredIndex,
                            onPlayClick = {
                                onNavigateToDetail(hero.type, hero.id)
                            },
                            height = 340
                        )
                    }

                    // Top App Bar: Responsive dynamic layout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .appTopBarPadding(additionalTop = 10.dp)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Brand Logo Icon Only (Text label removed)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryNeon),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "MyStream",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Dynamically resizing Search Bar (fits portrait mobile and landscape TV)
                        val searchInteraction = remember { MutableInteractionSource() }
                        val isSearchFocused by searchInteraction.collectIsFocusedAsState()

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSearchFocused) FocusRingOrange.copy(alpha = 0.22f) else Color(0x6607090E))
                                .border(
                                    width = if (isSearchFocused) 2.dp else 1.dp,
                                    color = if (isSearchFocused) FocusRingOrange else Color(0x33FFFFFF),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .focusRequester(searchFocusRequester)
                                .focusable(interactionSource = searchInteraction)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                onNavigateToSearch()
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .clickable(interactionSource = searchInteraction, indication = null, onClick = onNavigateToSearch)
                                .padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (isSearchFocused) FocusRingOrange else SecondaryCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Search movies, series, cast...",
                                color = if (isSearchFocused) TextPrimary else TextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Right Action Buttons (Custom URL & Settings)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { showCustomUrlDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddLink,
                                    contentDescription = "Play Custom URL / Magnet",
                                    tint = PrimaryNeon,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = onNavigateToSources,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings & Providers",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

                // Continue Watching Section
                if (continueWatchingList.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
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
                                    Text(
                                        text = "Continue Watching",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(PrimaryNeon.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${continueWatchingList.size}",
                                            color = PrimaryNeon,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Clear All button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceCard)
                                        .clickable {
                                            scope.launch {
                                                repository.clearAllPlaybackProgress()
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Clear All",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            LazyRow(
                                state = continueWatchingListState,
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(continueWatchingList, key = { _, record -> record.mediaId }) { index, record ->
                                    val preview = StremioMetaPreview(
                                        id = record.imdbId,
                                        type = record.type,
                                        name = record.title,
                                        poster = record.posterUrl,
                                        genres = listOfNotNull(record.subtitle)
                                    )
                                    PosterCard(
                                        item = preview,
                                        progressFraction = record.progressFraction,
                                        modifier = if (index == 0) Modifier.focusRequester(continueWatchingFirstItemFR) else Modifier,
                                        onClearClick = {
                                            scope.launch {
                                                repository.removePlaybackProgress(record.mediaId)
                                            }
                                        },
                                        onClick = {
                                            onNavigateToDetail(record.type, record.imdbId)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Watchlist Section
                if (watchlist.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
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
                                    Text(
                                        text = "My Watchlist",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SecondaryCyan.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${watchlist.size}",
                                            color = SecondaryCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Clear All button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceCard)
                                        .clickable {
                                            scope.launch {
                                                repository.clearAllWatchlist()
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Clear All",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            LazyRow(
                                state = watchlistListState,
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(watchlist, key = { _, item -> item.id }) { index, item ->
                                    val preview = StremioMetaPreview(
                                        id = item.imdbId,
                                        type = item.type,
                                        name = item.title,
                                        poster = item.posterUrl,
                                        genres = listOfNotNull(item.subtitle ?: item.torrentQuality)
                                    )
                                    PosterCard(
                                        item = preview,
                                        progressFraction = null,
                                        modifier = if (index == 0) Modifier.focusRequester(watchlistFirstItemFR) else Modifier,
                                        onClearClick = {
                                            scope.launch {
                                                repository.removeFromWatchlist(item.id)
                                            }
                                        },
                                        onClick = {
                                            onNavigateToDetail(item.type, item.imdbId)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // HuggingFace Direct Catalog Row (Instant Streams)
                if (hfCatalogItems.isNotEmpty()) {
                    item {
                        MediaCatalogRow(
                            title = "⚡ HuggingFace Direct (Instant Streams)",
                            items = hfCatalogItems,
                            scrollState = rowScrollStates.getOrPut("cat_hf_direct") { androidx.compose.foundation.lazy.LazyListState() },
                            firstItemFocusRequester = rowHfFirstItemFR,
                            onSeeMoreClick = {
                                onNavigateToCatalog("⚡ HuggingFace Direct", "movie", "hftor", null)
                            },
                            onItemClick = { item -> onNavigateToDetail(item.type, item.id) }
                        )
                    }
                }

                // All Indian Categories from data.json (preserving exact original names)
                itemsIndexed(indianCategories, key = { _, pair -> "indian_${pair.first}" }) { idx, pair ->
                    val (catKey, catItems) = pair
                    MediaCatalogRow(
                        title = catKey,
                        items = catItems,
                        scrollState = rowScrollStates.getOrPut("indian_$catKey") { androidx.compose.foundation.lazy.LazyListState() },
                        firstItemFocusRequester = getDynamicRowFirstItemFR(idx),
                        onSeeMoreClick = {
                            onNavigateToCatalog(catKey, "movie", "imdb-indian", catKey)
                        },
                        onItemClick = { item -> onNavigateToDetail(item.type, item.id) }
                    )
                }

                // Category 1: Popular Movies
                if (topMovies.isNotEmpty()) {
                    item {
                        MediaCatalogRow(
                            title = "Popular Movies",
                            items = topMovies,
                            scrollState = rowScrollStates.getOrPut("cat_top_movies") { androidx.compose.foundation.lazy.LazyListState() },
                            firstItemFocusRequester = row1FirstItemFR,
                            onSeeMoreClick = {
                                onNavigateToCatalog("Popular Movies", "movie", "top", null)
                            },
                            onItemClick = { item -> onNavigateToDetail(item.type, item.id) }
                        )
                    }
                }

                // Category 2: Popular Series
                if (topSeries.isNotEmpty()) {
                    item {
                        MediaCatalogRow(
                            title = "Popular Series",
                            items = topSeries,
                            scrollState = rowScrollStates.getOrPut("cat_top_series") { androidx.compose.foundation.lazy.LazyListState() },
                            firstItemFocusRequester = row2FirstItemFR,
                            onSeeMoreClick = {
                                onNavigateToCatalog("Popular Series", "series", "top", null)
                            },
                            onItemClick = { item -> onNavigateToDetail(item.type, item.id) }
                        )
                    }
                }

                // Category 3: Action & Adventure
                if (actionMovies.isNotEmpty()) {
                    item {
                        MediaCatalogRow(
                            title = "Action & Adventure",
                            items = actionMovies,
                            scrollState = rowScrollStates.getOrPut("cat_action_movies") { androidx.compose.foundation.lazy.LazyListState() },
                            firstItemFocusRequester = row3FirstItemFR,
                            onSeeMoreClick = {
                                onNavigateToCatalog("Action & Adventure", "movie", "top", "Action")
                            },
                            onItemClick = { item -> onNavigateToDetail(item.type, item.id) }
                        )
                    }
                }

                // Category 4: Sci-Fi Hits
                if (scifiMovies.isNotEmpty()) {
                    item {
                        MediaCatalogRow(
                            title = "Sci-Fi & Thriller",
                            items = scifiMovies,
                            scrollState = rowScrollStates.getOrPut("cat_scifi_movies") { androidx.compose.foundation.lazy.LazyListState() },
                            firstItemFocusRequester = row4FirstItemFR,
                            onSeeMoreClick = {
                                onNavigateToCatalog("Sci-Fi & Thriller", "movie", "top", "Sci-Fi")
                            },
                            onItemClick = { item -> onNavigateToDetail(item.type, item.id) }
                        )
                    }
                }

                // Category 5: Comedy Series
                if (comedySeries.isNotEmpty()) {
                    item {
                        MediaCatalogRow(
                            title = "Binge-Worthy Comedies",
                            items = comedySeries,
                            scrollState = rowScrollStates.getOrPut("cat_comedy_series") { androidx.compose.foundation.lazy.LazyListState() },
                            firstItemFocusRequester = row5FirstItemFR,
                            onSeeMoreClick = {
                                onNavigateToCatalog("Binge-Worthy Comedies", "series", "top", "Comedy")
                            },
                            onItemClick = { item -> onNavigateToDetail(item.type, item.id) }
                        )
                    }
                }

                // If nothing loaded (e.g. offline / slow connection)
                if (topMovies.isEmpty() && topSeries.isEmpty() && continueWatchingList.isEmpty() && watchlist.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = loadError ?: "Unable to fetch catalog items",
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )

                                val retryInteraction = remember { MutableInteractionSource() }
                                val isRetryFocused by retryInteraction.collectIsFocusedAsState()

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isRetryFocused) FocusRingOrange else PrimaryNeon.copy(alpha = 0.2f))
                                        .border(
                                            if (isRetryFocused) 2.5.dp else 1.dp,
                                            if (isRetryFocused) FocusRingOrange else PrimaryNeon,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .focusable(interactionSource = retryInteraction)
                                        .clickable(interactionSource = retryInteraction, indication = null) {
                                            reloadTrigger++
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = if (isRetryFocused) Color.Black else PrimaryNeon,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Retry Loading",
                                            color = if (isRetryFocused) Color.Black else TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
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
private fun MediaCatalogRow(
    title: String,
    items: List<StremioMetaPreview>,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    firstItemFocusRequester: FocusRequester,
    onSeeMoreClick: (() -> Unit)? = null,
    onItemClick: (StremioMetaPreview) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryBadgeIcon(categoryName = title)

            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val displayItems = remember(items) { items.take(15) }

        LazyRow(
            state = scrollState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(displayItems, key = { _, meta -> meta.id }) { index, meta ->
                PosterCard(
                    item = meta,
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                    onClick = { onItemClick(meta) }
                )
            }

            if (onSeeMoreClick != null && items.size >= 8) {
                item(key = "seemore_${title}") {
                    SeeMoreGridCard(
                        title = title,
                        onClick = onSeeMoreClick
                    )
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

    val borderColor = if (isFocused) FocusRingOrange else Color(0x22FFFFFF)
    val bgColor = if (isFocused) FocusRingOrange.copy(alpha = 0.18f) else SurfaceDark

    Box(
        modifier = modifier
            .width(140.dp)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(if (isFocused) 2.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
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
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isFocused) FocusRingOrange.copy(alpha = 0.25f) else PrimaryNeon.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = null,
                    tint = if (isFocused) FocusRingOrange else PrimaryNeon,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "See All",
                color = if (isFocused) FocusRingOrange else TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Explore full list",
                color = TextMuted,
                fontSize = 10.5.sp
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
        kotlinx.coroutines.delay(150)
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

@Composable
private fun CategoryBadgeIcon(categoryName: String) {
    val clean = categoryName.trim()
    when {
        clean.contains("Netflix", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE50914))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "N",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }
        clean.contains("Prime", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF00A8E1))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "prime",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
        clean.contains("Disney", ignoreCase = true) || clean.contains("Hotstar", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F1035))
                    .border(1.dp, Color(0xFF1E88E5), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Disney+",
                    color = Color(0xFF90CAF9),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
        clean.contains("Jio", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE50055))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Jio",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
        clean.contains("Zee5", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF8224E3))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ZEE5",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp
                )
            }
        }
        clean.contains("Sony", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFF6900))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LIV",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }
        }
        clean.equals("Top Rated", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF5C518))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "★ TOP",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
        clean.contains("Hindi", ignoreCase = true) -> {
            Text(
                text = "🇮🇳",
                fontSize = 16.sp
            )
        }
        clean.contains("Series", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x3300E5FF))
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SERIES",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
        clean.contains("Movie", ignoreCase = true) -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x336C5CE7))
                    .border(1.dp, Color(0xFF6C5CE7), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MOVIE",
                    color = Color(0xFFA29BFE),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}
