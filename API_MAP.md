# API_MAP (upload this file with every phase; the AI must return it updated)

Project: Wosux Stream (currently still named FreeTV in code). Live TV app, M3U playlists from iptv-org or a custom link.
All code: package `com.freetv.app`. ViewBinding ON. Theme: AppCompat (NO Material components). No Compose.
Paths: `K` = `app/src/main/java/com/freetv/app`, `R` = `app/src/main/res`

## Build facts
- Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7 (installed by the GitHub Actions workflow, no wrapper in repo), Java 17
- compileSdk 34, targetSdk 34, minSdk 21
- namespace `com.freetv.app` (code package, do not change). applicationId `com.wosux.stream`; debug build adds suffix `.test` (installs as `com.wosux.stream.test`)
- Built only by GitHub Actions (Actions tab > Build APK > Run workflow, or by pushing a tag `v*`; pushing to a branch does NOT build). Release is signed with `app/freetv.keystore`.
- CI outputs: `Wosux-Stream.apk` (release) and `Wosux-Stream-test.apk` (debug), uploaded as artifact `Wosux-Stream-apk`; a `v*` tag also attaches both to a GitHub Release.
- Dependencies: core-ktx 1.13.1, appcompat 1.7.0, recyclerview 1.3.2, activity-ktx 1.9.1, lifecycle (viewmodel, livedata, runtime) 2.8.4,
  kotlinx-coroutines-android 1.8.1, media3 1.4.1 (exoplayer, exoplayer-hls, exoplayer-dash, ui), coil 2.7.0, core-splashscreen 1.0.1, okhttp 4.12.0, media3-datasource-okhttp 1.4.1

## K/Models.kt
```kotlin
data class Channel(val name: String, val url: String, val logo: String? = null, val group: String? = null,
                   val referrer: String? = null, val userAgent: String? = null)
data class Source(val name: String, val url: String)
object ChannelStore { var playlist: List<Channel>; var index: Int }   // hand-off list from the list screen to PlayerActivity
object Sources { val ALL: Source; val categories: List<Source>; val languages: List<Source>; val countries: List<Source> }
```

## K/Prefs.kt (object; `Prefs.init(ctx)` is called in FreeTVApp.onCreate; SharedPreferences file "freetv")
```kotlin
var source: Source            // selected source, persisted
var customUrl: String
fun favorites(): List<Channel>
fun isFavorite(url: String): Boolean
fun toggleFavorite(c: Channel): Boolean   // true = now a favorite, false = removed
```
Favorites are stored as JSON in key "favs" (fields n, u, l, g, r, a), cached in memory after first load.

## K/M3uParser.kt
```kotlin
object M3uParser { fun parse(reader: BufferedReader): List<Channel> }   // dedupes by url; reads #EXTINF attrs + #EXTVLCOPT referrer/user-agent
```

## K/MainViewModel.kt (AndroidViewModel)
```kotlin
val channels: MutableLiveData<List<Channel>>
val loading: MutableLiveData<Boolean>
val message: MutableLiveData<String?>
val source: MutableLiveData<Source>
var query: String
var favoritesOnly: Boolean
fun setSource(s: Source)
fun load(force: Boolean)   // shows cached playlist first, refreshes if cache older than 6h or force; cache file filesDir/pl_<hash>.m3u
```
Private: cacheFile(), parseFile(), download() (HttpURLConnection, manual redirects).

## K/ChannelAdapter.kt
```kotlin
class ChannelAdapter(isFavorite: (Channel) -> Boolean,
                     onClick: (Channel, Int) -> Unit,
                     onLongClick: (Channel, Int) -> Unit) : ListAdapter<Channel, ChannelAdapter.VH>
class VH(val b: ItemChannelBinding)   // focus: scales 1.06 + translationZ; logo via Coil .load(url) with ic_tv placeholder/error
```

## K/FreeTVApp.kt
`class FreeTVApp : Application` : onCreate calls `CrashLog.install(this)` then `Prefs.init(this)`.

## K/MainActivity.kt (grid screen)
Grid of channels (span = screen dp / 150, clamp 2..10), buttons Source / Search / Favorites / Refresh, dialogs (source menu, country/category/language lists,
search text, custom M3U link). Click = set ChannelStore.playlist/index then start PlayerActivity. onResume after player: refresh favorites + restore focus.
onCreate calls `installSplashScreen()` as its FIRST line (before `super.onCreate`) and `CrashLog.showIfAny(this)` right after setContentView (keep both lines if the screen is ever moved).

## K/PlayerActivity.kt (full-screen live player)
ExoPlayer (Media3) created in onStart, released in onStop. `playCurrent()`, `zap(delta)`, overlay auto-hide 5 s, one retry on error,
BEHIND_LIVE_WINDOW handling, remote keys (UP/CH+ next, DOWN/CH- previous, OK toggles info, LEFT/RIGHT show overlay), hides system bars, keeps screen on.
Reads ChannelStore.playlist/index. Uses DefaultHttpDataSource with per-channel user-agent / referrer.

