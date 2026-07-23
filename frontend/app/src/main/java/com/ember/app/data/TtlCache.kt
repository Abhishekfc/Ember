package com.ember.app.data

/** A tiny in-memory, per-key cache with a fixed time-to-live — used by the hot, repeatedly-
 * fetched-unchanged repository reads (feed/friends/activity's default page) so a burst of
 * near-simultaneous callers (e.g. two screens sharing a repository instance, or a fast
 * pull-to-refresh double-tap) don't each force a fresh network round trip for data that was
 * just fetched a moment ago. TTL matches the backend's own Redis cache TTL (see CacheConfig.kt)
 * rather than trying to be fresher client-side than the server could ever actually provide.
 *
 * Deliberately per-exact-key, not a general "latest response" cache — a hit only ever serves
 * back the exact same query (same offset/limit) that was actually made, so two different pages
 * of the same list never get confused for one another. */
class TtlCache<K, V>(private val ttlMillis: Long) {
    private data class Entry<V>(val value: V, val expiresAtMillis: Long)
    private val entries = mutableMapOf<K, Entry<V>>()

    @Synchronized
    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAtMillis) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = Entry(value, System.currentTimeMillis() + ttlMillis)
    }

    @Synchronized
    fun invalidateAll() {
        entries.clear()
    }
}
