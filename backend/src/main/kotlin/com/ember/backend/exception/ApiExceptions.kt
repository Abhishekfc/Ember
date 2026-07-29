package com.ember.backend.exception

import org.springframework.http.HttpStatus

open class ApiException(val status: HttpStatus, message: String) : RuntimeException(message)

class EmailAlreadyRegisteredException :
    ApiException(HttpStatus.CONFLICT, "An account with this email already exists")

class UsernameAlreadyTakenException :
    ApiException(HttpStatus.CONFLICT, "That username is already taken")

class InvalidCredentialsException :
    ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password")

class ResourceNotFoundException(message: String) : ApiException(HttpStatus.NOT_FOUND, message)

class InvalidFriendRequestException(message: String) : ApiException(HttpStatus.BAD_REQUEST, message)

// Reaction feature disabled — see PhotoReactionService's own comment.
// class InvalidReactionException(message: String) : ApiException(HttpStatus.BAD_REQUEST, message)

class SubscriptionVerificationException(message: String) : ApiException(HttpStatus.BAD_REQUEST, message)

class PurchaseTokenAlreadyClaimedException :
    ApiException(HttpStatus.CONFLICT, "This purchase is already linked to a different account")

class RateLimitExceededException :
    ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts — please try again later")
