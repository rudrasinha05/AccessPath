package com.accesspath.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.accesspath.AccessPathApplication
import com.accesspath.R
import com.accesspath.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface UiMessage {
    data class Text(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiMessage
    data class Failure(val code: ErrorCode?, val detail: String? = null) : UiMessage
}

data class ScreenState(
    val snapshot: AppSnapshot? = null,
    val route: RouteResult? = null,
    val routing: Boolean = false,
    val busy: Boolean = false,
    val reviewer: Boolean = false,
    val message: UiMessage? = null,
    val loadError: Boolean = false
)

class AccessViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = (application as AccessPathApplication).repository
    private val mutex = Mutex()
    private val ui_ = MutableStateFlow(ScreenState(reviewer = saved[KEY_REVIEWER] ?: false))
    val ui: StateFlow<ScreenState> = ui_.asStateFlow()

    private val ready = viewModelScope.async(Dispatchers.IO) {
        runCatching { repository.initialize() }.onFailure {
            Log.e(TAG, "Initialisation failed", it)
            ui_.update { s -> s.copy(loadError = true) }
        }
    }

    init {
        viewModelScope.launch {
            ready.await()
            repository.snapshots.filterNotNull()
                .catch { e ->
                    Log.e(TAG, "Saved data unreadable", e)
                    ui_.update { it.copy(loadError = true) }
                }
                .collectLatest { s ->
                    ui_.update { it.copy(snapshot = s, routing = true) }
                    val route = withContext(Dispatchers.Default) { Routing.calculate(s) }
                    ensureActive()
                    ui_.update { if (it.snapshot?.revision == s.revision) it.copy(route = route, routing = false) else it }
                }
        }
    }

    /** Runs changes one at a time, so a quick second tap can never interleave with the first. */
    private fun perform(success: UiMessage? = null, work: suspend () -> UiMessage?) {
        viewModelScope.launch {
            ready.await()
            mutex.withLock {
                ui_.update { it.copy(busy = true) }
                try {
                    val result = work() ?: success
                    if (result != null) message(result)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AppError) {
                    message(UiMessage.Failure(e.code, e.detail))
                } catch (e: Exception) {
                    Log.e(TAG, "Action failed", e)
                    message(UiMessage.Failure(null))
                } finally {
                    ui_.update { it.copy(busy = false) }
                }
            }
        }
    }

    fun message(m: UiMessage?) = ui_.update { it.copy(message = m) }

    private fun nodeName(id: String) = ui_.value.snapshot?.node(id)?.name ?: id

    fun setReviewer(on: Boolean) {
        saved[KEY_REVIEWER] = on
        ui_.update { it.copy(reviewer = on) }
    }

    fun setCurrent(nodeId: String) = perform(UiMessage.Text(R.string.position_set, listOf(nodeName(nodeId)))) { repository.setCurrent(nodeId); null }
    fun setDestination(nodeId: String) = perform(UiMessage.Text(R.string.destination_set, listOf(nodeName(nodeId)))) { repository.setDestination(nodeId); null }
    fun swapEnds() = perform { repository.swapEnds(); null }
    fun selectFloor(floorId: String) = perform { repository.selectFloor(floorId); null }
    fun setProfile(profile: Profile) = perform { repository.setProfile(profile); null }

    fun goToNearest(kind: NearbyKind) = perform {
        val node = withContext(Dispatchers.Default) { Routing.nearest(repository.snapshot(), kind) }
        if (node == null) UiMessage.Text(R.string.nearest_none)
        else { repository.setDestination(node.id); UiMessage.Text(R.string.nearest_set, listOf(node.name)) }
    }

    /** Handles accesspath://node/{id} links, e.g. from a printed QR code at a landmark. */
    fun openLink(uri: Uri) {
        val id = parseNodeLink(uri) ?: return
        perform {
            if (repository.snapshot().node(id) == null) UiMessage.Text(R.string.error_link)
            else { repository.setCurrent(id); UiMessage.Text(R.string.position_set, listOf(nodeName(id))) }
        }
    }

    fun submitReport(id: String, edgeId: String, category: BarrierCategory, description: String) =
        perform(UiMessage.Text(R.string.report_submitted)) { repository.submitReport(id, edgeId, category, description); null }

    fun review(reportId: String, status: ReportStatus, reason: String, revision: Long) =
        perform(UiMessage.Text(R.string.decision_saved)) { repository.review(reportId, status, reason, revision); null }

    fun setFacility(id: String, status: Availability, reason: String, revision: Long) =
        perform(UiMessage.Text(R.string.facility_saved)) { repository.setFacility(id, status, reason, revision); null }

    fun setPathClosed(edgeId: String, closed: Boolean, reason: String) =
        perform(UiMessage.Text(if (closed) R.string.path_closed else R.string.path_reopened)) { repository.setPathClosed(edgeId, closed, reason); null }

    fun simulateAllLifts(available: Boolean) = perform { repository.simulateAllLifts(available); null }
    fun resetDemo() = perform(UiMessage.Text(R.string.reset_done)) { repository.resetDemo(); null }

    fun importMap(uri: Uri) = perform {
        val text = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MapValidator.MAX_BYTES) throw AppError(ErrorCode.MAP_TOO_LARGE)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } ?: return@perform UiMessage.Text(R.string.error_file)
        val warnings = repository.importMap(text)
        if (warnings.isEmpty()) UiMessage.Text(R.string.import_ok) else UiMessage.Text(R.string.import_ok_warn, listOf(warnings.joinToString(" ")))
    }

    fun exportMap(uri: Uri) = perform {
        val json = repository.exportMapJson()
        val ok = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null
        }
        UiMessage.Text(if (ok) R.string.export_ok else R.string.error_file)
    }

    companion object {
        private const val TAG = "AccessViewModel"
        private const val KEY_REVIEWER = "reviewer"

        fun parseNodeLink(uri: Uri): String? =
            if (uri.scheme == "accesspath" && uri.host == "node") uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() } else null
    }
}
