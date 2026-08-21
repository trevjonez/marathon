package com.malinskiy.marathon.device

/**
 * Devices whose serial is not in [excludingSerials]. If every candidate is excluded (e.g. only the device a prior
 * attempt already failed on remains available), returns all of [this] instead — a retry should still be able to
 * borrow the sole available device rather than block forever.
 */
fun <T : Device> Collection<T>.preferringNotIn(excludingSerials: Set<String>): List<T> =
    filter { it.serialNumber !in excludingSerials }.ifEmpty { toList() }
