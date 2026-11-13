package tiger.radio.loggerlibrary

import java.util.logging.Level
import java.util.logging.LogRecord
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFormatterTest {

    @Test
    fun formatsLevelAndMessage() {
        val record = LogRecord(Level.FINE, "hello")
        record.millis = 0L

        val formatted = CustomFormatter().format(record)

        assertTrue(formatted.contains(" D hello"))
        assertTrue(formatted.endsWith("\n"))
    }

    @Test
    fun finestLevelFallsBackToEmptyTag() {
        val record = LogRecord(Level.FINEST, "verbose")
        record.millis = 0L

        val formatted = CustomFormatter().format(record)

        // The legacy level map only contains FINER, so FINEST has no letter.
        assertTrue(formatted.contains("  verbose"))
    }

    @Test
    fun appendsThrowableStackTrace() {
        val record = LogRecord(Level.SEVERE, "boom")
        record.millis = 0L
        record.thrown = IllegalStateException("bad state")

        val formatted = CustomFormatter().format(record)

        assertTrue(formatted.contains("Throwable occurred: "))
        assertTrue(formatted.contains("IllegalStateException: bad state"))
    }
}
