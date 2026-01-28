package com.example.logbook

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import coil.compose.AsyncImage
import com.example.logbook.ui.theme.LogBookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/* =========================
   DataStore
   ========================= */

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "logbook_prefs")

private val KEY_ANILIST_TOKEN = stringPreferencesKey("anilist_access_token")

// ✅ V3 inclui anilistMediaId
private val KEY_SAVED_SERIES_V3 = stringSetPreferencesKey("saved_series_v3")
private val KEY_SAVED_SERIES_V2 = stringSetPreferencesKey("saved_series_v2")
private val KEY_SAVED_SERIES_V1 = stringSetPreferencesKey("saved_series_v1")

/* =========================
   AniList OAuth config
   ========================= */

private const val ANILIST_CLIENT_ID = "35231"
private const val ANILIST_REDIRECT_URI = "questlogredsinal://oauth"

/**
 * Secret via BuildConfig (app/build.gradle.kts + local.properties)
 */
private fun getAniListClientSecret(): String {
    val secret = BuildConfig.ANILIST_CLIENT_SECRET
    if (secret.isBlank()) {
        throw IllegalStateException(
            "AniList secret vazio. Define ANILIST_CLIENT_SECRET no local.properties (raiz do projeto)."
        )
    }
    return secret
}

/* =========================
   Seed map: URL -> AniList ID
   (usado para re-associar IDs automaticamente após migração)
   ========================= */

private val SEED_URL_TO_ANILIST_ID: Map<String, Int> = mapOf(
    "https://asuracomic.net/series/revenge-of-the-iron-blooded-sword-hound-f1ce5747" to 163824,
    "https://asuracomic.net/series/swordmasters-youngest-son-8cafb8c3" to 149332,

    "https://asuracomic.net/series/pick-me-up-infinite-gacha-8adbeae5" to 159441,
    "https://asuracomic.net/series/nano-machine-13bdbe88" to 120980,
    "https://asuracomic.net/series/reaper-of-the-drifting-moon-a6fc7e69" to 153432,
    "https://asuracomic.net/series/return-of-the-mount-hua-sect-0a0c8769" to 132144,
    "https://asuracomic.net/series/chronicles-of-the-martial-gods-return-ffec0cc1" to 150319,
    "https://asuracomic.net/series/standard-of-reincarnation-44a622fe" to 153880,
    "https://asuracomic.net/series/the-regressed-son-of-a-duke-is-an-assassin-e3c4ba04" to 175262,
    "https://asuracomic.net/series/your-talent-is-mine-296b6690" to 138366,
    "https://asuracomic.net/series/martial-god-regressed-to-level-2-e2dd287d" to 167834,
    "https://asuracomic.net/series/regressing-with-the-kings-power-e0d0d8af" to 170724,
    "https://asuracomic.net/series/chronicles-of-the-demon-faction-c7f86f5d" to 164222,
    "https://asuracomic.net/series/the-dark-magician-transmigrates-after-66666-years-c45f8c1b" to 137595,
    "https://asuracomic.net/series/terminally-ill-genius-dark-knight-89bc68db" to 165182,
    "https://asuracomic.net/series/the-last-adventurer-529df836" to 177982,
    "https://asuracomic.net/series/academys-undercover-professor-3e42f845" to 150836,
    "https://asuracomic.net/series/academys-genius-swordmaster-317f777a" to 167649,
    "https://asuracomic.net/series/surviving-the-game-as-a-barbarian-6f263f9b" to 164857,
    "https://asuracomic.net/series/the-knight-king-who-returned-with-a-god-3f3083c6" to 165287,
    "https://asuracomic.net/series/the-max-level-players-100th-regression-9c748d9f" to 170894,
    "https://asuracomic.net/series/reincarnator-34ec3584" to 172583,
    "https://asuracomic.net/series/i-obtained-a-mythic-item-0fe297ef" to 151025,
    "https://asuracomic.net/series/solo-max-level-newbie-22dfe932" to 137280,
    "https://asuracomic.net/series/genius-archers-streaming-55f918eb" to 180166,
    "https://asuracomic.net/series/emperor-of-solo-play-35d8ff02" to 191101,
    "https://asuracomic.net/series/heavenly-grand-archives-young-master-b6378212" to 160693,
    "https://asuracomic.net/series/magic-academys-genius-blinker-d3295a89" to 178379,
    "https://asuracomic.net/series/mr-devourer-please-act-like-a-final-boss-e407550e" to 172623,
    "https://asuracomic.net/series/absolute-sword-sense-f66c61f8" to 151460,
    "https://asuracomic.net/series/absolute-regression-71e97ca4" to 180891,
    "https://asuracomic.net/series/the-magic-towers-problem-child-f717dad7" to 189264,
    "https://asuracomic.net/series/the-indomitable-martial-king-3508be9a" to 176812,
    "https://asuracomic.net/series/solo-leveling-ragnarok-e6a9638d" to 179445,
    "https://asuracomic.net/series/player-who-returned-10000-years-later-9d59fa79" to 153284,
).mapKeys { it.key.trimEnd('/') }

/* =========================
   Models
   ========================= */

private data class Series(
    val id: String,
    val title: String,
    val seriesUrl: String,
    val maxChapter: Int,
    val imageRes: Int? = null,
    val coverUrl: String? = null,
    val lastUpdateMillis: Long = 0L,
    val lastReadChapter: Int = 0,
    val anilistMediaId: Int? = null,
) {
    fun chapterUrl(chapter: Int): String = "${seriesUrl.trimEnd('/')}/chapter/$chapter"
    fun unreadCount(): Int = (maxChapter - lastReadChapter).coerceAtLeast(0)
    fun continueChapter(): Int {
        if (maxChapter <= 0) return 0
        val next = lastReadChapter + 1
        return if (next <= maxChapter) next else maxChapter
    }
}

private data class SeedSeries(
    val url: String,
    val preferredTitle: String? = null,
    val imageRes: Int? = null,
    val anilistMediaId: Int? = null,
)

private sealed class ScreenState {
    data object Menu : ScreenState()
    data class Chapters(val seriesId: String) : ScreenState()
    data class Reader(val seriesId: String, val chapter: Int) : ScreenState()
}

private enum class SortMode(val label: String) {
    RECENT("Recente"),
    ALPHA("A–Z")
}

/* =========================
   MainActivity (Intent as State)
   ========================= */

class MainActivity : ComponentActivity() {

