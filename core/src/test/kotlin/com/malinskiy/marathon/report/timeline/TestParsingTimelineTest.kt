package com.malinskiy.marathon.report.timeline

import com.malinskiy.marathon.analytics.MappingTracker
import com.malinskiy.marathon.analytics.internal.pub.Track
import com.malinskiy.marathon.analytics.internal.sub.DevicePreparingEvent
import com.malinskiy.marathon.analytics.internal.sub.ExecutionReport
import com.malinskiy.marathon.analytics.internal.sub.ExecutionReportGenerator
import com.malinskiy.marathon.analytics.internal.sub.TestParsingEvent
import com.malinskiy.marathon.device.Device
import com.malinskiy.marathon.report.Reporter
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.time.Instant

class TestParsingTimelineTest {

    private fun report(parsing: List<TestParsingEvent>, preparing: List<DevicePreparingEvent> = emptyList()) = ExecutionReport(
        deviceConnectedEvents = emptyList(),
        deviceDisconnectedEvents = emptyList(),
        devicePreparingEvents = preparing,
        deviceProviderPreparingEvent = emptyList(),
        testEvents = emptyList(),
        testParsingEvents = parsing,
    )

    @Test
    fun `parsing gets its own bar on the parsing device's row, separate from its install`() {
        val result = TimelineSummaryProvider().generate(
            report(
                parsing = listOf(TestParsingEvent(Instant.ofEpochMilli(40_000), Instant.ofEpochMilli(65_000), "parser")),
                preparing = listOf(DevicePreparingEvent(Instant.ofEpochMilli(0), Instant.ofEpochMilli(40_000), "parser")),
            )
        )

        val row = result.measures.single()
        row.measure shouldBeEqualTo "parser"
        row.data.map { Triple(it.metricType, it.startDate, it.endDate) } shouldBeEqualTo listOf(
            Triple(MetricType.DEVICE_PREPARE, 0L, 40_000L),
            Triple(MetricType.TEST_PARSING, 40_000L, 65_000L),
        )
    }

    @Test
    fun `every attempt shows up, including ones that moved to another device`() {
        val result = TimelineSummaryProvider().generate(
            report(
                listOf(
                    TestParsingEvent(Instant.ofEpochMilli(0), Instant.ofEpochMilli(10), "a"),
                    TestParsingEvent(Instant.ofEpochMilli(20), Instant.ofEpochMilli(30), "b"),
                )
            )
        )

        result.measures.associate { m -> m.measure to m.data.map { it.metricType } } shouldBeEqualTo mapOf(
            "a" to listOf(MetricType.TEST_PARSING),
            "b" to listOf(MetricType.TEST_PARSING),
        )
    }

    @Test
    fun `tracked parsing reaches reporters, including a failed attempt`() {
        var captured: ExecutionReport? = null
        val generator = ExecutionReportGenerator(listOf(object : Reporter {
            override fun generate(executionReport: ExecutionReport) {
                captured = executionReport
            }
        }))
        val track = Track().add(MappingTracker(generator))
        val device = mock<Device> { on { serialNumber } doReturn "emulator-5554" }

        runBlocking {
            track.trackTestParsing(device, File("ok.log")) { "ok" } shouldBeEqualTo "ok"
            assertThrows<IllegalStateException> { track.trackTestParsing(device, File("boom.log")) { error("boom") } }
        }
        generator.close()

        captured!!.testParsingEvents.map { Triple(it.serialNumber, it.logFile, it.failed) } shouldBeEqualTo listOf(
            Triple("emulator-5554", File("ok.log"), false),
            Triple("emulator-5554", File("boom.log"), true),
        )
    }
}
