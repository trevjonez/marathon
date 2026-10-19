package com.malinskiy.marathon.report.html

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.malinskiy.marathon.analytics.internal.sub.DeviceConnectedEvent
import com.malinskiy.marathon.analytics.internal.sub.DevicePhaseEvent
import com.malinskiy.marathon.analytics.internal.sub.ExecutionReport
import com.malinskiy.marathon.analytics.internal.sub.TestEvent
import com.malinskiy.marathon.analytics.internal.sub.TestParsingEvent
import com.malinskiy.marathon.config.AnalyticsConfiguration
import com.malinskiy.marathon.config.Configuration
import com.malinskiy.marathon.config.vendor.VendorConfiguration
import com.malinskiy.marathon.device.DeviceInfo
import com.malinskiy.marathon.device.DevicePhase
import com.malinskiy.marathon.device.DevicePoolId
import com.malinskiy.marathon.device.NetworkState
import com.malinskiy.marathon.device.OperatingSystem
import com.malinskiy.marathon.execution.TestResult
import com.malinskiy.marathon.execution.TestStatus
import com.malinskiy.marathon.io.FileManager
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import com.malinskiy.marathon.test.Test as MarathonTest

class TestParsingLogPageTest {

    @TempDir
    lateinit var outputDir: File

    private val serial = "127.0.0.1:5037:localhost:33233"
    private val safeSerial = "127.0.0.1-5037-localhost-33233"

    private val device = DeviceInfo(
        operatingSystem = OperatingSystem("37"),
        serialNumber = serial,
        model = "sdk_gphone",
        manufacturer = "Google",
        networkState = NetworkState.CONNECTED,
        deviceFeatures = emptyList(),
        healthy = true,
    )

    private fun captureFile(body: String) = File(outputDir, "test-parsing/$safeSerial.log").apply {
        parentFile.mkdirs()
        writeText(body)
    }

    private fun generate(parsing: List<TestParsingEvent>, phases: List<DevicePhaseEvent> = emptyList()) {
        val configuration = Configuration.Builder(name = "parse", outputDir = outputDir).apply {
            vendorConfiguration = VendorConfiguration.StubVendorConfiguration
            analyticsConfiguration = AnalyticsConfiguration.DisabledAnalytics
            analyticsTracking = false
        }.build()
        val test = MarathonTest("com", "example", "t1", emptyList())
        val report = ExecutionReport(
            deviceConnectedEvents = listOf(DeviceConnectedEvent(Instant.now(), DevicePoolId("omni"), device)),
            deviceDisconnectedEvents = emptyList(),
            devicePreparingEvents = emptyList(),
            deviceProviderPreparingEvent = emptyList(),
            testEvents = listOf(
                TestEvent(Instant.now(), DevicePoolId("omni"), device, TestResult(test, device, "b", TestStatus.PASSED, 100, 200), true)
            ),
            testParsingEvents = parsing,
            devicePhaseEvents = phases,
        )
        HtmlSummaryReporter(Gson(), FileManager(0, 255, outputDir), outputDir, configuration).generate(report)
    }

    private fun payload(file: File, global: String): JsonObject {
        val line = file.readText().substringAfter("window.$global = ").substringBefore('\n').trim().trimEnd(';')
        return JsonParser.parseString(line).asJsonObject
    }

    private val page get() = File(outputDir, "html/test-parsing/$safeSerial.html")

    private fun parsingBars() = payload(File(outputDir, "html/index.html"), "timeline").getAsJsonArray("measures")
        .flatMap { it.asJsonObject.getAsJsonArray("data") }
        .map { it.asJsonObject }
        .filter { it.get("metricType").asString == "TEST_PARSING" }

    @Test
    fun `each parsing device gets a log page with its capture inlined`() {
        val log = captureFile("===== attempt =====\n10-18 02:57:54.000: I/TestRunner(1): run started\n")
        generate(listOf(TestParsingEvent(Instant.ofEpochMilli(10), Instant.ofEpochMilli(50), serial, log, failed = false)))

        val logs = payload(page, "logs")
        logs.get("kind").asString shouldBeEqualTo "test-parsing"
        logs.get("display_name").asString shouldBeEqualTo "Test parsing on $serial"
        val attempt = logs.getAsJsonArray("attempts").single().asJsonObject
        attempt.get("status").asString shouldBeEqualTo "passed"
        attempt.get("log_body").asString shouldBeEqualTo log.readText()
        attempt.getAsJsonObject("device").get("model_name").asString shouldBeEqualTo "sdk_gphone"
        // log_path resolves from the page back to the capture file
        File(page.parentFile, attempt.get("log_path").asString).canonicalFile shouldBeEqualTo log.canonicalFile
        // Assets resolve from one level below html/
        page.readText().contains("../app.min.js") shouldBe true
    }

