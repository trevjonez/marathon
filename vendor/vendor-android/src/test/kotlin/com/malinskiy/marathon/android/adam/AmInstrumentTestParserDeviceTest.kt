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

    private val configuration = TestConfigurationFactory.create()
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

private fun DeviceExpectation.listTests(output: String) {
    session {
        respondOkay()
        expectShell { "am instrument -w -r -e log true com.example.test/androidx.test.runner.AndroidJUnitRunner" }
            .accept()
            .respond(output)
    }
}
