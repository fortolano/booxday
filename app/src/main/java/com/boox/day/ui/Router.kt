package com.boox.day.ui
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.boox.day.R
import com.boox.day.api.OpenRouterClient
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.TaskRepository
import com.boox.day.editor.DrawCanvas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.boox.day.ui.views.BugReportScreen
import com.boox.day.ui.views.MainScreen
import com.boox.day.ui.views.SettingsView
import com.boox.day.ui.views.WelcomeView
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull


private val logRouter = TaggedLog("Router")
private const val STARTUP_CANVAS_HANDOFF_TIMEOUT_MS = 1800L

/**
 * Rotating taglines for splash screen (51 total).
 * Shuffled-deck rotation: every quote seen once before any repeats.
 */
private val splashTaglines = listOf(
    // --- Boox love ---
    "The calendar App your Boox deserved all along :)",
    "Because your Boox was almost perfect. Almost.",
    "Your Boox is complete now. You're welcome.",
    "The last calendar app your Boox will ever need.",
    "Your Boox called. It said 'thank you.'",
    "The app your Boox brags about to other tablets.",
    "Your Boox's new best friend. Don't tell your stylus.",
    "We made your favorite tablet even more favorite-er.",
    "Now your Boox can finally adult properly.",
    "Your Boox finally has its act together. You're next.",
    "We turned your e-reader into an e-achiever.",
    "Because you didn't buy a Boox to forget appointments.",
    "The calendar that truly gets the e-ink lifestyle.",
    "Built for Boox. Obsessed over by us.",
    "Making e-ink enthusiasts even more insufferable :)",
    "Because sticky notes on your Boox felt wrong.",
    "The calendar that should have shipped with your Boox.",
    "The app that makes other calendars jealous.",
    "Your Boox believes in you. So do we.",
    // --- E-ink lifestyle ---
    "Finally, a calendar worth the refresh rate.",
    "Your schedule, at a relaxing 15 frames per second.",
    "E-ink planning? Yes. Eye strain? Never.",
    "Your schedule, in glorious 16 shades of gray.",
    "Where gentle e-ink meets aggressive productivity.",
    "E-ink planning for people with impeccable taste.",
    "Where handwriting meets 'I have a meeting at 3.'",
    "We speak fluent e-ink.",
    "No blue light. No excuses.",
    "16 shades of getting things done.",
    "Refresh rate: low. Standards: high.",
    "Your retina called. It sends its thanks.",
    "Paper vibes. Digital powers.",
    // --- Self-aware / meta ---
    "You're reading this instead of planning. Classic.",
    "We spent way too long on this splash screen.",
    "Loading... your potential.",
    "This screen disappears in 3 seconds. Make a wish.",
    "The developer shed actual tears building this.",
    // --- Productivity roasts ---
    "Your to-do list isn't going to check itself.",
    "Organize your life. Or at least pretend to.",
    "Productivity, but make it cozy.",
    "Step one: open app. Step two: world domination.",
    "Your future self called. They said start planning.",
    // --- Absurdist / unexpected ---
    "Powered by caffeine and pixel dust.",
    "No ads. No tracking. Just vibes.",
    "Contains 0% artificial intelligence on this screen.",
    "Certified free of unnecessary animations.",
    "May cause sudden urges to be productive.",
    // --- Warm / wholesome ---
    "Good things take time. Like e-ink. And you.",
    "One day at a time. That's literally how this works.",
    "Small screen. Big plans.",
    "Here's to the planners, the dreamers, the list-makers."
)

