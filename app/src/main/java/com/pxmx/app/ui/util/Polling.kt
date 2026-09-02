package com.pxmx.app.ui.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Creates a cold polling flow that emits [Unit] periodically while collected.
 * When collected via [kotlinx.coroutines.flow.stateIn] with
 * [kotlinx.coroutines.flow.SharingStarted.WhileSubscribed], polling automatically starts
 * on the first subscriber and stops ~5s after the UI disconnects.
 *
 * @param intervalMs The delay between poll executions.
 * @param emitImmediately If true, emits immediately upon collection before starting the delay cycle.
 */
fun tickerFlow(
    intervalMs: Long,
    emitImmediately: Boolean = true,
): Flow<Unit> = flow {
    if (emitImmediately) {
        emit(Unit)
    }
    while (true) {
        delay(intervalMs)
        emit(Unit)
    }
}
