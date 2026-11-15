package com.rafsan.tvlauncher

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val wallpaperIo = Executors.newSingleThreadExecutor()
    private val idLocale = Locale("id", "ID")

    private lateinit var bgA: ImageView
    private lateinit var bgB: ImageView
    private var bgFront = false
    private lateinit var dimView: View
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weatherBody: TextView
    private lateinit var prayerBody: TextView
    private lateinit var cctvStatus: TextView
    private lateinit var cctvSurface: TextureView
    private lateinit var appRow: LinearLayout
    private var cctv: CctvPlayer? = null

    private var wallpaperFiles: List<File> = emptyList()
    private var wallpaperIndex = 0
    private var appSignature = ""
    private var started = false

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = SimpleDateFormat("HH:mm", Locale.US).format(now)
            date.text = SimpleDateFormat("EEEE, d MMMM yyyy", idLocale).format(now)
            ui.postDelayed(this, 20000)
        }
    }
    private val refresh = object : Runnable {
        override fun run() {
            loadWidgets()
            ui.postDelayed(this, 15 * 60 * 1000L)
        }
    }
    private val slide = object : Runnable {
        override fun run() {
            showNextWallpaper()
            ui.postDelayed(this, Prefs.slideSec.coerceAtLeast(10) * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Sfx.init(this)
        importConfig(intent)
        buildUi()
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { old, new ->
            if (old != null && new != null && started) Sfx.nav()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (importConfig(intent) && started) {
            loadWidgets()
            prepareWallpapers()
            cctv?.start(Prefs.rtsp)
        }
    }

    /**
     * Mengisi setelan lewat adb tanpa mengetik di TV, contoh:
     * adb shell "am start -n com.rafsan.tvlauncher/.MainActivity --es rtsp 'rtsp://...'"
     * Kunci yang diterima: city, country, rtsp, wp1, wp2, wp3.
     */
    private fun importConfig(intent: Intent?): Boolean {
        val extras = intent?.extras ?: return false
        var changed = false
        Prefs.edit {
            for (key in listOf("city", "country", "rtsp", "wp1", "wp2", "wp3")) {
                val value = extras.getString(key)
                if (value != null) {
                    putString(key, value.trim())
                    changed = true
                }
            }
        }
        if (changed) Toast.makeText(this, "Setelan diperbarui", Toast.LENGTH_SHORT).show()
        return changed
    }

    override fun onStart() {
        super.onStart()
        started = true
        tick.run()
        refresh.run()
        prepareWallpapers()
        applyBrightness(Prefs.brightness)
        cctv?.start(Prefs.rtsp)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    override fun onStop() {
        super.onStop()
        started = false
        ui.removeCallbacks(tick)
        ui.removeCallbacks(refresh)
        ui.removeCallbacks(slide)
        cctv?.stop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Ini layar utama, tombol Back tidak melakukan apa-apa.
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showBrightnessDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------------ UI

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun buildUi() {
        val root = FrameLayout(this)
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(40, 26, 10), Color.rgb(8, 8, 12))
        )
        root.background = gradient

        bgA = ImageView(this)
        bgB = ImageView(this)
        for (v in listOf(bgA, bgB)) {
            v.scaleType = ImageView.ScaleType.CENTER_CROP
            v.alpha = 0f
            root.addView(v, FrameLayout.LayoutParams(-1, -1))
        }
        val scrim = View(this)
        scrim.setBackgroundColor(Color.argb(90, 0, 0, 0))
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(32), dp(22), dp(32), dp(22))
        content.clipChildren = false
        content.clipToPadding = false
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        // Baris atas: jam + tombol
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        content.addView(top, LinearLayout.LayoutParams(-1, -2))

        val timeBox = LinearLayout(this)
        timeBox.orientation = LinearLayout.VERTICAL
        clock = text(40f, true)
        date = text(14f, false)
        timeBox.addView(clock)
        timeBox.addView(date)
        top.addView(timeBox, LinearLayout.LayoutParams(0, -2, 1f))

        top.addView(button("Kecerahan") { showBrightnessDialog() })
        top.addView(button("Pengaturan") { startActivity(Intent(this, SettingsActivity::class.java)) })
        top.addView(button("Setelan TV") {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        })

        // Ruang kosong supaya wallpaper kelihatan
        content.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        // Baris widget
        val widgets = LinearLayout(this)
        widgets.orientation = LinearLayout.HORIZONTAL
        widgets.clipChildren = false
        content.addView(widgets, LinearLayout.LayoutParams(-1, dp(170)))

        weatherBody = card(widgets, "CUACA", 210) { loadWidgets() }
        prayerBody = card(widgets, "JADWAL SHOLAT", 210) { loadWidgets() }
        prayerBody.typeface = Typeface.MONOSPACE
        buildCctvCard(widgets)

        // Baris aplikasi
        val label = text(13f, true)
        label.text = "APLIKASI"
        label.setPadding(dp(4), dp(14), 0, dp(6))
        content.addView(label)

        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.clipChildren = false
        scroll.clipToPadding = false
        appRow = LinearLayout(this)
        appRow.orientation = LinearLayout.HORIZONTAL
        appRow.clipChildren = false
        appRow.setPadding(dp(4), dp(8), dp(4), dp(8))
        scroll.addView(appRow, FrameLayout.LayoutParams(-2, -2))
        content.addView(scroll, LinearLayout.LayoutParams(-1, -2))

        // Peredup cadangan (hanya di dalam launcher) kalau izin overlay belum ada
        dimView = View(this)
        dimView.setBackgroundColor(Color.BLACK)
        dimView.alpha = 0f
        root.addView(dimView, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)
    }

    private fun text(size: Float, bold: Boolean): TextView {
        val t = TextView(this)
        t.setTextColor(Color.WHITE)
        t.textSize = size
        t.setShadowLayer(6f, 0f, 2f, Color.BLACK)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun button(label: String, action: () -> Unit): View {
        val b = text(14f, true)
        b.text = label
        b.isFocusable = true
        b.isSoundEffectsEnabled = false
        b.background = Ui.focusBg(this, 20)
        b.setPadding(dp(16), dp(8), dp(16), dp(8))
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.leftMargin = dp(10)
        b.layoutParams = lp
        b.setOnClickListener {
            Sfx.select()
            action()
        }
        return b
    }

    /** Membuat kartu widget, mengembalikan TextView isinya. */
    private fun card(parent: LinearLayout, title: String, widthDp: Int, onClick: () -> Unit): TextView {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.isFocusable = true
        box.isSoundEffectsEnabled = false
        box.background = Ui.focusBg(this)
        box.setPadding(dp(12), dp(10), dp(12), dp(10))
        val head = text(11f, true)
        head.text = title
        head.setTextColor(Color.rgb(255, 200, 87))
        val body = text(13f, false)
        body.text = "Memuat…"
        body.setLineSpacing(dp(2).toFloat(), 1f)
        body.setPadding(0, dp(6), 0, 0)
        box.addView(head)
        box.addView(body)
        box.setOnClickListener {
            Sfx.select()
            onClick()
        }
        val lp = LinearLayout.LayoutParams(dp(widthDp), -1)
        lp.rightMargin = dp(10)
        parent.addView(box, lp)
        return body
    }

    private fun buildCctvCard(parent: LinearLayout) {
        val box = FrameLayout(this)
        box.isFocusable = true
        box.isSoundEffectsEnabled = false
        box.background = Ui.focusBg(this)
        box.setPadding(dp(3), dp(3), dp(3), dp(3))
        cctvSurface = TextureView(this)
        box.addView(cctvSurface, FrameLayout.LayoutParams(-1, -1))
        val head = text(11f, true)
        head.text = "CCTV"
        head.setTextColor(Color.rgb(255, 200, 87))
        head.setPadding(dp(9), dp(7), 0, 0)
        box.addView(head, FrameLayout.LayoutParams(-2, -2))
        cctvStatus = text(12f, false)
        cctvStatus.gravity = Gravity.CENTER
        box.addView(cctvStatus, FrameLayout.LayoutParams(-1, -1))
        box.setOnClickListener {
            Sfx.select()
            if (Prefs.rtsp.isEmpty()) startActivity(Intent(this, SettingsActivity::class.java))
            else startActivity(Intent(this, CctvActivity::class.java))
        }
        parent.addView(box, LinearLayout.LayoutParams(dp(296), -1))
        cctv = CctvPlayer(this, cctvSurface) { cctvStatus.text = it }
    }

    // ------------------------------------------------------------- Widget

    private fun loadWidgets() {
        val city = Prefs.city
        val country = Prefs.country
        fetch(weatherBody) { Data.weather(city) }
        fetch(prayerBody) { Data.prayer(city, country) }
    }

    private fun fetch(target: TextView, job: () -> String) {
        io.execute {
            val result = try {
                job()
            } catch (e: Exception) {
                "Gagal memuat\n" + (e.message ?: e.javaClass.simpleName)
            }
            ui.post { target.text = result }
        }
    }

    // ---------------------------------------------------------- Wallpaper

    /** Sumber wallpaper: file di folder "wallpapers" kalau ada, kalau tidak dari URL di Pengaturan. */
    private fun prepareWallpapers() {
        val urls = Prefs.wallpapers()
        wallpaperIo.execute {
            val local = getExternalFilesDir("wallpapers")?.listFiles { f ->
                val n = f.name.lowercase(Locale.US)
                n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")
            }?.sortedBy { it.name } ?: emptyList()
            val files = ArrayList<File>()
            if (local.isNotEmpty()) {
                files.addAll(local)
            } else {
                val wpCache = File(cacheDir, "wallpapers")
                wpCache.mkdirs()
                for (url in urls) {
                    val f = File(wpCache, "wp_" + Integer.toHexString(url.hashCode()) + ".img")
                    try {
                        if (!f.exists() || f.length() == 0L) Net.download(url, f)
                        if (f.exists() && f.length() > 0L) files.add(f)
                    } catch (_: Exception) {
                    }
                }
            }
            ui.post {
                wallpaperFiles = files
                ui.removeCallbacks(slide)
                if (files.isNotEmpty() && started) slide.run()
            }
        }
    }

    private fun showNextWallpaper() {
        val files = wallpaperFiles
        if (files.isEmpty()) return
        val file = files[wallpaperIndex % files.size]
        wallpaperIndex++
        wallpaperIo.execute {
            val bmp: Bitmap? = try {
                Images.decode(file, 1920, 1080)
            } catch (_: Throwable) {
                null
            }
            if (bmp != null) ui.post { crossfade(bmp) }
        }
    }

    private fun crossfade(bmp: Bitmap) {
        val incoming = if (bgFront) bgA else bgB
        val outgoing = if (bgFront) bgB else bgA
        bgFront = !bgFront
        incoming.setImageBitmap(bmp)
        incoming.animate().alpha(1f).setDuration(900).start()
        outgoing.animate().alpha(0f).setDuration(900).withEndAction { outgoing.setImageDrawable(null) }.start()
    }

    // ------------------------------------------------------------ Aplikasi

    private fun loadApps() {
        val pm = packageManager
        val found = LinkedHashMap<String, ResolveInfo>()
        val tv = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phone = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (info in pm.queryIntentActivities(tv, 0)) found[info.activityInfo.packageName] = info
        for (info in pm.queryIntentActivities(phone, 0)) {
            if (!found.containsKey(info.activityInfo.packageName)) found[info.activityInfo.packageName] = info
        }
        found.remove(packageName)
        val apps = found.values.sortedBy { it.loadLabel(pm).toString().lowercase(Locale.getDefault()) }
        val signature = apps.joinToString(",") { it.activityInfo.packageName }
        if (signature == appSignature) return
        appSignature = signature

        appRow.removeAllViews()
        for (info in apps) {
            val pkg = info.activityInfo.packageName
            val tile = FrameLayout(this)
            tile.isFocusable = true
            tile.isSoundEffectsEnabled = false
            tile.background = Ui.focusBg(this, 8)
            tile.setPadding(dp(3), dp(3), dp(3), dp(3))
            Ui.focusScale(tile, 1.1f)

            val banner = try {
                info.activityInfo.loadBanner(pm) ?: info.activityInfo.applicationInfo.loadBanner(pm)
            } catch (_: Throwable) {
                null
            }
            if (banner != null) {
                val img = ImageView(this)
                img.scaleType = ImageView.ScaleType.FIT_XY
                img.setImageDrawable(banner)
                tile.addView(img, FrameLayout.LayoutParams(-1, -1))
            } else {
                val col = LinearLayout(this)
                col.orientation = LinearLayout.VERTICAL
                col.gravity = Gravity.CENTER
                val icon = ImageView(this)
                try {
                    icon.setImageDrawable(info.loadIcon(pm))
                } catch (_: Throwable) {
                }
                col.addView(icon, LinearLayout.LayoutParams(dp(36), dp(36)))
                val name = text(10f, false)
                name.text = info.loadLabel(pm)
                name.maxLines = 1
                name.ellipsize = TextUtils.TruncateAt.END
                name.gravity = Gravity.CENTER
                col.addView(name, LinearLayout.LayoutParams(-1, -2))
                tile.addView(col, FrameLayout.LayoutParams(-1, -1))
            }

            tile.setOnClickListener {
                Sfx.select()
                val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    try {
                        startActivity(launch)
                    } catch (_: Exception) {
                    }
                }
            }
            tile.setOnLongClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
                } catch (_: Exception) {
                }
                true
            }
            val lp = LinearLayout.LayoutParams(dp(124), dp(70))
            lp.rightMargin = dp(10)
            appRow.addView(tile, lp)
        }
        if (appRow.childCount > 0 && currentFocus == null) appRow.getChildAt(0).requestFocus()
    }

    // ---------------------------------------------------------- Kecerahan

    private fun applyBrightness(percent: Int) {
        val system = Dim.setLevel(this, percent)
        dimView.alpha = if (system) 0f else (100 - percent.coerceIn(10, 100)) / 100f * 0.9f
    }

    private fun showBrightnessDialog() {
        val dialog = Dialog(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(24), dp(20), dp(24), dp(20))
        box.background = Ui.rounded(Color.argb(235, 24, 20, 14), dp(14).toFloat())

        val title = text(18f, true)
        title.text = "Kecerahan"
        val value = text(30f, true)
        value.gravity = Gravity.CENTER
        value.text = Prefs.brightness.toString() + "%"
        val hint = text(11f, false)
        hint.gravity = Gravity.CENTER
        hint.text = if (Dim.canOverlay(this)) "Kiri / kanan untuk mengatur. Berlaku di semua aplikasi."
        else "Kiri / kanan untuk mengatur. Izin overlay belum aktif, jadi baru berlaku di launcher."

        val seek = SeekBar(this)
        seek.max = 90
        seek.progress = Prefs.brightness - 10
        seek.keyProgressIncrement = 5
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = progress + 10
                value.text = "$pct%"
                Prefs.edit { putInt("brightness", pct) }
                applyBrightness(pct)
                if (fromUser) Sfx.nav()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        box.addView(title)
        box.addView(value, LinearLayout.LayoutParams(-1, -2))
        val seekLp = LinearLayout.LayoutParams(dp(420), -2)
        seekLp.topMargin = dp(8)
        seekLp.bottomMargin = dp(10)
        box.addView(seek, seekLp)
        box.addView(hint, LinearLayout.LayoutParams(-1, -2))

        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(box, ViewGroup.LayoutParams(-2, -2))
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        seek.requestFocus()
    }
}
