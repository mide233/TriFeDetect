package com.mide.trifedetect

/**
 * 与 Concentration_V2 slave 固件的二进制帧协议。
 *
 * 帧格式: AA 55 LEN CMD PAYLOAD CHK
 *  - LEN = 1 + payload.size
 *  - CHK = LEN ^ CMD ^ PAYLOAD (逐字节 XOR)
 *  - 整帧长度 = 4 + LEN, 最大 32 字节, payload 上限 27 字节
 *  - 多字节整数一律小端
 *
 * 与 slave `app/Protocol.hpp` 保持一致。
 */
object TriFeProtocol {

    const val SOF1 = 0xAA
    const val SOF2 = 0x55
    const val FRAME_MAX = 32
    const val PAYLOAD_MAX = FRAME_MAX - 5

    /** Host -> Device 命令码 */
    object HostCmd {
        const val START = 0x01
        const val CALIBRATION = 0x02
        const val STOP = 0x03
        const val STATUS_QUERY = 0x04
    }

    /** Device -> Host 上报码 */
    object DeviceCmd {
        const val STATE = 0x81
        const val ERROR = 0x82
        const val RESULT = 0x83
        const val CALIBRATION = 0x84
        const val STATUS = 0x85
    }

    /** 设备工作状态 (对应 slave WorkState 枚举值) */
    enum class WorkState(val value: Int) {
        CALIBRATION(2),
        WORKING(3),
        READY(4),
        ERR_TILT(5),
        ERR_OPEN(6),
        ERR_LOW_POWER(7),
        ERR_NO_CONTAINER(8);

        val isError: Boolean
            get() = this != CALIBRATION && this != WORKING && this != READY

        companion object {
            fun from(value: Int): WorkState? = entries.firstOrNull { it.value == value }
        }
    }

    /** 组帧: SOF + LEN + CMD + PAYLOAD + CHK */
    fun buildFrame(cmd: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        require(payload.size <= PAYLOAD_MAX) {
            "payload too large: ${payload.size} > $PAYLOAD_MAX"
        }
        val len = 1 + payload.size
        val frame = ByteArray(4 + len)
        frame[0] = SOF1.toByte()
        frame[1] = SOF2.toByte()
        frame[2] = len.toByte()
        frame[3] = cmd.toByte()
        System.arraycopy(payload, 0, frame, 4, payload.size)

        var chk = 0
        for (i in 2 until 4 + payload.size) chk = chk xor (frame[i].toInt() and 0xFF)
        frame[4 + payload.size] = chk.toByte()
        return frame
    }

    /** 解析后的完整帧 */
    class Frame internal constructor(private val bytes: ByteArray) {
        val cmd: Int get() = bytes[3].toInt() and 0xFF

        /** payload (不含 CMD), 长度 = LEN - 1 */
        val payload: ByteArray
            get() {
                val len = bytes[2].toInt() and 0xFF
                return bytes.copyOfRange(4, 4 + (len - 1))
            }

        /** 原始整帧字节, 便于日志 */
        fun raw(): ByteArray = bytes.copyOf()

        override fun toString(): String =
            bytes.joinToString(" ") { String.format("%02X", it) }
    }

    private fun checksum(b: ByteArray, from: Int, count: Int): Int {
        var chk = 0
        for (i in 0 until count) chk = chk xor (b[from + i].toInt() and 0xFF)
        return chk
    }

    fun i32le(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
                ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or
                ((b[off + 3].toInt() and 0xFF) shl 24)

    fun u16le(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    /**
     * 跨读取块的流式帧解析器。与 slave `BluetoothLink::feedBinary()` 的状态机对等,
     * 可容忍帧被拆分成多次 read 返回; 非帧字节 (AT/ENLOG 文本) 会被自动丢弃。
     */
    class FrameParser {
        private enum class State { IDLE, SOF2, LEN, BODY, CHK }

        private var state = State.IDLE
        private val buf = ByteArray(FRAME_MAX)
        private var idx = 0
        private var bodyLen = 0

        fun feed(data: ByteArray, length: Int = data.size, out: MutableList<Frame>) {
            for (i in 0 until length) {
                val c = data[i].toInt() and 0xFF
                when (state) {
                    State.IDLE ->
                        if (c == SOF1) {
                            buf[0] = c.toByte(); idx = 1; state = State.SOF2
                        }

                    State.SOF2 ->
                        if (c == SOF2) {
                            buf[1] = c.toByte(); idx = 2; state = State.LEN
                        } else {
                            state = State.IDLE
                        }

                    State.LEN -> {
                        buf[2] = c.toByte()
                        if (c <= 0 || c > FRAME_MAX - 4) {
                            state = State.IDLE
                        } else {
                            bodyLen = c; idx = 3; state = State.BODY
                        }
                    }

                    State.BODY -> {
                        buf[idx++] = c.toByte()
                        if (idx >= 3 + bodyLen) state = State.CHK
                    }

                    State.CHK -> {
                        buf[idx] = c.toByte()
                        val expect = checksum(buf, 2, 1 + bodyLen)
                        if (c == expect) out.add(Frame(buf.copyOf(4 + bodyLen)))
                        state = State.IDLE
                    }
                }
            }
        }
    }

    /** 校准值 (rawValue 已换算为小数, uvLightLevel 为原始整数) */
    data class Calibration(val rawValue: Double, val uvLightLevel: Int)

    fun parseCalibration(frame: Frame): Calibration? {
        val p = frame.payload
        if (p.size < 6) return null
        return Calibration(i32le(p, 0) / 1000.0, u16le(p, 4))
    }

    fun parseResult(frame: Frame): Double? {
        val p = frame.payload
        if (p.size < 4) return null
        return i32le(p, 0) / 1000.0
    }

    fun parseState(frame: Frame): WorkState? {
        val p = frame.payload
        if (p.isEmpty()) return null
        return WorkState.from(p[0].toInt() and 0xFF)
    }

    data class DeviceStatus(val workState: WorkState?, val progress: Int, val battery: Int, val bt: Boolean)

    fun parseStatus(frame: Frame): DeviceStatus? {
        val p = frame.payload
        if (p.size < 4) return null
        return DeviceStatus(
            workState = WorkState.from(p[0].toInt() and 0xFF),
            progress = p[1].toInt() and 0xFF,
            battery = p[2].toInt() and 0xFF,
            bt = (p[3].toInt() and 0xFF) != 0
        )
    }
}
