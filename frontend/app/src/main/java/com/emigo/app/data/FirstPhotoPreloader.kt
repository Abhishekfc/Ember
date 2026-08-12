package com.emigo.app.data

import android.content.Context
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Warms Coil's cache for exactly one photo URL — the single photo about to be the very first
 * thing on screen (Home's featured card, page 0, on cold start), never a batch. A bulk version
 * of this (every feed + Memories photo, preloaded the instant any fetch landed) was tried and
 * fully reverted — see PROJECT_CONTEXT.md's "Image loading / Coil" section — because firing off
 * that many requests at once competed with the network for the real feed/Memories fetch and made
 * cold start slower and unreliable. One photo is a different scale of risk: it's the exact thing
 * already about to be looked at, not a guess about what might get swiped to later.
 *
 * Still dispatches onto its own background scope rather than the caller's thread — the one call
 * site that matters (MainActivity.onCreate, before setContent()) runs before Compose's first
 * frame, so anything synchronous there directly delays app launch, same lesson as the reverted
 * bulk version, just applied to a single request instead of none.
 *
 * [targetWidthPx] matters more than it looks like it should: this request has no Composable to
 * infer a size from (unlike the real AsyncImage that'll eventually show this photo, which Coil
 * constrains to its actual on-screen size automatically), so left unset it defaults to decoding
 * at the photo's full original resolution — a real cost for some of this app's test images
 * (several are multi-thousand-pixel PNGs several MB each). That's slower than the on-screen card
 * ever needs, and doesn't even reliably help: a mismatched decode size can miss Coil's memory
 * cache for the size the real request actually asks for. Passing the device's own screen width
 * makes this preload do the same useful work the real display needs, not extra, slower work. */
object FirstPhotoPreloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun preload(context: Context, url: String?, targetWidthPx: Int) {
        if (url == null) return
        val appContext = context.applicationContext
        scope.launch {
            appContext.imageLoader.enqueue(
                ImageRequest.Builder(appContext)
                    .data(url)
                    .size(Size(targetWidthPx, targetWidthPx))
                    .build(),
            )
        }
    }
}
