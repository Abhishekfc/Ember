package com.emigo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtlCacheTest {

    @Test
    fun returnsTheStoredValueWithinItsTtl() {
        val cache = TtlCache<String, Int>(ttlMillis = 60_000)
        cache.put("feed", 1)
        assertEquals(1, cache.get("feed"))
    }

    @Test
    fun missingKeyReturnsNull() {
        assertNull(TtlCache<String, Int>(ttlMillis = 60_000).get("nothing"))
    }

    @Test
    fun expiredEntryReturnsNull() {
        // A negative TTL means the entry is already expired the moment it is stored.
        val cache = TtlCache<String, Int>(ttlMillis = -1)
        cache.put("feed", 1)
        assertNull(cache.get("feed"))
    }

    @Test
    fun differentKeysDoNotMix() {
        val cache = TtlCache<String, String>(ttlMillis = 60_000)
        cache.put("page-0", "first")
        cache.put("page-1", "second")
        assertEquals("first", cache.get("page-0"))
        assertEquals("second", cache.get("page-1"))
    }

    @Test
    fun putReplacesThePreviousValue() {
        val cache = TtlCache<String, Int>(ttlMillis = 60_000)
        cache.put("feed", 1)
        cache.put("feed", 2)
        assertEquals(2, cache.get("feed"))
    }

    @Test
    fun invalidateAllClearsEverything() {
        val cache = TtlCache<String, Int>(ttlMillis = 60_000)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.invalidateAll()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }
}
