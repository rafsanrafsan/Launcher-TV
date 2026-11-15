package com.rafsan.tvlauncher

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

/** Pembaca status sistem: CPU, RAM, dan penyimpanan. */
object SysStat {
    private var prevIdle = 0L
    private var prevTotal = 0L

    /** true kalau angka CPU terakhir berasal dari /proc/stat (pemakaian nyata), false kalau perkiraan dari frekuensi. */
    var cpuExact = false
        private set

    private fun readLong(path: String): Long? = try {
        File(path).readText().trim().toLong()
    } catch (_: Exception) {
        null
    }

    /** Pemakaian CPU 0-100, atau -1 kalau tidak bisa dibaca. */
    fun cpuPercent(): Int {
        try {
            val line = File("/proc/stat").bufferedReader().use { it.readLine() }
            if (line != null && line.startsWith("cpu ")) {
                val parts = line.trim().split(Regex("\\s+")).drop(1).map { it.toLong() }
                val idle = parts[3] + (if (parts.size > 4) parts[4] else 0L)
                val total = parts.sum()
                val dIdle = idle - prevIdle
                val dTotal = total - prevTotal
                val first = prevTotal == 0L
                prevIdle = idle
                prevTotal = total
                if (!first && dTotal > 0) {
                    cpuExact = true
                    return (100 * (dTotal - dIdle) / dTotal).toInt().coerceIn(0, 100)
                }
            }
        } catch (_: Exception) {
        }
        // Android 8+ biasanya menutup /proc/stat untuk aplikasi, jadi perkirakan dari frekuensi tiap core.
        cpuExact = false
        var sum = 0.0
        var count = 0
        for (i in 0 until 8) {
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            val cur = readLong(base + "scaling_cur_freq") ?: continue
            val max = readLong(base + "cpuinfo_max_freq") ?: continue
            val min = readLong(base + "cpuinfo_min_freq") ?: 0L
            if (max <= min) continue
            sum += (cur - min).toDouble() / (max - min)
            count++
        }
        return if (count == 0) -1 else (sum / count * 100).toInt().coerceIn(0, 100)
    }

    /** Suhu CPU dalam °C, atau -1 kalau tidak tersedia. */
    fun cpuTemp(): Int {
        val raw = readLong("/sys/class/thermal/thermal_zone0/temp") ?: return -1
        val c = if (raw > 1000) raw / 1000 else raw
        return if (c in 1..150) c.toInt() else -1
    }

    /** Pasangan (terpakai, total) RAM dalam MB. */
    fun ram(ctx: Context): Pair<Long, Long> {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val mb = 1024L * 1024L
        return Pair((mi.totalMem - mi.availMem) / mb, mi.totalMem / mb)
    }

    /** Pasangan (terpakai, total) penyimpanan internal dalam MB. */
    fun storage(): Pair<Long, Long> {
        val fs = StatFs(Environment.getDataDirectory().path)
        val mb = 1024L * 1024L
        return Pair((fs.totalBytes - fs.availableBytes) / mb, fs.totalBytes / mb)
    }
}
