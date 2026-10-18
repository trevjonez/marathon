package com.malinskiy.marathon.analytics.internal.pub

import com.malinskiy.marathon.device.Device
import com.malinskiy.marathon.device.DeviceInfo
import com.malinskiy.marathon.device.DevicePhase
import com.malinskiy.marathon.device.DevicePoolId
import com.malinskiy.marathon.execution.TestResult
import java.io.File
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class Track : Tracker {
    private val delegates: CopyOnWriteArrayList<Tracker> = CopyOnWriteArrayList()

    fun add(tracker: Tracker): Track {
        delegates.add(tracker)
        return this
    }

    override fun deviceProviderInit(serialNumber: String, startTime: Instant, finishTime: Instant) {
        delegates.forEach { it.deviceProviderInit(serialNumber, startTime, finishTime) }
    }

    override fun devicePreparing(serialNumber: String, startTime: Instant, finishTime: Instant) {
        delegates.forEach { it.devicePreparing(serialNumber, startTime, finishTime) }
    }

    override fun testParsing(serialNumber: String, startTime: Instant, finishTime: Instant, logFile: File?, failed: Boolean) {
        delegates.forEach { it.testParsing(serialNumber, startTime, finishTime, logFile, failed) }
    }

    override fun devicePhase(serialNumber: String, phase: DevicePhase, label: String, startTime: Instant, finishTime: Instant, failed: Boolean) {
        delegates.forEach { it.devicePhase(serialNumber, phase, label, startTime, finishTime, failed) }
    }

    override fun deviceConnected(poolId: DevicePoolId, device: DeviceInfo) {
        delegates.forEach { it.deviceConnected(poolId, device) }
    }

    override fun deviceDisconnected(poolId: DevicePoolId, device: DeviceInfo) {
        delegates.forEach { it.deviceDisconnected(poolId, device) }
    }

    override fun test(poolId: DevicePoolId, device: DeviceInfo, testResult: TestResult, final: Boolean) {
        delegates.forEach { it.test(poolId, device, testResult, final) }
    }

    suspend fun trackDevicePreparing(device: Device, block: suspend () -> Unit) {
        val start = Instant.now()
        block.invoke()
        val finish = Instant.now()

        devicePreparing(device.serialNumber, start, finish)
    }

    /**
     * Records the attempt even when [block] throws, so failed parsing attempts still show where the time went
     */
    suspend fun <T> trackTestParsing(device: Device, logFile: File? = null, block: suspend () -> T): T {
        val start = Instant.now()
        var failed = true
        try {
            return block.invoke().also { failed = false }
        } finally {
            testParsing(device.serialNumber, start, Instant.now(), logFile, failed)
        }
    }

    suspend fun trackProviderDevicePreparing(device: Device, block: suspend () -> Unit) {
        val start = Instant.now()
        block.invoke()
        val finish = Instant.now()

        deviceProviderInit(device.serialNumber, start, finish)
    }
}
