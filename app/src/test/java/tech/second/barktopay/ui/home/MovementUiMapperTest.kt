package tech.second.barktopay.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.bark.Movement

class MovementUiMapperTest {

    private fun movement(
        status: String,
        effective: Long,
        intended: Long = effective,
        fee: ULong = 0uL,
        kind: String = "arkoor",
        createdAt: String = "2026-10-01T18:22:33Z"
    ) = Movement(
        id = 1u,
        status = status,
        subsystemName = "wallet",
        subsystemKind = kind,
        metadataJson = "{}",
        intendedBalanceSats = intended,
        effectiveBalanceSats = effective,
        offchainFeeSats = fee,
        sentToAddresses = emptyList(),
        receivedOnAddresses = emptyList(),
        inputVtxoIds = emptyList(),
        outputVtxoIds = emptyList(),
        exitedVtxoIds = emptyList(),
        createdAt = createdAt,
        updatedAt = createdAt,
        completedAt = null,
        paymentHash = null,
        lightningInvoice = null,
        lightningOffer = null
    )

    @Test
    fun `incoming final movement maps as settled receive`() {
        val row = MovementUiMapper.map(movement(status = "finished", effective = 5_000))
        assertTrue(row.isIncoming)
        assertEquals(5_000L, row.amountSats)
        assertTrue(row.settled)
        assertEquals("finished", row.statusLabel)
        assertEquals("arkoor", row.kindLabel)
    }

    @Test
    fun `incoming non-final movement is labelled settling`() {
        val row = MovementUiMapper.map(movement(status = "pending", effective = 5_000))
        assertTrue(row.isIncoming)
        assertFalse(row.settled)
        assertEquals("settling", row.statusLabel)
    }

    @Test
    fun `outgoing movement keeps sign semantics and fee`() {
        val row = MovementUiMapper.map(movement(status = "success", effective = -2_000, fee = 42uL))
        assertFalse(row.isIncoming)
        assertEquals(2_000L, row.amountSats)
        assertEquals(42uL, row.feeSats)
        assertTrue(row.settled)
    }

    @Test
    fun `zero effective falls back to intended amount`() {
        val row = MovementUiMapper.map(movement(status = "pending", effective = 0, intended = 7_500))
        assertFalse(row.isIncoming) // zero is neither; treated as outgoing for display
        assertEquals(7_500L, row.amountSats)
    }

    @Test
    fun `unknown status is humanized not crashing`() {
        val row = MovementUiMapper.map(movement(status = "WAITING_FOR_ROUND", effective = -100))
        assertEquals("waiting for round", row.statusLabel)
        assertFalse(row.settled)
    }

    @Test
    fun `iso timestamp formats, garbage passes through truncated`() {
        // TZ-independent: only assert the parsed date portion, not the local hour/minute.
        assertTrue(
            MovementUiMapper.formatTime("2026-10-01T18:22:33+02:00").contains("Oct 1")
        )
        assertEquals("not-a-timestamp", MovementUiMapper.formatTime("not-a-timestamp"))
    }

    @Test
    fun `blank kind becomes null`() {
        val row = MovementUiMapper.map(movement(status = "finished", effective = 1, kind = ""))
        assertNull(row.kindLabel)
    }
}
