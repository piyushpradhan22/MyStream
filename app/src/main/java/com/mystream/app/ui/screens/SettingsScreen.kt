package com.mystream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mystream.app.data.model.AppJsonConfig
import com.mystream.app.data.repository.SourcesRepository
import com.mystream.app.ui.theme.AccentAmber
import com.mystream.app.ui.theme.AccentRed
import com.mystream.app.ui.theme.BgDark
import com.mystream.app.ui.theme.FocusRingOrange
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SurfaceCard
import com.mystream.app.ui.theme.SurfaceDark
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.mystream.app.BuildConfig
import com.mystream.app.data.updater.AppUpdateCheckResult
import com.mystream.app.data.updater.AppUpdateManager
import com.mystream.app.ui.components.AppUpdateDialog
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
    val context = LocalContext.current
    val updateManager = remember { AppUpdateManager(context) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }

    if (showUpdateDialog && updateResult != null) {
        AppUpdateDialog(
            updateInfo = updateResult!!,
            isDownloading = isDownloadingUpdate,
            downloadProgress = downloadProgress,
            errorMessage = updateError,
            onDismiss = { showUpdateDialog = false },
            onStartDownload = {
                scope.launch {
                    isDownloadingUpdate = true
                    downloadProgress = 0
                    updateError = null
                    val dlRes = updateManager.downloadApk(updateResult!!.downloadUrl) { progress ->
                        downloadProgress = progress
                    }
                    isDownloadingUpdate = false
                    val apkFile = dlRes.getOrNull()
                    if (apkFile != null) {
                        val installRes = updateManager.installApk(apkFile)
                        if (installRes.isFailure) {
                            updateError = "Install failed: ${installRes.exceptionOrNull()?.message}"
                        }
                    } else {
                        updateError = dlRes.exceptionOrNull()?.message ?: "Download failed"
                    }
                }
            }
        )
    }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        listState.scrollToItem(0, 0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        LazyColumn(
            state = listState,
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
                    val backInteraction = remember { MutableInteractionSource() }
                    val isBackFocused by backInteraction.collectIsFocusedAsState()

                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            interactionSource = backInteraction,
                            modifier = Modifier
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
                        Text(
                            text = "Settings",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val editJsonInteraction = remember { MutableInteractionSource() }
                    val isEditJsonFocused by editJsonInteraction.collectIsFocusedAsState()

                    OutlinedButton(
                        onClick = { showRawJsonEditor = !showRawJsonEditor },
                        interactionSource = editJsonInteraction,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isEditJsonFocused) FocusRingOrange.copy(alpha = 0.25f)
                            else if (showRawJsonEditor) PrimaryNeon.copy(alpha = 0.15f)
                            else Color.Transparent,
                            contentColor = if (isEditJsonFocused) FocusRingOrange else PrimaryNeon
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isEditJsonFocused) 2.5.dp else 1.dp,
                            if (isEditJsonFocused) FocusRingOrange else PrimaryNeon.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isEditJsonFocused) FocusRingOrange else PrimaryNeon
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showRawJsonEditor) "Form View" else "Edit JSON",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEditJsonFocused) FocusRingOrange else PrimaryNeon
                        )
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "mystream_config.json",
                                color = PrimaryNeon,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Press OK / Tap to edit. PostgreSQL URL and shared PikPak password loaded at runtime:",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            TVInputField(
                                value = rawJsonText,
                                onValueChange = { rawJsonText = it },
                                label = "mystream_config.json",
                                placeholder = "Paste JSON configuration...",
                                singleLine = false,
                                minLines = 10,
                                maxLines = 20,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val saveJsonInteraction = remember { MutableInteractionSource() }
                            val isSaveJsonFocused by saveJsonInteraction.collectIsFocusedAsState()

                            Button(
                                onClick = {
                                    try {
                                        val json = Json { ignoreUnknownKeys = true; isLenient = true }
                                        val parsed = json.decodeFromString<AppJsonConfig>(rawJsonText)
                                        repository.saveJsonConfig(parsed)
                                        config = parsed
                                        saveStatus = "Saved JSON configuration successfully!"
                                        android.widget.Toast.makeText(context, "Saved & Encrypted Configuration Successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        saveStatus = "JSON Error: ${e.localizedMessage}"
                                    }
                                },
                                interactionSource = saveJsonInteraction,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSaveJsonFocused) FocusRingOrange else PrimaryNeon,
                                    contentColor = if (isSaveJsonFocused) Color.Black else Color.White
                                ),
                                border = if (isSaveJsonFocused) androidx.compose.foundation.BorderStroke(2.5.dp, FocusRingOrange) else null,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (isSaveJsonFocused) Color.Black else Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Save and Apply JSON Config",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSaveJsonFocused) Color.Black else Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val importInteraction = remember { MutableInteractionSource() }
                            val isImportFocused by importInteraction.collectIsFocusedAsState()

                            OutlinedButton(
                                onClick = {
                                    val res = repository.importConfigFromDownloads()
                                    if (res.isSuccess) {
                                        config = res.getOrThrow()
                                        saveStatus = "Successfully imported mystream_config.json from Downloads!"
                                        android.widget.Toast.makeText(context, "Imported & Encrypted Config Successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        saveStatus = "Import Error: ${res.exceptionOrNull()?.localizedMessage}"
                                    }
                                },
                                interactionSource = importInteraction,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isImportFocused) FocusRingOrange.copy(alpha = 0.25f) else Color.Transparent,
                                    contentColor = if (isImportFocused) FocusRingOrange else PrimaryNeon
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isImportFocused) 2.5.dp else 1.dp,
                                    if (isImportFocused) FocusRingOrange else PrimaryNeon.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isImportFocused) FocusRingOrange else PrimaryNeon
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Import /sdcard/Download/mystream_config.json",
                                    fontSize = 12.sp,
                                    color = if (isImportFocused) FocusRingOrange else PrimaryNeon
                                )
                            }

                            saveStatus?.let { msg ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (msg.startsWith("Saved") || msg.startsWith("Successfully")) PrimaryNeon.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.15f))
                                        .border(
                                            1.dp,
                                            if (msg.startsWith("Saved") || msg.startsWith("Successfully")) PrimaryNeon.copy(alpha = 0.5f) else AccentRed.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (msg.startsWith("Saved") || msg.startsWith("Successfully")) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (msg.startsWith("Saved") || msg.startsWith("Successfully")) PrimaryNeon else AccentRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = msg,
                                        color = if (msg.startsWith("Saved") || msg.startsWith("Successfully")) PrimaryNeon else AccentRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "1. Dynamic Accounts (PostgreSQL)",
                        color = PrimaryNeon,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Usernames are dynamically fetched from the PostgreSQL pool. Credentials are encrypted on device.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            TVInputField(
                                value = postgresUrl,
                                onValueChange = { postgresUrl = it },
                                label = "Database URL",
                                placeholder = "postgresql://user:pass@host:5432/dbname",
                                isMasked = true,
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = PrimaryNeon, modifier = Modifier.size(16.dp))
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            TVInputField(
                                value = sharedPikpakPassword,
                                onValueChange = { sharedPikpakPassword = it },
                                label = "Password",
                                placeholder = "Enter PikPak password",
                                isMasked = true,
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = PrimaryNeon, modifier = Modifier.size(16.dp))
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val testDbInteraction = remember { MutableInteractionSource() }
                                val isTestDbFocused by testDbInteraction.collectIsFocusedAsState()

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
                                    interactionSource = testDbInteraction,
                                    enabled = postgresUrl.isNotBlank() && !isTestingDb,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isTestDbFocused) FocusRingOrange.copy(alpha = 0.25f) else Color.Transparent,
                                        contentColor = if (isTestDbFocused) FocusRingOrange else PrimaryNeon
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (isTestDbFocused) 2.5.dp else 1.dp,
                                        if (isTestDbFocused) FocusRingOrange else PrimaryNeon.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isTestDbFocused) FocusRingOrange else PrimaryNeon
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isTestingDb) "Connecting..." else "Test DB",
                                        fontSize = 12.sp,
                                        color = if (isTestDbFocused) FocusRingOrange else PrimaryNeon
                                    )
                                }

                                val saveConfigInteraction = remember { MutableInteractionSource() }
                                val isSaveConfigFocused by saveConfigInteraction.collectIsFocusedAsState()

                                Button(
                                    onClick = {
                                        val updated = config.copy(
                                            postgresUrl = postgresUrl.trim(),
                                            pikpakPassword = sharedPikpakPassword.trim(),
                                            torrentioUrl = torrentioBase.trim()
                                        )
                                        repository.saveJsonConfig(updated)
                                        config = updated
                                        saveStatus = "Saved & Encrypted config successfully!"
                                        android.widget.Toast.makeText(context, "Saved & Encrypted Configuration Successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    interactionSource = saveConfigInteraction,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSaveConfigFocused) FocusRingOrange else PrimaryNeon,
                                        contentColor = if (isSaveConfigFocused) Color.Black else Color.White
                                    ),
                                    border = if (isSaveConfigFocused) androidx.compose.foundation.BorderStroke(2.5.dp, FocusRingOrange) else null,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSaveConfigFocused) Color.Black else Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Save Config",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSaveConfigFocused) Color.Black else Color.White
                                    )
                                }
                            }

                            saveStatus?.let { msg ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (msg.startsWith("Saved") || msg.startsWith("Successfully")) PrimaryNeon.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.15f))
                                        .border(
                                            1.dp,
                                            if (msg.startsWith("Saved") || msg.startsWith("Successfully")) PrimaryNeon.copy(alpha = 0.5f) else AccentRed.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (msg.startsWith("Saved") || msg.startsWith("Successfully")) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (msg.startsWith("Saved") || msg.startsWith("Successfully")) PrimaryNeon else AccentRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = msg,
                                        color = if (msg.startsWith("Saved") || msg.startsWith("Successfully")) PrimaryNeon else AccentRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            dbTestStatus?.let { msg ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = msg,
                                    color = if (msg.contains("Found") || msg.contains("successfully")) PrimaryNeon else AccentRed,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "2. Torrentio Base URL",
                        color = PrimaryNeon,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            TVInputField(
                                value = torrentioBase,
                                onValueChange = { torrentioBase = it },
                                label = "Torrentio URL",
                                placeholder = "https://torrentio.strem.fun",
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Dns, contentDescription = null, tint = PrimaryNeon)
                                }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "3. Audio & Subtitles Playback Preferences",
                        color = PrimaryNeon,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
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
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isFocused by interactionSource.collectIsFocusedAsState()

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isFocused) FocusRingOrange.copy(alpha = 0.25f)
                                                else if (isSel) PrimaryNeon.copy(alpha = 0.25f)
                                                else SurfaceCard
                                            )
                                            .border(
                                                width = if (isFocused) 2.dp else 1.dp,
                                                color = if (isFocused) FocusRingOrange
                                                else if (isSel) PrimaryNeon
                                                else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .focusable(interactionSource = interactionSource)
                                            .clickable(interactionSource = interactionSource, indication = null) {
                                                scope.launch {
                                                    repository.updateAppSettings(appSettings.copy(preferredAudioLanguage = lang))
                                                }
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (lang == "Hindi") "🇮🇳 Hindi" else if (lang == "English") "🇬🇧 English" else "Original",
                                            color = if (isFocused) FocusRingOrange else if (isSel) TextPrimary else TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel || isFocused) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

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
                                        text = if (appSettings.subtitlesEnabled) "Enabled (English)" else "Disabled",
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
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val isFocused by interactionSource.collectIsFocusedAsState()

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isFocused) FocusRingOrange.copy(alpha = 0.25f)
                                                    else if (isSubSel) PrimaryNeon.copy(alpha = 0.25f)
                                                    else SurfaceCard
                                                )
                                                .border(
                                                    width = if (isFocused) 2.dp else 1.dp,
                                                    color = if (isFocused) FocusRingOrange
                                                    else if (isSubSel) PrimaryNeon
                                                    else Color.Transparent,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .focusable(interactionSource = interactionSource)
                                                .clickable(interactionSource = interactionSource, indication = null) {
                                                    scope.launch {
                                                        repository.updateAppSettings(appSettings.copy(preferredSubtitleLanguage = subLang))
                                                    }
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = subLang,
                                                color = if (isFocused) FocusRingOrange else if (isSubSel) TextPrimary else TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSubSel || isFocused) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "4. Stream Links Local Cache & Expiry",
                        color = PrimaryNeon,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
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
                                    6 to "6h",
                                    12 to "12h",
                                    24 to "24h",
                                    0 to "Never"
                                ).forEach { (ttl, label) ->
                                    val isTtlSel = appSettings.linkCacheTtlHours == ttl
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isFocused by interactionSource.collectIsFocusedAsState()

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isFocused) FocusRingOrange.copy(alpha = 0.25f)
                                                else if (isTtlSel) PrimaryNeon.copy(alpha = 0.25f)
                                                else SurfaceCard
                                            )
                                            .border(
                                                width = if (isFocused) 2.dp else 1.dp,
                                                color = if (isFocused) FocusRingOrange
                                                else if (isTtlSel) PrimaryNeon
                                                else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .focusable(interactionSource = interactionSource)
                                            .clickable(interactionSource = interactionSource, indication = null) {
                                                scope.launch {
                                                    repository.updateAppSettings(appSettings.copy(linkCacheTtlHours = ttl))
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isFocused) FocusRingOrange else if (isTtlSel) TextPrimary else TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = if (isTtlSel || isFocused) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

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
                            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(10.dp))
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

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "6. App Version & Updates",
                        color = PrimaryNeon,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "MyStream Android",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                        color = TextMuted,
                                        fontSize = 11.5.sp
                                    )
                                }

                                val updateInteraction = remember { MutableInteractionSource() }
                                val isUpdateFocused by updateInteraction.collectIsFocusedAsState()

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isUpdateFocused) FocusRingOrange.copy(alpha = 0.25f) else PrimaryNeon.copy(alpha = 0.15f))
                                        .border(
                                            if (isUpdateFocused) 2.dp else 1.dp,
                                            if (isUpdateFocused) FocusRingOrange else PrimaryNeon.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .focusable(interactionSource = updateInteraction)
                                        .clickable(interactionSource = updateInteraction, indication = null) {
                                            if (isCheckingUpdate) return@clickable
                                            scope.launch {
                                                isCheckingUpdate = true
                                                updateStatusMessage = null
                                                updateError = null
                                                val res = updateManager.checkForUpdates()
                                                isCheckingUpdate = false
                                                val info = res.getOrNull()
                                                if (info != null) {
                                                    updateResult = info
                                                    if (info.isUpdateAvailable) {
                                                        showUpdateDialog = true
                                                    } else {
                                                        updateStatusMessage = "You're on the latest version (v${info.currentVersionName})"
                                                        android.widget.Toast.makeText(context, "You are using the latest version!", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    val msg = res.exceptionOrNull()?.message ?: "Failed to check updates"
                                                    updateStatusMessage = msg
                                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isCheckingUpdate) {
                                            CircularProgressIndicator(
                                                color = if (isUpdateFocused) FocusRingOrange else PrimaryNeon,
                                                modifier = Modifier.size(13.dp),
                                                strokeWidth = 1.8.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.SystemUpdate,
                                                contentDescription = null,
                                                tint = if (isUpdateFocused) FocusRingOrange else PrimaryNeon,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = if (isCheckingUpdate) "Checking..." else "Check for Updates",
                                            color = if (isUpdateFocused) FocusRingOrange else PrimaryNeon,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }

                            if (!updateStatusMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = updateStatusMessage!!,
                                    color = if (updateStatusMessage!!.contains("latest", ignoreCase = true) || updateStatusMessage!!.contains("up to date", ignoreCase = true)) PrimaryNeon else AccentRed,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * TV Remote-friendly text field that only opens an active edit dialog with keyboard when the user explicitly
 * presses DPAD Center / Enter on remote or taps the field. Navigating over it with DPAD highlights with orange ring and does not open keyboard.
 */
@Composable
private fun TVInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    isMasked: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    if (showDialog) {
        TVTextEditDialog(
            title = label,
            initialValue = value,
            placeholder = placeholder,
            singleLine = singleLine,
            onConfirm = { updated ->
                onValueChange(updated)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF141822) else Color(0xFF07090E))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) FocusRingOrange else Color(0x33FFFFFF),
                shape = RoundedCornerShape(10.dp)
            )
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                ) {
                    showDialog = true
                    true
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null) {
                showDialog = true
            }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            leadingIcon?.invoke()

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isFocused) FocusRingOrange else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isMasked && value.isNotBlank()) {
                        Text(
                            text = "🔒 Encrypted",
                            color = PrimaryNeon,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val displayText = if (value.isBlank()) placeholder else if (isMasked) "••••••••••••••••••••" else value
                Text(
                    text = displayText,
                    color = if (value.isBlank()) TextMuted else TextPrimary,
                    fontSize = 13.sp,
                    maxLines = if (singleLine) 1 else 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit",
                tint = if (isFocused) FocusRingOrange else TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun TVTextEditDialog(
    title: String,
    initialValue: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (e: Exception) {
            // ignore
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    }

                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(placeholder, color = TextMuted) },
                        singleLine = singleLine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = FocusRingOrange,
                            unfocusedBorderColor = Color(0x44FFFFFF),
                            focusedContainerColor = Color(0xFF07090E),
                            unfocusedContainerColor = Color(0xFF07090E)
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                onConfirm(text)
                            }
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cancelInteraction = remember { MutableInteractionSource() }
                        val isCancelFocused by cancelInteraction.collectIsFocusedAsState()

                        OutlinedButton(
                            onClick = onDismiss,
                            interactionSource = cancelInteraction,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isCancelFocused) FocusRingOrange.copy(alpha = 0.2f) else Color.Transparent,
                                contentColor = if (isCancelFocused) FocusRingOrange else TextSecondary
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isCancelFocused) 2.dp else 1.dp,
                                if (isCancelFocused) FocusRingOrange else Color(0x33FFFFFF)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = if (isCancelFocused) FocusRingOrange else TextSecondary)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        val saveInteraction = remember { MutableInteractionSource() }
                        val isSaveFocused by saveInteraction.collectIsFocusedAsState()

                        Button(
                            onClick = { onConfirm(text) },
                            interactionSource = saveInteraction,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSaveFocused) FocusRingOrange else PrimaryNeon,
                                contentColor = if (isSaveFocused) Color.Black else Color.White
                            ),
                            border = if (isSaveFocused) androidx.compose.foundation.BorderStroke(2.dp, FocusRingOrange) else null,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold, color = if (isSaveFocused) Color.Black else Color.White)
                        }
                    }
                }
            }
        }
    }
}
