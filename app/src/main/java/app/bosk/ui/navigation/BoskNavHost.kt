package app.bosk.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.bosk.ui.screens.aiinterface.AiInterfaceScreen
import app.bosk.ui.screens.flashcardgraph.FlashcardGraphScreen
import app.bosk.ui.screens.aipartner.AiPartnerScreen
import app.bosk.ui.screens.aitutor.AiTutorScreen
import app.bosk.ui.screens.home.HomeScreen
import app.bosk.ui.screens.notes.CompilerScreen
import app.bosk.ui.screens.notes.NoteEditorScreen
import app.bosk.ui.screens.notes.NotesSection
import app.bosk.ui.screens.notes.RecycleBinScreen
import app.bosk.ui.screens.queue.QueueScreen
import app.bosk.ui.screens.review.ReviewScreen
import app.bosk.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Root navigation. The five top-level screens live in a swipeable [HorizontalPager] (flick
 * left/right between sections, Clash-Royale style); the bottom bar selects a page with a
 * smooth animated slide. The full-screen note editor is a separate overlay route (no bottom
 * bar, no pager).
 */
@Composable
fun BoskNavHost() {
    val navController = rememberNavController()

    fun openNote(id: Long) = navController.navigate("${Routes.NOTE_EDITOR_BASE}?noteId=$id")
    fun openTutor(roadmap: Boolean, noteId: Long = -1L) =
        navController.navigate("${Routes.AI_TUTOR_BASE}?roadmap=$roadmap&note=$noteId")

    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainPager(
                onOpenNote = ::openNote,
                // New note: omit the arg → defaultValue -1L. Avoids a negative path segment,
                // which Navigation's typed-arg matcher mishandles (a crash source).
                onCreateNote = { navController.navigate(Routes.NOTE_EDITOR_BASE) },
                onOpenAiPartner = { navController.navigate(Routes.AI_PARTNER) },
                onOpenQueue = { navController.navigate(Routes.QUEUE) },
                onOpenCompiler = { navController.navigate(Routes.COMPILER) },
                onOpenAiInterface = { navController.navigate(Routes.AI_INTERFACE) },
                onOpenAiTutor = { roadmap -> openTutor(roadmap) },
                onOpenNoteInTutor = { id -> openTutor(roadmap = false, noteId = id) },
                onOpenRecycleBin = { navController.navigate(Routes.RECYCLE_BIN) },
                onOpenStats = { navController.navigate(Routes.STATS) },
            )
        }
        composable(Routes.STATS) {
            app.bosk.ui.screens.stats.StatsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AI_PARTNER) {
            AiPartnerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.QUEUE) {
            QueueScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.COMPILER) {
            CompilerScreen(onOpenNote = ::openNote, onBackToNotes = { navController.popBackStack() })
        }
        composable(Routes.AI_INTERFACE) {
            AiInterfaceScreen(
                onBack = { navController.popBackStack() },
                onOpenNote = ::openNote,
                onOpenFlashcardGraph = { navController.navigate(Routes.FLASHCARD_GRAPH) },
            )
        }
        composable(Routes.FLASHCARD_GRAPH) {
            FlashcardGraphScreen(onBack = { navController.popBackStack() }, onOpenNote = ::openNote)
        }
        composable(Routes.NOTE_GRAPH) {
            // The editor's "View connections" graph — full Graph-tab UI, scoped to the open note.
            app.bosk.ui.screens.graph.NoteGraphScreen(
                onBack = { navController.popBackStack() },
                onOpenNote = ::openNote,
                onOpenCompiler = { navController.navigate(Routes.COMPILER) },
            )
        }
        composable(Routes.RECYCLE_BIN) {
            RecycleBinScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = "${Routes.AI_TUTOR_BASE}?roadmap={roadmap}&note={note}",
            arguments = listOf(
                navArgument("roadmap") { type = NavType.BoolType; defaultValue = false },
                navArgument("note") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            AiTutorScreen(
                onBack = { navController.popBackStack() },
                onOpenNote = ::openNote,
                openWithRoadmap = entry.arguments?.getBoolean("roadmap") ?: false,
                autoNoteId = entry.arguments?.getLong("note") ?: -1L,
            )
        }
        composable(
            route = "${Routes.NOTE_EDITOR_BASE}?noteId={noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.LongType; defaultValue = -1L }),
        ) {
            // Opening a [[link]]/!link navigates to that note (a new editor entry on the stack).
            NoteEditorScreen(
                onBack = { navController.popBackStack() },
                onOpenNote = ::openNote,
                onOpenConnectionsGraph = { navController.navigate(Routes.NOTE_GRAPH) },
            )
        }
    }
}

