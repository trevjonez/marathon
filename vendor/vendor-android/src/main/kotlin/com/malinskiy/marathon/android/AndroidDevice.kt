package com.malinskiy.marathon.android

import com.android.sdklib.AndroidVersion
import com.malinskiy.marathon.android.logcat.LogcatProducer
import com.malinskiy.marathon.android.model.ShellCommandResult
import com.malinskiy.marathon.device.screenshot.Rotation
import com.malinskiy.marathon.config.vendor.android.VideoConfiguration
import com.malinskiy.marathon.device.Device
import com.malinskiy.marathon.device.DevicePhase
import com.malinskiy.marathon.device.screenshot.Screenshottable
import com.malinskiy.marathon.report.logs.LogProducer
import java.io.File
import java.time.Instant

interface AndroidDevice : Device, LogProducer, LogcatProducer, Screenshottable {
    val apiLevel: Int
    val version: AndroidVersion
    val fileManager: RemoteFileManager
    val externalStorageMount: String
    val initialRotation: Rotation

    /**
     * Called only once per device's lifetime
     */
    suspend fun setup()

    /** Reports one step taken outside test execution for the timeline, see [phase]. */
    fun recordPhase(phase: DevicePhase, label: String, start: Instant, finish: Instant, failed: Boolean, logFile: File? = null) = Unit

    suspend fun getProperty(name: String, cached: Boolean = true): String?

    /**
     * @return null if command did not complete successfully, otherwise cmd output
     */
    suspend fun executeShellCommand(command: String, errorMessage: String = ""): ShellCommandResult?

    /**
     * @return null if command did not complete successfully, otherwise cmd output
     */
    suspend fun safeExecuteShellCommand(command: String, errorMessage: String = ""): ShellCommandResult?

    /**
     * @throws com.malinskiy.marathon.android.exception.CommandRejectedException in case the command fails
     */
    suspend fun criticalExecuteShellCommand(command: String, errorMessage: String = ""): ShellCommandResult

    /**
     * @throws com.malinskiy.marathon.android.exception.TransferException
     */
    suspend fun pullFile(remoteFilePath: String, localFilePath: String)

    /**
     * Soft exception handling version of pullFile
     */
    suspend fun safePullFile(remoteFilePath: String, localFilePath: String)

    /**
     * @throws com.malinskiy.marathon.android.exception.TransferException
     */
    suspend fun pushFile(localFilePath: String, remoteFilePath: String, verify: Boolean)

    /**
     * @throws com.malinskiy.marathon.android.exception.TransferException
     */
    suspend fun pullFolder(remoteFolderPath: String, localFolderPath: String)
    suspend fun safePullFolder(remoteFolderPath: String, localFolderPath: String)
    suspend fun pushFolder(localFolderPath: String, remoteFolderPath: String)
    /**
     * @throws com.malinskiy.marathon.android.exception.InstallException in case of failure to push the apk
     */
    suspend fun installPackage(absolutePath: String, reinstall: Boolean, optionalParams: List<String>): ShellCommandResult?
    suspend fun installSplitPackages(absolutePaths: List<String>, reinstall: Boolean, optionalParams: List<String>): String
    suspend fun safeUninstallPackage(appPackage: String, keepData: Boolean = false): ShellCommandResult?
    suspend fun clearPackage(packageName: String): ShellCommandResult?
    
    suspend fun safeStartScreenRecorder(remoteFilePath: String, options: VideoConfiguration)
}

/** Runs [block] as a device phase on the timeline, recording it as failed if it throws. */
suspend fun <T> AndroidDevice.phase(phase: DevicePhase, label: String, logFile: File? = null, block: suspend () -> T): T {
    val start = Instant.now()
    var failed = true
    try {
        return block().also { failed = false }
    } finally {
        recordPhase(phase, label, start, Instant.now(), failed, logFile)
    }
}
