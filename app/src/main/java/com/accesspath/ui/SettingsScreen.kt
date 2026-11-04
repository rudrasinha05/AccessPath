package com.accesspath.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
fun SettingsScreen(ui: ScreenState, vm: AccessViewModel, onManage: () -> Unit, onMapNewArea: () -> Unit) {
    val state = ui.snapshot ?: return
    var editProfile by rememberSaveable { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importMap) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportMap) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("settings"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle(stringResource(R.string.tab_you))
        if (ui.server) AccountCard(ui, vm)

        Surface(onClick = { editProfile = true }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(Color(0xFF1A73E8), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(mobilityIcon(state.profile.mobility), contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(state.profile.mobility.label()), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.change))
            }
            InfoText(stringResource(state.profile.mobility.description()))
          }
        }

        Surface(onClick = onMapNewArea, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.map_new_area), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.map_new_area_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        AreaCheckSetting()

        NearbyAlertsSetting()

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

@Composable
private fun AreaCheckSetting() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var on by remember { mutableStateOf(com.accesspath.ui.mapping.AreaCheckPrefs.get(context) == com.accesspath.ui.mapping.AreaCheckPrefs.Choice.ON) }
    Row(
        Modifier.fillMaxWidth().toggleable(on, role = Role.Switch) { v ->
            on = v
            com.accesspath.ui.mapping.AreaCheckPrefs.set(context, if (v) com.accesspath.ui.mapping.AreaCheckPrefs.Choice.UNSET else com.accesspath.ui.mapping.AreaCheckPrefs.Choice.OFF)
        }.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.area_check_setting), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(on, onCheckedChange = null)
    }
}

@Composable
private fun AccountCard(ui: ScreenState, vm: AccessViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var emailDialog by rememberSaveable { mutableStateOf(false) }
    SectionCard(stringResource(R.string.account_title)) {
        val account = ui.account
        if (account == null) {
            InfoText(stringResource(R.string.account_intro))
            Button(
                onClick = {
                    val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
                        .filterIsInstance<android.app.Activity>().firstOrNull()
                    if (activity != null) scope.launch {
                        when (val r = com.accesspath.ui.auth.GoogleSignIn.idToken(activity)) {
                            is com.accesspath.ui.auth.GoogleResult.Token -> vm.signInWithGoogle(r.idToken, r.rawNonce)
                            com.accesspath.ui.auth.GoogleResult.Cancelled -> Unit
                            com.accesspath.ui.auth.GoogleResult.NoAccount -> vm.message(UiMessage.Text(R.string.google_no_account))
                            is com.accesspath.ui.auth.GoogleResult.Failed -> vm.message(UiMessage.Text(R.string.google_failed, listOf(r.message ?: "")))
                        }
                    }
                },
                enabled = !ui.busy, modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.sign_in_google)) }
            OutlinedButton(onClick = { emailDialog = true }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sign_in_email))
            }
        } else {
            Text(account.email ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            StatusPill(stringResource(roleLabel(account.role)), emphasis = account.role.canReview)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.syncNow() }, enabled = !ui.busy) { Text(stringResource(R.string.sync_now)) }
                TextButton(onClick = { vm.signOut() }, enabled = !ui.busy) { Text(stringResource(R.string.sign_out)) }
            }
        }
        InfoText(
            if (ui.syncing) stringResource(R.string.syncing)
            else ui.lastSync?.let { stringResource(R.string.last_sync, formatTime(it)) } ?: stringResource(R.string.not_synced)
        )
    }
    if (emailDialog) EmailDialog(onDismiss = { emailDialog = false }) { email, password, create ->
        emailDialog = false
        if (create) vm.signUpWithEmail(email, password) else vm.signInWithEmail(email, password)
    }
}

