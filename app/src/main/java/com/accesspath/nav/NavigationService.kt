package com.accesspath.nav

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.accesspath.MainActivity
import com.accesspath.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the navigation notification shows: the next instruction and the distance to it. */
data class NavInfo(val distance: String, val instruction: String, val eta: String)

/** Shared between the navigation screen and the notification. */
object NavSession {
    private val _info = MutableStateFlow<NavInfo?>(null)
    val info = _info.asStateFlow()
    private val _exit = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Fires when the person taps "Exit navigation" in the notification. */
    val exitRequests = _exit.asSharedFlow()

    fun start(context: Context, first: NavInfo) {
        _info.value = first
        runCatching { ContextCompat.startForegroundService(context, Intent(context, NavigationService::class.java)) }
    }
    fun update(info: NavInfo) { if (_info.value != null) _info.value = info }
    fun stop(context: Context) {
        _info.value = null
        context.stopService(Intent(context, NavigationService::class.java))
    }
    internal fun requestExit(context: Context) { _exit.tryEmit(Unit); stop(context) }
}

/**
 * Keeps turn-by-turn navigation going with the screen off or another app open, and shows the ongoing
 * "120 m · Turn left onto …" notification with an Exit navigation button, like other map apps.
 */
class NavigationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) { NavSession.requestExit(this); return START_NOT_STICKY }
        createChannel(this)
        val first = NavSession.info.value ?: run { stopSelf(); return START_NOT_STICKY }
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, ID, build(first), type)
        } catch (e: Exception) {
            // Location permission missing or the app is not allowed to start one now: navigate without the notification.
            stopSelf(); return START_NOT_STICKY
        }
        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        scope.launch {
            NavSession.info.collect { info ->
                if (info == null) { stopSelf(); return@collect }
                getSystemService(NotificationManager::class.java).notify(ID, build(info))
            }
        }
        return START_NOT_STICKY
    }

    private fun build(info: NavInfo) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_nav)
        .setContentTitle("${info.distance} · ${info.instruction}")
        .setContentText(info.eta)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
        .setColor(0xFF0E7C86.toInt())
        .setContentIntent(PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .addAction(0, getString(R.string.exit_navigation), PendingIntent.getService(this, 1,
            Intent(this, NavigationService::class.java).setAction(ACTION_EXIT), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .build()

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        private const val CHANNEL = "navigation"
        private const val ID = 4201
        private const val ACTION_EXIT = "com.accesspath.EXIT_NAVIGATION"

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.nav_channel), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.nav_channel_desc); setShowBadge(false) })
        }
    }
}
