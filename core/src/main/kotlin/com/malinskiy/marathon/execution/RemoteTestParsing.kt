package com.malinskiy.marathon.execution

import com.malinskiy.marathon.device.DeviceProvider
import com.malinskiy.marathon.exceptions.NoDevicesException
import com.malinskiy.marathon.log.MarathonLogging
import com.malinskiy.marathon.test.Test
import kotlinx.coroutines.withTimeoutOrNull

private val log = MarathonLogging.logger {}

/**
 * Runs on-device test discovery with retries. Each attempt borrows a device, preferring one not used by a prior failed
 * attempt (see [DeviceProvider.borrow]), so a device that appears online but is unusable doesn't fail the whole run when
 * other devices are available. Retries up to [RemoteTestParser.parsingAttempts] times, sleeping
 * [RemoteTestParser.parsingRetryDelayMillis] between attempts.
 *
 * The whole operation is bounded by [deviceInitializationTimeoutMillis]; exceeding it throws [NoDevicesException].
 * If every attempt fails within the deadline, the last failure propagates.
 */
internal suspend fun extractRemoteTests(
    testParser: RemoteTestParser<*>,
    deviceProvider: DeviceProvider,
    deviceInitializationTimeoutMillis: Long,
): List<Test> {
    return withTimeoutOrNull(deviceInitializationTimeoutMillis) {
        val triedSerials = mutableSetOf<String>()
        withRetry(testParser.parsingAttempts.coerceAtLeast(1), testParser.parsingRetryDelayMillis.coerceAtLeast(0)) {
            val borrowedDevice = deviceProvider.borrow(excludingSerials = triedSerials)
            triedSerials.add(borrowedDevice.serialNumber)
            try {
                testParser.extract(borrowedDevice)
            } catch (e: Throwable) {
                log.warn(e) { "Test parsing failed on ${borrowedDevice.serialNumber}; retrying on a different device if available" }
                throw e
            }
        }
    } ?: throw NoDevicesException("Timed out waiting for a temporary device for remote test parsing")
}
