package com.ninevesc

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.catch

class MainActivity : ComponentActivity() {
    private val link: Link by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(darkColorScheme(primary = Color(0xFFFF6A00))) {
                Surface(Modifier.fillMaxSize()) { App(link) }
            }
        }
    }
}

private val blePermissions =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
private fun App(link: Link) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val conn by link.transport.state.collectAsStateWithLifecycle()
    val state by link.state.collectAsStateWithLifecycle()
    val message by link.message.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        picking = link.transport.isEnabled
    }
    fun openPicker() {
        if (link.transport.isEnabled) picking = true else enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { ok -> ok }) openPicker()
    }

    Column(Modifier.safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("9VESC", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    when (val c = conn) {
                        is VescBleTransport.State.Connected -> "Connected · ${c.name ?: c.address}"
                        is VescBleTransport.State.Connecting -> "Connecting…"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (conn is VescBleTransport.State.Connected) {
                TextButton(onClick = link::disconnect) { Text("Disconnect") }
            } else {
                Button(onClick = {
                    val granted = blePermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                    if (granted) openPicker() else askPerms.launch(blePermissions)
                }) { Text("Connect") }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

        TabRow(selectedTabIndex = tab) {
            listOf("Throttle", "Display", "Advanced").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        val s = state
        Column(
            Modifier.fillMaxSize().verticalScroll(key(tab) { rememberScrollState() }).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s == null) {
                Text("Connect to your scooter's VESC over Bluetooth. The controller must run lisp/g30_dash_9vesc.lisp from this app's GitHub page.")
                return@Column
            }
            when (tab) {
                0 -> {
                    G30.modes.forEach { (name, base) -> Section(name, G30.modeFields(base), s, link) }
                    Section("Riding", G30.riding, s, link)
                }
                1 -> {
                    Section("G30 dash", G30.display, s, link)
                    Text(
                        "Stopped with the brake held, the dash shows cell voltage ×10; add throttle for the trip in km.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> {
                    Text(
                        "These are the VESC motor settings. Wrong values can damage the motor, controller or battery. " +
                            "Changes apply right away but are lost at power off until you save them.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    G30.advanced.forEach { (name, fields) -> Section(name, fields, s, link, numbers = true) }
                    Button(onClick = link::storeConf, Modifier.fillMaxWidth()) { Text("Save to controller") }
                }
            }
        }
    }

    if (picking) DevicePicker(link) { picking = false }
}

@Composable
private fun Section(title: String, fields: List<Field>, s: G30State, link: Link, numbers: Boolean = false) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            for (f in fields) {
                val raw = s.raw(f) ?: continue
                val onSet = { v: Double -> link.set(f, v) }
                when {
                    f.options.isNotEmpty() -> ChoiceField(f, raw, onSet)
                    numbers -> NumberField(f, raw, onSet)
                    else -> SliderField(f, raw, onSet)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceField(f: Field, raw: Double, onSet: (Double) -> Unit) {
    Text(f.label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        f.options.forEach { (v, name) ->
            FilterChip(selected = raw == v, onClick = { onSet(v) }, label = { Text(name) })
        }
    }
}

@Composable
private fun SliderField(f: Field, raw: Double, onSet: (Double) -> Unit) {
    var v by remember(raw) { mutableStateOf((raw * f.scale).coerceIn(f.min, f.max)) }
    Row {
        Text(f.label, Modifier.weight(1f))
        Text("${f.format(v)} ${f.unit}", fontWeight = FontWeight.Bold)
    }
    Slider(
        value = v.toFloat(),
        onValueChange = { v = f.snap(it.toDouble()) },
        valueRange = f.min.toFloat()..f.max.toFloat(),
        onValueChangeFinished = { onSet(v / f.scale) },
    )
}

@Composable
private fun NumberField(f: Field, raw: Double, onSet: (Double) -> Unit) {
    var text by remember(raw) { mutableStateOf(f.format(raw * f.scale)) }
    val value = text.toDoubleOrNull()
    val ok = value != null && value in f.min..f.max
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(if (f.unit.isEmpty()) f.label else "${f.label} (${f.unit})") },
        isError = !ok,
        supportingText = { Text("${f.format(f.min)} to ${f.format(f.max)}, tap ✓ on the keyboard to apply") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (ok) onSet(f.snap(value!!) / f.scale) }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DevicePicker(link: Link, onDismiss: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val devices by remember { link.transport.scan().catch { error = it.message } }.collectAsStateWithLifecycle(emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Pick your controller") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (devices.isEmpty() && error == null) Text("Scanning… Close VESC Tool on other phones first.")
                devices.forEach { d ->
                    Text(
                        "${d.name ?: "Unnamed"}  ·  ${d.address}",
                        fontWeight = if (d.likelyVesc) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable { link.connect(d.address); onDismiss() }.padding(vertical = 12.dp),
                    )
                }
            }
        },
    )
}
