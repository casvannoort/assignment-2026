package nl.assessment.conferences.domain

sealed class DomainException(val code: String, message: String) : RuntimeException(message)

class InvalidEvening(message: String) : DomainException("INVALID_EVENING", message)
