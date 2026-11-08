package com.example.sendbeta.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Starter data inserted the first time the database is created:
 *  - all 12 open climbing gyms in the City of Toronto (source: torontoclimbs.ca)
 *  - 4 sample routes and a few attempts so the app isn't empty on first launch
 */
object SeedData {

    private const val DAY = 24L * 60 * 60 * 1000

    // Calendar day numbers: SUNDAY=1, MONDAY=2, TUESDAY=3, WEDNESDAY=4, THURSDAY=5, FRIDAY=6, SATURDAY=7
    private val gyms = listOf(
        Gym(name = "The Rock Oasis", chipLabel = "Rock Oasis", area = "Leslieville",
            address = "204-388 Carlaw Ave, Toronto, ON", lat = 43.6655, lon = -79.3407,
            types = "Bouldering · Top rope · Lead · Auto-belay · Speed", dayPass = "$28",
            weekdayOpen = 9.0, weekdayClose = 23.0, weekendOpen = 9.0, weekendClose = 22.0),
        Gym(name = "Basecamp Bloor West", chipLabel = "Basecamp Bloor", area = "Bloor West",
            address = "677 Bloor St W, Toronto, ON", lat = 43.6644, lon = -79.4176,
            types = "Bouldering · Top rope · Lead · Auto-belay", dayPass = "$28",
            weekdayOpen = 10.0, weekdayClose = 23.0, weekendOpen = 10.0, weekendClose = 22.0,
            dayOverrides = "3:7.0-23.0;5:7.0-23.0"),
        Gym(name = "Basecamp Queen West", chipLabel = "Basecamp Queen W", area = "Queen West",
            address = "186 Spadina Ave, Toronto, ON", lat = 43.6497, lon = -79.3967,
            types = "Bouldering", dayPass = "$28",
            weekdayOpen = 10.0, weekdayClose = 23.0, weekendOpen = 10.0, weekendClose = 22.0),
        Gym(name = "Joe Rockhead's", chipLabel = "Joe Rockhead's", area = "Liberty Village",
            address = "29 Fraser Ave, Toronto, ON", lat = 43.6380, lon = -79.4256,
            types = "Bouldering · Top rope · Lead · Auto-belay", dayPass = "$28",
            weekdayOpen = 7.0, weekdayClose = 23.0, weekendOpen = 9.0, weekendClose = 22.0),
        Gym(name = "Boulderz Junction", chipLabel = "Boulderz Junction", area = "The Junction",
            address = "1444 Dupont St Unit 16, Toronto, ON", lat = 43.6668, lon = -79.4470,
            types = "Bouldering · Auto-belay", dayPass = "$28",
            weekdayOpen = 9.0, weekdayClose = 23.0, weekendOpen = 9.0, weekendClose = 21.0),
        Gym(name = "Boulderz Etobicoke", chipLabel = "Boulderz Etobicoke", area = "Etobicoke",
            address = "80 The East Mall Unit 9, Etobicoke, ON", lat = 43.6222, lon = -79.5603,
            types = "Bouldering · Top rope · Lead · Auto-belay", dayPass = "$32",
            weekdayOpen = 9.0, weekdayClose = 23.0, weekendOpen = 9.0, weekendClose = 21.0),
        Gym(name = "Rock On Climbing", chipLabel = "Rock On", area = "Canary District",
            address = "474 Front St E, Toronto, ON", lat = 43.6526, lon = -79.3555,
            types = "Bouldering", dayPass = "$24",
            weekdayOpen = 16.0, weekdayClose = 22.0, weekendOpen = 10.0, weekendClose = 20.0),
        Gym(name = "Ethos Climbing", chipLabel = "Ethos", area = "Waterfront",
            address = "128 Queens Quay E, Toronto, ON", lat = 43.6449, lon = -79.3678,
            types = "Bouldering", dayPass = "$30",
            weekdayOpen = 7.5, weekdayClose = 23.0, weekendOpen = 7.5, weekendClose = 22.0,
            dayOverrides = "2:16.0-23.0;3:16.0-23.0"),
        Gym(name = "Toronto Climbing Academy", chipLabel = "Climbing Academy", area = "East York",
            address = "11 Curity Ave Unit 3, Toronto, ON", lat = 43.7069, lon = -79.3090,
            types = "Bouldering · Top rope · Lead · Auto-belay", dayPass = "$31",
            weekdayOpen = 10.0, weekdayClose = 23.0, weekendOpen = 10.0, weekendClose = 20.0),
        Gym(name = "Hogtown Boulders", chipLabel = "Hogtown", area = "Don Mills",
            address = "238 Lesmill Rd Unit A, Toronto, ON", lat = 43.7593, lon = -79.3530,
            types = "Bouldering", dayPass = "$30",
            weekdayOpen = 10.0, weekdayClose = 23.0, weekendOpen = 10.0, weekendClose = 23.0),
        Gym(name = "True North Climbing", chipLabel = "True North", area = "North York",
            address = "75 Carl Hall Rd Unit 14, North York, ON", lat = 43.7488, lon = -79.4703,
            types = "Bouldering · Top rope · Lead · Auto-belay", dayPass = "$30",
            weekdayOpen = 10.0, weekdayClose = 22.0, weekendOpen = 10.0, weekendClose = 22.0),
        Gym(name = "Boulder Parc", chipLabel = "Boulder Parc", area = "Scarborough",
            address = "1415 Morningside Ave Unit 2, Scarborough, ON", lat = 43.8033, lon = -79.2101,
            types = "Bouldering", dayPass = "$29",
            weekdayOpen = 12.0, weekdayClose = 23.0, weekendOpen = 10.0, weekendClose = 20.0)
    )

