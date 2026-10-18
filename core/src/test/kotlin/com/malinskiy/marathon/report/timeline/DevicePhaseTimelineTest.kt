package com.malinskiy.marathon.report.timeline

import com.malinskiy.marathon.analytics.internal.sub.DeviceConnectedEvent
import com.malinskiy.marathon.analytics.internal.sub.DevicePhaseEvent
import com.malinskiy.marathon.analytics.internal.sub.DevicePreparingEvent
import com.malinskiy.marathon.analytics.internal.sub.ExecutionReport
import com.malinskiy.marathon.analytics.internal.sub.TestEvent
import com.malinskiy.marathon.device.DeviceInfo
import com.malinskiy.marathon.device.DevicePhase
import com.malinskiy.marathon.device.DevicePoolId
import com.malinskiy.marathon.device.NetworkState
import com.malinskiy.marathon.device.OperatingSystem
import com.malinskiy.marathon.execution.TestResult
import com.malinskiy.marathon.execution.TestStatus
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.time.Instant
import com.malinskiy.marathon.test.Test as MarathonTest

class DevicePhaseTimelineTest {
    private val device = DeviceInfo(
        operatingSystem = OperatingSystem("37"),
        serialNumber = "emulator-5554",
        model = "sdk",
        manufacturer = "Google",
        networkState = NetworkState.CONNECTED,
        deviceFeatures = emptyList(),
        healthy = true,
    )

    private fun phase(phase: DevicePhase, label: String, start: Long, end: Long, failed: Boolean = false) =
        DevicePhaseEvent(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end), device.serialNumber, phase, label, failed)

    private fun test(start: Long, end: Long) = TestEvent(
        Instant.ofEpochMilli(start),
        DevicePoolId("omni"),
        device,
        TestResult(MarathonTest("com.example", "Suite", "t$start", emptyList()), device, "b$start", TestStatus.PASSED, start, end),
        true,
    )

    private fun generate(phases: List<DevicePhaseEvent>, tests: List<TestEvent> = emptyList()) = TimelineSummaryProvider().generate(
        ExecutionReport(
            deviceConnectedEvents = listOf(DeviceConnectedEvent(Instant.EPOCH, DevicePoolId("omni"), device)),
            deviceDisconnectedEvents = emptyList(),
            devicePreparingEvents = listOf(DevicePreparingEvent(Instant.ofEpochMilli(0), Instant.ofEpochMilli(30), device.serialNumber)),
            deviceProviderPreparingEvent = emptyList(),
            testEvents = tests,
            devicePhaseEvents = phases,
        )
    ).measures.single()

    @Test
    fun `each phase is its own labelled bar, and replaces the coarse prepare bar`() {
        val row = generate(
            listOf(
                phase(DevicePhase.INSTALL, "install com.example", 0, 20),
                phase(DevicePhase.DEVICE_RESET, "reset output directory and logcat", 20, 30),
                phase(DevicePhase.CLEAR_DATA, "pm clear com.example", 30, 35, failed = true),
            )
        )
        row.data.map { listOf(it.metricType, it.testName, it.phase, it.betweenTests, it.failed, it.startDate, it.endDate) } shouldBeEqualTo listOf(
            listOf(MetricType.DEVICE_PHASE, "install com.example", "INSTALL", false, false, 0L, 20L),
            listOf(MetricType.DEVICE_PHASE, "reset output directory and logcat", "DEVICE_RESET", false, false, 20L, 30L),
            listOf(MetricType.DEVICE_PHASE, "pm clear com.example", "CLEAR_DATA", true, true, 30L, 35L),
        )
    }

    @Test
    fun `without phases the prepare bar stays`() {
        generate(emptyList()).data.map { it.metricType } shouldBeEqualTo listOf(MetricType.DEVICE_PREPARE)
    }

    @Test
    fun `average test time ignores device bars`() {
        val row = generate(
            listOf(phase(DevicePhase.INSTALL, "install", 0, 1), phase(DevicePhase.BEFORE_RUN, "before run", 1, 2)),
            listOf(test(10, 110), test(200, 500)),
        )
        row.executionStats.averageTestExecutionTimeMillis shouldBeEqualTo 200L
    }
}
