package com.example.domain

import com.example.data.local.KeySource
import com.example.data.local.StoredKeySlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class KeyRuntimeStatus {
    READY,
    ACTIVE,
    COOLDOWN,
    UNCONFIGURED,
    DISABLED,
    ERROR
}

data class KeySlotRuntimeState(
    val slotIndex: Int, // 1..4
    val label: String,
    val source: KeySource,
    val enabled: Boolean,
    val status: KeyRuntimeStatus,
    val cooldownRemainingSec: Int = 0,
    val requestsThisSession: Int = 0,
    val tokensThisSession: Int = 0,
    val lastError: String? = null
)

data class AcquiredKey(
    val slotIndex: Int,
    val apiKey: String,
    val label: String
)

/**
 * Thread-safe 4-API-key round-robin dispatcher with automatic 60-second 429 cooldown
 * and real-time runtime status flow for Dashboard and Isometric Studio visualization.
 */
class FourKeyPoolManager(scope: CoroutineScope) {

    private val mutex = Mutex()
    private var pointer = 0
    private var storedSlots: List<StoredKeySlot> = emptyList()
    private val cooldownUntilMs = LongArray(5) { 0L }
    private val activeSlotFlags = BooleanArray(5) { false }
    private val sessionRequests = IntArray(5) { 0 }
    private val sessionTokens = IntArray(5) { 0 }
    private val lastErrors = Array<String?>(5) { null }

    private val _runtimeStates = MutableStateFlow(defaultInitialStates())
    val runtimeStates: StateFlow<List<KeySlotRuntimeState>> = _runtimeStates.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                delay(1_000L)
                refreshStates()
            }
        }
    }

    suspend fun updateConfiguredSlots(slots: List<StoredKeySlot>) {
        mutex.withLock {
            storedSlots = slots
        }
        refreshStates()
    }

    suspend fun configuredKeyCount(): Int = mutex.withLock {
        storedSlots.count { it.enabled && it.isConfigured }
    }

    /**
     * Picks the next READY key in strict round-robin order (K1 -> K2 -> K3 -> K4 -> K1).
     * If all configured keys are temporarily in cooldown, returns null with shortest wait time.
     */
    suspend fun acquireNextKey(excludeSlots: Set<Int> = emptySet()): Pair<AcquiredKey?, Long> {
        val result = mutex.withLock {
            val now = System.currentTimeMillis()
            val candidates = storedSlots.filter { it.enabled && it.isConfigured }
            if (candidates.isEmpty()) return@withLock Pair(null, -1L)

            var minCooldownWait = Long.MAX_VALUE
            for (offset in 0 until 4) {
                val idx = ((pointer + offset) % 4) + 1 // 1..4
                val slot = storedSlots.firstOrNull { it.slotIndex == idx } ?: continue
                if (!slot.enabled || !slot.isConfigured) continue
                if (idx in excludeSlots) continue

                val cdUntil = cooldownUntilMs[idx]
                if (cdUntil > now) {
                    minCooldownWait = minOf(minCooldownWait, cdUntil - now)
                    continue
                }

                pointer = idx % 4 // Advance round-robin pointer
                activeSlotFlags[idx] = true
                lastErrors[idx] = null
                return@withLock Pair(
                    AcquiredKey(idx, slot.effectiveKey, slot.label),
                    0L
                )
            }
            val waitMs = if (minCooldownWait == Long.MAX_VALUE) 0L else minCooldownWait
            Pair(null, waitMs)
        }
        refreshStates()
        return result
    }

    suspend fun releaseSuccess(slotIndex: Int, tokensUsed: Int) {
        mutex.withLock {
            if (slotIndex in 1..4) {
                activeSlotFlags[slotIndex] = false
                sessionRequests[slotIndex] += 1
                sessionTokens[slotIndex] += tokensUsed
                lastErrors[slotIndex] = null
            }
        }
        refreshStates()
    }

    suspend fun markCooldown(slotIndex: Int, durationMs: Long = 60_000L, reason: String) {
        mutex.withLock {
            if (slotIndex in 1..4) {
                activeSlotFlags[slotIndex] = false
                cooldownUntilMs[slotIndex] = System.currentTimeMillis() + durationMs
                lastErrors[slotIndex] = reason
            }
        }
        refreshStates()
    }

    suspend fun markError(slotIndex: Int, reason: String) {
        mutex.withLock {
            if (slotIndex in 1..4) {
                activeSlotFlags[slotIndex] = false
                lastErrors[slotIndex] = reason
            }
        }
        refreshStates()
    }

    suspend fun clearCooldown(slotIndex: Int) {
        mutex.withLock {
            if (slotIndex in 1..4) {
                cooldownUntilMs[slotIndex] = 0L
                lastErrors[slotIndex] = null
            }
        }
        refreshStates()
    }

    private suspend fun refreshStates() {
        val snapshot = mutex.withLock {
            val now = System.currentTimeMillis()
            (1..4).map { idx ->
                val stored = storedSlots.firstOrNull { it.slotIndex == idx }
                val remainingMs = (cooldownUntilMs[idx] - now).coerceAtLeast(0L)
                val remainingSec = ((remainingMs + 999L) / 1000L).toInt()
                val status = when {
                    stored == null || !stored.isConfigured -> KeyRuntimeStatus.UNCONFIGURED
                    !stored.enabled -> KeyRuntimeStatus.DISABLED
                    activeSlotFlags[idx] -> KeyRuntimeStatus.ACTIVE
                    remainingSec > 0 -> KeyRuntimeStatus.COOLDOWN
                    lastErrors[idx] != null -> KeyRuntimeStatus.ERROR
                    else -> KeyRuntimeStatus.READY
                }
                KeySlotRuntimeState(
                    slotIndex = idx,
                    label = stored?.label ?: "GCP Project Key #$idx",
                    source = stored?.source ?: KeySource.UNCONFIGURED,
                    enabled = stored?.enabled ?: true,
                    status = status,
                    cooldownRemainingSec = remainingSec,
                    requestsThisSession = sessionRequests[idx],
                    tokensThisSession = sessionTokens[idx],
                    lastError = lastErrors[idx]
                )
            }
        }
        _runtimeStates.value = snapshot
    }

    private fun defaultInitialStates(): List<KeySlotRuntimeState> = (1..4).map { idx ->
        KeySlotRuntimeState(
            slotIndex = idx,
            label = "GCP Project Key #$idx",
            source = KeySource.UNCONFIGURED,
            enabled = true,
            status = KeyRuntimeStatus.UNCONFIGURED
        )
    }
}
