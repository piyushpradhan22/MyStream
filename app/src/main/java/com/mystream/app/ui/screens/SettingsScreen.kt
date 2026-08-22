package com.mystream.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mystream.app.data.model.AppJsonConfig
import com.mystream.app.data.repository.SourcesRepository
import com.mystream.app.ui.theme.AccentAmber
import com.mystream.app.ui.theme.AccentRed
import com.mystream.app.ui.theme.BgDark
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SecondaryCyan
import com.mystream.app.ui.theme.SurfaceCard
import com.mystream.app.ui.theme.SurfaceDark
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun SettingsScreen(
    repository: SourcesRepository,
    onBack: () -> Unit
) {
    val catalogSources by repository.catalogSourcesFlow.collectAsState(initial = emptyList())
    val appSettings by repository.appSettingsFlow.collectAsState(initial = com.mystream.app.data.model.AppSettingsConfig())
    var config by remember { mutableStateOf(repository.getJsonConfig()) }

    var postgresUrl by remember(config) {
        mutableStateOf(config.postgresUrl ?: "")
    }
    var sharedPikpakPassword by remember(config) {
        mutableStateOf(config.pikpakPassword.ifBlank { config.primaryAccount?.password ?: "" })
    }
    var torrentioBase by remember(config) {
        mutableStateOf(config.torrentioUrl)
    }

    var dbTestStatus by remember { mutableStateOf<String?>(null) }
    var isTestingDb by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf<String?>(null) }

    var showRawJsonEditor by remember { mutableStateOf(false) }
    var rawJsonText by remember(config) {
        val json = Json { prettyPrint = true; encodeDefaults = true }
        mutableStateOf(json.encodeToString(config))
    }

    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Text(
                            text = "Settings & Credentials",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { showRawJsonEditor = !showRawJsonEditor },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (showRawJsonEditor) "Form View" else "Edit JSON", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (showRawJsonEditor) {
                // Raw JSON Editor Mode (mystream_config.json)
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "mystream_config.json",
                                color = SecondaryCyan,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "PostgreSQL URL and shared PikPak password loaded at runtime:",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = rawJsonText,
                                onValueChange = { rawJsonText = it },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                ),
                                minLines = 10,
                                maxLines = 20,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF07090E),
                                    unfocusedContainerColor = Color(0xFF07090E),
                                    focusedBorderColor = PrimaryNeon,
                                    unfocusedBorderColor = TextMuted.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    try {
                                        val json = Json { ignoreUnknownKeys = true; isLenient = true }
                                        val parsed = json.decodeFromString<AppJsonConfig>(rawJsonText)
                                        repository.saveJsonConfig(parsed)
                                        config = parsed
                                        saveStatus = "Saved JSON configuration successfully!"
                                    } catch (e: Exception) {
                                        saveStatus = "JSON Error: ${e.localizedMessage}"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryNeon,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Save and Apply JSON Config", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    val res = repository.importConfigFromDownloads()
                                    if (res.isSuccess) {
                                        config = res.getOrThrow()
                                        saveStatus = "Successfully imported mystream_config.json from Downloads!"
                                    } else {
                                        saveStatus = "Import Error: ${res.exceptionOrNull()?.localizedMessage}"
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryCyan),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Import /sdcard/Download/mystream_config.json", fontSize = 12.sp)
                            }

                            saveStatus?.let { msg ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = msg,
                                    color = if (msg.startsWith("Saved")) SecondaryCyan else AccentRed,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // Section 1: PostgreSQL & Dynamic PikPak Accounts
                item {
                    Text(
                        text = "1. Dynamic Accounts (PostgreSQL)",
                        color = SecondaryCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Usernames are dynamically fetched from the PostgreSQL database pool. You only need to provide the shared PikPak password.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            OutlinedTextField(
                                value = postgresUrl,
                                onValueChange = { postgresUrl = it },
                                label = { Text("PostgreSQL URL (postgres_url)", color = TextSecondary) },
                                placeholder = { Text("postgresql://user:pass@host:5432/dbname", color = TextMuted) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = SecondaryCyan)
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = SecondaryCyan,
                                    unfocusedBorderColor = TextMuted
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = sharedPikpakPassword,
                                onValueChange = { sharedPikpakPassword = it },
                                label = { Text("Shared PikPak Password (pikpak_password)", color = TextSecondary) },
                                placeholder = { Text("Enter the password shared across DB accounts", color = TextMuted) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = SecondaryCyan)
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = SecondaryCyan,
                                    unfocusedBorderColor = TextMuted
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isTestingDb = true
                                        dbTestStatus = null
                                        scope.launch {
                                            val res = repository.testPostgresConnection(postgresUrl.trim())
                                            isTestingDb = false
                                            dbTestStatus = if (res.isSuccess) {
                                                res.getOrNull() ?: "Connected to DB successfully"
                                            } else {
                                                "DB Error: ${res.exceptionOrNull()?.localizedMessage}"
                                            }
                                        }
                                    },
                                    enabled = postgresUrl.isNotBlank() && !isTestingDb,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (isTestingDb) "Connecting..." else "Test DB", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        val updated = config.copy(
                                            postgresUrl = postgresUrl.trim(),
                                            pikpakPassword = sharedPikpakPassword.trim(),
                                            torrentioUrl = torrentioBase.trim()
                                        )
                                        repository.saveJsonConfig(updated)
                                        config = updated
                                        saveStatus = "Saved config successfully!"
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryNeon,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Save Config", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            dbTestStatus?.let { msg ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = msg,
                                    color = if (msg.contains("Found") || msg.contains("successfully")) SecondaryCyan else AccentRed,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Section 2: Torrentio Endpoint
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "2. Torrentio Base URL",
                        color = AccentAmber,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            OutlinedTextField(
                                value = torrentioBase,
                                onValueChange = { torrentioBase = it },
                                label = { Text("Torrentio URL", color = TextSecondary) },
                                placeholder = { Text("https://torrentio.strem.fun", color = TextMuted) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Dns, contentDescription = null, tint = AccentAmber)
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = AccentAmber,
                                    unfocusedBorderColor = TextMuted
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Section 3: Audio & Subtitle Preferences
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "3. Audio & Subtitles Playback Preferences",
                        color = Color(0xFFE65100),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Preferred Audio Language",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Hindi", "English", "Original").forEach { lang ->
                                    val isSel = appSettings.preferredAudioLanguage.equals(lang, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) Color(0xFFE65100) else SurfaceCard)
                                            .clickable {
                                                scope.launch {
                                                    repository.updateAppSettings(appSettings.copy(preferredAudioLanguage = lang))
                                                }
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (lang == "Hindi") "🇮🇳 Hindi (Default)" else if (lang == "English") "🇬🇧 English" else "Original",
                                            color = if (isSel) Color.White else TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Subtitles Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Subtitles",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (appSettings.subtitlesEnabled) "Enabled by default (English)" else "Disabled by default",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                androidx.compose.material3.Switch(
                                    checked = appSettings.subtitlesEnabled,
                                    onCheckedChange = { isChecked ->
                                        scope.launch {
                                            repository.updateAppSettings(appSettings.copy(subtitlesEnabled = isChecked))
                                        }
                                    },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        checkedThumbColor = PrimaryNeon,
                                        checkedTrackColor = PrimaryNeon.copy(alpha = 0.4f)
                                    )
                                )
                            }

                            if (appSettings.subtitlesEnabled) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Preferred Subtitle Language",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("English", "Hindi", "All").forEach { subLang ->
                                        val isSubSel = appSettings.preferredSubtitleLanguage.equals(subLang, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSubSel) PrimaryNeon else SurfaceCard)
                                                .clickable {
                                                    scope.launch {
                                                        repository.updateAppSettings(appSettings.copy(preferredSubtitleLanguage = subLang))
                                                    }
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (subLang == "English") "English (Default)" else subLang,
                                                color = if (isSubSel) Color.White else TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSubSel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 4: Stream Links Local Cache & Expiration
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "4. Stream Links Local Cache & Expiry",
                        color = SecondaryCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Cache Expiry (TTL)",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Preserves resolved URLs locally on disk across app restarts. Only re-resolves when expired or when you click Refresh (🔄).",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    1 to "1h",
                                    6 to "6h (Default)",
                                    12 to "12h",
                                    24 to "24h",
                                    0 to "Never"
                                ).forEach { (ttl, label) ->
                                    val isTtlSel = appSettings.linkCacheTtlHours == ttl
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isTtlSel) SecondaryCyan else SurfaceCard)
                                            .clickable {
                                                scope.launch {
                                                    repository.updateAppSettings(appSettings.copy(linkCacheTtlHours = ttl))
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isTtlSel) Color.Black else TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = if (isTtlSel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 5: Catalogs
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "5. Installed Stremio Add-on Sources",
                        color = PrimaryNeon,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(catalogSources, key = { it.id }) { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCard)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cat.name,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = cat.baseUrl,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = PrimaryNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
