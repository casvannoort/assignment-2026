package nl.assessment.conferences.web

import nl.assessment.conferences.domain.DomainException
import nl.assessment.conferences.domain.EveningOverlaps
import nl.assessment.conferences.domain.InvalidEvening
import nl.assessment.conferences.domain.SlotFull
import nl.assessment.conferences.domain.SlotNotFound
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class Unauthenticated : RuntimeException("unknown or missing X-User-Id")
class Forbidden(message: String) : RuntimeException(message)

data class ErrorResponse(val code: String, val message: String)

@RestControllerAdvice
class ErrorHandler {

    @ExceptionHandler
    fun domain(e: DomainException): ResponseEntity<ErrorResponse> {
        val status = when (e) {
            is InvalidEvening -> HttpStatus.BAD_REQUEST
            is EveningOverlaps, is SlotFull -> HttpStatus.CONFLICT
            is SlotNotFound -> HttpStatus.NOT_FOUND
        }
        return ResponseEntity.status(status).body(ErrorResponse(e.code, e.message!!))
    }

    @ExceptionHandler
    fun unauthenticated(e: Unauthenticated) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse("UNAUTHENTICATED", e.message!!))

    @ExceptionHandler
    fun forbidden(e: Forbidden) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse("FORBIDDEN", e.message!!))

    @ExceptionHandler
    fun unreadable(e: HttpMessageNotReadableException) =
        ResponseEntity.badRequest().body(ErrorResponse("INVALID_REQUEST", "request body is missing fields or is not valid JSON"))
}
