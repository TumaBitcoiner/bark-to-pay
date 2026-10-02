package tech.second.barktopay.ui.home

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs
import uniffi.bark.Movement

/** Display model for one history row. */
data class MovementRow(
    val isIncoming: Boolean,
    val amountSats: Long,
    val feeSats: ULong,
    val statusLabel: String,
    val settled: Boolean,
    val timeLabel: String,
    val kindLabel: String?
)

/**
 * Maps bark [Movement]s to rows. Defensive on purpose: the exact status vocabulary is
 * runtime-defined by barkd, so unknown values pass through humanized instead of breaking.
 * Pure JVM — unit-tested without Android.
 */
object MovementUiMapper {

    private val FINAL_STATUS_HINTS = listOf("success", "finish", "complete", "confirm")

    fun map(m: Movement): MovementRow {
        val effective = m.effectiveBalanceSats
        val isIncoming = effective > 0
        val amount = if (effective != 0L) abs(effective) else abs(m.intendedBalanceSats)
        val settled = FINAL_STATUS_HINTS.any { m.status.contains(it, ignoreCase = true) }
        return MovementRow(
            isIncoming = isIncoming,
            amountSats = amount,
            feeSats = m.offchainFeeSats,
            // Arkoor receipts are shown honestly as "settling" until they reach a final state.
            statusLabel = if (isIncoming && !settled) "settling" else humanize(m.status),
            settled = settled,
            timeLabel = formatTime(m.createdAt),
            kindLabel = m.subsystemKind.ifBlank { null }
        )
    }

    internal fun humanize(status: String): String =
        status.lowercase().replace('_', ' ').replace('-', ' ')

    internal fun formatTime(raw: String): String {
        val instant = try {
            OffsetDateTime.parse(raw).toInstant()
        } catch (e: DateTimeParseException) {
            try {
                Instant.parse(raw)
            } catch (e2: DateTimeParseException) {
                return raw.take(16)
            }
        }
        return DateTimeFormatter.ofPattern("MMM d, HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }
}
