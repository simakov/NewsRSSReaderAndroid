package com.newsrssreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.newsrssreader.data.NewsItemCache
import com.newsrssreader.ui.article.ArticleDetailScreen
import com.newsrssreader.ui.category.CategoryScreen
import com.newsrssreader.ui.components.MenuView
import com.newsrssreader.ui.home.HomeScreen
import com.newsrssreader.ui.theme.NewsRSSReaderTheme

class MainActivity : ComponentActivity() {
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
        enableEdgeToEdge()
        setContent {
            NewsRSSReaderTheme {
                AppRoot()
            }
        }
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
fun AppRoot() {
    val navController = rememberNavController()
    var menuShown by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedCategory = when (currentRoute) {
        "category/{key}" -> backStackEntry?.arguments?.getString("key") ?: ""
        else -> ""
    }

    BackHandler(enabled = menuShown) { menuShown = false }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    onMenuClick = { menuShown = true },
                    onArticleClick = { id -> navController.navigate("article/$id") },
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
        }

        AnimatedVisibility(
            visible = menuShown,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
        ) {
            MenuView(
                selectedCategory = selectedCategory,
                onDismiss = { menuShown = false },
                onCategorySelected = { key ->
                    menuShown = false
                    if (key == selectedCategory) {
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
            )
        }
    }
}
