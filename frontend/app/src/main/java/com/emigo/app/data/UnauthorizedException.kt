package com.emigo.app.data

/** Raised by a repository call that came back 401 — distinguished from every other failure
 * shape (network blip, validation error, server error) specifically so a caller that needs to
 * react to "this session is dead" (see WidgetUpdateWorker, which has no NetworkModule.sessionExpired
 * collector of its own when the app process isn't alive) can tell the two apart via
 * Result.exceptionOrNull() without parsing an error message string. */
class UnauthorizedException : Exception("Unauthorized")