/**
 * Main Router for Boox Day
 *
 * Navigation structure:
 * - "main" - Calendar + Drawing canvas (default start)
 * - "welcome" - First-time setup/permissions (if needed)
 * - "settings" - App settings
 * - "bugReport" - Bug reporting
 */
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@ExperimentalComposeUiApi
@Composable
fun Router() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Determine start destination based on first-run status only
    // NOTE: hasFilePermission check removed - MANAGE_EXTERNAL_STORAGE is now optional (Boox Notes only)
    // Database is in internal storage (context.filesDir) and doesn't need this permission
    val startDestination = remember {
        if (GlobalAppSettings.current.showWelcome) {
            "welcome"
        } else {
            "main"
        }
    }
    val shouldWaitForCanvasHandoff = remember(startDestination) {
        startDestination == "main"
    }
    val shouldBypassWelcomeGateForStartup = remember(startDestination) {
        startDestination == "main" && GlobalAppSettings.current.onboardingDismissedAt == 0L
    }

    // Splash screen state
    var showSplash by remember { mutableStateOf(true) }
    var showSplashBranding by remember { mutableStateOf(true) }
    var shouldComposeNavHost by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DrawCanvas.prepareStartupSplash(
            ignoreWelcomePopupUntilCanvasReady = shouldBypassWelcomeGateForStartup
        )
        // Let the splash paint once before composing the heavy navigation tree underneath it.
        withFrameNanos { }
        shouldComposeNavHost = true
    }

    // Select tagline ONCE at Router level (not inside AnimatedVisibility)
    // Shuffled-deck: every quote seen once before any repeats, then reshuffle
    val splashTagline = remember {
        val prefs = context.getSharedPreferences("splash_prefs", android.content.Context.MODE_PRIVATE)
        val key = "shuffled_order"

        // Load remaining shuffled indices, or create a fresh shuffle
        var remaining = prefs.getString(key, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { it.toIntOrNull() }
            ?.filter { it in splashTaglines.indices }
            ?.toMutableList()

        if (remaining.isNullOrEmpty()) {
            remaining = splashTaglines.indices.shuffled().toMutableList()
        }

        val nextIndex = remaining.removeAt(0)
        val tagline = splashTaglines[nextIndex]

        // Save remaining indices (empty string triggers reshuffle next launch)
        prefs.edit().putString(key, remaining.joinToString(",")).apply()
        tagline
    }

    // Pre-load data during splash screen with adaptive timing
    // Minimum 3s for logo visibility, max ~5s (3000ms min + 2000ms timeout)
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        logRouter.i("PERF: Splash pre-loading START")

        // Track completion of all preload tasks
        val preloadComplete = CompletableDeferred<Unit>()

        coroutineScope {
            // 1. Minimum splash duration (ensures logo is visible, prevents flash)
            val minDuration = launch { delay(3000) }

            // 2. The user's own AI key (nothing is fetched)
            val apiJob = launch(Dispatchers.IO) {
                if (OpenRouterClient.hasApiKey()) {
                    logRouter.i("PERF: AI key available at startup")
                } else {
                    logRouter.i("PERF: Startup without AI key - using local key prompt flow")
                }
            }

            // 3. Pre-create today's pages
            val pagesJob = launch(Dispatchers.IO) {
                try {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val dayPageRepo = DayPageRepository(context)
                    val taskRepo = TaskRepository(context)

                    val memosPageId = dayPageRepo.getOrCreatePageForDate(today)
                    logRouter.i("PERF: Memos page pre-created: $memosPageId")

                    val (tasks, _) = taskRepo.getOrCreateTasksForPage(today, 0)
                    val tasksPageId = tasks.firstOrNull()?.pageId
                    logRouter.i("PERF: Tasks page pre-created: $tasksPageId")

                    if (memosPageId != null && tasksPageId != null) {
                        GlobalAppSettings.setPreloadedPageIds(today, memosPageId, tasksPageId)
                        logRouter.i("PERF: Page IDs stored in GlobalAppSettings")
                    }
                } catch (e: Exception) {
                    logRouter.e("PERF: Page pre-creation failed: ${e.message}")
                }
            }

            // Wait for minimum duration (always)
            minDuration.join()

            // Wait for critical preloads with timeout
            // API key is non-critical (app works without it), pages are critical
            withTimeoutOrNull(2000) {
                pagesJob.join()
            }
        }

        logRouter.i("PERF: Splash pre-loading COMPLETE (${System.currentTimeMillis() - startTime}ms)")
        if (shouldWaitForCanvasHandoff) {
            // Native drawing can freeze normal UI posting on some BOOX firmware.
            // Remove the cover and let the app submit its UI before permitting raw drawing;
            // otherwise a white cover can remain visible even while the pen works (#1).
            showSplashBranding = false
            showSplash = false
            withFrameNanos { }
            withFrameNanos { }
            logRouter.i("STARTUP_FRAME: splash removed before native drawing activation")

            // Retain the existing raw-drawing settle delay and bounded readiness wait.
            DrawCanvas.notifySplashDismissed()
            val canvasReady = DrawCanvas.awaitStartupCanvasReady(STARTUP_CANVAS_HANDOFF_TIMEOUT_MS)
            if (!canvasReady) {
                logRouter.w("PERF: Startup canvas handoff timed out after ${STARTUP_CANVAS_HANDOFF_TIMEOUT_MS}ms")
            }
        } else {
            DrawCanvas.notifySplashDismissed()
        }
        DrawCanvas.finishStartupSplashHandoff()
        showSplash = false
    }

    Box(Modifier.fillMaxSize()) {
        if (shouldComposeNavHost) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                // Main screen - Calendar + Drawing Canvas
                composable(route = "main") {
                    MainScreen(navController = navController)
                }

                // Welcome/Setup screen (shown on first run or if permissions needed)
                composable(route = "welcome") {
                    WelcomeView(navController = navController)
                }

                // Settings screen
                composable(route = "settings") {
                    SettingsView(navController = navController)
                }

                // Bug report screen
                composable(route = "bugReport") {
                    BugReportScreen(navController = navController)
                }
            }
        }

        // Custom splash overlay with icon and "BooxDay" text
        // E-INK FIX: Use instant transition instead of fadeOut() to prevent ghosting artifacts
        AnimatedVisibility(
            visible = showSplash,
            exit = ExitTransition.None
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (showSplashBranding) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.offset(y = (-80).dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.splash_icon),
                            contentDescription = "BooxDay Logo",
                            modifier = Modifier.size(248.dp)
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = "BooxDay",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = splashTagline,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.DarkGray,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Disclaimer at bottom
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = (-48).dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "An independent app for Boox devices",
                            fontSize = 14.sp,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Made for Boox lovers, by Boox lovers",
                            fontSize = 14.sp,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
