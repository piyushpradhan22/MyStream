package com.mystream.app.ui.screens

import androidx.compose.foundation.background
import com.mystream.app.ui.utils.safeRequestFocus
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mystream.app.data.model.StremioMetaPreview
import com.mystream.app.data.repository.SourcesRepository
import com.mystream.app.ui.components.PosterCard
import com.mystream.app.ui.theme.BgDark
import com.mystream.app.ui.theme.FocusRingOrange
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SurfaceDark
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object SearchStateHolder {
    var query: String = ""
    var rawResults: List<StremioMetaPreview> = emptyList()
    var selectedFilter: String = "All"
    var trendingItems: List<StremioMetaPreview> = emptyList()
}

@Composable
fun SearchScreen(
    repository: SourcesRepository,
    onBack: () -> Unit,
    onNavigateToDetail: (type: String, id: String) -> Unit,
    initialQuery: String = ""
) {
    val initialSearchQuery = if (initialQuery.isNotBlank()) initialQuery else SearchStateHolder.query
    var query by remember { mutableStateOf(initialSearchQuery) }
    var rawResults by remember { mutableStateOf(if (initialQuery.isNotBlank() && initialQuery != SearchStateHolder.query) emptyList() else SearchStateHolder.rawResults) }
    var trendingItems by remember { mutableStateOf(SearchStateHolder.trendingItems) }
    var selectedFilter by remember { mutableStateOf(SearchStateHolder.selectedFilter) }
    var isSearching by remember { mutableStateOf(false) }
    var isSearchEditing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val searchFocusRequester = remember { FocusRequester() }
    var isSearchFieldFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val isEditing = isImeVisible || isSearchEditing

    LaunchedEffect(query, rawResults, selectedFilter, trendingItems) {
        SearchStateHolder.query = query
        SearchStateHolder.rawResults = rawResults
        SearchStateHolder.selectedFilter = selectedFilter
        SearchStateHolder.trendingItems = trendingItems
    }

    fun enterSearchEditingMode() {
        isSearchEditing = true
        scope.launch {
            repeat(3) {
                if (!isSearchFieldFocused) {
                    searchFocusRequester.safeRequestFocus()
                }
                delay(40)
            }
        }
    }

    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) {
            isSearchEditing = false
        }
    }

    LaunchedEffect(Unit) {
        if (query.isBlank()) {
            delay(120)
            searchFocusRequester.safeRequestFocus()
        }
    }

    LaunchedEffect(Unit) {
        if (trendingItems.isEmpty()) {
            try {
                val movies = repository.fetchCatalog("movie", "top", skip = 0).metas
                val series = repository.fetchCatalog("series", "top", skip = 0).metas
                trendingItems = (movies + series).distinctBy { it.id }
            } catch (_: Exception) {
            }
        }
    }

    fun performSearch(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) {
            searchJob?.cancel()
            rawResults = emptyList()
            isSearching = false
            return
        }

        searchJob?.cancel()
        searchJob = scope.launch {
            delay(250)
            isSearching = true
            try {
                // 1. Instant local search in Indian catalog if query matches
                val localMatches = repository.searchIndianCatalog(clean)

                // 2. Fetch Cinemeta movie and series catalogs concurrently with individual error handling
                val movieDeferred = async(Dispatchers.IO) {
                    try {
                        repository.fetchCatalog("movie", "top", search = clean).metas
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                val seriesDeferred = async(Dispatchers.IO) {
                    try {
                        repository.fetchCatalog("series", "top", search = clean).metas
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                val movieResults = movieDeferred.await()
                val seriesResults = seriesDeferred.await()

                val combined = (localMatches + movieResults + seriesResults).distinctBy { it.id }
                if (isActive) {
                    rawResults = combined
                }
            } catch (e: CancellationException) {
                // DO NOT wipe rawResults on cancellation! A newer search job is already running.
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SearchScreen", "Search error for '$clean'", e)
                if (isActive) {
                    rawResults = emptyList()
                }
            } finally {
                if (isActive) {
                    isSearching = false
                }
            }
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            delay(150)
            performSearch(initialQuery)
        }
    }

    val displayedResults = remember(rawResults, selectedFilter) {
        when (selectedFilter) {
            "Movies" -> rawResults.filter { it.type.equals("movie", ignoreCase = true) }
            "Series" -> rawResults.filter { it.type.equals("series", ignoreCase = true) }
            else -> rawResults
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val backInteraction = remember { MutableInteractionSource() }
                val isBackFocused by backInteraction.collectIsFocusedAsState()

                IconButton(
                    onClick = onBack,
                    interactionSource = backInteraction,
                    modifier = Modifier
                        .focusProperties { canFocus = !isEditing }
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isBackFocused) FocusRingOrange.copy(alpha = 0.25f) else Color.Transparent)
                        .border(
                            if (isBackFocused) 2.dp else 0.dp,
                            if (isBackFocused) FocusRingOrange else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (isBackFocused) FocusRingOrange else TextPrimary
                    )
                }

                val searchInteractionSource = remember { MutableInteractionSource() }
                val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()

                LaunchedEffect(isSearchFieldFocused, isSearchEditing) {
                    if (isSearchFieldFocused && isSearchEditing) {
                        keyboardController?.show()
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        performSearch(it)
                    },
                    placeholder = { Text("Search Cinemeta (movies, series)...", color = TextMuted) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = if (isSearchFocused || query.isNotEmpty()) FocusRingOrange else TextSecondary
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                rawResults = emptyList()
                                searchFocusRequester.safeRequestFocus()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    interactionSource = searchInteractionSource,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            performSearch(query)
                            isSearchEditing = false
                            keyboardController?.hide()
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = FocusRingOrange,
                        unfocusedBorderColor = Color(0x33FFFFFF)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester)
                        .onFocusChanged { isSearchFieldFocused = it.isFocused }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                        enterSearchEditingMode()
                                        true
                                    }

                                    Key.Back -> {
                                        isSearchEditing = false
                                        keyboardController?.hide()
                                        false
                                    }

                                    else -> false
                                }
                            } else false
                        }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                listOf("All", "Movies", "Series").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val chipInteraction = remember { MutableInteractionSource() }
                    val isChipFocused by chipInteraction.collectIsFocusedAsState()

                    Box(
                        modifier = Modifier
                            .focusProperties { canFocus = !isEditing }
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isChipFocused) FocusRingOrange.copy(alpha = 0.25f)
                                else if (isSelected) PrimaryNeon.copy(alpha = 0.25f)
                                else SurfaceDark
                            )
                            .border(
                                width = if (isChipFocused) 2.5.dp else 1.dp,
                                color = if (isChipFocused) FocusRingOrange
                                else if (isSelected) PrimaryNeon
                                else Color(0x22FFFFFF),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .focusable(interactionSource = chipInteraction)
                            .clickable(interactionSource = chipInteraction, indication = null) {
                                selectedFilter = filter
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (isChipFocused) FocusRingOrange else if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected || isChipFocused) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryNeon)
                }
            } else if (displayedResults.isEmpty() && query.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No results found for \"$query\"",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching with a different title or keyword",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (query.isBlank()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Popular & Trending",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 105.dp),
                        contentPadding = PaddingValues(bottom = 30.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(trendingItems, key = { it.id }) { item ->
                            PosterCard(
                                item = item,
                                modifier = Modifier.focusProperties { canFocus = !isEditing },
                                width = 110,
                                onClick = { onNavigateToDetail(item.type, item.id) }
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 105.dp),
                    contentPadding = PaddingValues(bottom = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedResults, key = { it.id }) { item ->
                        PosterCard(
                            item = item,
                            modifier = Modifier.focusProperties { canFocus = !isEditing },
                            width = 110,
                            onClick = { onNavigateToDetail(item.type, item.id) }
                        )
                    }
                }
            }
        }
    }
}
