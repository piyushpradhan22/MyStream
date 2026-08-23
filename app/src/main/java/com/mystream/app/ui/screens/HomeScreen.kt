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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.mystream.app.ui.components.ExitConfirmationDialog
import com.mystream.app.ui.theme.FocusRingOrange
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
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

    val row1FirstItemFR = remember { FocusRequester() }
    val row1SeeMoreFR = remember { FocusRequester() }

    val row2FirstItemFR = remember { FocusRequester() }
    val row2SeeMoreFR = remember { FocusRequester() }

    val row3FirstItemFR = remember { FocusRequester() }
    val row3SeeMoreFR = remember { FocusRequester() }

    val row4FirstItemFR = remember { FocusRequester() }
    val row4SeeMoreFR = remember { FocusRequester() }

    val row5FirstItemFR = remember { FocusRequester() }
    val row5SeeMoreFR = remember { FocusRequester() }

    var topMovies by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var topSeries by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var actionMovies by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var scifiMovies by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var comedySeries by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }

    var featuredItem by remember { mutableStateOf<StremioMetaPreview?>(null) }
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
                (context as? Activity)?.finishAffinity()
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
        isLoading = true
        loadError = null
        try {
            coroutineScope {
                launch(Dispatchers.IO) {
                    try {
                        val moviesRes = repository.fetchCatalog("movie", "top", skip = 0)
                        topMovies = moviesRes.metas
                        if (featuredItem == null) {
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
                            onPlayClick = {
                                onNavigateToDetail(hero.type, hero.id)
                            },
                            height = 340
                        )
                    }

                    // Floating App Bar at Top
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp)
                            .padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.CenterStart),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryNeon),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "MyStream",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        IconButton(
                            onClick = onNavigateToSearch,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = TextSecondary
                            )
                        }

                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(onClick = { showCustomUrlDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.AddLink,
                                    contentDescription = "Play Custom URL / Magnet",
                                    tint = PrimaryNeon
                                )
                            }

                            IconButton(onClick = onNavigateToSources) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings & Providers",
                                    tint = TextSecondary
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
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(continueWatchingList, key = { it.mediaId }) { record ->
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
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(watchlist, key = { it.id }) { item ->
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

                // Category 1: Popular Movies
                if (topMovies.isNotEmpty()) {
                    item {
                        MediaCatalogRow(
                            title = "Popular Movies",
                            items = topMovies,
                            firstItemFocusRequester = row1FirstItemFR,
                            seeMoreFocusRequester = row1SeeMoreFR,
                            onNavigateUpFromSeeMore = {
                                try { heroPlayFocusRequester.requestFocus() } catch (_: Exception) {}
                            },
                            onNavigateDownFromCards = {
                                try { row2SeeMoreFR.requestFocus() } catch (_: Exception) {}
                            },
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
                            firstItemFocusRequester = row2FirstItemFR,
                            seeMoreFocusRequester = row2SeeMoreFR,
                            onNavigateUpFromSeeMore = {
                                try { row1FirstItemFR.requestFocus() } catch (_: Exception) {}
                            },
                            onNavigateDownFromCards = {
                                try { row3SeeMoreFR.requestFocus() } catch (_: Exception) {}
                            },
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
                            firstItemFocusRequester = row3FirstItemFR,
                            seeMoreFocusRequester = row3SeeMoreFR,
                            onNavigateUpFromSeeMore = {
                                try { row2FirstItemFR.requestFocus() } catch (_: Exception) {}
                            },
                            onNavigateDownFromCards = {
                                try { row4SeeMoreFR.requestFocus() } catch (_: Exception) {}
                            },
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
                            firstItemFocusRequester = row4FirstItemFR,
                            seeMoreFocusRequester = row4SeeMoreFR,
                            onNavigateUpFromSeeMore = {
                                try { row3FirstItemFR.requestFocus() } catch (_: Exception) {}
                            },
                            onNavigateDownFromCards = {
                                try { row5SeeMoreFR.requestFocus() } catch (_: Exception) {}
                            },
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
                            firstItemFocusRequester = row5FirstItemFR,
                            seeMoreFocusRequester = row5SeeMoreFR,
                            onNavigateUpFromSeeMore = {
                                try { row4FirstItemFR.requestFocus() } catch (_: Exception) {}
                            },
                            onNavigateDownFromCards = null,
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
    onSeeMoreClick: () -> Unit,
    onItemClick: (StremioMetaPreview) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    seeMoreFocusRequester: FocusRequester = remember { FocusRequester() },
    onNavigateUpFromSeeMore: (() -> Unit)? = null,
    onNavigateDownFromCards: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            val seeMoreInteraction = remember { MutableInteractionSource() }
            val isSeeMoreFocused by seeMoreInteraction.collectIsFocusedAsState()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .focusRequester(seeMoreFocusRequester)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSeeMoreFocused) FocusRingOrange else PrimaryNeon.copy(alpha = 0.15f))
                    .border(
                        width = if (isSeeMoreFocused) 2.5.dp else 1.dp,
                        color = if (isSeeMoreFocused) FocusRingOrange else PrimaryNeon.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .focusable(interactionSource = seeMoreInteraction)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    onSeeMoreClick()
                                    true
                                }
                                Key.DirectionDown -> {
                                    try {
                                        firstItemFocusRequester.requestFocus()
                                        true
                                    } catch (_: Exception) {
                                        false
                                    }
                                }
                                Key.DirectionUp -> {
                                    if (onNavigateUpFromSeeMore != null) {
                                        try {
                                            onNavigateUpFromSeeMore()
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    } else false
                                }
                                else -> false
                            }
                        } else false
                    }
                    .clickable(interactionSource = seeMoreInteraction, indication = null, onClick = onSeeMoreClick)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "See More",
                    color = if (isSeeMoreFocused) Color.Black else PrimaryNeon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (isSeeMoreFocused) Color.Black else PrimaryNeon,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(items, key = { _, meta -> meta.id }) { index, meta ->
                PosterCard(
                    item = meta,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> {
                                        try {
                                            seeMoreFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    }
                                    Key.DirectionDown -> {
                                        if (onNavigateDownFromCards != null) {
                                            try {
                                                onNavigateDownFromCards()
                                                true
                                            } catch (_: Exception) {
                                                false
                                            }
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        },
                    onClick = { onItemClick(meta) }
                )
            }

            item {
                SeeMoreGridCard(onClick = onSeeMoreClick)
            }
        }
    }
}

@Composable
private fun SeeMoreGridCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = if (isFocused) FocusRingOrange else Color(0x22FFFFFF)
    val bgColor = if (isFocused) FocusRingOrange.copy(alpha = 0.15f) else SurfaceDark

    Box(
        modifier = modifier
            .width(135.dp)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (isFocused) 2.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isFocused) FocusRingOrange.copy(alpha = 0.2f) else PrimaryNeon.copy(alpha = 0.2f)),
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
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Open full grid",
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
