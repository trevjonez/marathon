package com.malinskiy.marathon.device

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class DeviceSelectionTest {

    private fun devices(vararg serials: String) = serials.map { DeviceStub(serialNumber = it) }

    @Test
    fun `returns all devices when nothing is excluded`() {
        devices("A", "B").preferringNotIn(emptySet()).map { it.serialNumber } shouldBeEqualTo listOf("A", "B")
    }

    @Test
    fun `excludes the given serial when an alternative exists`() {
        devices("A", "B").preferringNotIn(setOf("A")).map { it.serialNumber } shouldBeEqualTo listOf("B")
    }

    @Test
    fun `falls back to all devices when every candidate is excluded`() {
        devices("A").preferringNotIn(setOf("A")).map { it.serialNumber } shouldBeEqualTo listOf("A")
    }
}
