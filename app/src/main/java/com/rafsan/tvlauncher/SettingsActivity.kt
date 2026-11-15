package com.rafsan.tvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Layar pengaturan launcher. Semua nilai disimpan di TV, tidak ada yang masuk ke repo. */
class SettingsActivity : Activity() {
    private val fields = LinkedHashMap<String, EditText>()
    private lateinit var sfxBox: CheckBox
    private lateinit var form: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Sfx.init(this)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.rgb(18, 16, 12))
        form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(this, 40)
        form.setPadding(pad * 3, pad / 2, pad * 3, pad)
        scroll.addView(form)

        heading("Pengaturan Launcher")

        section("Cuaca & jadwal sholat")
        field("city", "Kota", Prefs.city, "contoh: Jakarta, Bandung, Surabaya")
        field("country", "Negara", Prefs.country, "")

        section("Berita")
        field("rss", "Alamat RSS", Prefs.rss, "alamat feed RSS situs berita")

        section("Jadwal bola")
        field("league", "Kode liga", Prefs.league, "eng.1 Inggris · esp.1 Spanyol · ita.1 Italia · idn.1 Indonesia · uefa.champions")

        section("CCTV")
        field("rtsp", "Alamat RTSP", Prefs.rtsp, "rtsp://user:password@192.168.1.10:554/... (pakai sub-stream biar ringan)")

        section("Wallpaper slideshow")
        field("wp1", "Gambar 1 (URL)", Prefs.str("wp1", Prefs.WP1), "")
        field("wp2", "Gambar 2 (URL)", Prefs.str("wp2", Prefs.WP2), "")
        field("wp3", "Gambar 3 (URL)", Prefs.str("wp3", Prefs.WP3), "")
        val slide = field("slideSec", "Ganti gambar tiap (detik)", Prefs.slideSec.toString(), "minimal 10")
        slide.inputType = InputType.TYPE_CLASS_NUMBER
        note("Foto sendiri: taruh file JPG/PNG di folder\n" + File(getExternalFilesDir("wallpapers"), "").path + "\nKalau folder itu berisi gambar, URL di atas diabaikan.")

        section("Suara")
        sfxBox = CheckBox(this)
        sfxBox.text = "Suara navigasi dan klik"
        sfxBox.setTextColor(Color.WHITE)
        sfxBox.textSize = 15f
        sfxBox.isChecked = Prefs.sfx
        form.addView(sfxBox)
        note("Suara sendiri: taruh nav.wav dan select.wav di folder\n" + File(getExternalFilesDir("sounds"), "").path)

        section("Kecerahan")
        note(
            if (Dim.canOverlay(this)) "Izin overlay aktif: peredup berlaku di semua aplikasi."
            else "Izin overlay belum aktif: peredup baru berlaku di launcher. Aktifkan lewat tombol di bawah, atau lewat adb:\nadb shell appops set $packageName SYSTEM_ALERT_WINDOW allow"
        )
        action("Buka izin overlay") {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                Toast.makeText(this, "Menu izin tidak tersedia di TV ini, pakai perintah adb.", Toast.LENGTH_LONG).show()
            }
        }

        section("")
        action("Simpan") { save() }

        setContentView(scroll)
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { old, new ->
            if (old != null && new != null) Sfx.nav()
        }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun heading(text: String) {
        form.addView(label(text, 24f, Color.WHITE, true))
    }

    private fun section(text: String) {
        val t = label(text.uppercase(), 12f, Color.rgb(255, 200, 87), true)
        t.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 4))
        form.addView(t)
    }

    private fun note(text: String) {
        val t = label(text, 12f, Color.rgb(190, 190, 190), false)
        t.setPadding(0, Ui.dp(this, 4), 0, 0)
        form.addView(t)
    }

    private fun field(key: String, title: String, value: String, hint: String): EditText {
        form.addView(label(title, 13f, Color.WHITE, false))
        val e = EditText(this)
        e.setText(value)
        e.hint = hint
        e.setSingleLine(true)
        e.textSize = 15f
        e.setTextColor(Color.WHITE)
        e.setHintTextColor(Color.GRAY)
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        form.addView(e, LinearLayout.LayoutParams(-1, -2))
        fields[key] = e
        return e
    }

    private fun action(text: String, onClick: () -> Unit) {
        val b = label(text, 15f, Color.WHITE, true)
        b.isFocusable = true
        b.isSoundEffectsEnabled = false
        b.background = Ui.focusBg(this, 20)
        val p = Ui.dp(this, 12)
        b.setPadding(p * 2, p, p * 2, p)
        b.setOnClickListener {
            Sfx.select()
            onClick()
        }
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.topMargin = Ui.dp(this, 8)
        form.addView(b, lp)
    }

    private fun save() {
        val wallpaperChanged = listOf("wp1", "wp2", "wp3").any { fields[it]?.text.toString().trim() != Prefs.str(it, "") }
        Prefs.edit {
            for ((key, edit) in fields) {
                val v = edit.text.toString().trim()
                if (key == "slideSec") putInt(key, (v.toIntOrNull() ?: 60).coerceAtLeast(10))
                else putString(key, v)
            }
            putBoolean("sfx", sfxBox.isChecked)
        }
        if (wallpaperChanged) File(cacheDir, "wallpapers").deleteRecursively()
        Toast.makeText(this, "Tersimpan", Toast.LENGTH_SHORT).show()
        finish()
    }
}
