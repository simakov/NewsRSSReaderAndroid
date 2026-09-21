package com.newsrssreader

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.newsrssreader.data.NewsItemCache
import com.newsrssreader.data.store.BookmarkStore
import com.newsrssreader.ui.article.ArticleDetailScreen
import com.newsrssreader.ui.bookmarks.BookmarksScreen
import com.newsrssreader.ui.category.CategoryScreen
import com.newsrssreader.ui.components.MenuView
import com.newsrssreader.ui.home.HomeScreen
import com.newsrssreader.ui.photo.PhotoViewerScreen
import com.newsrssreader.ui.theme.NewsRSSReaderTheme
import com.newsrssreader.ui.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    // Held here (not just inside AppRoot's viewModel()) so onResume can reach it directly to
    // retry an install that was deferred while the user was sent to the system settings screen
    // for the "install unknown apps" permission.
    private val updateViewModel: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() per the AndroidX Splash Screen API's documented usage
        // pattern; it takes over the window installed for the Theme.NewsRSSReader.Splash launch
        // theme (set in the manifest) and swaps back to Theme.NewsRSSReader once the first frame
        // is drawn.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Explicitly opt into edge-to-edge rather than relying on version-dependent behavior.
        // targetSdk 35 (Android 15) enforces edge-to-edge on API 35+ devices regardless of any
        // setDecorFitsSystemWindows(true) call, but on pre-35 devices the app would otherwise
        // draw *under* the system bars only if it opts in — without this call the app is
        // effectively edge-to-edge on 15+ but not on older OS versions, which would make
        // TopPanel/MenuView's windowInsetsPadding(WindowInsets.statusBars) a no-op there (the
        // system would already be reserving space for the status bar, so the inset would be
        // zero). Calling enableEdgeToEdge() here makes the layout edge-to-edge consistently
        // across all supported versions, so the explicit status bar inset in TopPanel/MenuView
        // has a real, consistent effect everywhere.
        //
        // Explicit (non-auto) status/navigation bar styles: TopPanel/MenuView always paint
        // AppColors.background (a fixed dark gray, 0xFF292929) behind the status bar in *both*
        // light and dark system theme (see Color.kt) — this app's dark-mode palette doesn't
        // mirror the system light/dark split. enableEdgeToEdge()'s default SystemBarStyle.auto()
        // instead picks dark-vs-light system-bar icons based on the *system* theme, so in system
        // light mode it would select dark icons — invisible against the always-dark bar
        // background. Force light (white) icons unconditionally to match the actual bar color.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // enableEdgeToEdge() only turns on Window.isNavigationBarContrastEnforced when the style
        // passed in is SystemBarStyle.auto(...) — passing the explicit .dark(...) above (needed to
        // force white nav-bar icons unconditionally, for the same reason as the status bar) makes
        // it turn contrast enforcement OFF instead. Most screens have a light background behind
        // the 3-button nav bar (e.g. the light news list), which would leave those forced-white
        // icons with nothing to contrast against. Re-enable enforcement explicitly so the system
        // still draws its own translucent scrim behind the nav bar for contrast — this has no
        // effect on gesture navigation (only 3-button nav), per Android's documented behavior.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = true
        }
        setContent {
            NewsRSSReaderTheme {
                AppRoot(updateViewModel = updateViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateViewModel.retryInstallIfNeeded(this)
    }
}

/**
 * Top-level composable wiring the whole app together, mirroring the role iOS's `ContentView`
 * plays: it owns the `menuShown` state above the navigation graph and hosts the slide-in
 * `MenuView` drawer as an overlay on top of whichever screen is currently showing. The currently
 * selected category is derived from the nav back stack itself (not hand-tracked state) so the
 * menu's highlight and the "already there, do nothing" short-circuit always agree with what's
 * actually on screen.
 */
@Composable
fun AppRoot(updateViewModel: UpdateViewModel = viewModel()) {
    val navController = rememberNavController()
    var menuShown by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val showUpdateBadge = updateUiState.release != null

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedCategory = when (currentRoute) {
        "category/{key}" -> backStackEntry?.arguments?.getString("key") ?: ""
        else -> ""
    }
    // Derived from the back stack for the same reason the category highlight is: it always agrees
    // with whatever screen is actually showing.
    val bookmarksSelected = currentRoute == "bookmarks"
    val bookmarks by BookmarkStore.bookmarks.collectAsStateWithLifecycle()

    BackHandler(enabled = menuShown) { menuShown = false }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    onMenuClick = { menuShown = true },
                    onArticleClick = { id -> navController.navigate("article/$id") },
                    showUpdateBadge = showUpdateBadge,
                )
            }
            composable(
                route = "category/{key}",
                arguments = listOf(navArgument("key") { type = NavType.StringType }),
            ) { entry ->
                val key = entry.arguments?.getString("key").orEmpty()
                CategoryScreen(
                    categoryKey = key,
                    onMenuClick = { menuShown = true },
                    onArticleClick = { id -> navController.navigate("article/$id") },
                    showUpdateBadge = showUpdateBadge,
                )
            }
            composable("bookmarks") {
                BookmarksScreen(
                    onMenuClick = { menuShown = true },
                    onArticleClick = { id -> navController.navigate("article/$id") },
                    showUpdateBadge = showUpdateBadge,
                )
            }
            composable(
                route = "article/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val item = NewsItemCache.get(id)
                if (item != null) {
                    ArticleDetailScreen(
                        newsItem = item,
                        onBack = { navController.popBackStack() },
                        onImageClick = { url -> navController.navigate("photo/${Uri.encode(url)}") },
                    )
                } else {
                    // Process death (or any other loss of the in-memory cache) can leave us with
                    // an id that no longer resolves to a NewsItem. There is nothing meaningful to
                    // render in that case, so just back out to whatever screen led here instead of
                    // showing a dead-end "not found" screen. Popping the back stack is a
                    // navigation side effect, so it must run in an effect, not directly in the
                    // composable body.
                    LaunchedEffect(id) { navController.popBackStack() }
                }
            }
            composable(
                route = "photo/{encodedUrl}",
                arguments = listOf(navArgument("encodedUrl") { type = NavType.StringType }),
            ) { entry ->
                // Navigation-Compose already decodes the path segment when matching it against
                // {encodedUrl}, so decoding again here would double-decode any literal
                // %-encoded sequence embedded in the URL itself (e.g. a CDN proxy URL like
                // "...?src=https%3A%2F%2F...") and corrupt it.
                val url = entry.arguments?.getString("encodedUrl").orEmpty()
                PhotoViewerScreen(
                    imageUrl = url,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        AnimatedVisibility(
            visible = menuShown,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
        ) {
            MenuView(
                selectedCategory = selectedCategory,
                onDismiss = { menuShown = false },
                bookmarksSelected = bookmarksSelected,
                bookmarkCount = bookmarks.size,
                onBookmarksClick = {
                    menuShown = false
                    if (!bookmarksSelected) {
                        // popUpTo("home") matches the category destinations: the drawer switches
                        // between top-level screens rather than stacking them, so back from here
                        // lands on Home instead of walking every screen the drawer visited.
                        navController.navigate("bookmarks") { popUpTo("home") }
                    }
                },
                onCategorySelected = { key ->
                    menuShown = false
                    if (key == selectedCategory && !bookmarksSelected) {
                        // Already there — nothing to do.
                    } else if (key.isEmpty()) {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    } else {
                        navController.navigate("category/$key") {
                            popUpTo("home")
                        }
                    }
                },
                updateState = updateUiState,
                onUpdateClick = { updateViewModel.startDownload(context) },
            )
        }
    }
}
