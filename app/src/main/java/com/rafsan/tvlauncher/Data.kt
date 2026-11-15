package com.rafsan.tvlauncher

import android.util.Xml
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Pengambil data widget. Semua fungsi dipanggil dari thread latar dan mengembalikan teks siap tampil. */
object Data {
    private val idLocale = Locale("id", "ID")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---------- Cuaca (Open-Meteo, tanpa API key) ----------
    fun weather(city: String): String {
        val geo = JSONObject(Net.text("https://geocoding-api.open-meteo.com/v1/search?count=1&language=id&name=" + enc(city)))
        val results = geo.optJSONArray("results")
        if (results == null || results.length() == 0) return "Kota \"$city\" tidak ditemukan"
        val place = results.getJSONObject(0)
        val lat = place.getDouble("latitude")
        val lon = place.getDouble("longitude")
        val w = JSONObject(
            Net.text(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto"
            )
        )
        val cur = w.getJSONObject("current")
        val daily = w.getJSONObject("daily")
        val temp = Math.round(cur.getDouble("temperature_2m"))
        val max = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0))
        val min = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0))
        val rain = daily.optJSONArray("precipitation_probability_max")?.optInt(0, -1) ?: -1
        val sb = StringBuilder()
        sb.append(place.optString("name", city)).append('\n')
        sb.append(temp).append("°C  ").append(weatherText(cur.optInt("weather_code", -1))).append('\n')
        sb.append("Maks ").append(max).append("° / Min ").append(min).append("°\n")
        sb.append("Lembap ").append(cur.optInt("relative_humidity_2m")).append("%")
        if (rain >= 0) sb.append("  Hujan ").append(rain).append("%")
        return sb.toString()
    }

    private fun weatherText(code: Int): String = when (code) {
        0 -> "Cerah"
        1 -> "Cerah berawan"
        2 -> "Berawan sebagian"
        3 -> "Mendung"
        45, 48 -> "Berkabut"
        51, 53, 55, 56, 57 -> "Gerimis"
        61, 66 -> "Hujan ringan"
        63 -> "Hujan sedang"
        65, 67 -> "Hujan lebat"
        71, 73, 75, 77, 85, 86 -> "Salju"
        80, 81 -> "Hujan lokal"
        82 -> "Hujan deras"
        95, 96, 99 -> "Badai petir"
        else -> ""
    }

    // ---------- Jadwal sholat (Aladhan, metode 20 = Kemenag RI) ----------
    fun prayer(city: String, country: String): String {
        val j = JSONObject(
            Net.text("https://api.aladhan.com/v1/timingsByCity?method=20&city=" + enc(city) + "&country=" + enc(country))
        )
        val t = j.getJSONObject("data").getJSONObject("timings")
        val names = listOf("Subuh" to "Fajr", "Dzuhur" to "Dhuhr", "Ashar" to "Asr", "Maghrib" to "Maghrib", "Isya" to "Isha")
        val now = Calendar.getInstance()
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        var nextMarked = false
        val sb = StringBuilder()
        for ((label, key) in names) {
            val time = t.optString(key, "--:--").take(5)
            val parts = time.split(":")
            val mins = if (parts.size == 2) (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0) else -1
            val isNext = !nextMarked && mins >= nowMin
            if (isNext) nextMarked = true
            sb.append(if (isNext) "▶ " else "   ").append(label.padEnd(8)).append(time).append('\n')
        }
        return sb.toString().trimEnd()
    }

    // ---------- Jadwal bola (scoreboard publik ESPN) ----------
    fun football(league: String): String {
        val day = SimpleDateFormat("yyyyMMdd", Locale.US)
        val cal = Calendar.getInstance()
        val from = day.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 7)
        val to = day.format(cal.time)
        val j = JSONObject(
            Net.text("https://site.api.espn.com/apis/site/v2/sports/soccer/" + enc(league) + "/scoreboard?dates=$from-$to")
        )
        val leagueName = j.optJSONArray("leagues")?.optJSONObject(0)?.optString("abbreviation", "") ?: ""
        val events = j.optJSONArray("events")
        if (events == null || events.length() == 0) return "Tidak ada laga 7 hari ke depan\n($league)"
        val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US)
        inFmt.timeZone = TimeZone.getTimeZone("UTC")
        val outFmt = SimpleDateFormat("EEE HH:mm", idLocale)
        val sb = StringBuilder()
        if (leagueName.isNotEmpty()) sb.append(leagueName).append('\n')
        var shown = 0
        for (i in 0 until events.length()) {
            if (shown >= 5) break
            val e = events.optJSONObject(i) ?: continue
            val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val teams = comp.optJSONArray("competitors") ?: continue
            var home = "?"
            var away = "?"
            var homeScore = ""
            var awayScore = ""
            for (k in 0 until teams.length()) {
                val c = teams.optJSONObject(k) ?: continue
                val team = c.optJSONObject("team")
                var name = team?.optString("abbreviation", "") ?: ""
                if (name.isEmpty()) name = team?.optString("shortDisplayName", "?") ?: "?"
                if (c.optString("homeAway") == "home") {
                    home = name
                    homeScore = c.optString("score", "")
                } else {
                    away = name
                    awayScore = c.optString("score", "")
                }
            }
            val state = e.optJSONObject("status")?.optJSONObject("type")?.optString("state", "pre") ?: "pre"
            val line = if (state == "pre") {
                val whenText = try {
                    val d: Date? = inFmt.parse(e.optString("date"))
                    if (d != null) outFmt.format(d) else ""
                } catch (_: Exception) {
                    ""
                }
                "$whenText  $home v $away"
            } else {
                val tag = if (state == "in") "LIVE" else "FT"
                "$tag  $home $homeScore-$awayScore $away"
            }
            sb.append(line.trim()).append('\n')
            shown++
        }
        return sb.toString().trimEnd()
    }

    // ---------- Berita (RSS) ----------
    fun news(rssUrl: String): String {
        val xml = Net.text(rssUrl)
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))
        val titles = ArrayList<String>()
        var inItem = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && titles.size < 5) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name
                if (name == "item" || name == "entry") {
                    inItem = true
                } else if (inItem && name == "title") {
                    val title = parser.nextText().trim()
                    if (title.isNotEmpty()) titles.add("• $title")
                }
            } else if (event == XmlPullParser.END_TAG) {
                if (parser.name == "item" || parser.name == "entry") inItem = false
            }
            event = parser.next()
        }
        return if (titles.isEmpty()) "Tidak ada berita" else titles.joinToString("\n")
    }
}
