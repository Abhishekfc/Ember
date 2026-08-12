package com.ember.backend.service

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the fix for user-search enumeration: [escapeLikeWildcards] is what stops a search string
 * being read as a SQL LIKE pattern instead of as literal text. Before it, searching `%` matched
 * every account in the system, turning "find a friend by name" into a way to page through the
 * whole user table.
 *
 * The escape character used here (`!`) has to stay in step with the `escape '!'` clause in
 * UserRepository.search — these tests pin the encoding that clause expects.
 */
class SearchQueryEscapingTest {

    @Test
    fun `percent is escaped so it matches literally instead of matching everything`() {
        assertEquals("!%", "%".escapeLikeWildcards())
        assertEquals("100!% cotton", "100% cotton".escapeLikeWildcards())
    }

    @Test
    fun `underscore is escaped so it matches literally instead of any single character`() {
        assertEquals("john!_doe", "john_doe".escapeLikeWildcards())
    }

    @Test
    fun `the escape character itself is escaped first`() {
        // If `!` weren't doubled, escaping the `%` below would emit `!%` — which the DB would then
        // read as an escaped percent, silently dropping the exclamation mark the user typed.
        assertEquals("!!!%", "!%".escapeLikeWildcards())
        assertEquals("hey!!", "hey!".escapeLikeWildcards())
    }

    @Test
    fun `ordinary names pass through completely untouched`() {
        listOf("priya", "Anna-Maria", "josé", "山田", "user.name", "a b").forEach {
            assertEquals(it, it.escapeLikeWildcards(), "escaping altered an ordinary name: $it")
        }
    }

    @Test
    fun `a wildcard-only query no longer collapses to match-everything`() {
        // The concrete enumeration attempt: two characters (clearing searchUsers' minimum length)
        // that previously formed the pattern `%%%%` and matched every row.
        assertEquals("!%!%", "%%".escapeLikeWildcards())
    }
}
