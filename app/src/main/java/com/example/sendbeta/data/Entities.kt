package com.example.sendbeta.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar

/**
 * A climbing gym in Toronto (reference data from torontoclimbs.ca, seeded on first launch).
 * Hours use a 24h clock as decimals (7.5 = 7:30 AM, 23.0 = 11 PM).
 * [dayOverrides] holds days with different hours, e.g. "3:7.0-23.0;5:7.0-23.0" (Calendar day numbers).
 */
@Entity(tableName = "gyms")
data class Gym(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val chipLabel: String,
    val area: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val types: String,
    val dayPass: String,
    val weekdayOpen: Double,
    val weekdayClose: Double,
    val weekendOpen: Double,
    val weekendClose: Double,
    val dayOverrides: String = ""
) {
    /** Label used on routes, e.g. "Joe Rockhead's · Liberty Village". */
    fun label(): String = "$name · $area"

    /** Opening hours (open, close) for a Calendar day-of-week. */
    fun hoursFor(day: Int): Pair<Double, Double> {
        dayOverrides.split(";").filter { it.isNotBlank() }.forEach { entry ->
            val (d, range) = entry.split(":")
            if (d.toInt() == day) {
                val (open, close) = range.split("-").map { it.toDouble() }
                return open to close
            }
        }
        return if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) weekendOpen to weekendClose
        else weekdayOpen to weekdayClose
    }
}

/** A boulder problem / route the user is projecting. */
@Entity(tableName = "routes")
data class Route(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val gymLabel: String,
    val grade: String,          // "V0" … "V10"
    val tags: String,           // comma separated, e.g. "Overhang,Dynamic"
    val color: String,          // "coral" | "lime" | "blue" | "violet"
    val rating: Int,            // 1–5 stars
    val notes: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun tagList(): List<String> = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    fun gradeNumber(): Int = grade.removePrefix("V").toIntOrNull() ?: 0

    companion object {
        /** Colour bucket for a grade so the list is easy to scan. */
        fun colorForGrade(grade: String): String = when (grade.removePrefix("V").toIntOrNull() ?: 0) {
            in 0..3 -> "violet"
            4 -> "lime"
            5 -> "coral"
            else -> "blue"
        }
    }
}

/** One recorded/imported attempt on a route. Deleting a route deletes its attempts (CASCADE). */
@Entity(
    tableName = "attempts",
    foreignKeys = [ForeignKey(
        entity = Route::class,
        parentColumns = ["id"],
        childColumns = ["routeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("routeId")]
)
data class Attempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val sent: Boolean,
    val note: String,
    val durationMs: Long,
    val videoPath: String?,     // copy of the video inside the app's private storage
    val source: String,         // "Imported video" | "Recorded attempt" | "Logged"
    val createdAt: Long = System.currentTimeMillis(),
    // Hold annotations placed on the video, stored as "x,y" fractions of the frame
    val startHold: String? = null,
    val cruxHold: String? = null,
    val topHold: String? = null
)

/** Attempt joined with its route, for the Home "Recent beta" list and the analyzer. */
data class AttemptWithRoute(
    val id: Long,
    val routeId: Long,
    val sent: Boolean,
    val note: String,
    val durationMs: Long,
    val videoPath: String?,
    val source: String,
    val createdAt: Long,
    val routeName: String,
    val gymLabel: String,
    val grade: String,
    val color: String,
    val startHold: String?,
    val cruxHold: String?,
    val topHold: String?
)
