package com.malinskiy.marathon.android.adam

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.server.junit5.AdbClient
import com.malinskiy.adam.server.junit5.AdbServer
import com.malinskiy.adam.server.junit5.AdbTest
import com.malinskiy.adam.server.stub.AndroidDebugBridgeServer
import com.malinskiy.adam.server.stub.dsl.DeviceExpectation
import com.malinskiy.marathon.analytics.internal.pub.Track
import com.malinskiy.marathon.android.AndroidTestBundleIdentifier
import com.malinskiy.marathon.config.vendor.VendorConfiguration
import com.malinskiy.marathon.test.Test as MarathonTest
import com.malinskiy.marathon.android.adam.log.Log
import com.malinskiy.marathon.android.adam.log.LogCatHeader
import com.malinskiy.marathon.android.adam.log.LogCatMessage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.amshove.kluent.shouldContain
import com.malinskiy.marathon.extension.escape
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.io.File

@AdbTest
class AmInstrumentTestParserDeviceTest {
    @AdbClient
    lateinit var client: AndroidDebugBridgeClient

    @AdbServer
    lateinit var server: AndroidDebugBridgeServer

    @TempDir
    lateinit var temp: File

    private val configuration by lazy { TestConfigurationFactory.create(outputDir = temp) }
    private val tracker = RecordingTracker()
    private val track = Track().add(tracker)
    private val device by lazy { TestDeviceFactory.createAdam(client, configuration, mock(), track) }
    private val parser by lazy {
        AmInstrumentTestParser(
            configuration,
            AndroidTestBundleIdentifier(),
            configuration.vendorConfiguration as VendorConfiguration.AndroidConfiguration,
            track,
        )
    }

    private fun parseLog() = File(temp, "test-parsing/${device.serialNumber.escape()}.log")

    @Test
    fun `parsing reuses an install that already happened`() {
        val tests = runBlocking {
            server.multipleSessions {
                serial("emulator-5554") {
                    boot()
                    cleanInstall(temp)
                    // No install expectations between here and the instrumentation: a reinstall would fail the stub
                    listTests(LISTED_TEST)
                }
                features("emulator-5554")
            }

            device.setup()
            device.ensureInstalled(configuration)
            parser.extract(device)
        }

        tests.map { it.copy(metaProperties = emptyList()) } shouldBeEqualTo listOf(MarathonTest("com.example", "FooTest", "bar", emptyList()))
        tracker.devicePreparing.size shouldBeEqualTo 1
        tracker.testParsing.size shouldBeEqualTo 1
    }

    @Test
    fun `parsing installs first when the device has no install yet, and the parse span excludes it`() {
        val tests = runBlocking {
            server.multipleSessions {
                serial("emulator-5554") {
                    boot()
                    cleanInstall(temp)
                    listTests(LISTED_TEST)
                }
                features("emulator-5554")
            }

            device.setup()
            parser.extract(device)
        }

        tests.size shouldBeEqualTo 1
        val install = tracker.devicePreparing.single()
        val parse = tracker.testParsing.single()
        parse.serialNumber shouldBeEqualTo device.serialNumber
        parse.start shouldBeGreaterOrEqualTo install.finish
    }

    @Test
    fun `a failed parse attempt is still recorded`() {
        runBlocking {
            server.multipleSessions {
                serial("emulator-5554") {
                    boot()
                    cleanInstall(temp)
                    // A crash is retried once on the same device without the listener override, then propagates
                    listTests(PROCESS_CRASHED)
                    listTests(PROCESS_CRASHED)
                }
                features("emulator-5554")
            }

            device.setup()
            assertThrows<Exception> { parser.extract(device) }
        }

        tracker.devicePreparing.size shouldBeEqualTo 1
        tracker.testParsing.size shouldBeEqualTo 1
    }

