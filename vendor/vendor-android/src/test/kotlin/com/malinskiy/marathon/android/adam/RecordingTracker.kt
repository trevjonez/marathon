package com.malinskiy.marathon.android.adam

import com.malinskiy.marathon.analytics.internal.pub.Tracker
import com.malinskiy.marathon.device.DeviceInfo
import com.malinskiy.marathon.device.DevicePoolId
import com.malinskiy.marathon.execution.TestResult
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class RecordingTracker : Tracker {
    data class Span(val serialNumber: String, val start: Instant, val finish: Instant)

    val devicePreparing = CopyOnWriteArrayList<Span>()
    val testParsing = CopyOnWriteArrayList<Span>()

    override fun devicePreparing(serialNumber: String, startTime: Instant, finishTime: Instant) {
        devicePreparing.add(Span(serialNumber, startTime, finishTime))
    }

    override fun testParsing(serialNumber: String, startTime: Instant, finishTime: Instant) {
        testParsing.add(Span(serialNumber, startTime, finishTime))
    }

    override fun deviceConnected(poolId: DevicePoolId, device: DeviceInfo) = Unit
    override fun deviceDisconnected(poolId: DevicePoolId, device: DeviceInfo) = Unit
    override fun deviceProviderInit(serialNumber: String, startTime: Instant, finishTime: Instant) = Unit
    override fun test(poolId: DevicePoolId, device: DeviceInfo, testResult: TestResult, final: Boolean) = Unit
}
