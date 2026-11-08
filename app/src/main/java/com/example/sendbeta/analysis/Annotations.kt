package com.example.sendbeta.analysis

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/** Kinds of marker a climber can place on the video. "single" types can only be placed once. */
enum class AnnotationType(val label: String, val color: Int, val single: Boolean) {
    START("Start", Color.parseColor("#1A7F55"), true),
    CRUX("Crux", Color.parseColor("#FF7F66"), true),
    TOP("Finish", Color.parseColor("#EDAF27"), true),
    FOOT("Foot", Color.parseColor("#3D8BFF"), false),
    REST("Rest", Color.parseColor("#8A6CFF"), false),
    HEEL("Heel hook", Color.parseColor("#C2185B"), false),
    DYNO("Dyno", Color.parseColor("#7CB342"), false),
    NOTE("Note", Color.parseColor("#18201D"), false)
}

/**
 * One marker on the video: where (x, y as fractions of the frame), when (timeMs),
 * and an optional text for notes.
 */
data class Annotation(
    val id: Long,
    val type: AnnotationType,
    val point: NormPoint,
    val timeMs: Long,
    val text: String? = null
) {
    /** Text drawn on the pin. */
    fun pinLabel(): String = when {
        type == AnnotationType.NOTE && !text.isNullOrBlank() -> text.take(20)
        else -> type.label.uppercase()
    }

    /**
     * How visible the marker is at [nowMs] (0 = hidden, 1 = fully visible).
     * It fades in just before its moment and fades out ~2 s after, so the video doesn't get cluttered.
     * The start hold is visible from the beginning of the clip until shortly after it's used.
     */
    fun alphaAt(nowMs: Long): Float {
        val showFrom = if (type == AnnotationType.START) 0L else timeMs - FADE_IN_MS
        val fullFrom = if (type == AnnotationType.START) 0L else timeMs
        val fullUntil = timeMs + HOLD_MS
        val hideAt = fullUntil + FADE_OUT_MS
        return when {
            nowMs < showFrom || nowMs > hideAt -> 0f
            nowMs < fullFrom -> (nowMs - showFrom).toFloat() / FADE_IN_MS
            nowMs <= fullUntil -> 1f
            else -> 1f - (nowMs - fullUntil).toFloat() / FADE_OUT_MS
        }
    }

    companion object {
        private const val FADE_IN_MS = 400L
        private const val HOLD_MS = 2000L
        private const val FADE_OUT_MS = 500L

        /** Stored in the database as JSON text. */
        fun toJson(list: List<Annotation>): String? {
            if (list.isEmpty()) return null
            val array = JSONArray()
            list.forEach { a ->
                array.put(JSONObject().apply {
                    put("id", a.id); put("type", a.type.name)
                    put("x", a.point.x.toDouble()); put("y", a.point.y.toDouble())
                    put("t", a.timeMs); a.text?.let { put("text", it) }
                })
            }
            return array.toString()
        }

        fun fromJson(json: String?): List<Annotation> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { i ->
                    val o = array.getJSONObject(i)
                    val type = runCatching { AnnotationType.valueOf(o.getString("type")) }.getOrNull() ?: return@mapNotNull null
                    Annotation(
                        id = o.optLong("id", i.toLong()),
                        type = type,
                        point = NormPoint(o.getDouble("x").toFloat(), o.getDouble("y").toFloat()),
                        timeMs = o.optLong("t", 0L),
                        text = o.optString("text").takeIf { o.has("text") }
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
