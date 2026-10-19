package com.malinskiy.marathon.android.adam

import com.malinskiy.marathon.analytics.internal.pub.Tracker
import com.malinskiy.marathon.device.DeviceInfo
import com.malinskiy.marathon.device.DevicePhase
import com.malinskiy.marathon.device.DevicePoolId
import com.malinskiy.marathon.execution.TestResult
import java.io.File
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class RecordingTracker : Tracker {
    data class Span(val serialNumber: String, val start: Instant, val finish: Instant, val logFile: File? = null, val failed: Boolean = false)

    val devicePreparing = CopyOnWriteArrayList<Span>()
    val testParsing = CopyOnWriteArrayList<Span>()
    val phases = CopyOnWriteArrayList<Phase>()

    data class Phase(val phase: DevicePhase, val label: String, val start: Instant, val finish: Instant, val failed: Boolean, val logFile: File? = null)

    override fun devicePhase(
        serialNumber: String,
        phase: DevicePhase,
        label: String,
        startTime: Instant,
        finishTime: Instant,
        failed: Boolean,
        logFile: File?,
    ) {
        phases.add(Phase(phase, label, startTime, finishTime, failed, logFile))
    }

    override fun devicePreparing(serialNumber: String, startTime: Instant, finishTime: Instant) {
        devicePreparing.add(Span(serialNumber, startTime, finishTime))
    }

    override fun testParsing(serialNumber: String, startTime: Instant, finishTime: Instant, logFile: File?, failed: Boolean) {
        testParsing.add(Span(serialNumber, startTime, finishTime, logFile, failed))
    }

    override fun deviceConnected(poolId: DevicePoolId, device: DeviceInfo) = Unit
    override fun deviceDisconnected(poolId: DevicePoolId, device: DeviceInfo) = Unit
    override fun deviceProviderInit(serialNumber: String, startTime: Instant, finishTime: Instant) = Unit
    override fun test(poolId: DevicePoolId, device: DeviceInfo, testResult: TestResult, final: Boolean) = Unit
}
