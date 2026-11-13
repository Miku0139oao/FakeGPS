package tiger.radio.loggerlibrary

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IOUtilsCopyStreamTest {

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() {
            closed = true
        }
    }

    private class TrackingOutputStream : ByteArrayOutputStream() {
        var closed = false
        override fun close() {
            closed = true
        }
    }

    @Test
    fun copyStreamCopiesMoreThanOneBuffer() {
        val payload = ByteArray(5000) { index -> (index % 251).toByte() }
        val output = ByteArrayOutputStream()

        val copied = IOUtils.copyStream(ByteArrayInputStream(payload), output, false)

        assertEquals(payload.size.toLong(), copied)
        assertArrayEquals(payload, output.toByteArray())
    }

    @Test
    fun closeWhenDoneClosesStreamsAfterCopy() {
        val input = TrackingInputStream(ByteArray(4096) { it.toByte() })
        val output = TrackingOutputStream()

        IOUtils.copyStream(input, output, true)

        assertTrue(input.closed)
        assertTrue(output.closed)
        assertEquals(4096, output.size())
    }

    @Test
    fun closeWhenDoneFalseLeavesStreamsOpen() {
        val input = TrackingInputStream(byteArrayOf(1, 2, 3))
        val output = TrackingOutputStream()

        IOUtils.copyStream(input, output, false)

        assertFalse(input.closed)
        assertFalse(output.closed)
        assertEquals(-1, input.read())
        output.write(4)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), output.toByteArray())
    }

    @Test
    fun customBufferSizeWorks() {
        val payload = ByteArray(100) { it.toByte() }
        val output = ByteArrayOutputStream()

        val copied = IOUtils.copyStream(ByteArrayInputStream(payload), output, false, 7)

        assertEquals(100L, copied)
        assertArrayEquals(payload, output.toByteArray())
    }
}
