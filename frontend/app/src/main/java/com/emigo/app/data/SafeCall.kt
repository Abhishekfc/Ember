package com.emigo.app.data

import java.io.IOException

/** Every repository's suspend calls hit the network before any response-code handling runs, so
 * a dropped connection (backend unreachable, tunnel down, timeout, DNS failure) throws instead
 * of returning a Response — and an uncaught throw out of a ViewModel's launched coroutine
 * crashes the whole app. Wrapping each repository method's body in this turns that into an
 * ordinary Result.failure the screen can show as an error state instead. */
suspend fun <T> safeCall(block: suspend () -> Result<T>): Result<T> =
    try {
        block()
    } catch (e: IOException) {
        Result.failure(Exception("Couldn't connect — check your connection"))
    }
