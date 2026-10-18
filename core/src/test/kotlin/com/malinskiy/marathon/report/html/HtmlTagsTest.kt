package com.malinskiy.marathon.report.html

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.malinskiy.marathon.analytics.internal.sub.DeviceConnectedEvent
import com.malinskiy.marathon.analytics.internal.sub.ExecutionReport
import com.malinskiy.marathon.analytics.internal.sub.TestEvent
import com.malinskiy.marathon.config.AnalyticsConfiguration
import com.malinskiy.marathon.config.Configuration
import com.malinskiy.marathon.config.OutputConfiguration
import com.malinskiy.marathon.config.TagRule
import com.malinskiy.marathon.config.vendor.VendorConfiguration
import com.malinskiy.marathon.device.DeviceInfo
import com.malinskiy.marathon.device.DevicePoolId
import com.malinskiy.marathon.device.NetworkState
import com.malinskiy.marathon.device.OperatingSystem
import com.malinskiy.marathon.execution.TestResult
import com.malinskiy.marathon.execution.TestStatus
import com.malinskiy.marathon.io.FileManager
import com.malinskiy.marathon.test.MetaProperty
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import com.malinskiy.marathon.test.Test as MarathonTest

class HtmlTagsTest {

    @TempDir
    lateinit var outputDir: File

    private val device = DeviceInfo(
        operatingSystem = OperatingSystem("34"),
        serialNumber = "emulator-5554",
        model = "Pixel 6",
        manufacturer = "Google",
        networkState = NetworkState.CONNECTED,
        deviceFeatures = emptyList(),
        healthy = true,
    )

    private val rules = listOf(
        TagRule("team", "com\\.aetna\\.teams\\..*"),
        TagRule("env", "com\\.aetna\\.test\\.(Mock|QA\\d?|Prod)Test"),
    )

    // Shaped like what TestAnnotationProducer reports: method and class annotations, including JUnit/Kotlin noise
    private val annotations = listOf(
        MetaProperty("com.aetna.teams.Redwood", mapOf("description" to "Claims EOB", "ticket" to "RW-12")),
        MetaProperty("com.aetna.test.MockTest"),
        MetaProperty("org.junit.Test", mapOf("expected" to "class org.junit.Test\$None", "timeout" to "0")),
        MetaProperty("kotlin.Metadata", mapOf("mv" to "[I@bdacb57")),
        // Class-level team that differs from the method-level one
        MetaProperty("com.aetna.teams.Moonlight", mapOf("description" to "", "ticket" to "UNKNOWN")),
        // Same tag again from the class level: de-duplicated, first one's attributes kept
        MetaProperty("com.aetna.teams.Redwood", mapOf("description" to "class level", "ticket" to "RW-1")),
    )

    private fun generate(tags: List<TagRule>, metaProperties: List<MetaProperty> = annotations) {
        val configuration = Configuration.Builder(name = "tags", outputDir = outputDir).apply {
            vendorConfiguration = VendorConfiguration.StubVendorConfiguration
            analyticsConfiguration = AnalyticsConfiguration.DisabledAnalytics
            analyticsTracking = false
            outputConfiguration = OutputConfiguration(tags = tags)
        }.build()
        val test = MarathonTest("com", "example", "tagged", metaProperties)
        val report = ExecutionReport(
            deviceConnectedEvents = listOf(DeviceConnectedEvent(Instant.now(), DevicePoolId("myPool"), device)),
            deviceDisconnectedEvents = emptyList(),
            devicePreparingEvents = emptyList(),
            deviceProviderPreparingEvent = emptyList(),
            testEvents = listOf(
                TestEvent(Instant.now(), DevicePoolId("myPool"), device, TestResult(test, device, "b", TestStatus.PASSED, 1, 2), true)
            ),
        )
        HtmlSummaryReporter(Gson(), FileManager(0, 255, outputDir), outputDir, configuration).generate(report)
    }

    private fun payload(path: String, global: String): JsonObject {
        val line = File(outputDir, path).readText().substringAfter("window.$global = ").substringBefore('\n').trim().trimEnd(';')
        return JsonParser.parseString(line).asJsonObject
    }

    private fun poolTest() = payload("html/pools/myPool.html", "pool").getAsJsonArray("tests")[0].asJsonObject
    private fun indexTest() = payload("html/index.html", "mainData").getAsJsonArray("pools")[0].asJsonObject
        .getAsJsonArray("tests")[0].asJsonObject
    private fun fullTest() = payload("html/pools/myPool/emulator-5554/com.example.tagged.html", "test")

    @Test
    fun `pool listing carries matched tags without attributes`() {
        generate(rules)
        for (test in listOf(poolTest(), indexTest())) {
            Gson().toJson(test.get("tags")) shouldBeEqualTo
                """[{"group":"team","value":"Redwood"},{"group":"env","value":"Mock"},{"group":"team","value":"Moonlight"}]"""
        }
    }

    @Test
    fun `test page carries tags with their non-blank attributes`() {
        generate(rules)
        Gson().toJson(fullTest().get("tags")) shouldBeEqualTo
            """[{"group":"team","value":"Redwood","attributes":{"description":"Claims EOB","ticket":"RW-12"}},""" +
            """{"group":"env","value":"Mock","attributes":{}},""" +
            """{"group":"team","value":"Moonlight","attributes":{"ticket":"UNKNOWN"}}]"""
    }

    @Test
    fun `first matching rule wins when rules overlap`() {
        generate(listOf(TagRule("suite", "com\\.aetna\\.test\\..*")) + rules)
        Gson().toJson(poolTest().get("tags").asJsonArray.map { it.asJsonObject.get("group").asString }) shouldBeEqualTo
            """["team","suite","team"]"""
    }

    @Test
    fun `no rules means no tags`() {
        generate(emptyList())
        poolTest().getAsJsonArray("tags").size() shouldBe 0
        fullTest().getAsJsonArray("tags").size() shouldBe 0
    }

    @Test
    fun `a test without annotations has no tags`() {
        generate(rules, metaProperties = emptyList())
        poolTest().getAsJsonArray("tags").size() shouldBe 0
    }
}
