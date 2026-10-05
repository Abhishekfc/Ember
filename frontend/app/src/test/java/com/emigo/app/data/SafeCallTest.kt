package com.emigo.app.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeCallTest {

    @Test
    fun passesASuccessfulResultThrough() = runBlocking {
        val result = safeCall { Result.success(5) }
        assertEquals(5, result.getOrNull())
    }

    @Test
    fun passesAnExplicitFailureThrough() = runBlocking {
        val failure = Exception("backend said no")
        val result = safeCall<Int> { Result.failure(failure) }
        assertTrue(result.isFailure)
        assertEquals("backend said no", result.exceptionOrNull()?.message)
    }

    @Test
    fun aDroppedConnectionBecomesAFailureInsteadOfACrash() = runBlocking {
        val result = safeCall<Int> { throw IOException("unreachable") }
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull()?.message.isNullOrBlank())
    }

    @Test
    fun otherExceptionsAreNotSwallowed() {
        // Only network drops are turned into a failure. A real bug must still surface.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { safeCall<Int> { throw IllegalStateException("bug") } }
        }
    }
}