@Composable
private fun EmailDialog(onDismiss: () -> Unit, onSubmit: (String, String, Boolean) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var create by rememberSaveable { mutableStateOf(false) }
    val valid = email.contains('@') && password.length >= 6
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (create) R.string.create_account else R.string.sign_in_email)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    email, { email = it.trim().take(120) }, label = { Text(stringResource(R.string.email)) }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    password, { password = it.take(72) }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    supportingText = { Text(stringResource(R.string.password_hint)) }, modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { create = !create }) { Text(stringResource(if (create) R.string.have_account else R.string.create_account)) }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = { onSubmit(email, password, create) }) {
                Text(stringResource(if (create) R.string.create_account else R.string.sign_in))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/**
 * Opt-in nearby alerts (geofencing). Shows a plain disclosure first, then asks for each permission in turn:
 * location, "Allow all the time" (Android needs it for alerts while the app is closed) and notifications.
 */
@Composable
private fun NearbyAlertsSetting() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var disclosure by rememberSaveable { mutableStateOf(false) }
    val status = remember(tick) { com.accesspath.geofence.Geofencing.status(context) }
    val count = remember(tick) { com.accesspath.geofence.Geofencing.activeCount(context) }
    fun refresh() = scope.launch {
        if (com.accesspath.geofence.Geofencing.isEnabled(context)) com.accesspath.geofence.Geofencing.setEnabled(context, true)
        tick++
    }
    // Permissions can change in system settings; check again whenever the screen comes back.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) }
    }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) runCatching {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        refresh()
    }
    fun askForeground() {
        val perms = buildList {
            add(android.Manifest.permission.ACCESS_FINE_LOCATION); add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            // Android 10 asks for "all the time" together with location; newer versions need a separate step.
            if (android.os.Build.VERSION.SDK_INT == 29) add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        foreground.launch(perms.toTypedArray())
    }

    SectionCard(stringResource(R.string.geo_setting_title)) {
        val on = status != com.accesspath.geofence.Geofencing.Status.OFF
        Row(
            Modifier.fillMaxWidth().testTag("nearbyAlerts").toggleable(on, role = Role.Switch) { v ->
                if (v) disclosure = true else scope.launch { com.accesspath.geofence.Geofencing.setEnabled(context, false); tick++ }
            }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.geo_setting_switch), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(on, onCheckedChange = null)
        }
        InfoText(stringResource(R.string.geo_setting_body))
        when (status) {
            com.accesspath.geofence.Geofencing.Status.OFF -> Unit
            com.accesspath.geofence.Geofencing.Status.UNAVAILABLE -> Banner(stringResource(R.string.geo_unavailable), warning = true)
            com.accesspath.geofence.Geofencing.Status.NEEDS_LOCATION -> {
                Banner(stringResource(R.string.geo_need_location), warning = true)
                Button(onClick = ::askForeground) { Text(stringResource(R.string.allow_location)) }
            }
            com.accesspath.geofence.Geofencing.Status.NEEDS_BACKGROUND -> {
                Banner(stringResource(R.string.geo_need_background), warning = true)
                Button(onClick = { background.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) { Text(stringResource(R.string.geo_allow_always)) }
            }
            com.accesspath.geofence.Geofencing.Status.NEEDS_NOTIFICATIONS -> {
                Banner(stringResource(R.string.geo_need_notifications), warning = true)
                Button(onClick = ::askForeground) { Text(stringResource(R.string.geo_allow_notifications)) }
            }
            com.accesspath.geofence.Geofencing.Status.NOTHING_TO_WATCH -> InfoText(stringResource(R.string.geo_nothing))
            com.accesspath.geofence.Geofencing.Status.ON -> StatusPill(stringResource(R.string.geo_active, count), emphasis = false)
        }
    }

    if (disclosure) AlertDialog(
        onDismissRequest = { disclosure = false },
        title = { Text(stringResource(R.string.geo_setting_title)) },
        text = { Text(stringResource(R.string.geo_disclosure)) },
        confirmButton = {
            Button(onClick = {
                disclosure = false
                scope.launch { com.accesspath.geofence.Geofencing.setEnabled(context, true); tick++; askForeground() }
            }) { Text(stringResource(R.string.geo_turn_on)) }
        },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text(stringResource(R.string.not_now)) } }
    )
}