    @Test
    fun `device logcat during the parse is written under test-parsing`() {
        runBlocking {
            server.multipleSessions {
                serial("emulator-5554") {
                    boot()
                    cleanInstall(temp)
                    listTests(LISTED_TEST, delayMillis = 1_000)
                }
                features("emulator-5554")
            }

            device.setup()
            device.onLogcat(logLine(Log.LogLevel.INFO, "Before", "not captured"))
            coroutineScope {
                launch {
                    // Lands while am instrument is still running
                    delay(500)
                    device.onLogcat(logLine(Log.LogLevel.DEBUG, "TestRunner", "run started"))
                }
                parser.extract(device)
            }
            device.onLogcat(logLine(Log.LogLevel.INFO, "After", "not captured"))
        }

        val lines = parseLog().readLines()
        lines.first() shouldContain "test parsing on ${device.serialNumber}"
        lines.drop(1) shouldBeEqualTo listOf("10-18 02:57:54.000 1-1/app D/TestRunner: run started")
        device.logcatListenerCount() shouldBeEqualTo 0
        tracker.testParsing.single().let {
            it.logFile shouldBeEqualTo parseLog()
            it.failed shouldBeEqualTo false
        }
    }

    @Test
    fun `a failed parse keeps its logcat and leaves no listeners behind`() {
        runBlocking {
            server.multipleSessions {
                serial("emulator-5554") {
                    boot()
                    cleanInstall(temp)
                    listTests(PROCESS_CRASHED, delayMillis = 500)
                    listTests(PROCESS_CRASHED)
                }
                features("emulator-5554")
            }

            device.setup()
            coroutineScope {
                launch {
                    delay(250)
                    device.onLogcat(logLine(Log.LogLevel.ERROR, "AndroidRuntime", "FATAL EXCEPTION: main"))
                }
                assertThrows<Exception> { parser.extract(device) }
            }
        }

        parseLog().readText() shouldContain "1-1/app E/AndroidRuntime: FATAL EXCEPTION: main"
        device.logcatListenerCount() shouldBeEqualTo 0
        tracker.testParsing.single().let {
            it.logFile shouldBeEqualTo parseLog()
            it.failed shouldBeEqualTo true
        }
    }
}


private val LISTED_TEST = """
    INSTRUMENTATION_STATUS: class=com.example.FooTest
    INSTRUMENTATION_STATUS: current=1
    INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
    INSTRUMENTATION_STATUS: numtests=1
    INSTRUMENTATION_STATUS: stream=
    INSTRUMENTATION_STATUS: test=bar
    INSTRUMENTATION_STATUS_CODE: 1
    INSTRUMENTATION_STATUS: class=com.example.FooTest
    INSTRUMENTATION_STATUS: current=1
    INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
    INSTRUMENTATION_STATUS: numtests=1
    INSTRUMENTATION_STATUS: stream=.
    INSTRUMENTATION_STATUS: test=bar
    INSTRUMENTATION_STATUS_CODE: 0
    INSTRUMENTATION_RESULT: stream=

    Time: 0.01

    OK (1 test)


    INSTRUMENTATION_CODE: -1
""".trimIndent() + "\n"

private val PROCESS_CRASHED = """
    INSTRUMENTATION_RESULT: shortMsg=Process crashed.
    INSTRUMENTATION_CODE: 0
""".trimIndent() + "\n"

private fun DeviceExpectation.listTests(output: String, delayMillis: Long? = null) {
    session {
        respondOkay()
        expectShell { "am instrument -w -r -e log true com.example.test/androidx.test.runner.AndroidJUnitRunner" }
            .accept()
            .apply { if (delayMillis != null) delay(delayMillis) }
            .respond(output)
    }
}

private fun logLine(level: Log.LogLevel, tag: String, text: String) =
    LogCatMessage(LogCatHeader(level, 1, 1, "app", tag, "10-18 02:57:54.000"), text)

private fun AdamAndroidDevice.logcatListenerCount(): Int =
    AdamAndroidDevice::class.java.getDeclaredField("logcatListeners").apply { isAccessible = true }.get(this)
        .let { (it as Collection<*>).size }
