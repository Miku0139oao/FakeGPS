package tiger.radio.loggerlibrary

import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.util.HashMap

/**
 * Static logging facade over [android.util.Log] with an optional file logger.
 * Release builds of the app configure no file, so coordinates are not persisted.
 */
object Logger {

    @Volatile
    private var logFile: LogFile? = null

    private val logFilesMap = HashMap<String, LogFile>()

    @JvmStatic
    fun isLoggable(tag: String, level: Int): Boolean = Log.isLoggable(tag, level)

    @JvmStatic
    fun v(tag: String, msg: String) {
        Log.v(tag, msg)
        logFile?.v(tag, msg)
    }

    @JvmStatic
    fun v(tag: String, msg: String, t: Throwable?) {
        Log.v(tag, msg, t)
        logFile?.v(tag, msg, t)
    }

    @JvmStatic
    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        logFile?.d(tag, msg)
    }

    @JvmStatic
    fun d(tag: String, msg: String, t: Throwable?) {
        Log.d(tag, msg, t)
        logFile?.d(tag, msg, t)
    }

    @JvmStatic
    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        logFile?.i(tag, msg)
    }

    @JvmStatic
    fun i(tag: String, msg: String, t: Throwable?) {
        Log.i(tag, msg, t)
        logFile?.i(tag, msg, t)
    }

    @JvmStatic
    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        logFile?.w(tag, msg)
    }

    @JvmStatic
    fun w(tag: String, msg: String, t: Throwable?) {
        Log.w(tag, msg, t)
        logFile?.w(tag, msg, t)
    }

    @JvmStatic
    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        logFile?.e(tag, msg)
    }

    @JvmStatic
    fun e(tag: String, msg: String, t: Throwable?) {
        Log.e(tag, msg, t)
        logFile?.e(tag, msg, t)
    }

    @JvmStatic
    fun wtf(tag: String, msg: String) {
        Log.wtf(tag, msg)
        logFile?.wtf(tag, msg)
    }

    @JvmStatic
    fun wtf(tag: String, msg: String, t: Throwable?) {
        Log.wtf(tag, msg, t)
        logFile?.wtf(tag, msg, t)
    }

    @JvmStatic
    fun f(tag: String, msg: String) {
        logFile?.f(tag, msg)
    }

    @JvmStatic
    fun f(tag: String, msg: String, t: Throwable?) {
        logFile?.f(tag, msg, t)
    }

    @JvmStatic
    @Synchronized
    fun dump(writer: PrintWriter) {
        logFile?.let { file ->
            file.dump(writer)
            for (log in logFilesMap.values) {
                log.dump(writer)
            }
        }
    }

    @JvmStatic
    @Synchronized
    fun configure(dir: File?, fileName: String?) {
        if (dir == null || fileName == null) {
            Log.w("Log", "Invalid configuration provided")
        } else {
            try {
                logFile = LogFile("default_logger", dir, fileName)
            } catch (e: IllegalArgumentException) {
                Log.w("Log", "Failed to create log file.")
            }
        }
    }

    @JvmStatic
    @Synchronized
    fun configureLogFile(name: String, dir: File?, fileName: String?) {
        if (dir == null || fileName == null) {
            Log.w("Log", "Invalid configuration provided")
        } else {
            try {
                logFilesMap[name] = LogFile(name, dir, fileName)
            } catch (e: IllegalArgumentException) {
                Log.w("Log", "Failed to create log file.")
            }
        }
    }

    @JvmStatic
    @Synchronized
    fun getLogFile(name: String): LogFile? = logFilesMap[name]
}