@Composable
private fun MainPager(
    onOpenNote: (Long) -> Unit,
    onCreateNote: () -> Unit,
    onOpenAiPartner: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenCompiler: () -> Unit,
    onOpenAiInterface: () -> Unit,
    onOpenAiTutor: (roadmap: Boolean) -> Unit,
    onOpenNoteInTutor: (Long) -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val pages = TopDestination.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    // Retains each tab's UI state (scroll positions, remembered saveables) across tab switches even
    // after the pager disposes an off-screen page — so returning to a tab restores instantly instead
    // of rebuilding from scratch. The heavy Graph stays lazily gated (LocalPageActive) on top of this.
    val stateHolder = rememberSaveableStateHolder()

    val badgeVm: NavBadgeViewModel = hiltViewModel()
    val dueCount by badgeVm.dueCount.collectAsStateWithLifecycle()

    // Only the Graph page disables pager swipe; derive it so the pager doesn't recompose this whole
    // scope on every settle frame.
    val userScrollEnabled by remember {
        derivedStateOf { pages[pagerState.currentPage] != TopDestination.Graph }
    }

    // Tab navigation = INSTANT page snap. An animated scroll has to compose+draw every page it
    // passes over for the whole animation; snapping shows the target's first frame and nothing
    // else, which is by far the smoothest. Manual swipes still get the pager's own fling.
    fun animateTo(index: Int) = scope.launch { pagerState.scrollToPage(index) }
    fun goTo(dest: TopDestination) = animateTo(pages.indexOf(dest))

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val navItemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pages.forEachIndexed { index, dest ->
                    NavigationBarItem(
                        colors = navItemColors,
                        selected = pagerState.currentPage == index,
                        onClick = { animateTo(index) },
                        icon = {
                            if (dest == TopDestination.Review && dueCount > 0) {
                                BadgedBox(badge = { Badge { Text(dueCount.toString()) } }) {
                                    Icon(dest.icon, contentDescription = stringResource(dest.labelRes))
                                }
                            } else {
                                Icon(dest.icon, contentDescription = stringResource(dest.labelRes))
                            }
                        },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
            // Pre-compose the neighbouring page so a swipe reveals an already-laid-out screen
            // instead of composing it mid-gesture (that first-composition was the swipe jank).
            beyondViewportPageCount = 1,
            // On the graph page, swipe is disabled so panning the graph doesn't change tabs —
            // the on-screen edge tabs handle prev/next there instead.
            userScrollEnabled = userScrollEnabled,
        ) { page ->
            // Plain translation only — no per-page graphicsLayer transform. A scale/alpha layer
            // forces every composed page (current + both neighbours) through an offscreen render
            // buffer for every frame of the swipe, which was the remaining swipe cost. Pages now
            // just slide. We also tell each page whether it's the SETTLED visible one so:
            //  • off-screen neighbours pause their continuous animations (see LocalPageActive),
            //  • the moment a swipe/drag starts, the outgoing page's animations stop competing
            //    with the scroll frames (Home's waveform/mini-graph were the "leaving Home" lag),
            //  • the Graph's heavy first composition waits until the fling has LANDED instead of
            //    running mid-animation and eating its frames (the "laggy for a bit" on arrival).
            // Leaving the graph is always an instant snap (its page disables pager swipe), so the
            // stricter gate never causes a visible placeholder on the way out.
            CompositionLocalProvider(
                LocalPageActive provides
                    (page == pagerState.currentPage && !pagerState.isScrollInProgress),
            ) {
              stateHolder.SaveableStateProvider(pages[page].name) {
                when (pages[page]) {
                    TopDestination.Home -> HomeScreen(
                        onStartReview = { goTo(TopDestination.Review) },
                        onOpenNote = onOpenNote,
                        onOpenGraph = { goTo(TopDestination.Graph) },
                        onOpenQueue = onOpenQueue,
                        onOpenAiInterface = onOpenAiInterface,
                        onOpenAiTutor = onOpenAiTutor,
                        onCreateNote = onCreateNote,
                        onOpenStats = onOpenStats,
                    )
                    TopDestination.Notes -> NotesSection(
                        onOpenNote = onOpenNote,
                        onCreateNote = onCreateNote,
                        onOpenAiTutor = { onOpenAiTutor(false) },
                        onOpenNoteInTutor = onOpenNoteInTutor,
                        onOpenRecycleBin = onOpenRecycleBin,
                    )
                    TopDestination.Graph -> app.bosk.ui.screens.graph.GraphSection(
                        onOpenNote = onOpenNote,
                        onOpenCompiler = onOpenCompiler,
                        onSwipePrev = { animateTo((page - 1).coerceAtLeast(0)) },
                        onSwipeNext = { animateTo((page + 1).coerceAtMost(pages.lastIndex)) },
                        onOpenTutor = { onOpenAiTutor(false) },
                    )
                    TopDestination.Review -> ReviewScreen()
                    TopDestination.Settings -> SettingsScreen(onOpenAiPartner = onOpenAiPartner)
                }
              }
            }
        }
    }
}
