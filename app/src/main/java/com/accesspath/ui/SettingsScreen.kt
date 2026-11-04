package com.accesspath.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.accesspath.BuildConfig
import com.accesspath.R

@Composable
fun SettingsScreen(ui: ScreenState, vm: AccessViewModel, onManage: () -> Unit) {
    val state = ui.snapshot ?: return
    var editProfile by rememberSaveable { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importMap) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportMap) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("settings"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle(stringResource(R.string.tab_you))

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(Color(0xFF1A73E8), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(mobilityIcon(state.profile.mobility), contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(state.profile.mobility.label()), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(onClick = { editProfile = true }) { Text(stringResource(R.string.change)) }
            }
            InfoText(stringResource(state.profile.mobility.description()))
        }

        Surface(onClick = onManage, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = Color(0xFF1A73E8))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.campus_management), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    InfoText(stringResource(R.string.campus_management_desc))
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }

        SectionCard(stringResource(R.string.language_title)) {
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            val options = listOf("" to stringResource(R.string.language_system), "en" to stringResource(R.string.language_en), "hi" to stringResource(R.string.language_hi))
            Column(Modifier.selectableGroup()) {
                options.forEach { (tag, label) ->
                    val selected = current.substringBefore('-') == tag
                    Row(
                        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) {
                            AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected, onClick = null)
                        Text(label, Modifier.padding(start = 12.dp))
                    }
                }
            }
        }

        SectionCard(stringResource(R.string.map_data_title)) {
            val map = state.map
            Text(stringResource(R.string.map_name, map.name, map.version), fontWeight = FontWeight.SemiBold)
            InfoText(stringResource(R.string.map_source, map.provenance))
            InfoText(map.verifiedAt?.let { stringResource(R.string.map_verified, formatTime(it)) } ?: stringResource(R.string.map_not_verified))
            InfoText(stringResource(R.string.map_counts, map.nodes.size, map.edges.distinctBy { listOf(it.from, it.to).sorted() }.size, map.floors.size))
            if (map.isDemo) Banner(stringResource(R.string.demo_warning), warning = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollable()) {
                OutlinedButton(enabled = !ui.busy, onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text(stringResource(R.string.import_map)) }
                OutlinedButton(enabled = !ui.busy, onClick = { exporter.launch("accesspath-map-${map.version}.json") }) { Text(stringResource(R.string.export_map)) }
            }
        }

        SectionCard(stringResource(R.string.links_title)) { InfoText(stringResource(R.string.links_body)) }

        SectionCard(stringResource(R.string.about_title)) {
            Text(stringResource(R.string.about_body))
            listOf(R.string.limit_position, R.string.limit_unknown, R.string.limit_local, R.string.limit_reviewer).forEach {
                InfoText("• " + stringResource(it))
            }
            InfoText(stringResource(R.string.version, BuildConfig.VERSION_NAME))
        }
    }

    if (editProfile) ProfileDialog(state.profile, { editProfile = false }) { editProfile = false; vm.setProfile(it) }
}
