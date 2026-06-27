package com.smartcockpit.util

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * "On This Day in History" data source backed by `assets/history.json`.
 *
 * JSON format:
 * ```json
 * {
 *   "MM-dd": "YYYY: Event description.",
 *   "06-25": "1998: Windows 98 released by Microsoft."
 * }
 * ```
 *
 * Key format: "MM-dd" (zero-padded month and day, year-agnostic).
 * Feb 29 is a valid key, gracefully handling leap years.
 *
 * [forToday] reads and parses the file exactly once per call (the call site
 * is wrapped in `remember {}` so file I/O happens only once per composition).
 */
object HistoryDictionary {

    private val format = SimpleDateFormat("MM-dd", Locale.ENGLISH)

    private const val FALLBACK = "A new peaceful day in the universe."

    /**
     * Opens `assets/history.json`, parses it with [JSONObject], and returns
     * the event string for today's "MM-dd" key.
     *
     * Defensive contract:
     * - File not found → returns [FALLBACK]
     * - Malformed JSON  → returns [FALLBACK]
     * - Key not present → returns [FALLBACK]
     *
     * Uses only `org.json.JSONObject` — no external dependencies required.
     */
    fun forToday(context: Context): String {
        return try {
            val today = format.format(Date())

            val json = context.assets
                .open("history.json")
                .bufferedReader()
                .use { it.readText() }

            val obj = JSONObject(json)

            if (obj.has(today)) obj.getString(today) else FALLBACK
        } catch (e: Exception) {
            e.printStackTrace()
            FALLBACK
        }
    }
}
