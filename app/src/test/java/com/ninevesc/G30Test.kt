package com.ninevesc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class G30Test {
    @Test
    fun setPacketAndReplyRoundTrip() {
        val power = G30.modeFields(5)[1] // drive power, 0-1 shown as %
        assertArrayEquals(
            byteArrayOf(36, 71, 2, 6, 0x3F, 0x33, 0x33, 0x33), // 0.7f big-endian
            G30.set(power, power.snap(70.4) / power.scale),
        )
        val rampUp = G30.riding.first { it.conf }
        assertEquals(3, G30.set(rampUp, 0.2)[2].toInt())

        // A reply like send-state in the lisp builds: 'G' status nvars nconfs, then floats
        val reply = PayloadWriter().u8(36).u8(71).u8(1).u8(2).u8(1)
            .f32Auto(25.0).f32Auto(0.7).f32Auto(-40.0).build()
        val s = G30.parse(reply)!!
        assertEquals(1, s.status)
        assertEquals(0.7, s.raw(power.copy(id = 1))!!, 1e-6)
        assertEquals(-40.0, s.raw(Field(0, "x", conf = true))!!, 0.0)
        assertNull(s.raw(Field(2, "missing")))
        assertNull(G30.parse(reply.copyOf(reply.size - 1)))
    }

    @Test
    fun snapAndFormat() {
        val f = Field(0, "v", 0.3, 1.5, 0.01, "V")
        assertEquals("0.55", f.format(f.snap(0.5512)))
        assertEquals(1.5, f.snap(9.0), 0.0)
        assertEquals("2050", Field(0, "w", 100.0, 10000.0, 50.0).let { it.format(it.snap(2061.0)) })
    }
}
