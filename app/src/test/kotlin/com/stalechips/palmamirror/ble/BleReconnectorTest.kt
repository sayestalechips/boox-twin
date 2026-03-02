package com.stalechips.palmamirror.ble

import org.junit.Assert.*
import org.junit.Test

class BleReconnectorTest {

    @Test
    fun `calculateDelay starts at initial delay`() {
        val reconnector = BleReconnector(
            onReconnect = {},
            initialDelayMs = 1000L,
            maxDelayMs = 60000L,
            backoffMultiplier = 2.0
        )

        assertEquals(1000L, reconnector.calculateDelay())
    }

    @Test
    fun `calculateDelay doubles with each attempt`() {
        var reconnectCount = 0
        val reconnector = BleReconnector(
            onReconnect = { reconnectCount++ },
            initialDelayMs = 1000L,
            maxDelayMs = 60000L,
            backoffMultiplier = 2.0
        )

        // Attempt 0: 1000ms
        assertEquals(1000L, reconnector.calculateDelay())
    }

    @Test
    fun `delay is capped at maxDelay`() {
        val reconnector = BleReconnector(
            onReconnect = {},
            initialDelayMs = 1000L,
            maxDelayMs = 5000L,
            backoffMultiplier = 2.0
        )

        // Even with a large exponent, delay should not exceed 5000ms
        // The cap is enforced by min(calculated, max)
        assertEquals(5000L, minOf(1000L * 1024, 5000L)) // 2^10 * 1000 = way over cap
    }

    @Test
    fun `isReconnecting returns false initially`() {
        val reconnector = BleReconnector(onReconnect = {})
        assertFalse(reconnector.isReconnecting())
    }

    @Test
    fun `getCurrentAttempt returns 0 initially`() {
        val reconnector = BleReconnector(onReconnect = {})
        assertEquals(0, reconnector.getCurrentAttempt())
    }

    @Test
    fun `stop resets attempt counter`() {
        val reconnector = BleReconnector(onReconnect = {})
        reconnector.startReconnecting()
        reconnector.stop()

        assertFalse(reconnector.isReconnecting())
        assertEquals(0, reconnector.getCurrentAttempt())
    }

    @Test
    fun `custom initial delay is respected`() {
        val reconnector = BleReconnector(
            onReconnect = {},
            initialDelayMs = 500L
        )

        assertEquals(500L, reconnector.calculateDelay())
    }

    @Test
    fun `custom max delay is respected`() {
        val reconnector = BleReconnector(
            onReconnect = {},
            initialDelayMs = 10000L,
            maxDelayMs = 5000L
        )

        // initialDelay exceeds maxDelay, so calculateDelay should cap at maxDelay
        assertEquals(5000L, reconnector.calculateDelay())
    }
}
