package com.rafsan.tvlauncher

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.TextureView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource

/** Pemutar RTSP sederhana di atas TextureView, otomatis mencoba lagi kalau putus. */
class CctvPlayer(
    private val ctx: Context,
    private val surface: TextureView,
    private val onStatus: (String) -> Unit
) {
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var url = ""
    private val retry = Runnable { if (url.isNotEmpty()) start(url) }

    fun start(rtspUrl: String) {
        stop()
        url = rtspUrl.trim()
        if (url.isEmpty()) {
            onStatus("Alamat RTSP belum diisi.\nBuka Pengaturan.")
            return
        }
        onStatus("Menyambung ke kamera…")
        try {
            val p = ExoPlayer.Builder(ctx.applicationContext).build()
            val source = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(10000L)
                .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            p.setMediaSource(source)
            p.volume = 0f
            p.setVideoTextureView(surface)
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) onStatus("")
                    if (state == Player.STATE_ENDED) scheduleRetry("Stream berhenti")
                }

                override fun onPlayerError(error: PlaybackException) {
                    scheduleRetry("Gagal: " + (error.cause?.message ?: error.errorCodeName))
                }
            })
            p.playWhenReady = true
            p.prepare()
            player = p
        } catch (e: Exception) {
            scheduleRetry("Gagal: " + e.message)
        }
    }

    private fun scheduleRetry(message: String) {
        onStatus("$message\nMencoba lagi…")
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, 10000)
    }

    fun stop() {
        handler.removeCallbacks(retry)
        url = ""
        player?.release()
        player = null
    }
}

/** CCTV layar penuh. Tekan Back untuk kembali. */
class CctvActivity : Activity() {
    private var cctv: CctvPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val surface = TextureView(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        val status = TextView(this)
        status.setTextColor(Color.WHITE)
        status.textSize = 18f
        status.gravity = Gravity.CENTER
        root.addView(status, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        cctv = CctvPlayer(this, surface) { status.text = it }
    }

    override fun onStart() {
        super.onStart()
        cctv?.start(Prefs.rtsp)
    }

    override fun onStop() {
        super.onStop()
        cctv?.stop()
    }
}
