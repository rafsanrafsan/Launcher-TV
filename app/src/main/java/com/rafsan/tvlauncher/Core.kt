package com.rafsan.tvlauncher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Semua setelan launcher disimpan di SharedPreferences. */
object Prefs {
    const val WP1 = "https://commons.wikimedia.org/wiki/Special:FilePath/Male%20Lion%20057.jpg?width=1920"
    const val WP2 = "https://commons.wikimedia.org/wiki/Special:FilePath/Lion%20(Panthera%20leo)%20(30941994012).jpg?width=1920"
    const val WP3 = "https://commons.wikimedia.org/wiki/Special:FilePath/Male%20lion,%20South%20Africa.jpg?width=1920"

    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    }

    fun str(key: String, def: String): String = sp.getString(key, def) ?: def
    fun int(key: String, def: Int): Int = sp.getInt(key, def)
    fun bool(key: String, def: Boolean): Boolean = sp.getBoolean(key, def)
    fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val e = sp.edit()
        e.block()
        e.apply()
    }

    val city get() = str("city", "Jakarta")
    val country get() = str("country", "Indonesia")
    val rss get() = str("rss", "https://www.antaranews.com/rss/terkini.xml")
    val league get() = str("league", "eng.1")
    val rtsp get() = str("rtsp", "")
    val slideSec get() = int("slideSec", 60)
    val sfx get() = bool("sfx", true)
    val brightness get() = int("brightness", 100)
    fun wallpapers(): List<String> =
        listOf(str("wp1", WP1), str("wp2", WP2), str("wp3", WP3)).map { it.trim() }.filter { it.isNotEmpty() }
}

object Net {
    private const val UA = "LauncherTV/1.0 (personal Android TV launcher)"

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        return c
    }

    fun text(url: String): String {
        val c = open(url)
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun download(url: String, dest: File) {
        val c = open(url)
        try {
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(dest)
        } finally {
            c.disconnect()
        }
    }
}

/** Decode gambar besar dengan hemat memori (TV ini cuma punya RAM 1GB). */
object Images {
    fun decode(file: File, maxW: Int, maxH: Int): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        opts.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeFile(file.path, opts)
    }
}

/** Suara navigasi dan pilih. File di folder "sounds" (nav.wav / select.wav) menimpa suara bawaan. */
object Sfx {
    private var pool: SoundPool? = null
    private var nav = 0
    private var select = 0

    fun init(ctx: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
        val dir = ctx.getExternalFilesDir("sounds")
        val navFile = File(dir, "nav.wav")
        val selFile = File(dir, "select.wav")
        nav = if (navFile.exists()) p.load(navFile.path, 1) else p.load(ctx, R.raw.nav, 1)
        select = if (selFile.exists()) p.load(selFile.path, 1) else p.load(ctx, R.raw.select, 1)
        pool = p
    }

    fun nav() {
        if (Prefs.sfx) pool?.play(nav, 0.6f, 0.6f, 1, 0, 1f)
    }

    fun select() {
        if (Prefs.sfx) pool?.play(select, 0.8f, 0.8f, 1, 0, 1f)
    }
}

/**
 * Pengatur kecerahan. TV tidak membuka kontrol backlight ke aplikasi biasa, jadi ini
 * meredupkan layar lewat lapisan hitam transparan di atas semua aplikasi (butuh izin overlay),
 * dan ikut mencoba setelan kecerahan sistem kalau diizinkan.
 */
object Dim {
    private var overlay: View? = null

    fun canOverlay(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx)

    /** @return true kalau lapisan sistem aktif (berlaku di semua aplikasi). */
    fun setLevel(ctx: Context, percent: Int): Boolean {
        val app = ctx.applicationContext
        val pct = percent.coerceIn(10, 100)
        val alpha = (100 - pct) / 100f * 0.9f
        try {
            if (Settings.System.canWrite(app)) {
                Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, pct * 255 / 100)
            }
        } catch (_: Exception) {
        }
        if (!canOverlay(app)) return false
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            var v = overlay
            if (alpha <= 0f) {
                if (v != null) {
                    wm.removeView(v)
                    overlay = null
                }
                return true
            }
            if (v == null) {
                v = View(app)
                v.setBackgroundColor(Color.BLACK)
                val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )
                wm.addView(v, lp)
                overlay = v
            }
            v.alpha = alpha
            return true
        } catch (_: Exception) {
            overlay = null
            return false
        }
    }
}

object Ui {
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun rounded(color: Int, radius: Float, strokeColor: Int = 0, strokeWidth: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = radius
        d.setColor(color)
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor)
        return d
    }

    /** Latar kartu/tombol: gelap transparan, jadi terang berbingkai saat difokus. */
    fun focusBg(ctx: Context, radiusDp: Int = 10): Drawable {
        val r = dp(ctx, radiusDp).toFloat()
        val s = StateListDrawable()
        s.addState(
            intArrayOf(android.R.attr.state_focused),
            rounded(Color.argb(200, 60, 45, 20), r, Color.rgb(255, 200, 87), dp(ctx, 2))
        )
        s.addState(intArrayOf(), rounded(Color.argb(140, 0, 0, 0), r))
        return s
    }

    /** Efek membesar saat fokus. */
    fun focusScale(v: View, scale: Float = 1.06f) {
        v.setOnFocusChangeListener { view, has ->
            view.animate().scaleX(if (has) scale else 1f).scaleY(if (has) scale else 1f).setDuration(120).start()
        }
    }
}
