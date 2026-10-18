package com.malinskiy.marathon.android.adam

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.server.junit5.AdbClient
import com.malinskiy.adam.server.junit5.AdbServer
import com.malinskiy.adam.server.junit5.AdbTest
import com.malinskiy.adam.server.stub.AndroidDebugBridgeServer
import com.malinskiy.adam.server.stub.dsl.DeviceExpectation
import com.malinskiy.marathon.analytics.internal.pub.Track
import com.malinskiy.marathon.android.AndroidTestBundleIdentifier
import com.malinskiy.marathon.android.executor.listeners.NoOpTestRunListener
import com.malinskiy.marathon.android.model.AndroidTestBundle
import com.malinskiy.marathon.config.Configuration
import com.malinskiy.marathon.config.vendor.VendorConfiguration
import com.malinskiy.marathon.device.DevicePhase
import com.malinskiy.marathon.test.TestBatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import com.malinskiy.marathon.test.Test as MarathonTest

@AdbTest
class AndroidDeviceTestRunnerPhaseTest {
    @AdbClient
    lateinit var client: AndroidDebugBridgeClient

    @AdbServer
    lateinit var server: AndroidDebugBridgeServer

    private val tracker = RecordingTracker()
    private val test = MarathonTest("com.example", "FooTest", "bar", emptyList())

    private fun run(configuration: Configuration, instrumentOutput: String, expectations: DeviceExpectation.() -> Unit) {
        val vendor = configuration.vendorConfiguration as VendorConfiguration.AndroidConfiguration
        val bundles = AndroidTestBundleIdentifier().apply {
            put(test, AndroidTestBundle(vendor.applicationOutput, vendor.testApplicationOutput!!, null, null))
        }
        val device = TestDeviceFactory.createAdam(client, configuration, mock(), Track().add(tracker))
        runBlocking {
            server.multipleSessions {
                serial("emulator-5554") {
                    boot()
                    expectations()
                    session {
                        respondOkay()
                        expectShell { "am instrument -w -r --no-window-animation -e class com.example.FooTest#bar com.example.test/androidx.test.runner.AndroidJUnitRunner" }
                            .accept()
                            .apply { delay(300) }
                            .respond(instrumentOutput)
                    }
                }
                features("emulator-5554")
            }
            device.setup()
            AndroidDeviceTestRunner(device, bundles).execute(configuration, TestBatch(listOf(test)), NoOpTestRunListener())
        }
    }

    @Test
    fun `a batch reports its between-test steps in order`() {
        run(TestConfigurationFactory.create(applicationPmClear = true, testApplicationPmClear = true), PASSING_RUN) {
            shell("pm clear com.example", "Success")
            shell("pm clear com.example.test", "Success")
        }

        tracker.phases.map { it.phase to it.label } shouldBeEqualTo listOf(
            DevicePhase.CLEAR_DATA to "pm clear com.example",
            DevicePhase.CLEAR_DATA to "pm clear com.example.test",
            DevicePhase.BEFORE_RUN to "before run",
            DevicePhase.INSTRUMENTATION_START to "start instrumentation",
            DevicePhase.AFTER_RUN to "after run",
        )
        tracker.phases.none { it.failed } shouldBeEqualTo true
        tracker.phases.zipWithNext().forEach { (a, b) -> b.start shouldBeGreaterOrEqualTo a.finish }
        // Covers the stubbed process startup delay
        val startup = tracker.phases.single { it.phase == DevicePhase.INSTRUMENTATION_START }
        (startup.finish.toEpochMilli() - startup.start.toEpochMilli()) shouldBeGreaterOrEqualTo 300L
    }

    @Test
    fun `no pm clear phases when clearing is off`() {
        run(TestConfigurationFactory.create(), PASSING_RUN) {}
        tracker.phases.map { it.phase } shouldBeEqualTo listOf(DevicePhase.BEFORE_RUN, DevicePhase.INSTRUMENTATION_START, DevicePhase.AFTER_RUN)
    }

    @Test
    fun `a run that never starts a test records a failed instrumentation start`() {
        run(TestConfigurationFactory.create(), CRASHED_RUN) {}
        tracker.phases.single { it.phase == DevicePhase.INSTRUMENTATION_START }.failed shouldBeEqualTo true
    }
}

private val PASSING_RUN = """
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

private val CRASHED_RUN = """
    INSTRUMENTATION_RESULT: shortMsg=Process crashed.
    INSTRUMENTATION_CODE: 0
""".trimIndent() + "\n"
