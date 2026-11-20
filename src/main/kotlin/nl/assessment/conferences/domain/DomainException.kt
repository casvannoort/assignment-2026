package nl.assessment.conferences.domain

sealed class DomainException(val code: String, message: String) : RuntimeException(message)

class InvalidEvening(message: String) : DomainException("INVALID_EVENING", message)
class EveningOverlaps : DomainException("EVENING_OVERLAPS", "the teacher already has an evening at this time")
