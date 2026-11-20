# Decisions

## Choices and why

### DDD-lite

The domain has real rules that must hold together: slot capacity, one booking per child per evening, no removing a slot that has bookings, and moving a booking as one atomic change. A DDD aggregate puts those rules in one place, in pure Kotlin, where they are easy to unit test without a database.

The problem itself is small, though, and full DDD brings ceremony that a four-hour assignment does not need. So we use only the parts that prevent bugs or keep the rules together:

| Used | Not used |
|---|---|
| One aggregate (`ConferenceEvening`) that guards the rules | Domain events, event sourcing |
| Value objects where they prevent mistakes: ids, `TimeRange` | Factories, specifications |
| A repository for the aggregate | An interface plus an implementation for every class |
| Domain errors as a sealed class | Multiple bounded contexts |
| Ubiquitous language (conversation, booking) | Mappers between domain, persistence and API models where they are not needed |

CQRS keeps the aggregate small: it only handles writes, and reads come straight from Postgres views.
