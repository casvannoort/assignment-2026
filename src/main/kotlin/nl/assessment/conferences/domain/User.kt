package nl.assessment.conferences.domain

sealed interface User {
    data class Teacher(val id: TeacherId) : User
    data class Parent(val id: ParentId) : User
}