    @Test
    fun `timeline parsing bars link to the page and carry the outcome`() {
        val log = captureFile("x\n")
        generate(
            listOf(
                TestParsingEvent(Instant.ofEpochMilli(10), Instant.ofEpochMilli(50), serial, log, failed = true),
                TestParsingEvent(Instant.ofEpochMilli(60), Instant.ofEpochMilli(90), serial, log, failed = false),
            )
        )

        parsingBars().map { it.get("logHref").asString to it.get("failed").asBoolean } shouldBeEqualTo listOf(
            "test-parsing/$safeSerial.html" to true,
            "test-parsing/$safeSerial.html" to false,
        )
        File(outputDir, "html/" + parsingBars().first().get("logHref").asString).exists() shouldBe true
        // Any failed attempt on the device marks its page failed
        payload(page, "logs").getAsJsonArray("attempts").single().asJsonObject.get("status").asString shouldBeEqualTo "failed"
    }

    @Test
    fun `no capture means no page and no link`() {
        generate(listOf(TestParsingEvent(Instant.ofEpochMilli(10), Instant.ofEpochMilli(50), serial)))
        page.exists() shouldBe false
        parsingBars().single().has("logHref") shouldBe false
    }

    @Test
    fun `a parsing device that never joined a pool still gets a page`() {
        val other = "emulator-5556"
        val log = File(outputDir, "test-parsing/$other.log").apply { parentFile.mkdirs(); writeText("y\n") }
        generate(listOf(TestParsingEvent(Instant.ofEpochMilli(10), Instant.ofEpochMilli(50), other, log)))
        payload(File(outputDir, "html/test-parsing/$other.html"), "logs").getAsJsonArray("attempts").single().asJsonObject
            .getAsJsonObject("device").get("serial").asString shouldBeEqualTo other
    }

    @Test
    fun `install logs get a page per device, linked from the install bars`() {
        val log = File(outputDir, "installs/$safeSerial.log").apply { parentFile.mkdirs(); writeText("===== install com.example =====\n") }
        fun install(pkg: String, start: Long, failed: Boolean) =
            DevicePhaseEvent(Instant.ofEpochMilli(start), Instant.ofEpochMilli(start + 5), serial, DevicePhase.INSTALL, "install $pkg", failed, log)
        generate(
            emptyList(),
            listOf(
                install("com.example", 10, failed = false),
                install("com.example.test", 20, failed = true),
                // Between-test steps carry no log
                DevicePhaseEvent(Instant.ofEpochMilli(30), Instant.ofEpochMilli(31), serial, DevicePhase.BEFORE_RUN, "before run"),
            ),
        )

        val page = File(outputDir, "html/installs/$safeSerial.html")
        val logs = payload(page, "logs")
        logs.get("kind").asString shouldBeEqualTo "install"
        logs.get("display_name").asString shouldBeEqualTo "Installs on $serial"
        val attempt = logs.getAsJsonArray("attempts").single().asJsonObject
        attempt.get("status").asString shouldBeEqualTo "failed"
        attempt.get("log_body").asString shouldBeEqualTo log.readText()
        File(page.parentFile, attempt.get("log_path").asString).canonicalFile shouldBeEqualTo log.canonicalFile

        val phaseBars = payload(File(outputDir, "html/index.html"), "timeline").getAsJsonArray("measures")
            .flatMap { it.asJsonObject.getAsJsonArray("data") }.map { it.asJsonObject }
            .filter { it.get("metricType").asString == "DEVICE_PHASE" }
        phaseBars.map { it.get("testName").asString to it.get("logHref")?.asString } shouldBeEqualTo listOf(
            "install com.example" to "installs/$safeSerial.html",
            "install com.example.test" to "installs/$safeSerial.html",
            "before run" to null,
        )
        File(outputDir, "test-parsing").exists() shouldBe false
    }
}
