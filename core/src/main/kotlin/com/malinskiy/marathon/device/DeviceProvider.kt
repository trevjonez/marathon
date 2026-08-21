package com.malinskiy.marathon.device

import kotlinx.coroutines.channels.Channel

interface DeviceProvider {
    sealed class DeviceEvent {
        class DeviceConnected(val device: Device) : DeviceEvent()
        class DeviceDisconnected(val device: Device) : DeviceEvent()
    }

    suspend fun initialize()

    /**
     * Remote test parsers require a temp device
     * This method should be called before reading from the [subscribe()] channel
     *
     * @param excludingSerials serial numbers to avoid when a usable alternative exists. Used to steer a retry onto a
     *      different device than a prior failed attempt. Providers fall back to an excluded device only if it's the
     *      sole one available.
     */
    suspend fun borrow(excludingSerials: Set<String> = emptySet()) : Device
    suspend fun terminate()
    fun subscribe(): Channel<DeviceEvent>
}
