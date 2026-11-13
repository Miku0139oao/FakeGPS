package tiger.radio.loggerlibrary

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord

/**
 * Formats log records as "{date} {level} {tag}: {message}".
 */
class CustomFormatter : Formatter() {

    override fun format(record: LogRecord): String {
        val level = LEVEL_MAP[record.level] ?: ""
        val builder = StringBuilder()
        builder.append(
            SimpleDateFormat("MM-dd HH:mm:ss.SSS ", Locale.US)
                .format(Date(record.millis))
        )
        builder.append(level)
        builder.append(" ")
        builder.append(formatMessage(record)).append("\n")
        record.thrown?.let { throwable ->
            builder.append("Throwable occurred: ")
            val stringWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            builder.append(stringWriter)
        }
        return builder.toString()
    }

    private companion object {
        private val LEVEL_MAP = mapOf(
            Level.FINER to "V",
            Level.FINE to "D",
            Level.INFO to "I",
            Level.WARNING to "W",
            Level.SEVERE to "E"
        )
    }
}
