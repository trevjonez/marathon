package com.malinskiy.marathon.android.adam.log

import com.malinskiy.marathon.android.logcat.LogcatListener
import com.malinskiy.marathon.android.logcat.LogcatProducer
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ParseLogcatCaptureTest {
    @TempDir
    lateinit var temp: File

    private val producer = object : LogcatProducer {
        val listeners = mutableListOf<LogcatListener>()
        override fun addLogcatListener(listener: LogcatListener) {
            listeners.add(listener)
        }

        override fun removeLogcatListener(listener: LogcatListener) {
            listeners.remove(listener)
        }

        fun emit(level: Log.LogLevel, tag: String, text: String) = runBlocking {
            listeners.toList().forEach { it.onMessage(LogCatMessage(LogCatHeader(level, 42, 43, "app", tag, "10-18 02:57:54.000"), text)) }
        }
    }

    private val file get() = File(temp, "test-parsing/emulator-5554.log")

    @Test
    fun `writes every line between start and close, under a header, into a new directory`() {
        val capture = ParseLogcatCapture(producer, file)
        producer.emit(Log.LogLevel.INFO, "Before", "not captured")
        capture.start("attempt one")
        producer.emit(Log.LogLevel.DEBUG, "TestRunner", "started")
        producer.emit(Log.LogLevel.ERROR, "AndroidRuntime", "FATAL EXCEPTION: main")
        capture.close()
        producer.emit(Log.LogLevel.INFO, "After", "not captured")

        val lines = file.readLines()
        lines.size shouldBeEqualTo 3
        lines[0] shouldContain "===== "
        lines[0] shouldContain "attempt one"
        lines[1] shouldBeEqualTo "10-18 02:57:54.000 42-43/app D/TestRunner: started"
        lines[2] shouldBeEqualTo "10-18 02:57:54.000 42-43/app E/AndroidRuntime: FATAL EXCEPTION: main"
        producer.listeners.size shouldBeEqualTo 0
    }

    @Test
    fun `keeps only the last warnings and errors for the failure summary`() {
        val capture = ParseLogcatCapture(producer, file, maxProblemLines = 2)
        capture.start("attempt")
        producer.emit(Log.LogLevel.WARN, "A", "one")
        producer.emit(Log.LogLevel.INFO, "B", "ignored")
        producer.emit(Log.LogLevel.ERROR, "C", "two")
        producer.emit(Log.LogLevel.ASSERT, "D", "three")
        capture.problems().map { it.substringAfter("/app ") } shouldBeEqualTo listOf("E/C: two", "A/D: three")
        capture.close()
    }

    @Test
    fun `a later attempt on the same device appends`() {
        ParseLogcatCapture(producer, file).apply { start("first"); producer.emit(Log.LogLevel.INFO, "T", "a"); close() }
        ParseLogcatCapture(producer, file).apply { start("second"); producer.emit(Log.LogLevel.INFO, "T", "b"); close() }
        file.readLines().filter { it.startsWith("=====") }.map { it.substringAfterLast(' ', "").let { _ -> it.contains("first") to it.contains("second") } } shouldBeEqualTo
            listOf(true to false, false to true)
        file.readLines().filterNot { it.startsWith("=====") }.map { it.substringAfterLast(": ") } shouldBeEqualTo listOf("a", "b")
    }

    @Test
    fun `closing twice is harmless`() {
        val capture = ParseLogcatCapture(producer, file)
        capture.start("attempt")
        capture.close()
        capture.close()
        producer.listeners.size shouldBeEqualTo 0
    }
}
