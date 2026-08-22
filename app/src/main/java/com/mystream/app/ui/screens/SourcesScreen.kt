package com.mystream.app.ui.screens

import androidx.compose.runtime.Composable
import com.mystream.app.data.repository.SourcesRepository

@Composable
fun SourcesScreen(
    repository: SourcesRepository,
    onBack: () -> Unit
) {
    SettingsScreen(
        repository = repository,
        onBack = onBack
    )
}
