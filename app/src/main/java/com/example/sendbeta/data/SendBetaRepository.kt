package com.example.sendbeta.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single entry point for all app data. Screens (via ViewModels) talk to the repository,
 * never to Room directly, so the data source could later be swapped for a remote API.
 */
class SendBetaRepository private constructor(
    private val appContext: Context,
    private val db: SendBetaDatabase
) {
    private val gymDao = db.gymDao()
    private val routeDao = db.routeDao()
    private val attemptDao = db.attemptDao()

    // ---------- Gyms ----------
    suspend fun getGyms(): List<Gym> = gymDao.getAll()

    // ---------- Routes ----------
    val routes: Flow<List<Route>> = routeDao.observeAll()
    fun route(id: Long): Flow<Route?> = routeDao.observe(id)
    suspend fun getRoutes(): List<Route> = routeDao.getAll()
    suspend fun addRoute(route: Route): Long = routeDao.insert(route)
    suspend fun updateRoute(route: Route) = routeDao.update(route)

    suspend fun deleteRoute(route: Route) {
        // Remove saved video files first; the attempt rows are removed by ON DELETE CASCADE
        attemptDao.getForRoute(route.id).forEach { deleteVideoFile(it.videoPath) }
        routeDao.delete(route)
    }

    // ---------- Attempts ----------
    fun attemptsFor(routeId: Long): Flow<List<Attempt>> = attemptDao.observeForRoute(routeId)
    fun recentAttempts(limit: Int = 3): Flow<List<AttemptWithRoute>> = attemptDao.observeRecent(limit)
    suspend fun getAttempt(id: Long): AttemptWithRoute? = attemptDao.getWithRoute(id)

    /**
     * Saves an attempt. If a video Uri is given, the video is copied into the app's private
     * storage so it can still be played later (picker/camera Uris are only temporary).
     */
    suspend fun saveAttempt(
        routeId: Long, sent: Boolean, note: String, durationMs: Long, videoUri: Uri?, source: String
    ): Long {
        val path = videoUri?.let { copyVideo(it) }
        return attemptDao.insert(
            Attempt(routeId = routeId, sent = sent, note = note, durationMs = durationMs,
                videoPath = path, source = source)
        )
    }

    suspend fun deleteAttempt(attempt: Attempt) {
        deleteVideoFile(attempt.videoPath)
        attemptDao.delete(attempt)
    }

    // ---------- Stats for Home ----------
    val sendCount: Flow<Int> = attemptDao.observeSendCount()
    val activeProjectCount: Flow<Int> = routeDao.observeActiveProjectCount()
    val sentGrades: Flow<List<String>> = attemptDao.observeSentGrades()
    fun sendsSince(since: Long): Flow<Int> = attemptDao.observeSendsSince(since)

    // ---------- Files ----------
    private suspend fun copyVideo(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(appContext.filesDir, "videos").apply { mkdirs() }
            val file = File(dir, "attempt_${System.currentTimeMillis()}.mp4")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun deleteVideoFile(path: String?) = withContext(Dispatchers.IO) {
        path?.let { File(it).delete() }
    }

    companion object {
        @Volatile
        private var INSTANCE: SendBetaRepository? = null

        fun get(context: Context): SendBetaRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SendBetaRepository(
                    context.applicationContext,
                    SendBetaDatabase.get(context)
                ).also { INSTANCE = it }
            }
    }
}
