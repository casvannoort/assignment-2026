package nl.assessment.conferences.web

import nl.assessment.conferences.domain.ChildAlreadyBooked
import nl.assessment.conferences.domain.ChildDoubleBooked
import nl.assessment.conferences.domain.DomainException
import nl.assessment.conferences.domain.EveningHasEnded
import nl.assessment.conferences.domain.EveningNotFound
import nl.assessment.conferences.domain.Forbidden
import nl.assessment.conferences.domain.EveningOverlaps
import nl.assessment.conferences.domain.InvalidEvening
import nl.assessment.conferences.domain.SlotFull
import nl.assessment.conferences.domain.SlotNotFound
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

class Unauthenticated : RuntimeException("unknown or missing X-User-Id")

data class ErrorResponse(val code: String, val message: String)

@RestControllerAdvice
class ErrorHandler {

    @ExceptionHandler
    fun domain(e: DomainException): ResponseEntity<ErrorResponse> {
        val status = when (e) {
            is InvalidEvening -> HttpStatus.BAD_REQUEST
            is EveningOverlaps, is SlotFull, is ChildAlreadyBooked, is ChildDoubleBooked, is EveningHasEnded,
            -> HttpStatus.CONFLICT
            is SlotNotFound, is EveningNotFound -> HttpStatus.NOT_FOUND
            is Forbidden -> HttpStatus.FORBIDDEN
        }
        return ResponseEntity.status(status).body(ErrorResponse(e.code, e.message!!))
    }

    @ExceptionHandler
    fun unauthenticated(e: Unauthenticated) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse("UNAUTHENTICATED", e.message!!))

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun invalidRequest() =
        ResponseEntity.badRequest().body(ErrorResponse("INVALID_REQUEST", "request has missing or malformed fields"))
}
