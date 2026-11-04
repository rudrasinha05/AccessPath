package com.accesspath.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.accesspath.R
import com.accesspath.data.remote.Poi
import com.accesspath.data.remote.PoiCategory
import com.accesspath.data.remote.Weather
import com.accesspath.domain.LatLng
import com.accesspath.domain.Mobility

/** AccessPath's own brand colours (not another app's). */
val BrandBlue = Color(0xFF1A56DB)
val BrandTeal = Color(0xFF0E7C86)
val BrandTealSoft = Color(0xFFCDEDEF)

fun PoiCategory.label(): Int = when (this) {
    PoiCategory.RESTAURANTS -> R.string.cat_restaurants; PoiCategory.COFFEE -> R.string.cat_coffee
    PoiCategory.PETROL -> R.string.cat_petrol; PoiCategory.HOTELS -> R.string.cat_hotels
    PoiCategory.ATMS -> R.string.cat_atms; PoiCategory.HOSPITALS -> R.string.cat_hospitals
    PoiCategory.SHOPPING -> R.string.cat_shopping; PoiCategory.TOILETS -> R.string.cat_toilets
    PoiCategory.ACCESSIBLE -> R.string.cat_accessible
}

fun PoiCategory.icon(): ImageVector = when (this) {
    PoiCategory.RESTAURANTS -> Icons.Filled.Restaurant; PoiCategory.COFFEE -> Icons.Filled.LocalCafe
    PoiCategory.PETROL -> Icons.Filled.LocalGasStation; PoiCategory.HOTELS -> Icons.Filled.Hotel
    PoiCategory.ATMS -> Icons.Filled.LocalAtm; PoiCategory.HOSPITALS -> Icons.Filled.LocalHospital
    PoiCategory.SHOPPING -> Icons.Filled.ShoppingBag; PoiCategory.TOILETS -> Icons.Filled.Wc
    PoiCategory.ACCESSIBLE -> Icons.AutoMirrored.Filled.Accessible
}

fun poiCategoryOf(p: Poi) = runCatching { PoiCategory.valueOf(p.kind) }.getOrDefault(PoiCategory.ACCESSIBLE)

