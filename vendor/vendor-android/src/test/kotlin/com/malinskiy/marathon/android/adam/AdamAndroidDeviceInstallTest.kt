package com.malinskiy.marathon.android.adam

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.server.junit5.AdbClient
import com.malinskiy.adam.server.junit5.AdbServer
import com.malinskiy.adam.server.junit5.AdbTest
import com.malinskiy.adam.server.stub.AndroidDebugBridgeServer
import com.malinskiy.adam.server.stub.dsl.DeviceExpectation
import com.malinskiy.marathon.analytics.internal.pub.Track
import com.malinskiy.marathon.device.DevicePhase
import com.malinskiy.marathon.exceptions.DeviceSetupException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.io.File

@AdbTest
class AdamAndroidDeviceInstallTest {
    @AdbClient
    lateinit var client: AndroidDebugBridgeClient

    @AdbServer
    lateinit var server: AndroidDebugBridgeServer

    @TempDir
    lateinit var temp: File

    private val configuration = TestConfigurationFactory.create()
    private val tracker = RecordingTracker()
    private val device by lazy { TestDeviceFactory.createAdam(client, configuration, mock(), Track().add(tracker)) }

    @Test
    fun `repeated calls install only once`() {
        runBlocking {
            server.multipleSessions {
                serial("emulator-5554") {
                    boot()
                    cleanInstall(temp)
                }
                features("emulator-5554")
            }

            device.setup()
            device.ensureInstalled(configuration)
            // Any adb traffic here would hit the stub with no expectation left and fail
            device.ensureInstalled(configuration)
        }

        tracker.devicePreparing.size shouldBeEqualTo 1
        tracker.phases.map { Triple(it.phase, it.label, it.failed) } shouldBeEqualTo listOf(
            Triple(DevicePhase.INSTALL, "install com.example", false),
            Triple(DevicePhase.INSTALL, "install com.example.test", false),
        )
    }

    @Test
    fun `concurrent callers share a single install`() {
        runBlocking {
            server.multipleSessions {
                serial("emulator-5554") {
                    boot()
                    cleanInstall(temp, installDelayMillis = 500)
                }
                features("emulator-5554")
            }

            device.setup()
            (1..3).map { async { device.ensureInstalled(configuration) } }.awaitAll()
        }

        tracker.devicePreparing.size shouldBeEqualTo 1
    }

    @Test
    fun `a failed install is retried by the next caller`() {
        runBlocking {
            server.multipleSessions {
                serial("emulator-5554") {
                    boot()
                    // AndroidAppInstaller makes three attempts per package before giving up
                    repeat(3) {
                        shell("pm list packages", "")
                        installApk(temp, "/data/local/tmp/app-debug.apk", "511", APP_MD5, "-r", stdout = "Failure")
                    }
                    cleanInstall(temp)
                }
                features("emulator-5554")
            }

            device.setup()
            assertThrows<DeviceSetupException> { device.ensureInstalled(configuration) }
            tracker.devicePreparing.size shouldBeEqualTo 0
            // One phase for all three attempts, marked failed
            tracker.phases.map { Triple(it.phase, it.label, it.failed) } shouldBeEqualTo listOf(
                Triple(DevicePhase.INSTALL, "install com.example", true),
            )

            device.ensureInstalled(configuration)
        }

        tracker.devicePreparing.size shouldBeEqualTo 1
    }

    @Test
    fun `prepare after an install only resets the device`() {
        runBlocking {
            server.multipleSessions {
                serial("emulator-5554") {
                    boot()
                    cleanInstall(temp)
                    shell("rm -r /sdcard", "")
                    shell("mkdir /sdcard", "")
                    shell("logcat -c", "")
                }
                features("emulator-5554")
            }

            device.setup()
            device.ensureInstalled(configuration)
            device.prepare(configuration)
        }

        // One span for the install, one for the reset; the install isn't repeated
        tracker.devicePreparing.size shouldBeEqualTo 2
        tracker.phases.map { it.phase } shouldBeEqualTo listOf(DevicePhase.INSTALL, DevicePhase.INSTALL, DevicePhase.DEVICE_RESET)
    }

    @Test
    fun `prepare installs when nothing has yet`() {
        runBlocking {
            server.multipleSessions {
                serial("emulator-5554") {
                    boot()
                    cleanInstall(temp)
                    shell("rm -r /sdcard", "")
                    shell("mkdir /sdcard", "")
                    shell("logcat -c", "")
                }
                features("emulator-5554")
            }

            device.setup()
            device.prepare(configuration)
            device.ensureInstalled(configuration)
        }

        tracker.devicePreparing.size shouldBeEqualTo 2
    }
}

internal const val APP_MD5 = "122fc3b5d69b262db9b84dfc00e8f1d4"
internal const val TEST_APP_MD5 = "8d103498247b3711817a9f18624dede7"

internal fun DeviceExpectation.cleanInstall(temp: File, installDelayMillis: Long? = null) {
    shell("pm list packages", "", delay = installDelayMillis)
    installApk(temp, "/data/local/tmp/app-debug.apk", "511", APP_MD5, "-r")
    shell("pm list packages", "package:com.example")
    installApk(temp, "/data/local/tmp/app-debug-androidTest.apk", "511", TEST_APP_MD5, "-r")
}
