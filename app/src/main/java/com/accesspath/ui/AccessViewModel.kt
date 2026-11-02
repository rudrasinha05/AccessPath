package com.accesspath.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accesspath.AccessPathApplication
import com.accesspath.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString

data class ScreenState(
    val snapshot: AppSnapshot? = null,
    val route: RouteResult? = null,
    val routing: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val syncConflicts: List<ConflictRecord> = emptyList()
)

class AccessViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as AccessPathApplication).repository
    val sensorMapper = SensorMapper(application)

    private val _ui = MutableStateFlow(ScreenState())
    val ui = _ui.asStateFlow()

    init {
        action { repository.initialize() }
        viewModelScope.launch {
            repository.snapshots.filterNotNull().catch { e -> _ui.update { it.copy(message="Cannot load saved data: ${e.message}") } }.collectLatest { s ->
                _ui.update { it.copy(snapshot=s, route=null, routing=true) }
                val route = withContext(Dispatchers.Default) { Routing.calculate(s) }
                ensureActive()
                if(_ui.value.snapshot?.revision == s.revision) _ui.update { it.copy(route=route, routing=false) }
            }
        }
    }

    fun action(success: String? = null, work: suspend () -> Unit) {
        if(_ui.value.busy) return
        _ui.update { it.copy(busy=true) }
        viewModelScope.launch {
            try { work(); if(success != null) message(success) }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { message(e.message ?: "Action failed. Please retry.") }
            finally { _ui.update { it.copy(busy=false) } }
        }
    }

    fun message(text: String?) { _ui.update { it.copy(message=text) } }
    
    fun importMap(uri: Uri) = action {
        val text = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                val buffer=java.io.ByteArrayOutputStream(); val bytes=ByteArray(8192); var total=0
                while(true) { val n=input.read(bytes); if(n<0) break; total+=n; require(total <= 10*1024*1024) { "Map exceeds 10 MB." }; buffer.write(bytes,0,n) }
                buffer.toString("UTF-8")
            } ?: error("Unable to open this file.")
        }
        val warnings=repository.importMap(text)
        message("Map imported. " + warnings.joinToString())
    }

    fun exportMap(uri: Uri) = action("Map exported successfully") {
        val s = repository.snapshot()
        val json = MapJson.encodeToString(s.map)
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("Unable to write file.")
        }
    }

    fun addLandmark(node: Node) = action("Landmark '${node.name}' added successfully") {
        repository.addLandmark(node)
    }

    fun editLandmark(nodeId: String, newName: String, newX: Double, newY: Double) = action("Landmark updated successfully") {
        repository.editLandmark(nodeId, newName, newX, newY)
    }

    fun deleteLandmark(nodeId: String) = action("Landmark deleted successfully") {
        repository.deleteLandmark(nodeId)
    }

    fun addConnector(edge: Edge, twoWay: Boolean) = action("Connector added successfully") {
        repository.addConnector(edge, twoWay)
    }

    fun syncRemoteWithConflicts(remotePayload: SyncPayload, account: ReviewerAccount) = action {
        val conflicts = repository.syncRemote(remotePayload, account)
        _ui.update { it.copy(syncConflicts = conflicts) }
        message("Synced. ${conflicts.size} conflict(s) resolved with safety-first rules.")
    }

    fun convertMappingSession(session: MappingSession) = action("Survey trail converted into navigation graph") {
        val (nodes, edges) = session.convertToNavigationElements()
        require(nodes.isNotEmpty()) { "Record at least 2 steps before converting." }
        nodes.forEach { repository.addLandmark(it) }
        edges.forEach { repository.addConnector(it, twoWay = false) }
    }

    fun findNearest(category: String) {
        val s = _ui.value.snapshot ?: return
        val pair = Routing.findNearestFacility(s, category)
        if (pair != null) {
            val (node, _) = pair
            action("Routed to nearest $category: ${node.name}") {
                repository.select(s.profile, s.current, node.id, s.revision)
            }
        } else {
            message("No accessible $category found from current location.")
        }
    }

    fun requestAssistance(urgency: String, note: String) {
        val s = _ui.value.snapshot ?: return
        action("Assistance request dispatched to campus control desk") {
            repository.requestAssistance(s.current, urgency, note)
        }
    }

    fun acknowledgeAssistance(requestId: String) = action("Assistance request acknowledged") {
        repository.acknowledgeAssistance(requestId)
    }

    fun submitReportWithMedia(
        id: String,
        edgeId: String,
        category: String,
        description: String,
        photoUri: String?,
        hasVoiceNote: Boolean,
        expiryHours: Int
    ) = action("Barrier report submitted with verification tracking") {
        repository.submitReportWithMedia(id, edgeId, category, description, photoUri, hasVoiceNote, expiryHours)
    }

    fun setMapStyle(style: MapStyle) = action("Map layer switched to ${style.name}") {
        repository.setMapStyle(style)
    }

    fun addGeofence(zone: GeofenceZone) = action("Geofence zone active: ${zone.name}") {
        repository.addGeofence(zone)
    }

    fun deleteGeofence(zoneId: String) = action("Geofence removed") {
        repository.deleteGeofence(zoneId)
    }

    fun switchCampus(campusId: String) = action("Loading worldwide campus map") {
        repository.switchCampus(campusId)
    }

    fun publishCampusToWorld() = action {
        val result = repository.publishCampusToWorld()
        message(result)
    }
}
