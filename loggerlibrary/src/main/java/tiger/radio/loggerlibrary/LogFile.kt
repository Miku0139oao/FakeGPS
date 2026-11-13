package tiger.radio.loggerlibrary

import android.util.Log
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.PrintWriter
import java.util.logging.FileHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger as JavaLogger

/**
 * A rotating java.util.logging file handler.
 */
class LogFile(name: String, dir: File?, fileName: String?) {

    private val logDir: File
    private val logFileName: String
    private val logRecord = LogRecord(Level.INFO, "")
    private val logger: JavaLogger

    init {
        if (dir == null || !dir.exists() || fileName == null) {
            Log.w("LogFile", "Invalid configuration provided")
            throw IllegalArgumentException()
        }
        logDir = dir
        logFileName = fileName
        val path = File(logDir, logFileName)
        logger = JavaLogger.getLogger(name)
        try {
            val handler: Handler = FileHandler(
                path.absolutePath, LOG_FILE_MAX_SIZE, LOG_FILE_MAX_COUNT, true
            )
            Log.d("LogFile", "path=" + path.absolutePath)
            handler.formatter = CustomFormatter()
            logger.useParentHandlers = false
            logger.addHandler(handler)
            logger.level = Level.ALL
        } catch (e: IOException) {
            Log.e("LogFile", "Exception: ", e)
        }
    }

    fun v(tag: String, msg: String) {
        log(Level.FINEST, tag, msg)
    }

    fun v(tag: String, msg: String, t: Throwable?) {
        Log.v(tag, msg, t)
        log(Level.FINEST, tag, msg, t)
    }

    fun d(tag: String, msg: String) {
        log(Level.FINE, tag, msg)
    }

    fun d(tag: String, msg: String, t: Throwable?) {
        log(Level.FINE, tag, msg, t)
    }

    fun i(tag: String, msg: String) {
        log(Level.INFO, tag, msg)
    }

    fun i(tag: String, msg: String, t: Throwable?) {
        log(Level.INFO, tag, msg, t)
    }

    fun w(tag: String, msg: String) {
        log(Level.WARNING, tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable?) {
        log(Level.WARNING, tag, msg, t)
    }

    fun e(tag: String, msg: String) {
        log(Level.SEVERE, tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable?) {
        log(Level.SEVERE, tag, msg, t)
    }

    fun wtf(tag: String, msg: String) {
        log(Level.SEVERE, tag, msg)
    }

    fun wtf(tag: String, msg: String, t: Throwable?) {
        log(Level.SEVERE, tag, msg, t)
    }

    fun f(tag: String, msg: String) {
        log(Level.INFO, tag, msg)
    }

    fun f(tag: String, msg: String, t: Throwable?) {
        log(Level.INFO, tag, msg, t)
    }

    @Synchronized
    private fun log(level: Level, tag: String, msg: String) {
        logRecord.millis = System.currentTimeMillis()
        logRecord.level = level
        logRecord.message = String.format("%s: %s", tag, msg)
        logRecord.thrown = null
        logger.log(logRecord)
    }

    @Synchronized
    private fun log(level: Level, tag: String, msg: String, t: Throwable?) {
        logRecord.millis = System.currentTimeMillis()
        logRecord.level = level
        logRecord.message = String.format("%s: %s", tag, msg)
        logRecord.thrown = t
        logger.log(logRecord)
    }

    @Synchronized
    fun dump(writer: PrintWriter) {
        for (i in 0 until 2) {
            val file = File(logDir, "$logFileName.$i")
            if (file.exists()) {
                dumpLogFile(file, writer)
            }
        }
    }

    private fun dumpLogFile(file: File, writer: PrintWriter) {
        writer.println()
        writer.println(file.name + ":")
        writer.println()
        try {
            FileReader(file).use { reader ->
                val buffer = CharArray(8192)
                while (true) {
                    val read = reader.read(buffer)
                    if (read == -1) {
                        return
                    }
                    writer.write(buffer, 0, read)
                }
            }
        } catch (e: IOException) {
            Log.w("LogFile", "Exception: ", e)
        }
    }

    companion object {
        const val LOG_FILE_MAX_SIZE = 2 * 1024 * 1024
        const val LOG_FILE_MAX_COUNT = 10
    }
}
