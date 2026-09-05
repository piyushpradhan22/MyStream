package com.mystream.app

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mystream.app.ui.theme.HotstarBg
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mystream.app.data.model.AppSettingsConfig
import com.mystream.app.data.model.MediaPlaybackItem
import com.mystream.app.ui.components.BackgroundTrailerPlayer
import com.mystream.app.ui.components.TrailerPlaybackManager
import com.mystream.app.ui.screens.CatalogGridScreen
import com.mystream.app.ui.screens.DetailScreen
import com.mystream.app.ui.screens.HomeScreen
import com.mystream.app.ui.screens.PlayerScreen
import com.mystream.app.ui.screens.SearchScreen
import com.mystream.app.ui.screens.SettingsScreen
import com.mystream.app.ui.screens.SourcesScreen
import com.mystream.app.ui.theme.BgDark
import com.mystream.app.ui.theme.MyStreamTheme
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var app: MyStreamApplication
    private var activePlaybackItem by mutableStateOf<MediaPlaybackItem?>(null)
    private var isInPiPMode by mutableStateOf(false)
    private var globalNavController: androidx.navigation.NavHostController? = null

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        globalNavController?.handleDeepLink(intent)
    }

    private fun isTvDevice(): Boolean {
        return com.mystream.app.ui.utils.DeviceUtils.isTvDevice(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTvDevice()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        app = application as MyStreamApplication

        setContent {
            MyStreamTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(com.mystream.app.ui.theme.HotstarBg),
                    color = com.mystream.app.ui.theme.HotstarBg
                ) {
                    AppNavigation()
                }
            }
        }
        disableComposeAccessibilityIfTalkBackDisabled()
    }

    @Composable
    private fun AppNavigation() {
        val navController = rememberNavController()
        globalNavController = navController

        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStackEntry?.destination?.route

        val appSettings by app.sourcesRepository.appSettingsFlow.collectAsState(initial = AppSettingsConfig())

        LaunchedEffect(currentRoute) {
            val isTrailerRoute = currentRoute == "home" || currentRoute?.startsWith("detail/") == true
            TrailerPlaybackManager.setVisibility(isTrailerRoute)
        }

        LaunchedEffect(appSettings) {
            TrailerPlaybackManager.setPlaybackEnabled(appSettings.trailerPlaybackEnabled)
            TrailerPlaybackManager.setMuted(appSettings.trailerAudioMuted)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Shared Root Background Trailer Player (Active for HomeScreen & DetailScreen)
            if (TrailerPlaybackManager.isTrailerLayerVisible &&
                TrailerPlaybackManager.isPlaybackEnabled &&
                !TrailerPlaybackManager.isStopped &&
                !TrailerPlaybackManager.activeTrailerYtId.isNullOrBlank()
            ) {
                val isHomeScreen = currentRoute == "home"
                val targetWidth = if (isHomeScreen) 0.72f else 1.0f
                val targetHeight = if (isHomeScreen) 0.62f else 1.0f

                val animWidth by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = targetWidth,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 450,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    ),
                    label = "TrailerWidthTransition"
                )
                val animHeight by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = targetHeight,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 450,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    ),
                    label = "TrailerHeightTransition"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(animWidth)
                        .fillMaxHeight(animHeight)
                        .align(Alignment.TopEnd)
                        .clipToBounds()
                ) {
                    BackgroundTrailerPlayer(
                        ytId = TrailerPlaybackManager.activeTrailerYtId!!,
                        isAudioMuted = TrailerPlaybackManager.isAudioMuted,
                        isStopped = TrailerPlaybackManager.isStopped,
                        isHomeScreen = isHomeScreen,
                        onPlaybackStarted = {
                            TrailerPlaybackManager.isVideoPlaying = true
                        },
                        onVideoEnded = {
                            TrailerPlaybackManager.isVideoPlaying = false
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            NavHost(
                navController = navController,
                startDestination = "home"
            ) {
            composable("home") {
                HomeScreen(
                    repository = app.sourcesRepository,
                    onNavigateToDetail = { type, id ->
                        navController.navigate("detail/$type/$id") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToCatalog = { title, type, catalogId, genre ->
                        val encodedTitle = URLEncoder.encode(title, "UTF-8")
                        val genreValue = genre ?: "none"
                        val encodedGenre = URLEncoder.encode(genreValue, "UTF-8")
                        navController.navigate("catalog?title=$encodedTitle&type=$type&catalogId=$catalogId&genre=$encodedGenre")
                    },
                    onPlayDirect = { item ->
                        activePlaybackItem = item
                        navController.navigate("player")
                    },
                    onNavigateToSearch = {
                        navController.navigate("search")
                    },
                    onNavigateToSources = {
                        navController.navigate("sources")
                    }
                )
            }

            composable(
                route = "catalog?title={title}&type={type}&catalogId={catalogId}&genre={genre}",
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType; defaultValue = "Catalog" },
                    navArgument("type") { type = NavType.StringType; defaultValue = "movie" },
                    navArgument("catalogId") { type = NavType.StringType; defaultValue = "top" },
                    navArgument("genre") { type = NavType.StringType; defaultValue = "none" }
                )
            ) { backStackEntry ->
                val rawTitle = backStackEntry.arguments?.getString("title") ?: "Catalog"
                val title = try { java.net.URLDecoder.decode(rawTitle, "UTF-8") } catch (_: Exception) { rawTitle }
                val type = backStackEntry.arguments?.getString("type") ?: "movie"
                val catalogId = backStackEntry.arguments?.getString("catalogId") ?: "top"
                val rawGenre = backStackEntry.arguments?.getString("genre")
                val cleanGenre = if (rawGenre.isNullOrBlank() || rawGenre == "null" || rawGenre == "{genre}" || rawGenre == "none") null else {
                    try { java.net.URLDecoder.decode(rawGenre, "UTF-8") } catch (_: Exception) { rawGenre }
                }

                CatalogGridScreen(
                    title = title,
                    type = type,
                    catalogId = catalogId,
                    genre = cleanGenre,
                    repository = app.sourcesRepository,
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { t, id ->
                        navController.navigate("detail/$t/$id")
                    }
                )
            }

            composable(
                route = "detail/{type}/{id}",
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType }
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "mystream://detail/{type}/{id}" },
                    navDeepLink { uriPattern = "https://mystream.app/detail/{type}/{id}" }
                )
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: "movie"
                val id = backStackEntry.arguments?.getString("id") ?: ""
                DetailScreen(
                    type = type,
                    id = id,
                    repository = app.sourcesRepository,
                    onBack = { navController.popBackStack() },
                    onPlay = { item ->
                        activePlaybackItem = item
                        navController.navigate("player")
                    }
                )
            }

            composable("search") {
                SearchScreen(
                    repository = app.sourcesRepository,
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { type, id ->
                        navController.navigate("detail/$type/$id") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Voice search handoff: "Hey Google, play X on MyStream" fires
            // mystream://search?q=X → opens SearchScreen pre-filled with the query
            composable(
                route = "searchq?q={q}",
                arguments = listOf(
                    navArgument("q") { type = NavType.StringType; defaultValue = "" }
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "mystream://search?q={q}" }
                )
            ) { backStackEntry ->
                val rawQ = backStackEntry.arguments?.getString("q") ?: ""
                val initialQuery = try { java.net.URLDecoder.decode(rawQ, "UTF-8") } catch (_: Exception) { rawQ }
                SearchScreen(
                    repository = app.sourcesRepository,
                    initialQuery = initialQuery,
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { type, id ->
                        navController.navigate("detail/$type/$id") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable("sources") {
                SettingsScreen(
                    repository = app.sourcesRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    repository = app.sourcesRepository,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("player") {
                activePlaybackItem?.let { item ->
                    PlayerScreen(
                        playerManager = app.playerManager,
                        item = item,
                        repository = app.sourcesRepository,
                        onBack = {
                            app.playerManager.pause()
                            activePlaybackItem = null
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}

    private fun enterPiPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                // PiP not supported or failed
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPiPMode = isInPictureInPictureMode
        if (!isTvDevice()) {
            requestedOrientation = if (isInPictureInPictureMode) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else if (activePlaybackItem != null) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isTvDevice()) {
            requestedOrientation = if (activePlaybackItem != null && !isInPiPMode) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        disableComposeAccessibilityIfTalkBackDisabled()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto-enter PiP if player is actively playing
        if (activePlaybackItem != null && app.playerManager.isPlaying.value) {
            enterPiPMode()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isInPiPMode) {
            app.playerManager.pause()
            Log.d(TAG, "Activity onPause: paused player")
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isTvDevice() && !isInPiPMode) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (!isInPiPMode) {
            app.playerManager.pause()
            Log.d(TAG, "Activity onStop: paused player")
        }
        com.mystream.app.player.MediaCacheManager.clearCacheAsync()
    }

    // Android TV D-Pad & Remote Control Key Handlers
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && handlePlaybackKey(event.keyCode)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handlePlaybackKey(keyCode: Int): Boolean {
        if (activePlaybackItem == null) return false

        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_BUTTON_A -> {
                app.playerManager.togglePlayPause()
                Log.d(TAG, "togglePlayPause via keyCode=$keyCode")
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                app.playerManager.play()
                Log.d(TAG, "play via keyCode=$keyCode")
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                app.playerManager.pause()
                Log.d(TAG, "pause via keyCode=$keyCode")
                return true
            }

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                app.playerManager.seekForward(10L)
                Log.d(TAG, "seekForward via keyCode=$keyCode")
                return true
            }

            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                app.playerManager.seekBack(10L)
                Log.d(TAG, "seekBack via keyCode=$keyCode")
                return true
            }

            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                app.playerManager.pause()
                activePlaybackItem = null
                onBackPressedDispatcher.onBackPressed()
                Log.d(TAG, "back via keyCode=$keyCode")
                return true
            }
        }

        return false
    }


    private fun findComposeAccessibilityDelegate(root: View): Any? {
        // Check this view's fields for composeAccessibilityDelegate
        for (field in root.javaClass.declaredFields) {
            try {
                if (field.name == "composeAccessibilityDelegate" || 
                    field.name == "w" ||
                    field.type.name.contains("AccessibilityDelegate") ||
                    field.type.name == "w0.G") {
                    field.isAccessible = true
                    val obj = field.get(root)
                    if (obj != null && (obj.javaClass.name.contains("AccessibilityDelegate") || obj.javaClass.name == "w0.G")) {
                        return obj
                    }
                }
            } catch (_: Exception) {}
        }
        // Recurse into children
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findComposeAccessibilityDelegate(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun disableComposeAccessibilityIfTalkBackDisabled() {
        try {
            val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager ?: return
            if (!am.isTouchExplorationEnabled) {
                window.decorView.post {
                    try {
                        val delegate = findComposeAccessibilityDelegate(window.decorView)
                        if (delegate == null) {
                            Log.w(TAG, "Compose accessibility delegate not found in view tree")
                            return@post
                        }

                        // 1. Clear enabledServices so isEnabled$ui_release() returns false
                        var servicesCleared = false
                        try {
                            val enabledServicesField = delegate.javaClass.getDeclaredField("enabledServices").apply { isAccessible = true }
                            enabledServicesField.set(delegate, java.util.ArrayList<Any>())
                            servicesCleared = true
                            Log.d(TAG, "Successfully cleared delegate.enabledServices to empty list")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to clear enabledServices by name, searching fields: ${e.message}")
                            for (field in delegate.javaClass.declaredFields) {
                                if (field.name == "enabledServices" || (java.util.List::class.java.isAssignableFrom(field.type) && field.name != "H" && field.name != "scrollObservationScopes")) {
                                    try {
                                        field.isAccessible = true
                                        field.set(delegate, java.util.ArrayList<Any>())
                                        servicesCleared = true
                                        Log.d(TAG, "Cleared delegate.${field.name} to empty list")
                                        break
                                    } catch (_: Exception) {}
                                }
                            }
                        }

                        // 2. Remove listeners so key remapper services cannot trigger re-queries
                        try {
                            val enabledListenerField = delegate.javaClass.getDeclaredField("enabledStateListener").apply { isAccessible = true }
                            val enabledListener = enabledListenerField.get(delegate) as? android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener
                            if (enabledListener != null) {
                                am.removeAccessibilityStateChangeListener(enabledListener)
                                Log.d(TAG, "Removed AccessibilityStateChangeListener")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not remove enabledStateListener: ${e.message}")
                        }

                        try {
                            val touchListenerField = delegate.javaClass.getDeclaredField("touchExplorationStateListener").apply { isAccessible = true }
                            val touchListener = touchListenerField.get(delegate) as? android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener
                            if (touchListener != null) {
                                am.removeTouchExplorationStateChangeListener(touchListener)
                                Log.d(TAG, "Removed TouchExplorationStateChangeListener")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not remove touchExplorationStateListener: ${e.message}")
                        }

                        Log.d(TAG, "Compose accessibility defused: clearedServices=$servicesCleared (TalkBack not active)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not patch Compose accessibility delegate: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Accessibility check error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.mystream.app.player.MediaCacheManager.clearCacheAsync()
    }
}
