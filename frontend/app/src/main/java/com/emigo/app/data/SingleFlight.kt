package com.emigo.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coalesces truly-concurrent calls for the same key into one real network request — unlike
 * [TtlCache] (which only dedupes calls that land sequentially, within its TTL window), this
 * catches two callers that both ask for the same thing at essentially the same instant, before
 * either has a result to hand back yet.
 *
 * Runs the actual work on its own internal scope, not whichever caller's coroutine happened to
 * start it — if that caller (e.g. a ViewModel whose screen was just closed) gets cancelled while
 * awaiting the result, that must only cancel *their* wait, not the shared in-flight request every
 * other concurrent caller is also awaiting. */
class SingleFlight<K, V> {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<K, Deferred<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        val deferred = mutex.withLock {
            inFlight.getOrPut(key) {
                scope.async {
                    try {
                        block()
                    } finally {
                        mutex.withLock { inFlight.remove(key) }
                    }
                }
            }
        }
        return deferred.await()
    }
}
