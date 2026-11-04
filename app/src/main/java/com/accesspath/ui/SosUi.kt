package com.accesspath.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.accesspath.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val SosRed = Color(0xFFD93025)

@Serializable
data class EmergencyContact(val name: String, val phone: String)

/** People to alert in an emergency, kept on this phone only. */
class EmergencyContacts(context: Context) {
    private val prefs = context.getSharedPreferences("accesspath_sos", Context.MODE_PRIVATE)
    private val list = ListSerializer(EmergencyContact.serializer())
    fun all(): List<EmergencyContact> = prefs.getString("contacts", null)?.let { runCatching { Json.decodeFromString(list, it) }.getOrNull() }.orEmpty()
    fun save(v: List<EmergencyContact>) = prefs.edit().putString("contacts", Json.encodeToString(list, v.take(5))).apply()
}

/** Round red SOS button on the map. */
@Composable
fun SosButton(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = SosRed, contentColor = Color.White, shadowElevation = 6.dp, modifier = Modifier.size(52.dp).testTag("sos")) {
        Box(contentAlignment = Alignment.Center) { Text("SOS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
    }
}

/**
 * Emergency help: call the emergency number, send your live location to your emergency contacts by SMS,
 * route to the nearest hospital, and, inside a mapped building, the nearest exit.
 * Nothing is sent without the person pressing send in their own phone or SMS app.
 */
@Composable
fun SosSheet(
    locationText: String?, hasCampus: Boolean, onDismiss: () -> Unit,
    onNearestHospital: () -> Unit, onNearestExit: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { EmergencyContacts(context) }
    var contacts by remember { mutableStateOf(store.all()) }
    var adding by remember { mutableStateOf(false) }
    fun open(i: Intent) = runCatching { context.startActivity(i) }

    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("sosSheet"),
        icon = { Icon(Icons.Filled.Sos, null, tint = SosRed, modifier = Modifier.size(36.dp)) },
        title = { Text(stringResource(R.string.sos_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }, colors = ButtonDefaults.buttonColors(containerColor = SosRed),
                    modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Call, null); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.sos_call), fontWeight = FontWeight.Bold)
                }
                SosRow(Icons.Filled.ShareLocation, stringResource(R.string.sos_send_location),
                    if (contacts.isEmpty()) stringResource(R.string.sos_no_contacts) else contacts.joinToString { it.name }) {
                    val text = context.getString(R.string.sos_message, locationText ?: context.getString(R.string.sos_location_unknown))
                    if (contacts.isEmpty()) open(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                    else open(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + contacts.joinToString(";") { it.phone })).putExtra("sms_body", text))
                }
                SosRow(Icons.Filled.LocalHospital, stringResource(R.string.sos_hospital), stringResource(R.string.sos_hospital_desc), onNearestHospital)
                if (hasCampus) SosRow(Icons.AutoMirrored.Filled.DirectionsWalk, stringResource(R.string.sos_exit), stringResource(R.string.sos_exit_desc), onNearestExit)
                HorizontalDivider()
                Text(stringResource(R.string.sos_contacts), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                contacts.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.name} · ${c.phone}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { contacts = contacts - c; store.save(contacts) }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove)) }
                    }
                }
                if (contacts.size < 5) TextButton(onClick = { adding = true }) { Icon(Icons.Filled.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sos_add_contact)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })

    if (adding) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        val valid = name.isNotBlank() && phone.count { it.isDigit() } >= 6
        AlertDialog(onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.sos_add_contact)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.sos_contact_name)) }, singleLine = true)
                    OutlinedTextField(phone, { phone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' }.take(20) }, label = { Text(stringResource(R.string.sos_contact_phone)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                }
            },
            confirmButton = { Button(enabled = valid, onClick = { contacts = contacts + EmergencyContact(name.trim(), phone.replace(" ", "")); store.save(contacts); adding = false }) { Text(stringResource(R.string.save_place)) } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun SosRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = SosRed); Spacer(Modifier.width(14.dp))
            Column { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
