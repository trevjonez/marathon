@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.malinskiy.marathon.execution

import com.malinskiy.marathon.actor.unboundedChannel
import com.malinskiy.marathon.device.Device
import com.malinskiy.marathon.device.DeviceProvider
import com.malinskiy.marathon.device.DeviceStub
import com.malinskiy.marathon.device.preferringNotIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime
import com.malinskiy.marathon.test.Test as MarathonTest

class RemoteTestParsingTest {

    private val tests = listOf(MarathonTest("pkg", "Clazz", "method", emptySet()))

    @Test
    fun `retries on a different device and succeeds`() = runTest {
        val provider = RecordingDeviceProvider(DeviceStub(serialNumber = "A"), DeviceStub(serialNumber = "B"))
        val parser = FakeRemoteTestParser(parsingAttempts = 3, failFirst = 1, result = tests)

        val result = extractRemoteTests(parser, provider, deviceInitializationTimeoutMillis = 600_000)

        result shouldBeEqualTo tests
        parser.extractCalls shouldBeEqualTo 2
        provider.borrowCalls.size shouldBeEqualTo 2
        // the second borrow was told to avoid the serial the first attempt already failed on...
        provider.borrowCalls[1] shouldContain provider.borrowedSerials[0]
        // ...and a different device was chosen
        provider.borrowedSerials[1] shouldNotBeEqualTo provider.borrowedSerials[0]
    }

    @Test
    fun `stops after parsingAttempts and propagates the last failure`() = runTest {
        val provider = RecordingDeviceProvider(DeviceStub(serialNumber = "A"), DeviceStub(serialNumber = "B"))
        val parser = FakeRemoteTestParser(parsingAttempts = 2, failFirst = Int.MAX_VALUE, result = tests)

        var thrown: Throwable? = null
        try {
            extractRemoteTests(parser, provider, deviceInitializationTimeoutMillis = 600_000)
        } catch (e: ParseBoom) {
            thrown = e
        }

        (thrown is ParseBoom) shouldBeEqualTo true
        parser.extractCalls shouldBeEqualTo 2
    }

    @Test
    fun `reuses the only available device when there is no alternative`() = runTest {
        val provider = RecordingDeviceProvider(DeviceStub(serialNumber = "solo"))
        val parser = FakeRemoteTestParser(parsingAttempts = 3, failFirst = 1, result = tests)

        val result = extractRemoteTests(parser, provider, deviceInitializationTimeoutMillis = 600_000)

        result shouldBeEqualTo tests
        provider.borrowedSerials shouldBeEqualTo listOf("solo", "solo")
    }
}

private class ParseBoom : RuntimeException("parse boom")

private class FakeRemoteTestParser(
    override val parsingAttempts: Int,
    private val failFirst: Int,
    private val result: List<MarathonTest>,
) : RemoteTestParser<DeviceProvider> {
    override val parsingRetryDelayMillis: Long = 0
    var extractCalls = 0
        private set

    override suspend fun extract(device: Device): List<MarathonTest> {
        extractCalls++
        if (extractCalls <= failFirst) throw ParseBoom()
        return result
    }
}

/**
 * Deterministic (picks the first eligible device rather than a random one) so tests can assert exactly which device
 * each attempt borrowed. Honors [excludingSerials] via the same [preferringNotIn] the real providers use.
 */
private class RecordingDeviceProvider(vararg devices: DeviceStub) : DeviceProvider {
    private val devices = devices.toList()
    val borrowCalls = mutableListOf<Set<String>>()
    val borrowedSerials = mutableListOf<String>()

    override suspend fun initialize() = Unit
    override suspend fun terminate() = Unit
    override fun subscribe(): Channel<DeviceProvider.DeviceEvent> = unboundedChannel()

    override suspend fun borrow(excludingSerials: Set<String>): Device {
        borrowCalls.add(excludingSerials.toSet())
        val chosen = devices.preferringNotIn(excludingSerials).first()
        borrowedSerials.add(chosen.serialNumber)
        return chosen
    }
}
