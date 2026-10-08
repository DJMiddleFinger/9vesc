package com.ninevesc

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color.TRANSPARENT
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.catch

// ScooterHacking Utility look: blue app bar, flat dark page, blue section titles, switch and slider rows.
private val Bar = Color(0xFF3D6EB0)
private val Blue = Color(0xFF4F86C9)
private val Bg = Color(0xFF1B1B1B)
private val Dim = Color(0xFF9A9A9A)
private val Track = Color(0xFF2C3A4D)
private val Square = RoundedCornerShape(3.dp)

class MainActivity : ComponentActivity() {
    private val link: Link by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))
        setContent {
            MaterialTheme(darkColorScheme(primary = Blue, background = Bg, surface = Bg, onSurface = Color(0xFFE4E4E4))) {
                Surface(color = Bg) { App(link) }
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
    val context = LocalContext.current
    val conn by link.transport.state.collectAsStateWithLifecycle()
    val state by link.state.collectAsStateWithLifecycle()
    val message by link.message.collectAsStateWithLifecycle()
    val demo by link.demo.collectAsStateWithLifecycle()
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
    fun connect() {
        val granted = blePermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) openPicker() else askPerms.launch(blePermissions)
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().background(Bar).statusBarsPadding().height(56.dp).padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("9VESC", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (demo) "Demo mode" else when (val c = conn) {
                        is VescBleTransport.State.Connected -> c.name ?: c.address
                        is VescBleTransport.State.Connecting -> "Connecting…"
                        else -> "Not connected"
                    },
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                )
            }
            if (demo || conn is VescBleTransport.State.Connected) {
                TextButton(onClick = link::disconnect) {
                    Text(if (demo) "EXIT DEMO" else "DISCONNECT", color = Color.White, letterSpacing = 1.sp)
                }
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = Bg, contentColor = Blue) {
            listOf("Throttle", "Display", "Advanced").forEachIndexed { i, t ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    selectedContentColor = Blue,
                    unselectedContentColor = Dim,
                    text = { Text(t.uppercase(), letterSpacing = 1.sp, fontSize = 13.sp) },
                )
            }
        }
        message?.let {
            Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(Color(0xFF8A2E2E)).padding(20.dp, 10.dp))
        }

        val s = state
        Column(
            Modifier.fillMaxSize().verticalScroll(key(tab) { rememberScrollState() }).navigationBarsPadding().padding(20.dp, 4.dp, 20.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (s == null) {
                Header("Connect")
                Text(
                    "Connect to your scooter's VESC over Bluetooth. The controller must run lisp/g30_dash_9vesc.lisp from this app's GitHub page.",
                    color = Dim,
                    fontSize = 14.sp,
                )
                ShuButton("Connect", ::connect)
                ShuButton("Demo mode", link::startDemo, outlined = true)
                return@Column
            }
            when (tab) {
                0 -> {
                    G30.modes.forEach { (name, base) -> Section("$name mode", G30.modeFields(base), s, link) }
                    Section("Brake", G30.brake, s, link)
                    Note("Max regen = motor brake current × the mode's regen %, and the battery regen current caps it too.")
                    Section("Riding", G30.riding, s, link)
                }
                1 -> {
                    Section("Dash", G30.display, s, link)
                    Note("Stopped with the brake held, the dash shows cell voltage ×10. Add throttle to see the trip in km.")
                }
                else -> {
                    Note("These are the VESC motor settings. Wrong values can damage the motor, controller or battery. Each change is written to the controller right away.")
                    G30.advanced.forEach { (name, fields) -> Section(name, fields, s, link, numbers = true) }
                }
            }
        }
    }

    if (picking) DevicePicker(link) { picking = false }
}

@Composable
private fun Header(title: String) =
    Text(title, color = Blue, fontSize = 22.sp, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))

@Composable
private fun Note(text: String) = Text(text, color = Dim, fontSize = 12.sp)

