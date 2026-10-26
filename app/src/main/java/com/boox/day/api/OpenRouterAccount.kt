package com.boox.day.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the user's OpenRouter account can pay for paid models right now (Oct 2026). A normal key
 * can't read the balance (OpenRouter's credits endpoint needs a management key), so this comes from
 * the key check (never bought credit, or the key's spending limit used up) and from paid requests
 * OpenRouter refused for credit.
 *
 * "Free only" lasts [FREE_ONLY_MS], then paid models are tried again, so credit added on the
 * website starts working without a restart. Memory only: a restart tries paid models first.
 * Compose state, so the AI Models card shows the free models as soon as this changes.
 */
object OpenRouterAccount {
    const val FREE_ONLY_MS = 30 * 60 * 1000L

    /** Free-model ids end in ":free" (OpenRouter's free variants) */
    fun isFreeModel(modelId: String): Boolean = modelId.endsWith(":free")

    private var freeOnlyUntilMs by mutableLongStateOf(0L)

    /** True while paid models should be skipped: BooxDay's choice uses its free models. */
    fun isFreeOnly(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs < freeOnlyUntilMs

    /** What OpenRouter said about the key (`GET /api/v1/key`); a new key is always checked first. */
    fun onKeyChecked(isFreeTier: Boolean, limitRemaining: Double?, nowMs: Long = System.currentTimeMillis()) {
        val keyLimitUsedUp = limitRemaining != null && limitRemaining <= 0.0
        freeOnlyUntilMs = if (isFreeTier || keyLimitUsedUp) nowMs + FREE_ONLY_MS else 0L
    }

    /** OpenRouter refused a paid request: no credit left, or the key's spending limit is used up. */
    fun onPaidRequestRefused(nowMs: Long = System.currentTimeMillis()) {
        freeOnlyUntilMs = nowMs + FREE_ONLY_MS
    }

    /** A paid model answered: the account has credit. */
    fun onPaidRequestSucceeded() {
        freeOnlyUntilMs = 0L
    }
}