    var latestIntent: Intent? by mutableStateOf(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setBackgroundDrawableResource(android.R.color.black)

        latestIntent = intent

        setContent { LogBookTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntent = intent
    }
}

/* =========================
   Anti-crash: imageRes NÃO é persistido
   ========================= */

private fun imageResForKnownSeeds(seriesUrl: String): Int? {
    val u = seriesUrl.trimEnd('/')
    return when (u) {
        "https://asuracomic.net/series/revenge-of-the-iron-blooded-sword-hound-f1ce5747" -> R.drawable.bloodhound1
        "https://asuracomic.net/series/swordmasters-youngest-son-8cafb8c3" -> R.drawable.swordmaster1
        else -> null
    }
}

private fun attachSeedAniIdIfMissing(s: Series): Series {
    val key = s.seriesUrl.trimEnd('/')
    val seedId = SEED_URL_TO_ANILIST_ID[key]
    return if (s.anilistMediaId == null && seedId != null) s.copy(anilistMediaId = seedId) else s
}

/* =========================
   App root
   ========================= */

@Composable
private fun App() {
    val context = LocalContext.current
    val activity = (context as MainActivity)
    val scope = rememberCoroutineScope()

    var aniListToken by remember { mutableStateOf<String?>(null) }
    var authMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        aniListToken = loadAniListToken(context)
    }

    val observedIntent = activity.latestIntent

