package com.malinskiy.marathon.android

import com.malinskiy.marathon.android.adam.AmInstrumentTestParser
import com.malinskiy.marathon.config.Configuration
import com.malinskiy.marathon.config.vendor.VendorConfiguration
import com.malinskiy.marathon.config.vendor.android.TestParserConfiguration
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.io.File

class AmInstrumentTestParserTest {

    private fun parserWith(testParserConfiguration: TestParserConfiguration) = AmInstrumentTestParser(
        configuration = mock<Configuration>(),
        testBundleIdentifier = AndroidTestBundleIdentifier(),
        vendorConfiguration = VendorConfiguration.AndroidConfiguration(
            androidSdk = File(""),
            applicationOutput = File(""),
            testApplicationOutput = File(""),
            splitApks = emptyList(),
            extraApplicationsOutput = emptyList(),
            testParserConfiguration = testParserConfiguration,
        ),
    )

    @Test
    fun `parsingAttempts and delay come from the remote parser configuration`() {
        val parser = parserWith(
            TestParserConfiguration.RemoteTestParserConfiguration(parsingAttempts = 5, parsingRetryDelayMillis = 2000)
        )
        parser.parsingAttempts shouldBeEqualTo 5
        parser.parsingRetryDelayMillis shouldBeEqualTo 2000L
    }

    @Test
    fun `parsingAttempts is coerced to at least one`() {
        val parser = parserWith(TestParserConfiguration.RemoteTestParserConfiguration(parsingAttempts = 0))
        parser.parsingAttempts shouldBeEqualTo 1
    }

    @Test
    fun `defaults apply when the parser configuration is not remote`() {
        val parser = parserWith(TestParserConfiguration.LocalTestParserConfiguration)
        parser.parsingAttempts shouldBeEqualTo 3
        parser.parsingRetryDelayMillis shouldBeEqualTo 0L
    }
}
