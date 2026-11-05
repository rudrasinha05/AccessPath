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
    val loadError: Boolean = false,
    /** True when a campus server is configured: roles come from accounts, data is shared. */
    val server: Boolean = false,
    val account: Account? = null,
    val syncing: Boolean = false,
    val lastSync: Long? = null
)

data class Account(val email: String?, val role: Role)

class AccessViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = (application as AccessPathApplication).repository
    private val sync = com.accesspath.data.remote.RemoteSync((application as AccessPathApplication).supabase, repository)
    private val mutex = Mutex()
    private val ui_ = MutableStateFlow(
        if (sync.configured) ScreenState(server = true, account = sync.signedInEmail?.let { Account(it, Role.STUDENT) })
        else ScreenState(reviewer = saved[KEY_REVIEWER] ?: false)
    )
    val ui: StateFlow<ScreenState> = ui_.asStateFlow()
    private val pending = (application as AccessPathApplication).pendingSurveys
    /** Areas this phone mapped, with their review status (for the Contribute tab). */
    private val mySurveys_ = MutableStateFlow(pending.all().filter { it.mine })
    val mySurveys: StateFlow<List<com.accesspath.data.PendingSurvey>> = mySurveys_.asStateFlow()
    /** Areas waiting for an admin (Manage screen): this phone's own plus, for reviewers, everyone's from the server. */
    private val reviewQueue_ = MutableStateFlow<List<com.accesspath.data.PendingSurvey>>(emptyList())
    val reviewQueue: StateFlow<List<com.accesspath.data.PendingSurvey>> = reviewQueue_.asStateFlow()
    private fun refreshMine() { mySurveys_.value = pending.all().filter { it.mine } }

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

    init {
        // Areas recorded before reviews existed were shown straight away; move them to the review queue.
        viewModelScope.launch {
            ready.await()
            runCatching { moveUnverifiedToReview() }.onFailure { Log.w(TAG, "Could not move drafts to review", it) }
        }
    }

    private suspend fun moveUnverifiedToReview() {
        val s = repository.snapshot()
        s.map.buildings.filter { it.draft }.forEach { b ->
            val survey = Survey.of(s.map, b.id) ?: return@forEach
            if (pending.all().none { it.id == b.id }) pending.add(com.accesspath.data.PendingSurvey(survey))
            runCatching { repository.deleteSurvey(b.id, "Moved to the review queue") }
        }
        refreshMine()
    }

    init {
        if (sync.configured) viewModelScope.launch {
            ready.await()
            while (true) {
                runCatching { refreshAccount(); syncQuietly(); syncMySurveys() }
                kotlinx.coroutines.delay(60_000)
            }
        }
    }

    // ---- Account and server sync ------------------------------------------------------------------

    private suspend fun refreshAccount() {
        if (!sync.signedIn) { ui_.update { it.copy(account = null, reviewer = false) }; return }
        val profile = runCatching { sync.profile() }.getOrNull()
        val role = Role.of(profile?.role)
        ui_.update { it.copy(account = Account(profile?.email ?: sync.signedInEmail, role), reviewer = role.canReview) }
    }

    private suspend fun syncQuietly() {
        ui_.update { it.copy(syncing = true) }
        try {
            sync.syncAll(ui_.value.account?.role ?: Role.STUDENT)
            ui_.update { it.copy(lastSync = System.currentTimeMillis()) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Log.w(TAG, "Sync failed", e)
        } finally { ui_.update { it.copy(syncing = false) } }
    }

    fun syncNow() = perform {
        sync.syncAll(ui_.value.account?.role ?: Role.STUDENT)
        ui_.update { it.copy(lastSync = System.currentTimeMillis()) }
        UiMessage.Text(R.string.sync_done)
    }

    fun signInWithGoogle(idToken: String, rawNonce: String) = perform {
        (getApplication<AccessPathApplication>()).supabase.signInWithGoogle(idToken, rawNonce)
        afterSignIn()
    }

    fun signInWithEmail(email: String, password: String) = perform {
        (getApplication<AccessPathApplication>()).supabase.signInWithEmail(email.trim(), password)
        afterSignIn()
    }

    fun signUpWithEmail(email: String, password: String) = perform {
        val ready = (getApplication<AccessPathApplication>()).supabase.signUpWithEmail(email.trim(), password)
        if (ready) afterSignIn() else UiMessage.Text(R.string.check_email)
    }

    private suspend fun afterSignIn(): UiMessage {
        refreshAccount()
        runCatching { sync.syncAll(ui_.value.account?.role ?: Role.STUDENT) }
        return UiMessage.Text(R.string.signed_in_as, listOf(ui_.value.account?.email ?: ""))
    }

    fun signOut() = perform(UiMessage.Text(R.string.signed_out)) {
        (getApplication<AccessPathApplication>()).supabase.signOut()
        ui_.update { it.copy(account = null, reviewer = false) }
        null
    }

    /** Server mode: shared changes need a signed-in reviewer and go to the server first. */
    private fun requireReviewer() {
        val s = ui_.value
        if (!s.server) return
        if (s.account == null) throw AppError(ErrorCode.NEEDS_SIGN_IN)
        if (!s.account.role.canReview) throw AppError(ErrorCode.NEEDS_REVIEWER)
    }

    fun publishMap() = perform(UiMessage.Text(R.string.map_published)) {
        if (ui_.value.account?.role != Role.ADMIN) throw AppError(ErrorCode.NEEDS_ADMIN)
        sync.publishMap(repository.snapshot().map)
        null
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

    /** The unmapped-area check runs once per app start, even if its screen is left and re-entered. */
    var areaChecked = false
        private set
    fun markAreaChecked() { areaChecked = true }

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
        perform(UiMessage.Text(R.string.report_submitted)) {
            repository.submitReport(id, edgeId, category, description)
            (getApplication<AccessPathApplication>()).contributions.add(com.accesspath.data.Contributions.Kind.CAMPUS_REPORT)
            if (!ui_.value.server) return@perform null
            if (!sync.signedIn) return@perform UiMessage.Text(R.string.report_saved_sign_in)
            try { sync.pushPendingReports(); null } catch (e: AppError) { UiMessage.Text(R.string.report_saved_offline) }
        }

    fun review(reportId: String, status: ReportStatus, reason: String, revision: Long) =
        perform(UiMessage.Text(R.string.decision_saved)) {
            requireReviewer()
            if (ui_.value.server) sync.review(reportId, status, reason.trim())
            repository.review(reportId, status, reason, revision)
            null
        }

    fun setFacility(id: String, status: Availability, reason: String, revision: Long) =
        perform(UiMessage.Text(R.string.facility_saved)) {
            requireReviewer()
            if (ui_.value.server) sync.setFacility(repository.snapshot().map.id, id, status, reason.trim())
            repository.setFacility(id, status, reason, revision)
            null
        }

    fun setPathClosed(edgeId: String, closed: Boolean, reason: String) =
        perform(UiMessage.Text(if (closed) R.string.path_closed else R.string.path_reopened)) {
            requireReviewer()
            if (ui_.value.server) sync.setClosure(repository.snapshot().map.id, edgeId, closed, reason.trim())
            repository.setPathClosed(edgeId, closed, reason)
            null
        }

    /**
     * Sends a mapped walk for review. It is NOT added to the map: it appears for everyone only after an admin
     * approves it in Campus management.
     */
    fun saveSurvey(trail: List<TrailPoint>, places: List<SurveyPlace>, start: LatLng, headingDeg: Double, name: String? = null) =
        perform {
            val stamp = System.currentTimeMillis()
            val title = name?.trim()?.takeIf { it.isNotEmpty() }
                ?: ("Area " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(stamp)))
            val survey = withContext(Dispatchers.Default) { SurveyBuilder.build("survey_$stamp", title.take(60), trail, places, start, headingDeg) }
                ?: return@perform UiMessage.Text(R.string.mapping_too_short)
            (getApplication<AccessPathApplication>()).contributions.add(com.accesspath.data.Contributions.Kind.MAPPED_AREA)
            pending.add(com.accesspath.data.PendingSurvey(survey))
            refreshMine()
            val sent = ui_.value.server && sync.signedIn && runCatching { sync.submitSurvey(survey) }.isSuccess
            if (sent) pending.update(survey.building.id) { it.copy(uploaded = true) }
            refreshMine()
            UiMessage.Text(when { sent -> R.string.survey_sent_review; ui_.value.server -> R.string.survey_saved_sign_in_review; else -> R.string.survey_saved_local_review })
        }

    /** Sends areas mapped while offline or signed out, and picks up the admin's decisions. */
    private suspend fun syncMySurveys() {
        if (!sync.signedIn) return
        pending.all().filter { it.mine && !it.uploaded }.forEach { p ->
            if (runCatching { sync.submitSurvey(p.survey) }.isSuccess) pending.update(p.id) { it.copy(uploaded = true) }
        }
        val mine = pending.all().filter { it.mine && it.uploaded && it.status == com.accesspath.data.ReviewStatus.PENDING }
        runCatching { sync.surveyStatuses(mine.map { it.id }) }.getOrNull()?.forEach { st ->
            val status = runCatching { com.accesspath.data.ReviewStatus.valueOf(st.status) }.getOrNull() ?: return@forEach
            pending.update(st.id) { it.copy(status = status, reason = st.reason) }
        }
        refreshMine()
    }

    /** Loads what an admin has to check: areas from this phone and, for signed-in reviewers, from everyone. */
    fun loadReviewQueue() = viewModelScope.launch {
        val local = pending.all().filter { it.status == com.accesspath.data.ReviewStatus.PENDING }
        val remote = if (ui_.value.server && ui_.value.reviewer) runCatching { sync.surveysToReview() }.getOrDefault(emptyList())
            .map { (survey, at) -> com.accesspath.data.PendingSurvey(survey, uploaded = true, createdAt = at, mine = false) } else emptyList()
        reviewQueue_.value = (local + remote).distinctBy { it.id }.sortedBy { it.createdAt }
    }

    /** An admin approves an area: it is added to the map for everyone. */
    fun approveSurvey(id: String) = perform(UiMessage.Text(R.string.survey_approved)) {
        requireReviewer()
        val item = reviewQueue_.value.firstOrNull { it.id == id } ?: pending.all().firstOrNull { it.id == id } ?: return@perform null
        if (ui_.value.server) {
            if (!item.uploaded) sync.submitSurvey(item.survey)
            sync.reviewSurvey(id, approve = true, reason = "")
        }
        repository.addSurvey(item.survey.approved())
        pending.update(id) { it.copy(status = com.accesspath.data.ReviewStatus.APPROVED) }
        reviewQueue_.value = reviewQueue_.value.filterNot { it.id == id }
        refreshMine()
        null
    }

    /** An admin rejects an area with a reason; it never appears on the map. */
    fun rejectSurvey(id: String, reason: String) = perform(UiMessage.Text(R.string.survey_rejected)) {
        requireReviewer()
        if (reason.trim().length !in ReportRules.MIN_TEXT..ReportRules.MAX_TEXT) throw AppError(ErrorCode.REASON_LENGTH)
        val item = reviewQueue_.value.firstOrNull { it.id == id } ?: pending.all().firstOrNull { it.id == id } ?: return@perform null
        if (ui_.value.server) {
            if (!item.uploaded) sync.submitSurvey(item.survey)
            sync.reviewSurvey(id, approve = false, reason = reason.trim())
        }
        pending.update(id) { it.copy(status = com.accesspath.data.ReviewStatus.REJECTED, reason = reason.trim()) }
        reviewQueue_.value = reviewQueue_.value.filterNot { it.id == id }
        refreshMine()
        null
    }

    /** Removes an approved area from the map (and the server). */
    fun deleteSurvey(buildingId: String, reason: String) =
        perform(UiMessage.Text(R.string.draft_deleted)) {
            if (ui_.value.server) { requireReviewer(); sync.deleteSurvey(buildingId) }
            repository.deleteSurvey(buildingId, reason); null
        }


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

    /** Writes printable QR placards (one per place) to the chosen PDF file. */
    fun exportQrPlacards(uri: Uri) = perform {
        val s = repository.snapshot()
        val app = getApplication<Application>()
        val count = withContext(Dispatchers.IO) {
            app.contentResolver.openOutputStream(uri, "wt")?.use { QrPlacards.write(app, s, it) }
        } ?: return@perform UiMessage.Text(R.string.error_file)
        UiMessage.Text(R.string.qr_saved, listOf(count))
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
            if (uri.scheme == LocationLinks.SCHEME && uri.host == LocationLinks.HOST) uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() } else null
    }
}
