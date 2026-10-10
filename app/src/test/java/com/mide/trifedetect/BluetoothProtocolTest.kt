package com.mide.trifedetect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothProtocolTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun hostCommandsMatchFirmware() {
        assertEquals("AA 55 01 01 00", hex(TriFeProtocol.buildFrame(TriFeProtocol.HostCmd.START)))
        assertEquals("AA 55 01 02 03", hex(TriFeProtocol.buildFrame(TriFeProtocol.HostCmd.CALIBRATION)))
        assertEquals("AA 55 01 03 02", hex(TriFeProtocol.buildFrame(TriFeProtocol.HostCmd.STOP)))
        assertEquals("AA 55 01 04 05", hex(TriFeProtocol.buildFrame(TriFeProtocol.HostCmd.STATUS_QUERY)))
    }

    @Test
    fun parserHandlesJunkAndMultipleFrames() {
        val parser = TriFeProtocol.FrameParser()
        val out = ArrayList<TriFeProtocol.Frame>()
        // 前面混入 AT/ENLOG 文本, 之后两帧
        val stream = bytes(0x2B, 0x43, 0x4F, 0x4E, 0x4E, 0x0D, 0x0A) +
                TriFeProtocol.buildFrame(TriFeProtocol.HostCmd.START) +
                TriFeProtocol.buildFrame(TriFeProtocol.HostCmd.STOP)
        parser.feed(stream, stream.size, out)
        assertEquals(2, out.size)
        assertEquals(TriFeProtocol.HostCmd.START, out[0].cmd)
        assertEquals(TriFeProtocol.HostCmd.STOP, out[1].cmd)
    }

    @Test
    fun parserHandlesSplitFrames() {
        val parser = TriFeProtocol.FrameParser()
        val out = ArrayList<TriFeProtocol.Frame>()
        val frame = TriFeProtocol.buildFrame(TriFeProtocol.HostCmd.STATUS_QUERY)
        parser.feed(frame, 3, out)
        assertTrue(out.isEmpty())
        parser.feed(frame.copyOfRange(3, frame.size), frame.size - 3, out)
        assertEquals(1, out.size)
        assertEquals(TriFeProtocol.HostCmd.STATUS_QUERY, out[0].cmd)
    }

    @Test
    fun parserRejectsBadChecksum() {
        val parser = TriFeProtocol.FrameParser()
        val out = ArrayList<TriFeProtocol.Frame>()
        val bad = bytes(0xAA, 0x55, 0x01, 0x01, 0x7F) // 校验应为 0x00
        parser.feed(bad, bad.size, out)
        assertTrue(out.isEmpty())
    }

    @Test
    fun parseResultMilli() {
        val payload = bytes(0xD2, 0x04, 0x00, 0x00) // 1234
        val frame = TriFeProtocol.buildFrame(TriFeProtocol.DeviceCmd.RESULT, payload)
        val parser = TriFeProtocol.FrameParser()
        val out = ArrayList<TriFeProtocol.Frame>()
        parser.feed(frame, frame.size, out)
        assertEquals(1, out.size)
        assertEquals(1.234, TriFeProtocol.parseResult(out[0])!!, 1e-9)
    }

    @Test
    fun parseCalibration() {
        val payload = bytes(0x2C, 0x01, 0x00, 0x00, 0x46, 0x00) // 300 -> 0.3, uv 70
        val frame = TriFeProtocol.buildFrame(TriFeProtocol.DeviceCmd.CALIBRATION, payload)
        val parser = TriFeProtocol.FrameParser()
        val out = ArrayList<TriFeProtocol.Frame>()
        parser.feed(frame, frame.size, out)
        val c = TriFeProtocol.parseCalibration(out[0])
        assertNotNull(c)
        assertEquals(0.3, c!!.rawValue, 1e-9)
        assertEquals(70, c.uvLightLevel)
    }

    @Test
    fun parseWorkStateAndStatus() {
        val parser = TriFeProtocol.FrameParser()
        val out = ArrayList<TriFeProtocol.Frame>()

        val errState = TriFeProtocol.buildFrame(
            TriFeProtocol.DeviceCmd.ERROR,
            bytes(TriFeProtocol.WorkState.ERR_LOW_POWER.value)
        )
        parser.feed(errState, errState.size, out)
        val st = TriFeProtocol.parseState(out[0])
        assertEquals(TriFeProtocol.WorkState.ERR_LOW_POWER, st)
        assertTrue(st!!.isError)

        out.clear()
        val status = TriFeProtocol.buildFrame(
            TriFeProtocol.DeviceCmd.STATUS,
            bytes(TriFeProtocol.WorkState.WORKING.value, 50, 88, 1)
        )
        parser.feed(status, status.size, out)
        val s = TriFeProtocol.parseStatus(out[0])!!
        assertEquals(TriFeProtocol.WorkState.WORKING, s.workState)
        assertEquals(50, s.progress)
        assertEquals(88, s.battery)
        assertTrue(s.bt)
        assertFalse(TriFeProtocol.WorkState.READY.isError)
    }

    private fun hex(b: ByteArray) = b.joinToString(" ") { String.format("%02X", it) }
}