/** The top search bar: app mark, search, voice, camera and profile. */
@Composable
fun HomeSearchBar(accountInitial: String?, onSearch: () -> Unit, onVoice: () -> Unit, onCamera: () -> Unit, onProfile: () -> Unit) {
    Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth().height(60.dp)) {
        Row(Modifier.fillMaxSize().clickable(role = Role.Button, onClick = onSearch).padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(BrandBlue, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.search_here), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).testTag("searchBar"))
            IconButton(onClick = onVoice) { Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_search)) }
            IconButton(onClick = onCamera) { Icon(Icons.Filled.CameraAlt, contentDescription = stringResource(R.string.map_this_area)) }
            IconButton(onClick = onProfile) {
                Box(Modifier.size(36.dp).border(2.dp, BrandBlue, CircleShape).padding(3.dp).background(BrandTeal, CircleShape), contentAlignment = Alignment.Center) {
                    if (accountInitial != null) Text(accountInitial, color = Color.White, fontWeight = FontWeight.Bold)
                    else Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.tab_you), tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Chip row under the search bar: the person's mobility, then place categories. */
@Composable
fun CategoryChips(mobility: Mobility, selected: PoiCategory?, onMobility: () -> Unit, onCategory: (PoiCategory?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeChip(stringResource(mobility.label()), mobilityIcon(mobility), selected = false, accent = true, onClick = onMobility)
        listOf(PoiCategory.ACCESSIBLE, PoiCategory.TOILETS, PoiCategory.RESTAURANTS, PoiCategory.PETROL, PoiCategory.HOSPITALS,
            PoiCategory.ATMS, PoiCategory.HOTELS, PoiCategory.SHOPPING, PoiCategory.COFFEE).forEach { c ->
            HomeChip(stringResource(c.label()), c.icon(), selected = c == selected, modifier = Modifier.testTag("chip_${c.name.lowercase()}")) {
                onCategory(if (c == selected) null else c)
            }
        }
    }
}

@Composable
private fun HomeChip(label: String, icon: ImageVector, selected: Boolean, accent: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp), onClick = onClick, shadowElevation = 3.dp, modifier = modifier,
        color = if (selected) BrandTealSoft else MaterialTheme.colorScheme.surface,
        border = if (accent) BorderStroke(1.5.dp, BrandBlue) else null
    ) {
        Row(Modifier.height(40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (accent || selected) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Round floating button used on the right side of the map. */
@Composable
fun MapRoundButton(icon: ImageVector, description: String, tint: Color = MaterialTheme.colorScheme.onSurface, container: Color = MaterialTheme.colorScheme.surface, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = container, shadowElevation = 4.dp, modifier = modifier.size(52.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, tint = tint) }
    }
}

fun weatherIcon(w: Weather): ImageVector = when (w.code) {
    0, 1 -> if (w.isDay) Icons.Filled.WbSunny else Icons.Filled.NightsStay
    2, 3, 45, 48 -> Icons.Filled.Cloud
    in 51..67, in 80..82 -> Icons.Filled.WaterDrop
    in 71..77, 85, 86 -> Icons.Filled.AcUnit
    in 95..99 -> Icons.Filled.Thunderstorm
    else -> Icons.Filled.Cloud
}

fun aqiColor(aqi: Int): Color = when {
    aqi <= 50 -> Color(0xFF188038); aqi <= 100 -> Color(0xFFF9AB00); aqi <= 150 -> Color(0xFFE8710A)
    aqi <= 200 -> Color(0xFFD93025); aqi <= 300 -> Color(0xFF8E24AA); else -> Color(0xFF7B1E1E)
}

/** Sheet header: the area's name with live weather and air quality. */
@Composable
fun AreaHeader(area: String?, weather: Weather?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(area ?: stringResource(R.string.explore_nearby), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (weather != null) Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(weatherIcon(weather), contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${Math.round(weather.temperatureC)}°", style = MaterialTheme.typography.titleSmall)
                }
                weather.usAqi?.let { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(aqiColor(a), CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.aqi_value, a), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

fun wheelchairLabel(v: String?): Int? = when (v) {
    "yes", "designated" -> R.string.wc_yes; "limited" -> R.string.wc_limited; "no" -> R.string.wc_no; else -> null
}

/** Results of a category, nearest first, with distance and accessibility. */
@Composable
fun PoiList(category: PoiCategory, pois: List<Poi>, loading: Boolean, failed: Boolean, from: LatLng?, onRetry: () -> Unit, onPick: (Poi) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("poiList")) {
        Text(stringResource(R.string.nearby_category, stringResource(category.label())), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        when {
            loading -> { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp)); InfoText(stringResource(R.string.loading_places)) }
            failed -> { Banner(stringResource(R.string.places_failed), warning = true); OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) } }
            pois.isEmpty() -> InfoText(stringResource(R.string.places_none))
            else -> LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(pois, key = { it.id }) { p -> PoiRow(p, from) { onPick(p) } }
                item { Text(stringResource(R.string.osm_credit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

@Composable
private fun PoiRow(p: Poi, from: LatLng?, onClick: () -> Unit) {
    val cat = poiCategoryOf(p)
    ListItem(
        headlineContent = { Text(p.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val parts = listOfNotNull(from?.let { dist(it.metresToSafe(p.at)) }, p.address.takeIf { it.isNotBlank() })
            Column {
                if (parts.isNotEmpty()) Text(parts.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                wheelchairLabel(p.wheelchair)?.let { Text(stringResource(it), color = if (p.wheelchair == "no") Color(0xFFD93025) else Color(0xFF188038), style = MaterialTheme.typography.labelMedium) }
            }
        },
        leadingContent = {
            Box(Modifier.size(40.dp).background(BrandTealSoft, CircleShape), contentAlignment = Alignment.Center) { Icon(cat.icon(), contentDescription = null, tint = BrandTeal) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
    )
}

private fun LatLng.metresToSafe(o: LatLng) = com.accesspath.data.remote.Places.distance(this, o)

/** The place card for a real place: actions first, then the details OpenStreetMap has. */
@Composable
fun PoiSheet(
    p: Poi, from: LatLng?, saved: Boolean,
    onDirections: () -> Unit, onStart: () -> Unit, onShare: () -> Unit, onSave: () -> Unit,
    onCall: (String) -> Unit, onWebsite: (String) -> Unit, onClose: () -> Unit
) {
    val cat = poiCategoryOf(p)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("poiSheet")) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Text(listOfNotNull(stringResource(cat.label()), from?.let { dist(it.metresToSafe(p.at)) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.address.isNotBlank()) Text(p.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSave) { Icon(if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = stringResource(R.string.save_place)) }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share)) }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onDirections, colors = ButtonDefaults.buttonColors(containerColor = BrandTeal, contentColor = Color.White)) {
                Icon(Icons.Filled.Directions, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.directions))
            }
            FilledTonalButton(onClick = onStart) { Icon(Icons.Filled.Navigation, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.start)) }
            p.phone?.let { ph -> FilledTonalButton(onClick = { onCall(ph) }) { Icon(Icons.Filled.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.call)) } }
            FilledTonalButton(onClick = onShare) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.share)) }
            FilledTonalButton(onClick = onSave) {
                Icon(if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(if (saved) R.string.saved else R.string.save_place))
            }
        }
        // Accessibility first: it is what this app is for.
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Accessible, contentDescription = null, tint = BrandBlue)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(wheelchairLabel(p.wheelchair) ?: R.string.wc_unknown), fontWeight = FontWeight.Medium)
                    if (p.hasAccessibleToilet) Text(stringResource(R.string.wc_toilet), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        p.hours?.let { DetailRow(Icons.Filled.Schedule, it) }
        p.phone?.let { DetailRow(Icons.Filled.Call, it) { onCall(it) } }
        p.website?.let { DetailRow(Icons.Filled.Public, it) { onWebsite(it) } }
        Text(stringResource(R.string.osm_credit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailRow(icon: ImageVector, text: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (onClick != null) BrandBlue else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Map type and accessibility layers, as a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(style: MapStyle, category: PoiCategory?, onStyle: (MapStyle) -> Unit, onCategory: (PoiCategory?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.map_type), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                LayerTile(stringResource(R.string.map_default), Icons.Filled.Map, Color(0xFFDDEFE3), style == MapStyle.DEFAULT) { onStyle(MapStyle.DEFAULT) }
                LayerTile(stringResource(R.string.map_satellite), Icons.Filled.Satellite, Color(0xFF5F6B5A), style == MapStyle.SATELLITE) { onStyle(MapStyle.SATELLITE) }
                LayerTile(stringResource(R.string.map_terrain), Icons.Filled.Terrain, Color(0xFFB9C2A8), style == MapStyle.TERRAIN) { onStyle(MapStyle.TERRAIN) }
            }
            HorizontalDivider()
            Text(stringResource(R.string.map_details), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                LayerTile(stringResource(R.string.cat_accessible), Icons.AutoMirrored.Filled.Accessible, Color(0xFFDCE6FB), category == PoiCategory.ACCESSIBLE) {
                    onCategory(if (category == PoiCategory.ACCESSIBLE) null else PoiCategory.ACCESSIBLE)
                }
                LayerTile(stringResource(R.string.cat_toilets), Icons.Filled.Wc, Color(0xFFD5F0EC), category == PoiCategory.TOILETS) {
                    onCategory(if (category == PoiCategory.TOILETS) null else PoiCategory.TOILETS)
                }
                LayerTile(stringResource(R.string.cat_hospitals), Icons.Filled.LocalHospital, Color(0xFFFBDCDC), category == PoiCategory.HOSPITALS) {
                    onCategory(if (category == PoiCategory.HOSPITALS) null else PoiCategory.HOSPITALS)
                }
            }
        }
    }
}

@Composable
private fun LayerTile(label: String, icon: ImageVector, bg: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(4.dp)) {
        Box(
            Modifier.size(76.dp).border(if (selected) 3.dp else 0.dp, if (selected) BrandTeal else Color.Transparent, RoundedCornerShape(16.dp))
                .padding(4.dp).background(bg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = if (bg == Color(0xFF5F6B5A)) Color.White else Color(0xFF3C4043), modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) BrandTeal else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}
