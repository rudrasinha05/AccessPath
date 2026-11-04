package com.accesspath.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.accesspath.AccessPathApplication
import com.accesspath.R
import com.accesspath.data.Contributions
import com.accesspath.data.SavedPlace
import com.accesspath.domain.LatLng
import com.accesspath.ui.mapping.DeviceSensors
import kotlinx.coroutines.launch

/** You tab: Home and Work, saved places and recent places, all kept on this phone. */
@Composable
fun YourPlacesCard(onOpenPlace: (SavedPlace) -> Unit) {
    val context = LocalContext.current
    val local = (context.applicationContext as AccessPathApplication).localPlaces
    var tick by remember { mutableIntStateOf(0) }
    val home = remember(tick) { local.home() }
    val work = remember(tick) { local.work() }
    val saved = remember(tick) { local.saved() }
    val recents = remember(tick) { local.recents().take(5) }
    val scope = rememberCoroutineScope()
    var settingHome by remember { mutableStateOf<Boolean?>(null) }
    fun setHere(isHome: Boolean) = scope.launch {
        val fix = DeviceSensors.currentLocation(context, 8_000)
        if (fix == null) return@launch
        val sp = SavedPlace(context.getString(if (isHome) R.string.home else R.string.work), formatLatLng(LatLng(fix.latitude, fix.longitude)), fix.latitude, fix.longitude)
        if (isHome) local.setHome(sp) else local.setWork(sp)
        tick++
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { settingHome?.let { setHere(it) } }
    fun ask(isHome: Boolean) {
        settingHome = isHome
        if (DeviceSensors.hasLocationPermission(context)) setHere(isHome)
        else permission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    SectionCard(stringResource(R.string.your_places)) {
        Column(Modifier.testTag("yourPlaces")) {
            PlaceRow(Icons.Filled.Home, stringResource(R.string.home), home?.detail ?: stringResource(R.string.set_to_here),
                onClick = { home?.let(onOpenPlace) ?: ask(true) }, onClear = home?.let { { local.setHome(null); tick++ } })
            PlaceRow(Icons.Filled.Work, stringResource(R.string.work), work?.detail ?: stringResource(R.string.set_to_here),
                onClick = { work?.let(onOpenPlace) ?: ask(false) }, onClear = work?.let { { local.setWork(null); tick++ } })
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(pluralStringResource(R.plurals.saved_count, saved.size, saved.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (saved.isEmpty()) InfoText(stringResource(R.string.saved_empty))
            saved.forEach { p -> PlaceRow(Icons.Filled.Bookmark, p.name, p.detail, onClick = { onOpenPlace(p) }, onClear = { local.toggleSaved(p); tick++ }) }
            if (recents.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.recent), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                recents.forEach { p -> PlaceRow(Icons.Filled.History, p.name, p.detail, onClick = { onOpenPlace(p) }, onClear = { local.removeRecent(p); tick++ }) }
            }
        }
    }
}

@Composable
private fun PlaceRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit, onClear: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
        supportingContent = { if (detail.isNotBlank()) Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Box(Modifier.size(40.dp).background(BrandTealSoft, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = BrandTeal) } },
        trailingContent = onClear?.let { c -> { IconButton(onClick = c) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove)) } } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickableRow(onClick)
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)

/** Points and level from what this phone has added: barrier reports and mapped areas. */
@Composable
fun ContributionsCard(onReportOutdoor: () -> Unit, onMapArea: () -> Unit) {
    val context = LocalContext.current
    val c = (context.applicationContext as AccessPathApplication).contributions
    val points = c.points()
    val level = Contributions.level(points)
    val next = Contributions.nextLevelAt(points)
    val prev = Contributions.LEVELS[level - 1]
    Surface(shape = MaterialTheme.shapes.extraLarge, color = BrandTealSoft, modifier = Modifier.fillMaxWidth().testTag("contributions")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(BrandTeal, CircleShape), contentAlignment = Alignment.Center) {
                    Text(level.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.level_n, level), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = BrandTeal)
                    Text(stringResource(R.string.points_n, points), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (next != null) {
                LinearProgressIndicator(progress = { (points - prev).toFloat() / (next - prev) }, color = BrandTeal, modifier = Modifier.fillMaxWidth())
                InfoText(stringResource(R.string.points_to_next, next - points, level + 1))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat(c.count(Contributions.Kind.OUTDOOR_BARRIER) + c.count(Contributions.Kind.CAMPUS_REPORT), stringResource(R.string.stat_reports))
                Stat(c.count(Contributions.Kind.MAPPED_AREA), stringResource(R.string.stat_mapped))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionTile(Icons.Filled.AddAlert, stringResource(R.string.report_outdoor), Color(0xFFD93025), Modifier.weight(1f).testTag("reportOutdoor"), onReportOutdoor)
        ActionTile(Icons.Filled.CameraAlt, stringResource(R.string.map_new_area), BrandTeal, Modifier.weight(1f), onMapArea)
    }
}

@Composable
private fun Stat(n: Int, label: String) {
    Column { Text(n.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.heightIn(min = 96.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** This phone's outdoor barrier reports, newest first, with a way to remove one that is cleared. */
@Composable
fun MyOutdoorReports(tick: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AccessPathApplication
    val mine = remember(tick) { app.barriers.local() }
    val scope = rememberCoroutineScope()
    if (mine.isEmpty()) return
    SectionTitle(stringResource(R.string.my_outdoor_reports))
    mine.forEach { b ->
        val ago = android.text.format.DateUtils.getRelativeTimeSpanString(b.time, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
        ListItem(
            headlineContent = { Text(stringResource(b.kind.label()), fontWeight = FontWeight.Medium) },
            supportingContent = { Text(listOf(ago, formatLatLng(b.at), if (b.localOnly) stringResource(R.string.only_on_phone) else stringResource(R.string.shared)).joinToString(" · ") + (if (b.note.isNotBlank()) "\n${b.note}" else "")) },
            leadingContent = { Icon(b.kind.icon(), null, tint = Color(0xFFD93025)) },
            trailingContent = { TextButton(onClick = { scope.launch { app.barriers.clear(b); onChanged() } }) { Text(stringResource(R.string.barrier_cleared)) } },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
