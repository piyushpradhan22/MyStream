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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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

@Composable
fun HomeScreen(
    repository: SourcesRepository,
    onNavigateToDetail: (type: String, id: String) -> Unit,
    onNavigateToCatalog: (title: String, type: String, catalogId: String, genre: String?) -> Unit,
    onPlayDirect: (MediaPlaybackItem) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSources: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val continueWatchingList by repository.continueWatchingFlow.collectAsState(initial = emptyList())
    val watchlist by repository.watchlistFlow.collectAsState(initial = emptyList())

    var topMovies by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var topSeries by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var actionMovies by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var scifiMovies by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var comedySeries by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }

    var featuredItem by remember { mutableStateOf<StremioMetaPreview?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showCustomUrlDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val moviesRes = repository.fetchCatalog("movie", "top", skip = 0)
            topMovies = moviesRes.metas
            featuredItem = moviesRes.metas.firstOrNull()

            val seriesRes = repository.fetchCatalog("series", "top", skip = 0)
            topSeries = seriesRes.metas

            val actionRes = repository.fetchCatalog("movie", "top", genre = "Action", skip = 0)
            actionMovies = actionRes.metas

            val scifiRes = repository.fetchCatalog("movie", "top", genre = "Sci-Fi", skip = 0)
            scifiMovies = scifiRes.metas

            val comedyRes = repository.fetchCatalog("series", "top", genre = "Comedy", skip = 0)
            comedySeries = comedyRes.metas
        } catch (e: Exception) {
            // fallback
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
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryNeon)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // Top Header Row / App Bar with Safe Top Status Bar Inset
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp)
                            .padding(top = 18.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
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

                        Row(
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

                            IconButton(onClick = onNavigateToSearch) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = TextSecondary
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

                // Hero Banner
                featuredItem?.let { hero ->
                    item {
                        HeroBanner(
                            item = hero,
                            onPlayClick = {
                                onNavigateToDetail(hero.type, hero.id)
                            },
                            onDetailsClick = {
                                onNavigateToDetail(hero.type, hero.id)
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
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
                            onSeeMoreClick = {
                                onNavigateToCatalog("Binge-Worthy Comedies", "series", "top", "Comedy")
                            },
                            onItemClick = { item -> onNavigateToDetail(item.type, item.id) }
                        )
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
    modifier: Modifier = Modifier
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeMoreClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "See More",
                    color = SecondaryCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = SecondaryCyan,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.id }) { meta ->
                PosterCard(
                    item = meta,
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

    val borderColor = if (isFocused) PrimaryNeon else Color(0x22FFFFFF)
    val bgColor = if (isFocused) PrimaryNeon.copy(alpha = 0.15f) else SurfaceDark

    Box(
        modifier = modifier
            .width(135.dp)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
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
                    .background(PrimaryNeon.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = null,
                    tint = PrimaryNeon,
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
