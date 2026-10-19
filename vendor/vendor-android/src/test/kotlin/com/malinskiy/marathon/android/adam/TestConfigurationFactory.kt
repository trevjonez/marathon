package com.malinskiy.marathon.android.adam

import com.malinskiy.marathon.config.Configuration
import com.malinskiy.marathon.config.vendor.VendorConfiguration
import com.malinskiy.marathon.config.vendor.android.AllureConfiguration
import com.malinskiy.marathon.config.vendor.android.FileSyncConfiguration
import java.io.File
import java.nio.file.Files

object TestConfigurationFactory {
    fun create(
        autoGrantPermission: Boolean = false,
        installOptions: String = "",
        fileSyncConfiguration: FileSyncConfiguration = FileSyncConfiguration(),
        allureConfiguration: AllureConfiguration = AllureConfiguration(),
        isCodeCoverageEnabled: Boolean = false,
        applicationOutput: File? = File(javaClass.classLoader.getResource("apk/app-debug.apk").file),
        testApplicationOutput: File? = File(javaClass.classLoader.getResource("apk/app-debug-androidTest.apk").file),
        splitApks: List<File>? = emptyList(),
        extraApplicationsOutput: List<File>? = emptyList(),
        // Installs and parsing write device logs under the output dir; keep them out of the source tree
        outputDir: File = Files.createTempDirectory("marathon-test").toFile(),
        applicationPmClear: Boolean = false,
        testApplicationPmClear: Boolean = false,
    ): Configuration {
        return Configuration.Builder(
            name = "",
            outputDir = outputDir,
        ).apply {
            vendorConfiguration = VendorConfiguration.AndroidConfiguration(
                androidSdk = File(""),
                applicationOutput = applicationOutput,
                testApplicationOutput = testApplicationOutput,
                splitApks = splitApks,
                extraApplicationsOutput = extraApplicationsOutput,
                autoGrantPermission = autoGrantPermission,
                installOptions = installOptions,
                fileSyncConfiguration = fileSyncConfiguration,
                allureConfiguration = allureConfiguration,
                applicationPmClear = applicationPmClear,
                testApplicationPmClear = testApplicationPmClear,
            )
            this.isCodeCoverageEnabled = isCodeCoverageEnabled
            debug = false
            analyticsTracking = false
        }.build()
    }
}