    fun populate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            gyms.forEach { g ->
                db.insert("gyms", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
                    put("name", g.name); put("chipLabel", g.chipLabel); put("area", g.area)
                    put("address", g.address); put("lat", g.lat); put("lon", g.lon)
                    put("types", g.types); put("dayPass", g.dayPass)
                    put("weekdayOpen", g.weekdayOpen); put("weekdayClose", g.weekdayClose)
                    put("weekendOpen", g.weekendOpen); put("weekendClose", g.weekendClose)
                    put("dayOverrides", g.dayOverrides)
                })
            }

            val pink = insertRoute(db, "The Pink Arête", "The Rock Oasis · Leslieville", "V5",
                "Arête,Balance", 4,
                "Keep the left hip glued in through the arête. Trust the right toe, then drive through the heel before reaching.",
                now - 20 * DAY)
            val dyno = insertRoute(db, "The Dyno Problem", "Basecamp Queen West · Queen West", "V4",
                "Dynamic,Overhang", 4, "Load the legs, look at the target hold, commit fully.", now - 15 * DAY)
            val sloper = insertRoute(db, "Sloper Traverse", "Joe Rockhead's · Liberty Village", "V6",
                "Slopers,Overhang", 3, "Hips close to the wall, keep tension through the toes.", now - 10 * DAY)
            val quiet = insertRoute(db, "Quiet Feet", "Boulderz Etobicoke · Etobicoke", "V3",
                "Slab,Crimps", 5, "Silent feet drill: place each foot once.", now - 6 * DAY)

            insertAttempt(db, pink, false, "Fell at the crux", 18_000, now - 12 * DAY)
            insertAttempt(db, pink, true, "Sent it!", 22_000, now - 2 * DAY)
            insertAttempt(db, dyno, false, "Short on the dyno", 9_000, now - 4 * DAY)
            insertAttempt(db, sloper, false, "Slipped off the second sloper", 25_000, now - 3 * DAY)
            insertAttempt(db, quiet, true, "Clean send", 31_000, now - 1 * DAY)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertRoute(
        db: SupportSQLiteDatabase, name: String, gymLabel: String, grade: String,
        tags: String, rating: Int, notes: String, createdAt: Long
    ): Long = db.insert("routes", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
        put("name", name); put("gymLabel", gymLabel); put("grade", grade); put("tags", tags)
        put("color", Route.colorForGrade(grade)); put("rating", rating); put("notes", notes)
        put("createdAt", createdAt)
    })

    private fun insertAttempt(
        db: SupportSQLiteDatabase, routeId: Long, sent: Boolean, note: String, durationMs: Long, createdAt: Long
    ) {
        db.insert("attempts", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
            put("routeId", routeId); put("sent", if (sent) 1 else 0); put("note", note)
            put("durationMs", durationMs); putNull("videoPath"); put("source", "Logged")
            put("createdAt", createdAt)
        })
    }
}