    LaunchedEffect(observedIntent) {
        val data = observedIntent?.data ?: return@LaunchedEffect

        val error = data.getQueryParameter("error")
        val errorDesc = data.getQueryParameter("error_description")
        if (!error.isNullOrBlank()) {
            authMessage = "AniList login falhou: $error ${errorDesc ?: ""}".trim()
            return@LaunchedEffect
        }

        val code = parseAuthCodeFromRedirect(data) ?: return@LaunchedEffect

        try {
            val token = exchangeAniListCodeForToken(code)
            saveAniListToken(context, token)
            aniListToken = token
            authMessage = "AniList ligado com sucesso ✅"
        } catch (e: Exception) {
            authMessage = "Erro a obter token do AniList: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    val seriesState = remember { mutableStateListOf<Series>() }
    var screen by remember { mutableStateOf<ScreenState>(ScreenState.Menu) }

    val defaultSeeds = remember {
        listOf(
            SeedSeries(
                url = "https://asuracomic.net/series/revenge-of-the-iron-blooded-sword-hound-f1ce5747",
                preferredTitle = "Revenge of the Iron-Blooded Sword Hound",
                imageRes = R.drawable.bloodhound1,
                anilistMediaId = 163824
            ),
            SeedSeries(
                url = "https://asuracomic.net/series/swordmasters-youngest-son-8cafb8c3",
                preferredTitle = "Swordmaster’s Youngest Son",
                imageRes = R.drawable.swordmaster1,
                anilistMediaId = 149332
            ),
            SeedSeries("https://asuracomic.net/series/pick-me-up-infinite-gacha-8adbeae5", anilistMediaId = 159441),
            SeedSeries("https://asuracomic.net/series/nano-machine-13bdbe88", anilistMediaId = 120980),
            SeedSeries("https://asuracomic.net/series/reaper-of-the-drifting-moon-a6fc7e69", anilistMediaId = 153432),
            SeedSeries("https://asuracomic.net/series/return-of-the-mount-hua-sect-0a0c8769", anilistMediaId = 132144),
            SeedSeries("https://asuracomic.net/series/chronicles-of-the-martial-gods-return-ffec0cc1", anilistMediaId = 150319),
            SeedSeries("https://asuracomic.net/series/standard-of-reincarnation-44a622fe", anilistMediaId = 153880),
            SeedSeries("https://asuracomic.net/series/the-regressed-son-of-a-duke-is-an-assassin-e3c4ba04", anilistMediaId = 175262),
            SeedSeries("https://asuracomic.net/series/your-talent-is-mine-296b6690", anilistMediaId = 138366),
            SeedSeries("https://asuracomic.net/series/martial-god-regressed-to-level-2-e2dd287d", anilistMediaId = 167834),
            SeedSeries("https://asuracomic.net/series/regressing-with-the-kings-power-e0d0d8af", anilistMediaId = 170724),
            SeedSeries("https://asuracomic.net/series/chronicles-of-the-demon-faction-c7f86f5d", anilistMediaId = 164222),
            SeedSeries("https://asuracomic.net/series/the-dark-magician-transmigrates-after-66666-years-c45f8c1b", anilistMediaId = 137595),
            SeedSeries("https://asuracomic.net/series/terminally-ill-genius-dark-knight-89bc68db", anilistMediaId = 165182),
            SeedSeries("https://asuracomic.net/series/the-last-adventurer-529df836", anilistMediaId = 177982),
            SeedSeries("https://asuracomic.net/series/academys-undercover-professor-3e42f845", anilistMediaId = 150836),
            SeedSeries("https://asuracomic.net/series/academys-genius-swordmaster-317f777a", anilistMediaId = 167649),
            SeedSeries("https://asuracomic.net/series/surviving-the-game-as-a-barbarian-6f263f9b", anilistMediaId = 164857),
            SeedSeries("https://asuracomic.net/series/the-knight-king-who-returned-with-a-god-3f3083c6", anilistMediaId = 165287),
            SeedSeries("https://asuracomic.net/series/the-max-level-players-100th-regression-9c748d9f", anilistMediaId = 170894),
            SeedSeries("https://asuracomic.net/series/reincarnator-34ec3584", anilistMediaId = 172583),
            SeedSeries("https://asuracomic.net/series/i-obtained-a-mythic-item-0fe297ef", anilistMediaId = 151025),
            SeedSeries("https://asuracomic.net/series/solo-max-level-newbie-22dfe932", anilistMediaId = 137280),
            SeedSeries("https://asuracomic.net/series/genius-archers-streaming-55f918eb", anilistMediaId = 180166),
            SeedSeries("https://asuracomic.net/series/emperor-of-solo-play-35d8ff02", anilistMediaId = 191101),
            SeedSeries("https://asuracomic.net/series/heavenly-grand-archives-young-master-b6378212", anilistMediaId = 160693),
            SeedSeries("https://asuracomic.net/series/magic-academys-genius-blinker-d3295a89", anilistMediaId = 178379),
            SeedSeries("https://asuracomic.net/series/mr-devourer-please-act-like-a-final-boss-e407550e", anilistMediaId = 172623),
            SeedSeries("https://asuracomic.net/series/absolute-sword-sense-f66c61f8", anilistMediaId = 151460),
            SeedSeries("https://asuracomic.net/series/absolute-regression-71e97ca4", anilistMediaId = 180891),
            SeedSeries("https://asuracomic.net/series/the-magic-towers-problem-child-f717dad7", anilistMediaId = 189264),
            SeedSeries("https://asuracomic.net/series/the-indomitable-martial-king-3508be9a", anilistMediaId = 176812),
            SeedSeries("https://asuracomic.net/series/solo-leveling-ragnarok-e6a9638d", anilistMediaId = 179445),
            SeedSeries("https://asuracomic.net/series/player-who-returned-10000-years-later-9d59fa79", anilistMediaId = 153284),
        )
    }

    // load/seed + merge maxChapter + lastRead
    LaunchedEffect(Unit) {
        val loaded = loadSavedSeries(context)

        if (loaded.isEmpty()) {
            val seeded = buildDefaultsFromSeeds(context, defaultSeeds)
            val lastReadMap = loadAllLastRead(context)
            val mergedSeeded = seeded.map { it.copy(lastReadChapter = lastReadMap[it.id] ?: 0) }

            seriesState.clear()
            seriesState.addAll(mergedSeeded)

            saveSeriesList(context, seriesState.toList())
            for (s in seriesState) saveMaxChapter(context, s.id, s.maxChapter)
        } else {
            val maxMap = loadAllMaxChapters(context)
            val lastReadMap = loadAllLastRead(context)

            val merged = loaded.map { s ->
                val storedMax = maxMap[s.id]
                val storedRead = lastReadMap[s.id]
                s.copy(
                    maxChapter = if (storedMax != null && storedMax > s.maxChapter) storedMax else s.maxChapter,
                    lastReadChapter = storedRead ?: s.lastReadChapter
                )
            }

            seriesState.clear()
            seriesState.addAll(merged)
        }
    }

    fun getSeriesById(id: String): Series? = seriesState.firstOrNull { it.id == id }

    suspend fun markRead(seriesId: String, chapter: Int) {
        val idx = seriesState.indexOfFirst { it.id == seriesId }
        if (idx >= 0) {
            val current = seriesState[idx]
            val newRead = maxOf(current.lastReadChapter, chapter)
            if (newRead != current.lastReadChapter) {
                seriesState[idx] = current.copy(lastReadChapter = newRead)
                saveLastRead(context, seriesId, newRead)
                saveSeriesList(context, seriesState.toList())
            }
        }
    }

    suspend fun updateSeries(
        seriesId: String,
        newTitle: String? = null,
        newUrl: String? = null,
        coverProvided: Boolean = false,
        newCoverUrl: String? = null
    ) {
        val idx = seriesState.indexOfFirst { it.id == seriesId }
        if (idx >= 0) {
            val cur = seriesState[idx]
            val finalCover = if (coverProvided) newCoverUrl else cur.coverUrl
            val updated = cur.copy(
                title = newTitle ?: cur.title,
                seriesUrl = newUrl ?: cur.seriesUrl,
                coverUrl = finalCover
            )
            // ✅ se URL mudou e é seed, re-associa anilistMediaId automaticamente
            val fixed = attachSeedAniIdIfMissing(updated.copy(imageRes = imageResForKnownSeeds(updated.seriesUrl)))
            seriesState[idx] = fixed
            saveSeriesList(context, seriesState.toList())
        }
    }

    suspend fun removeSeries(seriesId: String) {
        val idx = seriesState.indexOfFirst { it.id == seriesId }
        if (idx >= 0) {
            seriesState.removeAt(idx)
            saveSeriesList(context, seriesState.toList())
            removeSeriesKeys(context, seriesId)
        }
    }

    if (authMessage != null) {
        AlertDialog(
            onDismissRequest = { authMessage = null },
            title = { Text("AniList") },
            text = { Text(authMessage!!) },
            confirmButton = { TextButton(onClick = { authMessage = null }) { Text("OK") } }
        )
    }

    when (val s = screen) {
        is ScreenState.Menu -> {
            HomeScreen(
                seriesList = seriesState,
                scope = scope,
                aniListToken = aniListToken,
                onAniListLogin = { startAniListLogin(context) },
                onAniListLogout = {
                    scope.launch {
                        clearAniListToken(context)
                        aniListToken = null
                    }
                },
                onOpenSeries = { id -> screen = ScreenState.Chapters(id) },
                onContinue = { id ->
                    val series = getSeriesById(id) ?: return@HomeScreen
                    val ch = series.continueChapter()
                    if (ch > 0) screen = ScreenState.Reader(id, ch)
                },
                onRename = { id, title -> updateSeries(id, newTitle = title) },
                onEditUrl = { id, url -> updateSeries(id, newUrl = url) },
                onSetCoverUrl = { id, coverUrl ->
                    val cleaned = coverUrl.trim().ifBlank { null }
                    updateSeries(id, coverProvided = true, newCoverUrl = cleaned)
                },
                onRemove = { id -> removeSeries(id) },
                onAddManhwaByUrl = { url -> addManhwaFlow(context, seriesState, url) },
                onCheckUpdates = { progressCb ->
                    val before = seriesState.associate { it.id to it.maxChapter }

                    val updates = checkLatestBySeriesPageWithProgress(seriesState) { done, total, title ->
                        progressCb(done, total, title)
                    }

                    val now = System.currentTimeMillis()
                    for ((id, latest) in updates) {
                        if (latest == null) continue
                        val idx = seriesState.indexOfFirst { it.id == id }
                        if (idx >= 0 && latest > seriesState[idx].maxChapter) {
                            val cur = seriesState[idx]
                            seriesState[idx] = cur.copy(
                                maxChapter = latest,
                                lastUpdateMillis = now
                            )
                            saveMaxChapter(context, id, latest)
                        }
                    }

                    saveSeriesList(context, seriesState.toList())
                    buildUpdateMessage(seriesState, before, updates)
                },
                onSyncAllAniList = { progressCb ->
                    val token = aniListToken
                    if (token.isNullOrBlank()) return@HomeScreen "AniList não está ligado."
                    syncAllAniListProgress(
                        token = token,
                        series = seriesState.toList(),
                        onProgressMain = progressCb
                    )
                }
            )
        }

        is ScreenState.Chapters -> {
            val series = getSeriesById(s.seriesId)
            if (series == null) {
                screen = ScreenState.Menu
            } else {
                ChapterPicker(
                    series = series,
                    onBack = { screen = ScreenState.Menu },
                    onOpenChapter = { ch -> screen = ScreenState.Reader(series.id, ch) }
                )
            }
        }

        is ScreenState.Reader -> {
            val series = getSeriesById(s.seriesId)
            if (series == null) {
                screen = ScreenState.Menu
            } else {
                LaunchedEffect(series.id, s.chapter) {
                    markRead(series.id, s.chapter)
                }
                ReaderScreen(
                    url = series.chapterUrl(s.chapter),
                    onBackToChapters = { screen = ScreenState.Chapters(series.id) }
                )
            }
        }
    }
}

/* =========================
   Home Screen
   ========================= */

@Composable
private fun HomeScreen(
    seriesList: List<Series>,
    scope: kotlinx.coroutines.CoroutineScope,
    aniListToken: String?,
    onAniListLogin: () -> Unit,
    onAniListLogout: () -> Unit,
    onOpenSeries: (String) -> Unit,
    onContinue: (String) -> Unit,
    onRename: suspend (String, String) -> Unit,
    onEditUrl: suspend (String, String) -> Unit,
    onSetCoverUrl: suspend (String, String) -> Unit,
    onRemove: suspend (String) -> Unit,
    onAddManhwaByUrl: suspend (String) -> String,
    onCheckUpdates: suspend ((done: Int, total: Int, title: String?) -> Unit) -> String,
    onSyncAllAniList: suspend ((done: Int, total: Int, title: String?) -> Unit) -> String
) {
    val aniListConnected = !aniListToken.isNullOrBlank()

    var showActions by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }
    var resultsText by remember { mutableStateOf("") }

    var isBusy by remember { mutableStateOf(false) }
    var progDone by remember { mutableStateOf(0) }
    var progTotal by remember { mutableStateOf(0) }
    var progTitle by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.RECENT) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    fun progressFraction(): Float {
        if (!isBusy || progTotal <= 0) return 0f
        return (progDone.toFloat() / progTotal.toFloat()).coerceIn(0f, 1f)
    }

