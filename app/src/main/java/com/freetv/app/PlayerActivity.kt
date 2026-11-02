package com.freetv.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.freetv.app.databinding.ActivityPlayerBinding

/**
 * Full-screen live player.
 *  Remote:  UP / CH+ = next channel,  DOWN / CH- = previous channel,  OK = show / hide info,
 *           LEFT / RIGHT = move between the on-screen buttons,  BACK = back to the list.
 *  Touch:   tap the screen to show the buttons.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var index = 0
    private var retried = false

    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlayTask = Runnable { b.overlay.isVisible = false }
    private val retryTask = Runnable { player?.prepare() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ChannelStore.playlist.isEmpty()) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        hideSystemBars()

        index = ChannelStore.index.coerceIn(0, ChannelStore.playlist.lastIndex)

        b.touchLayer.setOnClickListener { toggleOverlay() }
        b.btnPrev.setOnClickListener { zap(-1) }
        b.btnNext.setOnClickListener { zap(1) }
        b.btnFav.setOnClickListener { toggleFavorite() }
    }

    override fun onStart() {
        super.onStart()
        if (!::b.isInitialized) return
        val p = ExoPlayer.Builder(this).build()
        p.addListener(listener)
        b.playerView.player = p
        player = p
        playCurrent()
    }

    override fun onStop() {
        super.onStop()
        if (!::b.isInitialized) return
        handler.removeCallbacksAndMessages(null)
        b.playerView.player = null
        player?.release()
        player = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // ---------- playback ----------

    private fun playCurrent() {
        val list = ChannelStore.playlist
        val ch = list.getOrNull(index)
        if (ch == null) {
            finish()
            return
        }
        ChannelStore.index = index
        retried = false
        handler.removeCallbacks(retryTask)
        showInfo(ch)

        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setUserAgent(ch.userAgent ?: DEFAULT_UA)
        ch.referrer?.let { http.setDefaultRequestProperties(mapOf("Referer" to it)) }

        val item = MediaItem.Builder().setUri(ch.url)
        val lower = ch.url.lowercase()
        if (lower.contains(".m3u8")) {
            item.setMimeType(MimeTypes.APPLICATION_M3U8)
        } else if (lower.contains(".mpd")) {
            item.setMimeType(MimeTypes.APPLICATION_MPD)
        }

        val source = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(http)
            .createMediaSource(item.build())

        player?.let {
            it.setMediaSource(source)
            it.prepare()
            it.playWhenReady = true
        }
    }

    private fun zap(delta: Int) {
        val n = ChannelStore.playlist.size
        if (n == 0) return
        index = ((index + delta) % n + n) % n
        playCurrent()
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            b.buffering.isVisible = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) b.error.isVisible = false
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player ?: return
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                p.seekToDefaultPosition()
                p.prepare()
                return
            }
            if (!retried) {          // one quiet retry before giving up
                retried = true
                handler.postDelayed(retryTask, 1500)
                return
            }
            b.buffering.isVisible = false
            b.error.isVisible = true
            showOverlay()
        }
    }

    // ---------- overlay / favorites ----------

    private fun showInfo(ch: Channel) {
        b.chName.text = ch.name
        val pos = "${index + 1} / ${ChannelStore.playlist.size}"
        b.chInfo.text = ch.group?.let { "$pos  •  $it" } ?: pos
        b.error.isVisible = false
        updateFavButton()
        showOverlay()
    }

    private fun showOverlay() {
        b.overlay.isVisible = true
        handler.removeCallbacks(hideOverlayTask)
        handler.postDelayed(hideOverlayTask, 5000)
    }

    private fun toggleOverlay() {
        if (b.overlay.isVisible) {
            handler.removeCallbacks(hideOverlayTask)
            b.overlay.isVisible = false
        } else {
            showOverlay()
        }
    }

    private fun toggleFavorite() {
        val ch = ChannelStore.playlist.getOrNull(index) ?: return
        Prefs.toggleFavorite(ch)
        updateFavButton()
        showOverlay()
    }

    private fun updateFavButton() {
        val ch = ChannelStore.playlist.getOrNull(index) ?: return
        b.btnFav.setText(if (Prefs.isFavorite(ch.url)) R.string.fav_on else R.string.fav_off)
    }

    // ---------- remote control ----------

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val firstPress = (event?.repeatCount ?: 0) == 0
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (firstPress) zap(1)
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (firstPress) zap(-1)
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_INFO -> {
                if (firstPress) toggleOverlay()
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_SPACE -> {
                player?.let { it.playWhenReady = !it.playWhenReady }
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> showOverlay()   // then normal focus movement between buttons
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
