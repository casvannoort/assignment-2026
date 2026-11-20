package nl.assessment.conferences.domain

import java.util.UUID

@JvmInline value class EveningId(val value: UUID) { companion object { fun new() = EveningId(UUID.randomUUID()) } }
@JvmInline value class SlotId(val value: UUID) { companion object { fun new() = SlotId(UUID.randomUUID()) } }

@JvmInline value class TeacherId(val value: UUID)
@JvmInline value class ParentId(val value: UUID)
@JvmInline value class ChildId(val value: UUID)
