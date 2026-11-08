package com.example.sendbeta.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.sendbeta.data.Attempt
import com.example.sendbeta.data.AttemptWithRoute
import com.example.sendbeta.data.Route
import com.example.sendbeta.data.SendBetaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar

/** Routes screen: list of all routes + create / update / delete. */
class RoutesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SendBetaRepository.get(app)

    val routes: LiveData<List<Route>> = repo.routes.asLiveData()

    fun addRoute(route: Route) {
        viewModelScope.launch { repo.addRoute(route) }
    }
    fun updateRoute(route: Route) {
        viewModelScope.launch { repo.updateRoute(route) }
    }
    fun deleteRoute(route: Route) {
        viewModelScope.launch { repo.deleteRoute(route) }
    }
}

/** Route detail screen: one route + its attempt history. */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SendBetaRepository.get(app)
    private val routeId = MutableStateFlow(0L)

    fun load(id: Long) { routeId.value = id }

    val route: LiveData<Route?> = routeId.flatMapLatest { repo.route(it) }.asLiveData()
    val attempts: LiveData<List<Attempt>> = routeId.flatMapLatest { repo.attemptsFor(it) }.asLiveData()

    fun updateRoute(route: Route) {
        viewModelScope.launch { repo.updateRoute(route) }
    }
    fun deleteRoute(route: Route) {
        viewModelScope.launch { repo.deleteRoute(route) }
    }
    /** Quick log: an attempt without a video. */
    fun logAttempt(routeId: Long, sent: Boolean, note: String) {
        viewModelScope.launch {
            repo.saveAttempt(routeId = routeId, sent = sent, note = note, durationMs = 0L, videoUri = null, source = "Logged")
        }
    }
    fun updateAttempt(attempt: Attempt) {
        viewModelScope.launch { repo.updateAttempt(attempt) }
    }
    fun deleteAttempt(attempt: Attempt) {
        viewModelScope.launch { repo.deleteAttempt(attempt) }
    }
}

/** Home screen: stats + recent attempts, all calculated from the database. */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SendBetaRepository.get(app)

    val recentAttempts: LiveData<List<AttemptWithRoute>> = repo.recentAttempts(3).asLiveData()
    val totalSends: LiveData<Int> = repo.sendCount.asLiveData()
    val sendsThisWeek: LiveData<Int> = repo.sendsSince(startOfWeek()).asLiveData()
    val activeProjects: LiveData<Int> = repo.activeProjectCount.asLiveData()
    val topGrade: LiveData<String> = repo.sentGrades.map { grades ->
        grades.maxByOrNull { it.removePrefix("V").toIntOrNull() ?: 0 } ?: "—"
    }.asLiveData()

    private fun startOfWeek(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
