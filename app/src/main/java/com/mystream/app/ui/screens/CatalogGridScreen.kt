package com.mystream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CatalogGridScreen(
    title: String,
    type: String,
    catalogId: String = "top",
    genre: String? = null,
    repository: SourcesRepository,
    onBack: () -> Unit,
    onNavigateToDetail: (type: String, id: String) -> Unit
) {
    var items by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var skipCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }

    val firstItemFocusRequester = remember { FocusRequester() }

    val cleanGenre = remember(genre) {
        if (genre.isNullOrBlank() || genre == "null" || genre == "{genre}" || genre == "none") null else genre
    }

    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // Initial load
    LaunchedEffect(type, catalogId, cleanGenre) {
        isLoading = true
        skipCount = 0
        try {
            val res = repository.fetchCatalog(type = type, catalogId = catalogId, genre = cleanGenre, skip = 0)
            items = res.metas
            skipCount = res.metas.size
            hasMore = res.metas.size >= 20
        } catch (e: Exception) {
            items = emptyList()
            hasMore = false
        } finally {
            isLoading = false
        }
    }

    // Auto focus first item on TV when items load
    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty()) {
            delay(150)
            try {
                firstItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    fun loadMore() {
        if (isLoadingMore || !hasMore || isLoading) return
        isLoadingMore = true
        scope.launch {
            try {
                val nextSkip = skipCount
                val res = repository.fetchCatalog(type = type, catalogId = catalogId, genre = cleanGenre, skip = nextSkip)
                val newMetas = res.metas.filter { n -> items.none { it.id == n.id } }
                if (newMetas.isNotEmpty()) {
                    items = items + newMetas
                    skipCount = nextSkip + newMetas.size
                    hasMore = newMetas.size >= 20
                } else {
                    hasMore = false
                }
            } catch (e: Exception) {
                hasMore = false
            } finally {
                isLoadingMore = false
            }
        }
    }

    // Trigger loadMore proactively when user reaches near the end of the loaded grid
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && !isLoadingMore && !isLoading) {
            loadMore()
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
                .padding(top = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val backInteraction = remember { MutableInteractionSource() }
                    val isBackFocused by backInteraction.collectIsFocusedAsState()

                    IconButton(
                        onClick = onBack,
                        interactionSource = backInteraction,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isBackFocused) FocusRingOrange.copy(alpha = 0.25f) else SurfaceDark)
                            .border(
                                if (isBackFocused) 2.dp else 1.dp,
                                if (isBackFocused) FocusRingOrange else Color(0x22FFFFFF),
                                RoundedCornerShape(10.dp)
                            )
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                    try {
                                        firstItemFocusRequester.requestFocus()
                                        true
                                    } catch (_: Exception) {
                                        false
                                    }
                                } else false
                            }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isBackFocused) FocusRingOrange else TextPrimary
                        )
                    }

                    Column {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (items.isNotEmpty()) {
                            Text(
                                text = "${items.size} titles loaded",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (hasMore && !isLoading) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryNeon.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Infinite Scroll",
                            color = PrimaryNeon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryNeon)
                }
            } else if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No titles found in this category.",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    state = gridState,
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 50.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(items, key = { _, meta -> meta.id }) { index, meta ->
                        if (index >= items.size - 6 && hasMore && !isLoadingMore && !isLoading) {
                            LaunchedEffect(Unit) {
                                loadMore()
                            }
                        }
                        PosterCard(
                            item = meta,
                            width = 0,
                            modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                            onClick = { onNavigateToDetail(meta.type, meta.id) }
                        )
                    }

                    if (isLoadingMore) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = PrimaryNeon,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
