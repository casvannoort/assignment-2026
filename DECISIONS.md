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

### CQRS read side: plain SQL views

Reads go through plain Postgres views (`evening_summary`, `app_user`, ...) over the same tables the aggregate writes to. A view stores no data: Postgres runs its query each time it is read. So the read side is always up to date, nothing is stored twice, and the shape of each read lives in one place in the schema.

Considered and not chosen: projection tables updated from domain events when the aggregate is saved, and event sourcing. Both store a separate read model, which means more code and a read side that can fall behind. They become worth it when reads get heavy or the read model must scale on its own.

## Left out on purpose

- **One conversation length per evening.** All slots of an evening have the same length, but each evening can have its own. A teacher cannot plan a single longer conversation within an evening. Supporting that means either letting a child book two adjacent slots (relaxing "one booking per child per evening") or giving slots their own length.