@Composable
private fun ShuButton(text: String, onClick: () -> Unit, outlined: Boolean = false) {
    val label: @Composable () -> Unit = { Text(text.uppercase(), letterSpacing = 1.sp, fontSize = 13.sp) }
    val m = Modifier.fillMaxWidth().height(44.dp)
    if (outlined) {
        OutlinedButton(onClick, m, shape = Square, border = BorderStroke(1.dp, Blue), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { label() }
    } else {
        Button(onClick, m, shape = Square, colors = ButtonDefaults.buttonColors(containerColor = Bar, contentColor = Color.White)) { label() }
    }
}

@Composable
private fun Section(title: String, fields: List<Field>, s: G30State, link: Link, numbers: Boolean = false) {
    Header(title)
    for (f in fields) {
        val raw = s.raw(f) ?: continue
        val onSet = { v: Double -> link.set(f, v) }
        when {
            f.options === G30.offOn -> SwitchRow(f, raw, onSet)
            f.options.isNotEmpty() -> ChoiceSlider(f, raw, onSet)
            numbers -> NumberField(f, raw, onSet)
            else -> ValueSlider(f, raw, onSet)
        }
    }
}

@Composable
private fun SwitchRow(f: Field, raw: Double, onSet: (Double) -> Unit) {
    val on = raw > 0
    Row(Modifier.fillMaxWidth().clickable { onSet(if (on) 0.0 else 1.0) }, verticalAlignment = Alignment.CenterVertically) {
        Text(f.label, Modifier.weight(1f), fontSize = 14.sp)
        Switch(
            checked = on,
            onCheckedChange = { onSet(if (it) 1.0 else 0.0) },
            colors = SwitchDefaults.colors(checkedTrackColor = Bar, checkedThumbColor = Color.White, uncheckedTrackColor = Track, uncheckedBorderColor = Track),
        )
    }
}

/** SHU-style row: bold label, value on the right, thin slider underneath (ticks when there are few steps). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderRow(label: String, value: String, pos: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onMove: (Float) -> Unit, onDone: () -> Unit) {
    val colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue, inactiveTrackColor = Track, activeTickColor = Bg, inactiveTickColor = Blue)
    Column {
        Row(Modifier.padding(top = 8.dp)) {
            Text(label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = pos,
            onValueChange = onMove,
            valueRange = range,
            steps = steps,
            onValueChangeFinished = onDone,
            colors = colors,
            thumb = { Box(Modifier.size(20.dp).background(Blue, CircleShape)) },
            track = { SliderDefaults.Track(it, Modifier.height(3.dp), colors = colors, drawStopIndicator = null, thumbTrackGapSize = 0.dp) },
        )
    }
}

@Composable
private fun ValueSlider(f: Field, raw: Double, onSet: (Double) -> Unit) {
    var v by remember(raw) { mutableStateOf((raw * f.scale).coerceIn(f.min, f.max)) }
    val stepCount = ((f.max - f.min) / f.step).toInt() - 1
    SliderRow(
        f.label, "${f.format(v)} ${f.unit}".trim(), v.toFloat(), f.min.toFloat()..f.max.toFloat(),
        steps = if (stepCount in 1..20) stepCount else 0,
        onMove = { v = f.snap(it.toDouble()) },
        onDone = { onSet(v / f.scale) },
    )
}

@Composable
private fun ChoiceSlider(f: Field, raw: Double, onSet: (Double) -> Unit) {
    var i by remember(raw) { mutableIntStateOf(f.options.indexOfFirst { it.first == raw }.coerceAtLeast(0)) }
    SliderRow(
        f.label, f.options[i].second, i.toFloat(), 0f..(f.options.size - 1).toFloat(),
        steps = f.options.size - 2,
        onMove = { i = Math.round(it) },
        onDone = { onSet(f.options[i].first) },
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
        shape = Square,
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
        containerColor = Color(0xFF262626),
        shape = Square,
        confirmButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Blue) } },
        title = { Text("Select your scooter", color = Blue) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (devices.isEmpty() && error == null) Text("Scanning… Close VESC Tool on other phones first.", color = Dim)
                devices.forEach { d ->
                    Column(Modifier.fillMaxWidth().clickable { link.connect(d.address); onDismiss() }.padding(vertical = 10.dp)) {
                        Text(d.name ?: "Unnamed", fontWeight = if (d.likelyVesc) FontWeight.Bold else FontWeight.Normal)
                        Text(d.address, color = Dim, fontSize = 12.sp)
                    }
                }
            }
        },
    )
}