## Layout ids
- activity_main: progress, count, btnSource, btnSearch, btnFav, btnRefresh, list, empty
- item_channel: logo, star, name
- activity_player: playerView, touchLayer, buffering, error, overlay, chName, chInfo, btnPrev, btnFav, btnNext

## Resources
- colors: bg, surface, surface_focus, accent, text, text_dim, logo_bg, brand_blue, brand_blue_mid, brand_cyan, silver, silver_dim, live_red, ok_green, dead_gray, scrim, ic_launcher_background
- styles: Theme.FreeTV (AppCompat.NoActionBar), Theme.FreeTV.Player, Theme.Wosux.Starting (parent Theme.SplashScreen; splash theme of MainActivity), Btn
- drawables: bg_button, bg_card, bg_logo, bg_overlay, ic_tv, banner (drawable-xhdpi)
- mipmaps: ic_launcher (PNG in mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi + adaptive XML in mipmap-anydpi-v26), ic_launcher_foreground (PNG in the same five density folders; also the splash icon),
  ic_launcher_round (adaptive XML in mipmap-anydpi-v26 only)
- strings: app_name, source_btn, search, search_active, favorites_off, favorites_on, refresh, channels_count, empty_list, empty_favorites,
  fav_added, fav_removed, choose_source, src_all, src_country, src_category, src_language, src_custom, search_title, search_hint,
  custom_hint, custom_name, custom_invalid, load, clear, err_empty, err_refresh, err_load, prev, next, fav_off, fav_on, player_error

## Manifest
Permissions INTERNET, ACCESS_NETWORK_STATE. Leanback + touchscreen not required. usesCleartextTraffic=true. App class .FreeTVApp, theme Theme.FreeTV, icon @mipmap/ic_launcher, roundIcon @mipmap/ic_launcher_round, TV banner @drawable/banner.
MainActivity = LAUNCHER + LEANBACK_LAUNCHER, theme Theme.Wosux.Starting (splash, switches to Theme.FreeTV after launch). PlayerActivity = sensorLandscape, theme Theme.FreeTV.Player, not exported.

## Added by phases (the AI appends below)

### Phase 01: CrashLog
- `K/CrashLog.kt`, `object CrashLog`:
  - `fun install(app: Application)`: sets the default uncaught-exception handler; writes device, SDK, versionName, thread and stack trace to `filesDir/last_crash.txt`, then calls the previous handler.
  - `fun pending(ctx: Context): String?`: saved crash text or null.
  - `fun clear(ctx: Context)`: deletes the saved crash.
  - `fun showIfAny(activity: AppCompatActivity)`: dialog "Wosux Stream crashed last time" (first 4000 chars, selectable); "Copy" copies the FULL text and keeps the dialog open; "Close" deletes the file.
- Hooks: `FreeTVApp.onCreate` -> `CrashLog.install(this)` first; `MainActivity.onCreate` -> `CrashLog.showIfAny(this)` after setContentView.
- Limitation: a crash inside Application.onCreate repeats before the dialog can appear.

### Phase 02: Brand colors
- `R/values/colors.xml` values changed: bg #0A0D12, surface #131923, surface_focus #1C2533, accent #02E1FE (cyan, was yellow), text #FFFFFF, text_dim #9AA4B2, logo_bg #E9ECF2.
- New colors: brand_blue #0088FF, brand_blue_mid #02AEF9, brand_cyan #02E1FE, silver #E2E3E9, silver_dim #9AA0AA, live_red #FF4D5A, ok_green #2EE59D, dead_gray #5B6475, scrim #CC0A0D12.
- Still hard-coded elsewhere (to be replaced in later phases): item_channel.xml star color #F59E0B, activity_player.xml error background #AA000000, bg_overlay.xml gradient.

### Phase 03: App name, applicationId, CI file names
- `app/build.gradle.kts`: `applicationId` = `"com.wosux.stream"` (was `com.freetv.app`). `namespace` stays `"com.freetv.app"`. Signing config `freetv`, `app/freetv.keystore` and the debug `applicationIdSuffix = ".test"` are unchanged.
- `R/values/strings.xml`: `app_name` = `Wosux Stream` (was `FreeTV`). The launcher label comes from `@string/app_name` in the manifest.
- `.github/workflows/build-apk.yml`: `on:` is now only `push` with `tags: [ 'v*' ]` plus `workflow_dispatch:` (the branch-push trigger was removed). `cp` lines, the artifact (`Wosux-Stream-apk`) and the release `files:` list use `Wosux-Stream.apk` and `Wosux-Stream-test.apk`. Nothing else in the workflow changed.
- New applicationId = new app on the device: the old FreeTV must be uninstalled by hand and its favorites do not carry over.
- Still says FreeTV (left alone on purpose, not part of this phase): class `FreeTVApp`, styles `Theme.FreeTV` / `Theme.FreeTV.Player`, `rootProject.name` in `settings.gradle.kts`, the `.keystore` file name, and the text in `README.md` (updated in Phase 40).

