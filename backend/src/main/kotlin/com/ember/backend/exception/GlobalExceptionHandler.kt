package com.ember.backend.exception

import org.hibernate.StaleStateException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Instant

data class ErrorResponse(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val message: String?,
)

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ex.status).body(
            ErrorResponse(status = ex.status.value(), error = ex.status.reasonPhrase, message = ex.message)
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(status = HttpStatus.BAD_REQUEST.value(), error = "Bad Request", message = message)
        )
    }

    /** A unique-constraint violation from a genuine concurrent race (e.g. two people adding each
     * other at the same moment, a duplicate recipientId, a username taken a moment ago by
     * someone else) is the DB correctly doing its job, not a server bug — it should read to the
     * client as "that just changed under you," a 409, not an opaque 500. */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        logger.warn("Data integrity violation (likely a concurrent race): {}", ex.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(status = HttpStatus.CONFLICT.value(), error = "Conflict", message = "That action conflicts with existing data — please try again")
        )
    }

    /** Guards a narrower race than the DB constraints above can prevent outright: two concurrent
     * writes to the *same* row (e.g. accepting a friend request the instant the other side
     * removes it) can make an UPDATE/DELETE affect zero rows where exactly one was expected. */
    @ExceptionHandler(IncorrectResultSizeDataAccessException::class, ObjectOptimisticLockingFailureException::class, StaleStateException::class)
    fun handleConcurrentModification(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.warn("Concurrent modification race: {}", ex.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(status = HttpStatus.CONFLICT.value(), error = "Conflict", message = "This was just changed elsewhere — please refresh and try again")
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class, MissingServletRequestParameterException::class)
    fun handleBadRequest(ex: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(status = HttpStatus.BAD_REQUEST.value(), error = "Bad Request", message = "Malformed request")
        )

    // NoResourceFoundException (not just NoHandlerFoundException) because Spring Boot 3's default
    // routing tries the static-resource handler before giving up on an unmatched path, and that's
    // what actually throws for a plain unknown URL — NoHandlerFoundException only fires under a
    // non-default config this app doesn't set. Both mean the same thing to a caller ("no such
    // endpoint") and neither is a real server error, so both get a plain 404 with no ERROR-level
    // stack trace — left uncaught here, NoResourceFoundException fell through to the generic
    // handleUnexpected below and logged a full stack trace for something as routine as a typo'd
    // URL or a client hitting an endpoint (e.g. /actuator/health) this app never implemented.
    @ExceptionHandler(NoHandlerFoundException::class, NoResourceFoundException::class)
    fun handleNotFound(ex: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(status = HttpStatus.NOT_FOUND.value(), error = "Not Found", message = "No such endpoint")
        )

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
            ErrorResponse(status = HttpStatus.METHOD_NOT_ALLOWED.value(), error = "Method Not Allowed", message = ex.message)
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = "Internal Server Error",
                message = "Something went wrong",
            )
        )
    }
}
