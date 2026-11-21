# Decisions

## Choices and why

**Stack.** Kotlin, Spring Boot, JdbcTemplate, Flyway and PostgreSQL in Docker Compose: widely used, and close to the Infowijs stack. JdbcTemplate over JPA keeps the SQL visible. Postgres because the core problem is concurrent bookings, which row locks solve properly.

**DDD-lite.** The domain has rules that must hold together: slot capacity, one booking per child per evening, no removing a slot with bookings, moving as one atomic change. One aggregate, `ConferenceEvening`, guards them in pure Kotlin, so they are unit tested without a database. Only the parts that prevent bugs are used:

| Used | Not used |
|---|---|
| One aggregate that guards the rules | Domain events, event sourcing |
| Value objects: ids as value classes, `TimeRange` | Factories, specifications |
| A repository for the aggregate | An interface plus an implementation for every class |
| Domain errors as a sealed class | Multiple bounded contexts |
| Ubiquitous language: conversation, booking | Mappers where they are not needed |

**CQRS with plain SQL views.** Writes go through the aggregate; reads skip the domain and read Postgres views (`evening_summary`, `slot_availability`, `child_bookings`, `evening_overview`, `app_user`). A view stores no data, so the read side is always up to date and nothing is stored twice. Projection tables or event sourcing would give a separate read model, at the cost of more code and a read side that can lag behind. Worth it only when reads get heavy.

**The CQRS trade-off.** Some rules span evenings: a child cannot be in two conversations at once, and a teacher cannot have overlapping evenings. One aggregate cannot see that, so the command runs a query before it saves. Strict CQRS avoids that, because a read model can lag. Here it cannot: same database, same transaction.

**Pessimistic locking.** Each command locks what it changes with `SELECT … FOR UPDATE`: the evening (capacity), the child (overlap across evenings) or the teacher (overlapping evenings). A competing request waits a few milliseconds and then gets a clear answer such as `SLOT_FULL`, with no retry needed. Optimistic locking makes the loser retry; with about 30 parents per evening, waiting is simpler. Locks are always taken in the same order (evening, then child) to avoid deadlocks. Concurrency tests prove each lock: they fail when the lock is removed.

**Rules I chose where the assignment was open.**
- Capacity is set per evening when it is created (default 1); every slot gets it.
- Leftover time shorter than one conversation is dropped: 19:00–20:00 in 25 minutes gives two slots.
- One booking per child per evening. The overlap rule is per child, not per parent: two parents can split up.
- Any parent of the child can move or cancel. The overview shows the parent who last booked or moved.
- A slot with bookings cannot be removed (`409`); the bookings are never silently lost.
- An evening has ended at its end time. After that nothing changes.
- A slot can no longer be booked or moved into once it has started (`409`); a booking can still be cancelled.
- A parent only sees and books evenings of their children's teachers.
- Identity comes from one header, `X-User-Id`; the role follows from the seed data.
- Times are the school's local time; the `Clock` is fixed to `Europe/Amsterdam`.

**Tests.** TDD: for each rule a failing `test:` commit, then the commit that makes it pass (setup and end-to-end tests had no red step). Of the 84 tests, 29 are pure domain tests, 27 run commands, repository and views against a real Postgres (Testcontainers), 24 test the HTTP layer and 4 are end-to-end. The middle is heavier than a classic pyramid, because the locks and views only prove themselves against a real database.

## Left out on purpose

- **One conversation length per evening.** A single longer conversation needs either two adjacent slots for one child or slots with their own length.
- Changing the capacity of one slot, deleting an evening, a teacher cancelling a booking.
- Real login, managing teachers, parents and children, schools, notifications, a UI.

## What breaks first at a hundred schools

1. **Security.** Anyone can send any `X-User-Id` and act as any parent or teacher. This must be real authentication before going live.
2. **No schools.** All data shares one space. Parents only see their children's teachers, which hides most of it, but there is no school boundary or school admin.
3. **Missing indexes.** Postgres does not index foreign keys. `slot.evening_id`, `booking.slot_id`, `booking.child_id` and `evening.teacher_id` are scanned, and the views get slower as data grows. These indexes are the first migration I would add.
4. **Rush hour.** When booking opens, parents of one evening wait on that evening's lock, and the connection pool (10 by default) fills up. Per evening that is fine; with many evenings opening at once the app needs more connections or instances.
5. **No notifications.** Parents only find out about changes by looking.

## How I used AI

I used Claude Code as a pair programmer, not as a fleet of agents. We first worked out the plan together in `PLAN.md`: it proposed options with trade-offs, I chose (Spring Boot, DDD-lite, pessimistic locking, views). Then it built one phase per pull request with TDD, one todo at a time. It committed only on the phase branch, and pushed only after I said so. I reviewed every pull request, asked it to explain parts I wanted to understand (Spring beans, `@Transactional`, value classes, views) and asked for changes: views for every read, lookups out of the aggregate repository, `404`s instead of empty lists, access checks in the query services, and blocking slot removal after an evening.
