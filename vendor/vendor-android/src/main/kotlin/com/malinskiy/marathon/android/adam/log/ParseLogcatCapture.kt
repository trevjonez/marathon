package com.malinskiy.marathon.android.adam.log

import com.malinskiy.marathon.android.logcat.LogcatListener
import com.malinskiy.marathon.android.logcat.LogcatProducer
import java.io.File
import java.io.FileWriter
import java.time.Instant

/**
 * Writes a device's full logcat to [file] while remote test parsing runs on it. Attempts append to the same file under
 * a header, so a device that parses twice (listener override retry) keeps both windows. Warnings and above are also kept
 * in memory so a failure can be summarised in the runner log without opening the file.
 */
class ParseLogcatCapture(
    private val producer: LogcatProducer,
    val file: File,
    private val maxProblemLines: Int = 50,
) : LogcatListener {
    private val writer = file.apply { parentFile.mkdirs() }.let { FileWriter(it, Charsets.UTF_8, true).buffered(DEFAULT_BUFFER) }
    private val problems = ArrayDeque<String>()
    private var closed = false

    fun start(description: String) {
        synchronized(this) {
            writer.appendLine("===== ${Instant.now()} $description =====")
        }
        producer.addLogcatListener(this)
    }

    override suspend fun onMessage(message: LogCatMessage) {
        // Same shape as test logs (AdamAndroidDevice.onLogcat), which the report's logcat viewer parses
        val line = "${message.timestamp} ${message.pid}-${message.tid}/${message.appName} ${message.logLevel.priorityLetter}/${message.tag}: ${message.message}"
        synchronized(this) {
            if (closed) return
            writer.appendLine(line)
            if (message.logLevel.priority >= Log.LogLevel.WARN.priority) {
                if (problems.size == maxProblemLines) problems.removeFirst()
                problems.addLast(line)
            }
        }
    }

    /** The last [maxProblemLines] warning-or-worse lines seen, oldest first. */
    fun problems(): List<String> = synchronized(this) { problems.toList() }

    override fun close() {
        producer.removeLogcatListener(this)
        synchronized(this) {
            if (closed) return
            closed = true
            writer.close()
        }
    }

    private companion object {
        const val DEFAULT_BUFFER = 64 * 1024
    }
}
