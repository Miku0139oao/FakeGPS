package tiger.radio.loggerlibrary

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Small stream helpers. [copyStream] closes the streams only after the whole
 * copy has finished, never inside the read loop.
 */
object IOUtils {

    @JvmStatic
    fun closeQuietly(p: ParcelFileDescriptor?) {
        if (p != null) {
            try {
                p.close()
            } catch (ignored: IOException) {
                // Closing is best effort.
            }
        }
    }

    @JvmStatic
    fun closeQuietly(c: Closeable?) {
        if (c != null) {
            try {
                c.close()
            } catch (ignored: IOException) {
                // Closing is best effort.
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun copyStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        closeWhenDone: Boolean,
        bufferSizeBytes: Int = 1024
    ): Long {
        val buffer = ByteArray(bufferSizeBytes)
        var total = 0L
        try {
            while (true) {
                val read = inputStream.read(buffer, 0, buffer.size)
                if (read == -1) {
                    break
                }
                total += read
                outputStream.write(buffer, 0, read)
            }
        } finally {
            if (closeWhenDone) {
                closeQuietly(inputStream)
                closeQuietly(outputStream)
            }
        }
        return total
    }
}
