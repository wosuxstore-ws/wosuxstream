package com.freetv.app

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.freetv.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private lateinit var adapter: ChannelAdapter

    private var visible: List<Channel> = emptyList()
    private var cameFromPlayer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // must stay BEFORE super.onCreate (swaps Theme.Wosux.Starting for Theme.FreeTV)
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        CrashLog.showIfAny(this)

        // As many columns as fit: ~2 on a phone, ~6-8 on a TV
        val dm = resources.displayMetrics
        val span = ((dm.widthPixels / dm.density) / 150f).toInt().coerceIn(2, 10)

        adapter = ChannelAdapter(
            isFavorite = { Prefs.isFavorite(it.url) },
            onClick = { _, pos -> play(pos) },
            onLongClick = { ch, pos -> toggleFavorite(ch, pos) }
        )
        b.list.layoutManager = GridLayoutManager(this, span)
        b.list.adapter = adapter
        (b.list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        b.btnSource.setOnClickListener { showSourceMenu() }
        b.btnSearch.setOnClickListener { showSearch() }
        b.btnFav.setOnClickListener {
            vm.favoritesOnly = !vm.favoritesOnly
            applyFilter()
        }
        b.btnRefresh.setOnClickListener { vm.load(force = true) }

        vm.channels.observe(this) { applyFilter() }
        vm.loading.observe(this) { isLoading ->
            b.progress.isVisible = isLoading
            b.empty.isVisible = visible.isEmpty() && !isLoading
        }
        vm.source.observe(this) { s -> b.btnSource.text = getString(R.string.source_btn, s.name) }
        vm.message.observe(this) { msg ->
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                vm.message.value = null
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (cameFromPlayer) {
            cameFromPlayer = false
            // Favorites may have changed inside the player
            if (vm.favoritesOnly) applyFilter() else adapter.notifyItemRangeChanged(0, adapter.itemCount)

            // Put the cursor back on the channel that was playing last
            val idx = ChannelStore.index
            if (idx in visible.indices) {
                b.list.scrollToPosition(idx)
                b.list.postDelayed({
                    b.list.findViewHolderForAdapterPosition(idx)?.itemView?.requestFocus()
                }, 150)
            }
        }
    }

    private fun applyFilter() {
        val base = if (vm.favoritesOnly) Prefs.favorites() else vm.channels.value.orEmpty()
        val q = vm.query.trim()
        visible = if (q.isEmpty()) base else base.filter {
            it.name.contains(q, ignoreCase = true) || (it.group?.contains(q, ignoreCase = true) == true)
        }
        adapter.submitList(visible)

        b.count.text = getString(R.string.channels_count, visible.size)
        b.btnFav.setText(if (vm.favoritesOnly) R.string.favorites_on else R.string.favorites_off)
        b.btnSearch.text = if (q.isEmpty()) getString(R.string.search) else getString(R.string.search_active, q)
        b.empty.setText(if (vm.favoritesOnly) R.string.empty_favorites else R.string.empty_list)
        b.empty.isVisible = visible.isEmpty() && vm.loading.value != true
    }

    private fun play(position: Int) {
        if (position !in visible.indices) return
        ChannelStore.playlist = visible
        ChannelStore.index = position
        cameFromPlayer = true
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun toggleFavorite(ch: Channel, pos: Int) {
        val added = Prefs.toggleFavorite(ch)
        Toast.makeText(this, if (added) R.string.fav_added else R.string.fav_removed, Toast.LENGTH_SHORT).show()
        if (vm.favoritesOnly && !added) applyFilter() else adapter.notifyItemChanged(pos)
    }

    // ---------- dialogs (all work with a TV remote) ----------

    private fun showSourceMenu() {
        val items = arrayOf(
            getString(R.string.src_all),
            getString(R.string.src_country),
            getString(R.string.src_category),
            getString(R.string.src_language),
            getString(R.string.src_custom)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_source)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> vm.setSource(Sources.ALL)
                    1 -> pickSource(R.string.src_country, Sources.countries)
                    2 -> pickSource(R.string.src_category, Sources.categories)
                    3 -> pickSource(R.string.src_language, Sources.languages)
                    4 -> promptCustomUrl()
                }
            }
            .show()
    }

    private fun pickSource(titleRes: Int, list: List<Source>) {
        val names = list.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setItems(names) { _, i -> vm.setSource(list[i]) }
            .show()
    }

    private fun showSearch() {
        promptText(
            title = getString(R.string.search_title),
            hint = getString(R.string.search_hint),
            initial = vm.query,
            okLabel = getString(R.string.search),
            onOk = {
                vm.query = it
                applyFilter()
            },
            onClear = {
                vm.query = ""
                applyFilter()
            }
        )
    }

    private fun promptCustomUrl() {
        promptText(
            title = getString(R.string.src_custom),
            hint = getString(R.string.custom_hint),
            initial = Prefs.customUrl,
            okLabel = getString(R.string.load),
            onOk = { text ->
                val url = text.trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    Prefs.customUrl = url
                    vm.setSource(Source(getString(R.string.custom_name), url))
                } else {
                    Toast.makeText(this, R.string.custom_invalid, Toast.LENGTH_LONG).show()
                }
            },
            onClear = null
        )
    }

    private fun promptText(
        title: String,
        hint: String,
        initial: String,
        okLabel: String,
        onOk: (String) -> Unit,
        onClear: (() -> Unit)?
    ) {
        val input = EditText(this)
        input.setText(initial)
        input.setHint(hint)
        input.setSingleLine(true)
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        input.setSelection(input.text.length)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this)
        box.setPadding(pad, pad / 3, pad, 0)
        box.addView(
            input,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(okLabel) { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
        if (onClear != null) {
            builder.setNeutralButton(R.string.clear) { _, _ -> onClear() }
        }
        builder.show()
    }
}