    val visibleSeries by remember(seriesList, query, sortMode) {
        derivedStateOf {
            val q = query.trim()
            val filtered = if (q.isEmpty()) seriesList else {
                seriesList.filter { it.title.contains(q, ignoreCase = true) }
            }

            when (sortMode) {
                SortMode.ALPHA -> filtered.sortedBy { it.title.lowercase(Locale.ROOT) }
                SortMode.RECENT -> filtered.sortedWith(
                    compareByDescending<Series> { it.lastUpdateMillis }
                        .thenBy { it.title.lowercase(Locale.ROOT) }
                )
            }
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var newUrl by remember { mutableStateOf("") }

    var contextSeries by remember { mutableStateOf<Series?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    var showEditUrlDialog by remember { mutableStateOf(false) }
    var editUrlInput by remember { mutableStateOf("") }

    var showCoverDialog by remember { mutableStateOf(false) }
    var coverUrlInput by remember { mutableStateOf("") }

    var showRemoveDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showActions = true }) { Text("≡") }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Procurar") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Ordenar: ${sortMode.label}", style = MaterialTheme.typography.bodyMedium)

                Box {
                    TextButton(onClick = { sortMenuOpen = true }) { Text("Mudar") }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(SortMode.RECENT.label) },
                            onClick = { sortMode = SortMode.RECENT; sortMenuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text(SortMode.ALPHA.label) },
                            onClick = { sortMode = SortMode.ALPHA; sortMenuOpen = false }
                        )
                    }
                }
            }

            if (isBusy) {
                LinearProgressIndicator(
                    progress = { progressFraction() },
                    modifier = Modifier.fillMaxWidth()
                )
                val label = progTitle?.let { " — $it" } ?: ""
                Text(
                    text = "${progDone}/${progTotal}$label",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val rows = visibleSeries.chunked(2)
                items(
                    items = rows,
                    key = { row -> row[0].id }
                ) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SeriesCard(
                            modifier = Modifier.weight(1f),
                            series = row[0],
                            onClick = { onOpenSeries(row[0].id) },
                            onContinue = { onContinue(row[0].id) },
                            onLongClick = {
                                contextSeries = row[0]
                                showContextMenu = true
                            }
                        )

                        if (row.size == 2) {
                            SeriesCard(
                                modifier = Modifier.weight(1f),
                                series = row[1],
                                onClick = { onOpenSeries(row[1].id) },
                                onContinue = { onContinue(row[1].id) },
                                onLongClick = {
                                    contextSeries = row[1]
                                    showContextMenu = true
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (showActions) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showActions = false },
            title = { Text("Opções") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Escolhe uma ação:")
                    if (isBusy) {
                        LinearProgressIndicator(
                            progress = { progressFraction() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val label = progTitle?.let { " — $it" } ?: ""
                        Text(
                            text = "A processar: ${progDone}/${progTotal}$label",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        enabled = !isBusy,
                        onClick = {
                            showActions = false
                            onAniListLogin()
                        }
                    ) { Text(if (aniListConnected) "AniList (ligado)" else "Ligar AniList") }

                    if (aniListConnected) {
                        TextButton(
                            enabled = !isBusy,
                            onClick = {
                                showActions = false
                                onAniListLogout()
                                resultsText = "AniList desligado."
                                showResults = true
                            }
                        ) { Text("Desligar AniList") }

                        TextButton(
                            enabled = !isBusy,
                            onClick = {
                                isBusy = true
                                progDone = 0
                                progTotal = 0
                                progTitle = null

                                scope.launch {
                                    resultsText = onSyncAllAniList { done, total, title ->
                                        progDone = done
                                        progTotal = total
                                        progTitle = title
                                    }
                                    isBusy = false
                                    showActions = false
                                    showResults = true
                                }
                            }
                        ) { Text("Sync All") }
                    }

                    TextButton(
                        enabled = !isBusy,
                        onClick = {
                            showActions = false
                            showAddDialog = true
                        }
                    ) { Text("Adicionar manhwa") }

                    TextButton(
                        enabled = !isBusy,
                        onClick = {
                            isBusy = true
                            progDone = 0
                            progTotal = seriesList.size
                            progTitle = null

                            scope.launch {
                                resultsText = onCheckUpdates { done, total, title ->
                                    progDone = done
                                    progTotal = total
                                    progTitle = title
                                }
                                isBusy = false
                                showActions = false
                                showResults = true
                            }
                        }
                    ) { Text("Verificar updates") }
                }
            },
            dismissButton = {
                TextButton(enabled = !isBusy, onClick = { showActions = false }) { Text("Fechar") }
            }
        )
    }

    if (showContextMenu && contextSeries != null) {
        val s = contextSeries!!
        AlertDialog(
            onDismissRequest = { showContextMenu = false },
            title = { Text(s.title) },
            text = { Text("O que queres fazer?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        renameInput = s.title
                        showContextMenu = false
                        showRenameDialog = true
                    }) { Text("Renomear") }

                    TextButton(onClick = {
                        editUrlInput = s.seriesUrl
                        showContextMenu = false
                        showEditUrlDialog = true
                    }) { Text("Editar URL") }

                    TextButton(onClick = {
                        coverUrlInput = s.coverUrl ?: ""
                        showContextMenu = false
                        showCoverDialog = true
                    }) { Text("Definir capa (URL)") }

                    TextButton(onClick = {
                        showContextMenu = false
                        showRemoveDialog = true
                    }) { Text("Remover") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showContextMenu = false }) { Text("Cancelar") }
            }
        )
    }

    if (showRenameDialog && contextSeries != null) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showRenameDialog = false },
            title = { Text("Renomear") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Título") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy && renameInput.trim().isNotEmpty(),
                    onClick = {
                        val id = contextSeries!!.id
                        isBusy = true
                        scope.launch {
                            onRename(id, renameInput.trim())
                            isBusy = false
                            showRenameDialog = false
                            resultsText = "Nome atualizado."
                            showResults = true
                        }
                    }
                ) { Text(if (isBusy) "A guardar..." else "Guardar") }
            },
            dismissButton = {
                TextButton(enabled = !isBusy, onClick = { showRenameDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showEditUrlDialog && contextSeries != null) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showEditUrlDialog = false },
            title = { Text("Editar URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cola o URL da página da série (…/series/...).")
                    OutlinedTextField(
                        value = editUrlInput,
                        onValueChange = { editUrlInput = it },
                        singleLine = true,
                        label = { Text("URL") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy && editUrlInput.trim().startsWith("http"),
                    onClick = {
                        val id = contextSeries!!.id
                        isBusy = true
                        scope.launch {
                            onEditUrl(id, editUrlInput.trim())
                            isBusy = false
                            showEditUrlDialog = false
                            resultsText = "URL atualizada."
                            showResults = true
                        }
                    }
                ) { Text(if (isBusy) "A guardar..." else "Guardar") }
            },
            dismissButton = {
                TextButton(enabled = !isBusy, onClick = { showEditUrlDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showCoverDialog && contextSeries != null) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showCoverDialog = false },
            title = { Text("Definir capa (URL)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Deixa vazio para remover.")
                    OutlinedTextField(
                        value = coverUrlInput,
                        onValueChange = { coverUrlInput = it },
                        singleLine = true,
                        label = { Text("URL da capa") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        val id = contextSeries!!.id
                        isBusy = true
                        scope.launch {
                            onSetCoverUrl(id, coverUrlInput)
                            isBusy = false
                            showCoverDialog = false
                            resultsText = "Capa atualizada."
                            showResults = true
                        }
                    }
                ) { Text(if (isBusy) "A guardar..." else "Guardar") }
            },
            dismissButton = {
                TextButton(enabled = !isBusy, onClick = { showCoverDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showRemoveDialog && contextSeries != null) {
        val s = contextSeries!!
        AlertDialog(
            onDismissRequest = { if (!isBusy) showRemoveDialog = false },
            title = { Text("Remover") },
            text = { Text("Remover \"${s.title}\" da tua lista?") },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        isBusy = true
                        scope.launch {
                            onRemove(s.id)
                            isBusy = false
                            showRemoveDialog = false
                            resultsText = "Removido."
                            showResults = true
                        }
                    }
                ) { Text(if (isBusy) "A remover..." else "Remover") }
            },
            dismissButton = {
                TextButton(enabled = !isBusy, onClick = { showRemoveDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showAddDialog = false },
            title = { Text("Adicionar manhwa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cola o URL da página da série (…/series/...).")
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        singleLine = true,
                        label = { Text("URL da série") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy && newUrl.trim().isNotEmpty(),
                    onClick = {
                        isBusy = true
                        scope.launch {
                            resultsText = onAddManhwaByUrl(newUrl.trim())
                            isBusy = false
                            showAddDialog = false
                            showResults = true
                            newUrl = ""
                        }
                    }
                ) { Text(if (isBusy) "A adicionar..." else "Adicionar") }
            },
            dismissButton = {
                TextButton(enabled = !isBusy, onClick = { showAddDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showResults) {
        AlertDialog(
            onDismissRequest = { showResults = false },
            title = { Text("Resultado") },
            text = { Text(resultsText.ifBlank { "OK" }) },
            confirmButton = { TextButton(onClick = { showResults = false }) { Text("OK") } }
        )
    }
}

/* =========================
   Series card
   ========================= */

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SeriesCard(
    modifier: Modifier,
    series: Series,
    onClick: () -> Unit,
    onContinue: () -> Unit,
    onLongClick: () -> Unit
) {
    val unread = series.unreadCount()
    Card(
        modifier = modifier
            .height(220.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            val painter = series.imageRes?.let { resId ->
                runCatching { painterResource(id = resId) }.getOrNull()
            }

            when {
                painter != null -> {
                    Image(
                        painter = painter,
                        contentDescription = series.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                !series.coverUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = series.coverUrl,
                        contentDescription = series.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = series.title,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (unread > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "+$unread",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = onContinue,
                        enabled = series.maxChapter > 0,
                        modifier = Modifier
                            .wrapContentWidth()
                            .height(36.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        val ch = series.continueChapter()
                        Text(
                            text = if (ch > 0) "$ch" else "-",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

/* =========================
   Chapter picker
   ========================= */

@Composable
private fun ChapterPicker(
    series: Series,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit
) {
    BackHandler { onBack() }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) { Text("Voltar") }

            Text(
                text = "${series.title} — Capítulos (até ${series.maxChapter})",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Text(
                text = "Lidos até: ${series.lastReadChapter}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val chapters = (1..series.maxChapter).toList().reversed()
                val rows = chapters.chunked(2)

                items(
                    items = rows,
                    key = { row -> row[0] }
                ) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ChapterButton(
                            modifier = Modifier.weight(1f),
                            chapter = row[0],
                            isRead = row[0] <= series.lastReadChapter,
                            onClick = { onOpenChapter(row[0]) }
                        )
                        if (row.size == 2) {
                            ChapterButton(
                                modifier = Modifier.weight(1f),
                                chapter = row[1],
                                isRead = row[1] <= series.lastReadChapter,
                                onClick = { onOpenChapter(row[1]) }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterButton(
    modifier: Modifier,
    chapter: Int,
    isRead: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

    if (isRead) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.height(52.dp),
            shape = shape
        ) {
            Text("Cap. $chapter ✓", style = MaterialTheme.typography.labelMedium)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.height(52.dp),
            shape = shape
        ) {
            Text("Cap. $chapter", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/* =========================
   Reader
   ========================= */

@Composable
private fun ReaderScreen(
    url: String,
    onBackToChapters: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val stripUiForThisPage = remember(url) {
        url.startsWith("https://asuracomic.net/") && url.contains("/chapter/")
    }

    val stripJs = remember {
        """
        (function() {
          const END_SENTINEL = /\/images\/EndDesign\.webp(\?.*)?$/i;
          const BAD_SRC = /(facebook|fb|twitter|t\.co|x\.com|whatsapp|wa\.me|pinterest|pinimg|telegram|discord|reddit|share|social|icon|logo)/i;
          const BAD_HREF = /(facebook\.com|twitter\.com|x\.com|t\.co|whatsapp\.com|wa\.me|pinterest\.com|telegram\.me|t\.me)/i;

          function getSrc(img) { return (img.currentSrc || img.src || '').toString(); }
          function isEndSentinel(src) {
            if (!src) return false;
            const s = src.split('#')[0];
            return END_SENTINEL.test(s);
          }
          function isBadByLink(img) {
            try {
              var a = img.closest('a');
              if (!a) return false;
              var href = (a.getAttribute('href') || '').trim();
              return BAD_HREF.test(href);
            } catch (e) { return false; }
          }
          function isBigEnough(img) {
            const nh = img.naturalHeight || 0;
            const nw = img.naturalWidth || 0;
            const r = img.getBoundingClientRect();
            const rh = r ? r.height : 0;
            const rw = r ? r.width : 0;
            const bigByNatural = (nh >= 700 && nw >= 400);
            const bigByRect = (rh >= 500 && rw >= 250);
            return bigByNatural || bigByRect;
          }

          function collectChapterImagesIncludingEnd() {
            const all = Array.from(document.querySelectorAll('img'));
            const out = [];
            const seen = new Set();

            for (const img of all) {
              const src = getSrc(img);
              if (!src) continue;

              if (isEndSentinel(src)) {
                if (!seen.has(src)) { seen.add(src); out.push(img); }
                break;
              }

              if (BAD_SRC.test(src)) continue;
              if (isBadByLink(img)) continue;
              if (!isBigEnough(img)) continue;

              if (seen.has(src)) continue;
              seen.add(src);
              out.push(img);
            }
            return out;
          }

          function renderImagesOnly(imgs) {
            document.documentElement.style.background = 'black';
            document.body.style.background = 'black';
            document.body.style.margin = '0';
            document.body.style.padding = '0';

            var container = document.createElement('div');
            container.style.background = 'black';
            container.style.margin = '0';
            container.style.padding = '0';

            imgs.forEach(function(img) {
              var clone = img.cloneNode(true);
              clone.removeAttribute('width');
              clone.removeAttribute('height');
              clone.style.width = '100%';
              clone.style.height = 'auto';
              clone.style.display = 'block';
              clone.style.margin = '0';
              clone.style.padding = '0';
              container.appendChild(clone);
            });

            document.body.innerHTML = '';
            document.body.appendChild(container);

            var meta = document.querySelector('meta[name=viewport]');
            if (!meta) {
              meta = document.createElement('meta');
              meta.name = 'viewport';
              document.head.appendChild(meta);
            }
            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
          }

          function tryStrip() {
            const imgs = collectChapterImagesIncludingEnd();
            const hasEnd = imgs.length > 0 && isEndSentinel(getSrc(imgs[imgs.length - 1]));
            if (hasEnd && imgs.length >= 4) {
              renderImagesOnly(imgs);
              return true;
            }
            return false;
          }

          let attempts = 0;
          const timer = setInterval(function() {
            attempts++;
            const done = tryStrip();
            if (done || attempts >= 20) clearInterval(timer);
          }, 600);
        })();
        """.trimIndent()
    }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) wv.goBack() else onBackToChapters()
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Button(
                onClick = onBackToChapters,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) { Text("Voltar") }

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(0xFF000000.toInt())
                        setBackgroundResource(android.R.color.black)

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                super.onPageFinished(view, finishedUrl)
                                if (stripUiForThisPage && view != null) {
                                    view.evaluateJavascript(stripJs, null)
                                }
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        loadUrl(url)
                        webViewRef = this
                    }
                },
                update = { wv ->
                    webViewRef = wv
                    if (wv.url != url) wv.loadUrl(url)
                }
            )
        }
    }
}

/* =========================
   AniList OAuth (Authorization Code)
   ========================= */

private fun startAniListLogin(context: Context) {
    val authUrl = Uri.parse("https://anilist.co/api/v2/oauth/authorize").buildUpon()
        .appendQueryParameter("client_id", ANILIST_CLIENT_ID)
        .appendQueryParameter("redirect_uri", ANILIST_REDIRECT_URI)
        .appendQueryParameter("response_type", "code")
        .build()

    CustomTabsIntent.Builder().build().launchUrl(context, authUrl)
}

private fun parseAuthCodeFromRedirect(data: Uri): String? = data.getQueryParameter("code")

private suspend fun exchangeAniListCodeForToken(code: String): String =
    withContext(Dispatchers.IO) {
        val url = URL("https://anilist.co/api/v2/oauth/token")
        val secret = getAniListClientSecret()

        val form =
            "grant_type=authorization_code" +
                    "&client_id=${URLEncoder.encode(ANILIST_CLIENT_ID, "UTF-8")}" +
                    "&client_secret=${URLEncoder.encode(secret, "UTF-8")}" +
                    "&redirect_uri=${URLEncoder.encode(ANILIST_REDIRECT_URI, "UTF-8")}" +
                    "&code=${URLEncoder.encode(code, "UTF-8")}"

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }

        BufferedWriter(OutputStreamWriter(conn.outputStream, Charsets.UTF_8)).use { it.write(form) }

        val rc = conn.responseCode
        val text = (if (rc in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

        conn.disconnect()

        val token = Regex(""""access_token"\s*:\s*"([^"]+)"""")
            .find(text)?.groupValues?.getOrNull(1)

        if (token.isNullOrBlank()) {
            throw RuntimeException("AniList token exchange falhou (HTTP $rc): $text")
        }

        token
    }

private suspend fun saveAniListToken(context: Context, token: String) {
    context.dataStore.edit { prefs -> prefs[KEY_ANILIST_TOKEN] = token }
}

private suspend fun loadAniListToken(context: Context): String? {
    val prefs = context.dataStore.data.first()
    return prefs[KEY_ANILIST_TOKEN]
}

private suspend fun clearAniListToken(context: Context) {
    context.dataStore.edit { prefs -> prefs.remove(KEY_ANILIST_TOKEN) }
}

/* =========================
   AniList GraphQL Sync
   ========================= */

private fun jsonQuote(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

private suspend fun aniListGraphQL(token: String, query: String, variablesJson: String): String =
    withContext(Dispatchers.IO) {
        val url = URL("https://graphql.anilist.co")
        val payload = """{"query":${jsonQuote(query)},"variables":$variablesJson}"""

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }

        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

        conn.disconnect()

        if (code !in 200..299) throw RuntimeException("AniList GraphQL HTTP $code: $text")
        text
    }

/**
 * ✅ Não fixa type=MANGA para evitar falhas se o ID for outro tipo.
 * Media(id) já resolve o item; mediaListEntry funciona se houver lista para o user.
 */
private suspend fun aniListGetProgress(token: String, mediaId: Int): Int {
    val q = """
        query (${"$"}mediaId: Int) {
          Media(id: ${"$"}mediaId) {
            mediaListEntry {
              progress
            }
          }
        }
    """.trimIndent()

    val json = aniListGraphQL(token, q, """{"mediaId":$mediaId}""")
    return Regex(""""progress"\s*:\s*(\d+)""")
        .find(json)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
}

private suspend fun aniListSetProgress(token: String, mediaId: Int, progress: Int) {
    val m = """
        mutation (${"$"}mediaId: Int, ${"$"}progress: Int) {
          SaveMediaListEntry(mediaId: ${"$"}mediaId, progress: ${"$"}progress) {
            id
            progress
          }
        }
    """.trimIndent()

    aniListGraphQL(token, m, """{"mediaId":$mediaId,"progress":$progress}""")
}

private suspend fun syncAllAniListProgress(
    token: String,
    series: List<Series>,
    onProgressMain: suspend (done: Int, total: Int, title: String?) -> Unit
): String = withContext(Dispatchers.IO) {

    val candidates = series.filter { it.anilistMediaId != null && it.lastReadChapter > 0 }
    val total = candidates.size
    var done = 0

    val updatedTitles = mutableListOf<String>()
    val failedTitles = mutableListOf<String>()

    for (s in candidates) {
        withContext(Dispatchers.Main) { onProgressMain(done, total, s.title) }

        try {
            val mediaId = s.anilistMediaId!!
            val remote = aniListGetProgress(token, mediaId)
            val local = s.lastReadChapter

            if (local > remote) {
                aniListSetProgress(token, mediaId, local)
                updatedTitles.add("${s.title} → $local")
            }
            // se local <= remote: não dizemos nada (não há "ignoradas")
        } catch (_: Exception) {
            failedTitles.add(s.title)
        }

        done++
        withContext(Dispatchers.Main) { onProgressMain(done, total, s.title) }
    }

    if (total == 0) return@withContext "Não há séries com AniList ID para sincronizar."

    val lines = mutableListOf<String>()

    if (updatedTitles.isNotEmpty()) {
        lines.add("✅ Atualizadas: ${updatedTitles.size}")
        updatedTitles.take(12).forEach { lines.add("• $it") }
        if (updatedTitles.size > 12) lines.add("• … +${updatedTitles.size - 12}")
    } else {
        lines.add("✅ Atualizadas: 0")
        lines.add("Nenhuma série precisava de update.")
    }

    if (failedTitles.isNotEmpty()) {
        lines.add("")
        lines.add("⚠️ Falhas: ${failedTitles.size}")
        failedTitles.take(12).forEach { lines.add("• $it") }
        if (failedTitles.size > 12) lines.add("• … +${failedTitles.size - 12}")
    }

    lines.joinToString("\n")
}
private suspend fun buildDefaultsFromSeeds(context: Context, seeds: List<SeedSeries>): List<Series> =
    withContext(Dispatchers.IO) {
        val out = mutableListOf<Series>()

        for (seed in seeds) {
            val url = seed.url.trim()
            val id = makeStableIdFromSeriesUrl(url)

            val html = try {
                httpGetText(url)
            } catch (_: Exception) {
                out.add(
                    Series(
                        id = id,
                        title = seed.preferredTitle ?: id,
                        seriesUrl = url,
                        maxChapter = 0,
                        imageRes = seed.imageRes,
                        coverUrl = null,
                        lastUpdateMillis = 0L,
                        lastReadChapter = 0,
                        anilistMediaId = seed.anilistMediaId
                    )
                )
                continue
            }

            val titleFromHtml = extractTitleFromHtml(html)
            val latest = extractLatestChapterNumber(html) ?: 0
            val cover = extractOgImage(html)
            val title = seed.preferredTitle ?: (titleFromHtml ?: id)

            out.add(
                Series(
                    id = id,
                    title = title,
                    seriesUrl = url,
                    maxChapter = latest,
                    imageRes = seed.imageRes,
                    coverUrl = if (seed.imageRes != null) null else cover,
                    lastUpdateMillis = 0L,
                    lastReadChapter = 0,
                    anilistMediaId = seed.anilistMediaId
                )
            )
        }

        saveSeriesList(context, out)
        for (s in out) saveMaxChapter(context, s.id, s.maxChapter)
        out
    }

/* =========================
   Add manhwa by URL
   ========================= */

private suspend fun addManhwaFlow(
    context: Context,
    seriesState: MutableList<Series>,
    inputUrl: String
): String = withContext(Dispatchers.IO) {
    val url = inputUrl.trim()
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        return@withContext "URL inválido (precisa começar por http/https)."
    }

    val html = try {
        httpGetText(url)
    } catch (_: Exception) {
        return@withContext "Não consegui abrir a página da série."
    }

    val title = extractTitleFromHtml(html) ?: "Manhwa"
    val latest = extractLatestChapterNumber(html) ?: 0
    val cover = extractOgImage(html)
    val id = makeStableIdFromSeriesUrl(url)

    if (seriesState.any { it.id == id }) {
        return@withContext "Já existe na tua lista: $title"
    }

    val newSeries = attachSeedAniIdIfMissing(
        Series(
            id = id,
            title = title,
            seriesUrl = url,
            maxChapter = latest,
            coverUrl = cover,
            lastUpdateMillis = 0L,
            lastReadChapter = 0,
            anilistMediaId = null
        )
    )

    withContext(Dispatchers.Main) { seriesState.add(newSeries) }

    saveSeriesList(context, seriesState.toList())
    if (latest > 0) saveMaxChapter(context, id, latest)

    "Adicionado: $title (até $latest)"
}

/* =========================
   Updates with progress
   ========================= */

private suspend fun checkLatestBySeriesPageWithProgress(
    series: List<Series>,
    onProgressMain: suspend (done: Int, total: Int, title: String?) -> Unit
): Map<String, Int?> = withContext(Dispatchers.IO) {
    val out = mutableMapOf<String, Int?>()
    val total = series.size
    var done = 0

    for (s in series) {
        withContext(Dispatchers.Main) { onProgressMain(done, total, s.title) }

        out[s.id] = try {
            val html = httpGetText(s.seriesUrl)
            extractLatestChapterNumber(html)
        } catch (_: Exception) {
            null
        }

        done += 1
        withContext(Dispatchers.Main) { onProgressMain(done, total, s.title) }
    }

    out
}

private fun buildUpdateMessage(
    current: List<Series>,
    beforeMax: Map<String, Int>,
    updates: Map<String, Int?>
): String {
    val lines = mutableListOf<String>()
    var failed = 0

    for (s in current) {
        val latest = updates[s.id]
        if (latest == null) {
            failed++
            continue
        }
        val before = beforeMax[s.id] ?: s.maxChapter
        if (latest > before) {
            lines.add("✅ ${s.title}: +${latest - before} capítulo(s) ($before → $latest)")
        }
    }

    return when {
        lines.isNotEmpty() -> lines.joinToString("\n")
        failed > 0 -> "Não houve nenhum update (algumas séries não puderam ser verificadas)."
        else -> "Não houve nenhum update."
    }
}

/* =========================
   HTML helpers
   ========================= */

private fun extractLatestChapterNumber(html: String): Int? {
    val regex = Regex(
        pattern = """/chapter/(\d+)\b""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    var max: Int? = null
    for (m in regex.findAll(html)) {
        val n = m.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
        max = if (max == null || n > max) n else max
    }
    return max
}

private fun extractTitleFromHtml(html: String): String? {
    val m = Regex(
        """<title>\s*(.*?)\s*</title>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).find(html) ?: return null

    val raw = m.groupValues[1]
        .replace(Regex("""\s+"""), " ")
        .trim()

    return raw
        .replace(" | Asura Scans", "", ignoreCase = true)
        .replace(" - Asura Scans", "", ignoreCase = true)
        .trim()
}

private fun extractOgImage(html: String): String? {
    Regex(
        """<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    ).find(html)?.let {
        return it.groupValues.getOrNull(1)?.trim()?.ifBlank { null }
    }

    Regex(
        """<meta[^>]+name=["']twitter:image["'][^>]+content=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    ).find(html)?.let {
        return it.groupValues.getOrNull(1)?.trim()?.ifBlank { null }
    }

    return null
}

private fun makeStableIdFromSeriesUrl(url: String): String {
    val lower = url.lowercase(Locale.ROOT)
    val idx = lower.indexOf("/series/")
    if (idx >= 0) {
        val after = url.substring(idx + "/series/".length)
        val slug = after.trim('/').takeWhile { it != '/' }
        val cleaned = slug.replace(Regex("""[^a-zA-Z0-9_-]"""), "")
        if (cleaned.isNotBlank()) return cleaned
    }
    val h = url.hashCode().toString().replace("-", "n")
    return "s_$h"
}

/* =========================
   HTTP
   ========================= */

private fun httpGetText(urlStr: String): String {
    val url = URL(urlStr)
    val conn = (url.openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 20_000
        requestMethod = "GET"
        setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        )
        setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        setRequestProperty("Accept-Language", "pt-PT,pt;q=0.9,en;q=0.8")
    }

    val code = conn.responseCode
    if (code !in 200..299) {
        conn.disconnect()
        throw RuntimeException("HTTP $code")
    }

    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    conn.disconnect()
    return text
}

/* =========================
   DataStore: maxChapter
   ========================= */

private fun maxChapterKey(seriesId: String) = intPreferencesKey("maxChapter_$seriesId")

private suspend fun saveMaxChapter(context: Context, seriesId: String, maxChapter: Int) {
    context.dataStore.edit { prefs ->
        prefs[maxChapterKey(seriesId)] = maxChapter
    }
}

private suspend fun loadAllMaxChapters(context: Context): Map<String, Int> {
    val prefs = context.dataStore.data.first()
    val out = mutableMapOf<String, Int>()
    for (entry in prefs.asMap()) {
        val k = entry.key.name
        if (!k.startsWith("maxChapter_")) continue
        val id = k.removePrefix("maxChapter_")
        val v = entry.value
        if (v is Int) out[id] = v
    }
    return out
}

/* =========================
   DataStore: lastRead
   ========================= */

private fun lastReadKey(seriesId: String) = intPreferencesKey("lastRead_$seriesId")

private suspend fun saveLastRead(context: Context, seriesId: String, lastRead: Int) {
    context.dataStore.edit { prefs ->
        prefs[lastReadKey(seriesId)] = lastRead
    }
}

private suspend fun loadAllLastRead(context: Context): Map<String, Int> {
    val prefs = context.dataStore.data.first()
    val out = mutableMapOf<String, Int>()
    for (entry in prefs.asMap()) {
        val k = entry.key.name
        if (!k.startsWith("lastRead_")) continue
        val id = k.removePrefix("lastRead_")
        val v = entry.value
        if (v is Int) out[id] = v
    }
    return out
}

private suspend fun removeSeriesKeys(context: Context, seriesId: String) {
    context.dataStore.edit { prefs ->
        prefs.remove(maxChapterKey(seriesId))
        prefs.remove(lastReadKey(seriesId))
    }
}

/* =========================
   DataStore: series list (V3 + migração V2/V1)
   - ✅ V3 inclui anilistMediaId
   - ✅ nunca guardar imageRes
   ========================= */

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")

private fun serializeSeriesV3(s: Series): String {
    val cover = s.coverUrl ?: ""
    val imgRes = "" // nunca guardar resId
    val last = s.lastUpdateMillis.toString()
    val mediaId = s.anilistMediaId?.toString() ?: ""

    // V3: id|title|url|max|cover|imgRes|lastUpdate|anilistMediaId
    return listOf(
        s.id, s.title, s.seriesUrl, s.maxChapter.toString(), cover, imgRes, last, mediaId
    ).joinToString("|") { enc(it) }
}

private fun deserializeSeriesAny(line: String): Series? {
    val parts = line.split("|")

    // V3
    if (parts.size >= 8) {
        val id = dec(parts[0])
        val title = dec(parts[1])
        val url = dec(parts[2])
        val max = dec(parts[3]).toIntOrNull() ?: 0
        val cover = dec(parts[4]).ifBlank { null }
        val last = dec(parts[6]).toLongOrNull() ?: 0L
        val mediaId = dec(parts[7]).toIntOrNull()

        return Series(
            id = id,
            title = title,
            seriesUrl = url,
            maxChapter = max,
            coverUrl = cover,
            imageRes = imageResForKnownSeeds(url),
            lastUpdateMillis = last,
            lastReadChapter = 0,
            anilistMediaId = mediaId
        )
    }

    // V2
    if (parts.size >= 7) {
        val id = dec(parts[0])
        val title = dec(parts[1])
        val url = dec(parts[2])
        val max = dec(parts[3]).toIntOrNull() ?: 0
        val cover = dec(parts[4]).ifBlank { null }
        val last = dec(parts[6]).toLongOrNull() ?: 0L

        return Series(
            id = id,
            title = title,
            seriesUrl = url,
            maxChapter = max,
            coverUrl = cover,
            imageRes = imageResForKnownSeeds(url),
            lastUpdateMillis = last,
            lastReadChapter = 0,
            anilistMediaId = null
        )
    }

    // V1
    if (parts.size >= 6) {
        val id = dec(parts[0])
        val title = dec(parts[1])
        val url = dec(parts[2])
        val max = dec(parts[3]).toIntOrNull() ?: 0
        val cover = dec(parts[4]).ifBlank { null }

        return Series(
            id = id,
            title = title,
            seriesUrl = url,
            maxChapter = max,
            coverUrl = cover,
            imageRes = imageResForKnownSeeds(url),
            lastUpdateMillis = 0L,
            lastReadChapter = 0,
            anilistMediaId = null
        )
    }

    return null
}

private suspend fun saveSeriesList(context: Context, list: List<Series>) {
    context.dataStore.edit { prefs ->
        prefs[KEY_SAVED_SERIES_V3] = list.map { serializeSeriesV3(it) }.toSet()
    }
}

private suspend fun loadSavedSeries(context: Context): List<Series> {
    val prefs = context.dataStore.data.first()
    val lastReadMap = loadAllLastRead(context)

    // V3
    val v3 = prefs[KEY_SAVED_SERIES_V3].orEmpty()
    if (v3.isNotEmpty()) {
        val base = v3.mapNotNull { deserializeSeriesAny(it) }
        val fixed = base.map { s ->
            attachSeedAniIdIfMissing(
                s.copy(
                    imageRes = imageResForKnownSeeds(s.seriesUrl),
                    lastReadChapter = lastReadMap[s.id] ?: 0
                )
            )
        }
        saveSeriesList(context, fixed) // regrava (garante ids + formato)
        return fixed
    }

    // V2 -> migra para V3 + re-associa ids seed
    val v2 = prefs[KEY_SAVED_SERIES_V2].orEmpty()
    if (v2.isNotEmpty()) {
        val base = v2.mapNotNull { deserializeSeriesAny(it) }
        val fixed = base.map { s ->
            attachSeedAniIdIfMissing(
                s.copy(
                    imageRes = imageResForKnownSeeds(s.seriesUrl),
                    lastReadChapter = lastReadMap[s.id] ?: 0
                )
            )
        }
        saveSeriesList(context, fixed)
        return fixed
    }

    // V1 -> migra para V3 + re-associa ids seed
    val v1 = prefs[KEY_SAVED_SERIES_V1].orEmpty()
    if (v1.isNotEmpty()) {
        val base = v1.mapNotNull { deserializeSeriesAny(it) }
        val fixed = base.map { s ->
            attachSeedAniIdIfMissing(
                s.copy(
                    imageRes = imageResForKnownSeeds(s.seriesUrl),
                    lastReadChapter = lastReadMap[s.id] ?: 0
                )
            )
        }
        saveSeriesList(context, fixed)
        return fixed
    }

    return emptyList()
}
