package nl.assessment.conferences.domain

sealed class DomainException(val code: String, message: String) : RuntimeException(message)

class InvalidEvening(message: String) : DomainException("INVALID_EVENING", message)
class EveningOverlaps : DomainException("EVENING_OVERLAPS", "the teacher already has an evening at this time")
class SlotNotFound : DomainException("SLOT_NOT_FOUND", "slot not found in this evening")
class SlotFull : DomainException("SLOT_FULL", "slot is full")
class ChildAlreadyBooked : DomainException("CHILD_ALREADY_BOOKED", "the child already has a booking this evening")
class EveningHasEnded : DomainException("EVENING_HAS_ENDED", "the evening has already ended")
class EveningNotFound : DomainException("EVENING_NOT_FOUND", "evening not found")
class Forbidden(message: String) : DomainException("FORBIDDEN", message)
