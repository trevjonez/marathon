package com.malinskiy.marathon.device

/**
 * A step a device spends time on outside test execution, reported for the execution timeline. [betweenTests] steps
 * repeat around every batch, the rest happen once while the device is prepared.
 */
enum class DevicePhase(val betweenTests: Boolean) {
    INSTALL(false),
    DEVICE_RESET(false),
    CLEAR_DATA(true),
    BEFORE_RUN(true),
    INSTRUMENTATION_START(true),
    AFTER_RUN(true),
}
