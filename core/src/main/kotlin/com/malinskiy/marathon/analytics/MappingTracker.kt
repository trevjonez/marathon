package com.malinskiy.marathon.analytics

import com.malinskiy.marathon.analytics.internal.pub.Tracker
import com.malinskiy.marathon.analytics.internal.sub.DeviceConnectedEvent
import com.malinskiy.marathon.analytics.internal.sub.DeviceDisconnectedEvent
import com.malinskiy.marathon.analytics.internal.sub.DevicePhaseEvent
import com.malinskiy.marathon.analytics.internal.sub.DevicePreparingEvent
import com.malinskiy.marathon.analytics.internal.sub.DeviceProviderPreparingEvent
import com.malinskiy.marathon.analytics.internal.sub.TestEvent
import com.malinskiy.marathon.analytics.internal.sub.TestParsingEvent
import com.malinskiy.marathon.analytics.internal.sub.TrackerInternal
import com.malinskiy.marathon.device.DeviceInfo
import com.malinskiy.marathon.device.DevicePhase
import com.malinskiy.marathon.device.DevicePoolId
import com.malinskiy.marathon.execution.TestResult
import java.io.File
import java.time.Instant

class MappingTracker(private val trackerInternal: TrackerInternal) : Tracker {
    override fun deviceConnected(poolId: DevicePoolId, device: DeviceInfo) {
        trackerInternal.track(DeviceConnectedEvent(Instant.now(), poolId, device))
    }

    override fun deviceDisconnected(poolId: DevicePoolId, device: DeviceInfo) {
        trackerInternal.track(DeviceDisconnectedEvent(Instant.now(), poolId, device))
    }

    override fun deviceProviderInit(serialNumber: String, startTime: Instant, finishTime: Instant) {
        trackerInternal.track(DeviceProviderPreparingEvent(startTime, finishTime, serialNumber))
    }

    override fun devicePreparing(serialNumber: String, startTime: Instant, finishTime: Instant) {
        trackerInternal.track(DevicePreparingEvent(startTime, finishTime, serialNumber))
    }

    override fun testParsing(serialNumber: String, startTime: Instant, finishTime: Instant, logFile: File?, failed: Boolean) {
        trackerInternal.track(TestParsingEvent(startTime, finishTime, serialNumber, logFile, failed))
    }

    override fun devicePhase(
        serialNumber: String,
        phase: DevicePhase,
        label: String,
        startTime: Instant,
        finishTime: Instant,
        failed: Boolean,
        logFile: File?,
    ) {
        trackerInternal.track(DevicePhaseEvent(startTime, finishTime, serialNumber, phase, label, failed, logFile))
    }

    override fun test(poolId: DevicePoolId, device: DeviceInfo, testResult: TestResult, final: Boolean) {
        trackerInternal.track(TestEvent(Instant.now(), poolId, device, testResult, final))
    }
}
