package com.ninevesc

import java.util.Locale
import kotlin.math.roundToLong

/**
 * One setting of lisp/g30_dash_9vesc.lisp. [id] is its index in the script's `vars` list, or in
 * `confs` when [conf] (a VESC config param). [min]/[max]/[step] are in display units = raw * [scale].
 */
data class Field(
    val id: Int,
    val label: String,
    val min: Double = 0.0,
    val max: Double = 1.0,
    val step: Double = 1.0,
    val unit: String = "",
    val scale: Double = 1.0,
    val conf: Boolean = false,
    val options: List<Pair<Double, String>> = emptyList(),
) {
    private val decimals = step.toBigDecimal().stripTrailingZeros().scale().coerceAtLeast(0)

    fun snap(display: Double): Double =
        ((display - min) / step).roundToLong().times(step).plus(min).coerceIn(min, max)

    fun format(display: Double): String = String.format(Locale.US, "%.${decimals}f", display)
}

/** Script state from one reply: [status] 0 ok, 1 refused while moving, 2 bad request. */
data class G30State(val status: Int, val vars: List<Double>, val confs: List<Double>) {
    fun raw(f: Field): Double? = (if (f.conf) confs else vars).getOrNull(f.id)
}

object G30 {
    private const val CUSTOM_APP_DATA = 36
    private const val MAGIC = 71 // 'G'

    fun read(): ByteArray = byteArrayOf(CUSTOM_APP_DATA.toByte(), MAGIC.toByte(), 1)
    fun storeConf(): ByteArray = byteArrayOf(CUSTOM_APP_DATA.toByte(), MAGIC.toByte(), 4)
    fun set(f: Field, raw: Double): ByteArray =
        PayloadWriter().u8(CUSTOM_APP_DATA).u8(MAGIC).u8(if (f.conf) 3 else 2).u8(f.id).f32Auto(raw).build()

    fun parse(p: ByteArray): G30State? {
        if (p.size < 5 || p[0].toInt() != CUSTOM_APP_DATA || p[1].toInt() != MAGIC) return null
        val r = PayloadReader(p, 2)
        val status = r.u8()
        val nv = r.u8()
        val nc = r.u8()
        if (r.remaining < 4 * (nv + nc)) return null
        return G30State(status, List(nv) { r.f32Auto().toDouble() }, List(nc) { r.f32Auto().toDouble() })
    }

    val modes = listOf("Eco" to 0, "Drive" to 5, "Sport" to 10)
    fun modeFields(base: Int) = listOf(
        Field(base, "Top speed", 1.0, 99.0, unit = "km/h"),
        Field(base + 1, "Motor power", 5.0, 100.0, unit = "%", scale = 100.0),
        Field(base + 2, "Max watts", 100.0, 10000.0, 50.0, "W"),
        Field(base + 3, "Field weakening", 0.0, 60.0, unit = "A"),
        Field(base + 4, "Regen braking", 0.0, 100.0, unit = "%", scale = 100.0),
    )

    private val offOn = listOf(0.0 to "Off", 1.0 to "On")
    private val dashValues = listOf(
        "Speed", "Battery %", "Controller °C", "Motor °C", "Battery A", "Motor A",
        "Power ×100 W", "Cell V ×10", "Trip km", "Duty %", "Battery V",
    ).mapIndexed { i, s -> i.toDouble() to s }

    val riding = listOf(
        Field(25, "Mode at power on", options = listOf(2.0 to "Eco", 1.0 to "Drive", 4.0 to "Sport")),
        Field(15, "Start speed (kick to start)", 0.0, 10.0, unit = "km/h"),
        Field(24, "Throttle curve (1 = linear, higher = softer start)", 0.5, 3.0, 0.1),
        Field(15, "Throttle ramp up", 0.0, 2.0, 0.05, "s", conf = true),
        Field(16, "Throttle ramp down", 0.0, 2.0, 0.05, "s", conf = true),
        Field(16, "Cruise control", options = offOn),
        Field(17, "Cruise after holding throttle", 2.0, 15.0, unit = "s"),
        Field(18, "Flash brake light", options = offOn),
        Field(19, "Auto power off (0 = never)", 0.0, 30.0, unit = "min"),
    )

    val calibration = listOf(
        Field(22, "Throttle dead zone", 0.3, 1.5, 0.01, "V"),
        Field(23, "Brake dead zone", 0.3, 1.5, 0.01, "V"),
    )

    val display = listOf(
        Field(26, "While riding", options = dashValues),
        Field(27, "When stopped", options = dashValues),
        Field(28, "Small red digits", options = listOf(0.0 to "Off", 1.0 to "Controller °C", 2.0 to "Motor °C")),
        Field(29, "Speed unit", options = listOf(0.0 to "km/h", 1.0 to "mph")),
        Field(20, "Temp icon: controller above", 40.0, 110.0, unit = "°C"),
        Field(21, "Temp icon: motor above", 40.0, 150.0, unit = "°C"),
    )

    val advanced = listOf(
        "Current" to listOf(
            Field(0, "Motor current max", 1.0, 300.0, 0.5, "A", conf = true),
            Field(1, "Motor current brake", -300.0, 0.0, 0.5, "A", conf = true),
            Field(2, "Battery current max", 1.0, 250.0, 0.5, "A", conf = true),
            Field(3, "Battery current regen", -250.0, 0.0, 0.5, "A", conf = true),
            Field(4, "Absolute max current", 10.0, 400.0, 1.0, "A", conf = true),
        ),
        "Speed & duty" to listOf(
            Field(5, "Max ERPM", 1000.0, 200000.0, 100.0, conf = true),
            Field(6, "Max reverse ERPM", -200000.0, 0.0, 100.0, conf = true),
            Field(7, "Max duty cycle", 50.0, 99.0, 0.5, "%", 100.0, conf = true),
            Field(14, "Field weakening from duty", 50.0, 99.0, 0.5, "%", 100.0, conf = true),
        ),
        "Voltage" to listOf(
            Field(8, "Min input voltage", 8.0, 120.0, 0.1, "V", conf = true),
            Field(9, "Max input voltage", 8.0, 120.0, 0.1, "V", conf = true),
            Field(10, "Battery cutoff start", 8.0, 120.0, 0.1, "V", conf = true),
            Field(11, "Battery cutoff end", 8.0, 120.0, 0.1, "V", conf = true),
        ),
        "Motor temperature" to listOf(
            Field(12, "Limit start", 40.0, 150.0, 1.0, "°C", conf = true),
            Field(13, "Limit end", 40.0, 160.0, 1.0, "°C", conf = true),
        ),
        "Scooter" to listOf(
            Field(17, "Wheel diameter", 100.0, 600.0, 1.0, "mm", 1000.0, conf = true),
            Field(18, "Motor poles", 2.0, 60.0, 2.0, conf = true),
            Field(19, "Battery cells in series", 6.0, 30.0, 1.0, conf = true),
        ),
        "Input calibration" to calibration,
    )
}
