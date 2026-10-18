package com.malinskiy.marathon.analytics.internal.pub

import com.malinskiy.marathon.device.DeviceInfo
import com.malinskiy.marathon.device.DevicePoolId
import com.malinskiy.marathon.execution.TestResult
import java.io.File
import java.time.Instant

/**
 * Interface for reporting events
 */
interface Tracker {
    fun deviceConnected(poolId: DevicePoolId, device: DeviceInfo)
    fun deviceDisconnected(poolId: DevicePoolId, device: DeviceInfo)
    fun deviceProviderInit(serialNumber: String, startTime: Instant, finishTime: Instant)
    fun devicePreparing(serialNumber: String, startTime: Instant, finishTime: Instant)

    /**
     * On-device test discovery by a remote test parser, one call per attempt (including failed ones). [logFile] is the
     * device log captured for the attempt, when the parser keeps one.
     */
    fun testParsing(serialNumber: String, startTime: Instant, finishTime: Instant, logFile: File? = null, failed: Boolean = false) = Unit

    /**
     * @param final signals if the test execution status is final, i.e. no more retries will happen
     *              reporters such as jUnit should handle this as an indication to save report
     */
    fun test(poolId: DevicePoolId, device: DeviceInfo, testResult: TestResult, final: Boolean)
}
