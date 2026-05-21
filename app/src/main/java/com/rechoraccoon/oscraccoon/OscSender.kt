package com.rechoraccoon.oscraccoon

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object OscSender {

    private const val VRCHAT_OSC_PORT = 9000
    private const val CHATBOX_ADDRESS = "/chatbox/input"

    fun sendChatboxMessage(message: String, immediate: Boolean = true) {
        Thread {
            try {
                val packet = buildOscMessage(CHATBOX_ADDRESS, message, immediate)
                val socket = DatagramSocket()
                socket.send(
                    DatagramPacket(
                        packet,
                        packet.size,
                        InetAddress.getByName("127.0.0.1"),
                        VRCHAT_OSC_PORT
                    )
                )
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun clearChatbox() {
        sendChatboxMessage("", true)
    }

    /**
     * Builds a raw OSC message packet.
     * OSC format: address string (padded to 4-byte boundary) + type tag string + arguments
     */
    private fun buildOscMessage(address: String, vararg args: Any): ByteArray {
        val buffer = ByteBuffer.allocate(1024)

        // Address
        writeOscString(buffer, address)

        // Type tag string
        val typeTag = StringBuilder(",")
        for (arg in args) {
            when (arg) {
                is String -> typeTag.append('s')
                is Int -> typeTag.append('i')
                is Float -> typeTag.append('f')
                is Boolean -> typeTag.append(if (arg) 'T' else 'F')
            }
        }
        writeOscString(buffer, typeTag.toString())

        // Arguments
        for (arg in args) {
            when (arg) {
                is String -> writeOscString(buffer, arg)
                is Int -> buffer.putInt(arg)
                is Float -> buffer.putFloat(arg)
                is Boolean -> { /* booleans encoded in type tag only */ }
            }
        }

        val result = ByteArray(buffer.position())
        buffer.rewind()
        buffer.get(result)
        return result
    }

    private fun writeOscString(buffer: ByteBuffer, s: String) {
        val bytes = s.toByteArray(StandardCharsets.UTF_8)
        buffer.put(bytes)
        buffer.put(0) // null terminator
        // Pad to 4-byte boundary
        val totalLen = bytes.size + 1
        val padLen = (4 - (totalLen % 4)) % 4
        repeat(padLen) { buffer.put(0) }
    }
}
