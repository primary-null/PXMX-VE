package com.pxmx.app.data.repo

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/** Optional enrichment may fail, but never consume authentication or structured cancellation. */
internal fun Throwable.rethrowAuthOrCancellation() {
    generateSequence(this) { it.cause }.forEach { error ->
        if (error is CancellationException) throw error
        if (error is HttpException && error.code() in setOf(401, 403)) throw error
        if (error is PveHttpException && error.code in setOf(401, 403)) throw error
    }
}

internal inline fun <T> attemptRead(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: Exception) {
    e.rethrowAuthOrCancellation()
    Result.failure(e)
}
