package com.accesspath.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accesspath.AccessPathApplication
import com.accesspath.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ScreenState(val snapshot: AppSnapshot? = null, val route: RouteResult? = null, val routing: Boolean = false, val busy: Boolean = false, val message: String? = null)
class AccessViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as AccessPathApplication).repository
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
}