### Phase 04: Launcher icon + TV banner
- New `R/mipmap-anydpi-v26/ic_launcher.xml` and `R/mipmap-anydpi-v26/ic_launcher_round.xml`: identical `<adaptive-icon>` files, `<background android:drawable="@color/ic_launcher_background" />` and `<foreground android:drawable="@mipmap/ic_launcher_foreground" />`. Android 8.0+ (API 26+) uses these; older Android uses the PNG `ic_launcher` from the density folders.
- New `R/values/ic_launcher_background.xml`: color `ic_launcher_background` = `#0A0D12` (same value as `bg`, separate name on purpose for the launcher).
- `app/src/main/AndroidManifest.xml`: `<application>` gained `android:roundIcon="@mipmap/ic_launcher_round"`. Nothing else changed (`android:icon="@mipmap/ic_launcher"` and `android:banner="@drawable/banner"` were already there).
- Art files made and placed by the user, not by the AI (sizes as the Phase 04 plan specifies, not checked by the AI): `ic_launcher.png` (48/72/96/144/192 px) and `ic_launcher_foreground.png` (108/162/216/324/432 px) in `R/mipmap-mdpi`, `-hdpi`, `-xhdpi`, `-xxhdpi`, `-xxxhdpi`, and the new TV banner `R/drawable-xhdpi/banner.png` (same name as before, so no manifest change was needed for it).
- Resource names now in use: mipmap `ic_launcher`, `ic_launcher_round`, `ic_launcher_foreground`; color `ic_launcher_background`. Phase 05 (splash) may reference `@mipmap/ic_launcher_foreground`.
- Limitation: `ic_launcher_round` exists only in `mipmap-anydpi-v26` (no round PNGs in the density folders), so it resolves on Android 8.0+ only.

### Phase 05: Splash screen
- `app/build.gradle.kts`: new dependency `implementation("androidx.core:core-splashscreen:1.0.1")` (the only new library; no other version changed).
- `R/values/themes.xml`: new style `Theme.Wosux.Starting` (parent `Theme.SplashScreen`, supplied by the library): `windowSplashScreenBackground` = `@color/bg`, `windowSplashScreenAnimatedIcon` = `@mipmap/ic_launcher_foreground`, `postSplashScreenTheme` = `@style/Theme.FreeTV`. Existing styles `Theme.FreeTV`, `Theme.FreeTV.Player` and `Btn` are untouched.
- `app/src/main/AndroidManifest.xml`: `MainActivity` now has `android:theme="@style/Theme.Wosux.Starting"`. The `<application>` theme stays `Theme.FreeTV`; `PlayerActivity` keeps `Theme.FreeTV.Player`.
- `K/MainActivity.kt`: new import `androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen`; `onCreate` now begins with `installSplashScreen()` BEFORE `super.onCreate(savedInstanceState)`. Every other line is unchanged (including `CrashLog.showIfAny(this)` after setContentView).
- No `setKeepOnScreenCondition` and no exit animation: the splash goes away as soon as MainActivity draws its first frame.
- Rule for later phases: `installSplashScreen()` must stay the first call in `MainActivity.onCreate`, before `super.onCreate`, wherever that activity is edited (Phase 17 keeps it). Other activities do not use the splash theme.
- Needs `@mipmap/ic_launcher_foreground` (user-supplied art from Phase 04); the build fails if that PNG is missing.

### Phase 06: Net.kt (one shared network client)
- `app/build.gradle.kts`: new dependencies `implementation("com.squareup.okhttp3:okhttp:4.12.0")` and `implementation("androidx.media3:media3-datasource-okhttp:1.4.1")`. No other version changed.
- New `K/Net.kt`, `object Net`:
  - `const val UA = "WosuxStream/1.0 (Android)"`
  - `val client: OkHttpClient` (lazy): connect 10 s, read 20 s, retryOnConnectionFailure true, followRedirects + followSslRedirects true, `Dispatcher` maxRequests 64 / maxRequestsPerHost 16, application interceptor that sets `User-Agent = UA` only when the request has no User-Agent header. No `Cache` (live segments must not fill the disk).
  - `val probe: OkHttpClient` (lazy) = `client.newBuilder()` with connect 4 s, read 4 s, callTimeout 6 s. Shares the connection pool and dispatcher of `client`.
- `Net` is NOT used anywhere yet (HttpURLConnection in MainViewModel, Coil and DefaultHttpDataSource in PlayerActivity are unchanged). Phases 07, 08, 09, 11 start using it.
