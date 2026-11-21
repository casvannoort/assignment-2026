# Plan: Parent-teacher conferences

Backend that lets a teacher set up a conference evening and lets parents book a time slot.
Assignment: https://github.com/infowijs/code-assessment-2026 (time box: 4 hours).

## Stack

- Kotlin, Spring Boot (web), JdbcTemplate (plain SQL, no JPA), Flyway (schema, views, seed data)
- Docker Compose runs both the app and PostgreSQL: `docker compose up` is all a reviewer needs
- JUnit 5, Testcontainers (real Postgres in tests)

## Approach

- **TDD, red-green-refactor.** Write one failing test, make it pass with the least code, then clean up.
- **Test pyramid:** mostly unit tests on the domain. A few integration tests (SQL, views, concurrency). A handful of e2e tests (HTTP).
- **Functional tests:** test behaviour ("a full slot rejects a booking"), not implementation (no mock verification).
- **DDD-lite:** one aggregate, value objects, repository, domain errors. Pure domain, no framework code in it. No events, factories or extra mappers (see DECISIONS.md).
- **CQRS:** commands (writes) go through the aggregate. Queries (reads) skip the domain and read from Postgres views.

## Domain (write side)

```
ConferenceEvening (aggregate root)        conversationLength
 ├─ Slot      id, TimeRange, capacity (max conversations)
 └─ Booking   id, slotId, childId, bookedBy: ParentId
```

**Ubiquitous language:**
- **Conversation:** a talk between a teacher and the parent(s) of one child. It sets the slot length and a slot's capacity.
- **Booking:** a reserved conversation. A parent books, moves or cancels it. One booking is one conversation, so there is no separate `Conversation` entity.

**Building blocks:**
- **Value objects:** `TimeRange`, ids as `@JvmInline value class` (`EveningId`, `SlotId`, `ChildId`, `ParentId`, `TeacherId`).
- **Ids:** UUIDs generated in the domain, so the aggregate needs no database to be created or tested.
- **Teachers, parents, children** are fixed seed data. The domain only knows their ids. Lookups answer which parents belong to a child and which teachers teach a child.
- **Domain errors:** a sealed class (`SlotFull`, `ChildAlreadyBooked`, `SlotHasBookings`, ...). The web layer maps them to HTTP status codes.
- **Time:** one `Clock` bean, fixed to `Europe/Amsterdam`. Domain, commands and queries all take "now" from it, never from the database.

**Repository:**
- **Load:** `SELECT ... FROM evening WHERE id = ? FOR UPDATE` (pessimistic lock), then its slots and bookings. Concurrent commands on the same evening wait for each other.
- **Save:** in the same transaction, upsert slots and bookings by id (`INSERT ... ON CONFLICT (id) DO UPDATE`). Delete only the rows that are no longer in the aggregate.

**Commands:**
- Each command is one `@Transactional` method: lock → check → call domain → save. The lock only works inside that one transaction.
- **Rules across evenings** (rules 8 and 9) cannot live in one aggregate. The command locks a shared row and runs a query before it calls the domain:
  - booking or moving: lock the evening, then the child (`SELECT ... FROM child WHERE id = ? FOR UPDATE`), then look for an overlapping booking of that child;
  - creating an evening: lock the teacher, then look for an overlapping evening of that teacher.
- Always lock in the same order (evening first, then child) to avoid deadlocks.

## Read side (CQRS)

Plain Postgres views (not projection tables or event sourcing, see DECISIONS.md), created in Flyway, read by small query services with JdbcTemplate. Every read goes through a view:

| View | Used by |
|---|---|
| `evening_overview` | teacher overview: every slot (empty ones too, via `LEFT JOIN`), child name, parent name |
| `slot_availability` | parent's slot list: capacity, booked count, free places |
| `child_bookings` | parent's bookings: all bookings of their children |

Access checks live in the services, not the controllers: commands check writes, query services check reads. A controller only checks the role (teacher or parent).

Queries that depend on "today" (upcoming evenings) get the date from the `Clock` as a parameter, not from `CURRENT_DATE`.

## Layers

```
domain/       pure Kotlin: aggregate, rules, errors               → unit tests
command/      transactional: lock → check → call domain → save    → integration + e2e
query/        all read logic: views, lookups, response DTOs       → integration + e2e
web/          controllers, error mapping, identity headers        → e2e
db/           aggregate repository: load (locked), save, and the overlap checks that need those locks → integration
```

## Rules

1. A teacher creates an evening from date, start, end, conversation length and capacity (default 1). Slots are generated from these values. A trailing part that is too short for a full slot is dropped. An evening in the past is rejected.
2. A slot accepts bookings up to its capacity. Different children can share a slot.
3. A child has at most one booking per evening.
4. A parent can only book for their own children. Every parent of the child sees, moves and cancels the booking. `bookedBy` is the parent who last booked or moved it.
5. A teacher can remove a slot only when it has no bookings.
6. A teacher sees and manages only their own evenings.
7. A child is linked to their teachers (seed data). A parent sees and books only evenings of their children's teachers.
8. A child cannot have two overlapping bookings, also not across evenings of different teachers. This holds per child, not per parent: two parents can split up.
9. A teacher cannot have two overlapping evenings.
10. An evening has passed once its end time has passed. After that it cannot be booked, moved or cancelled, and its slots cannot be removed.

## API

Identity comes from one header (no real login): `X-User-Id`. The role follows from the seed data: the id is either a teacher or a parent. An unknown id gives `401`.

| Method | Path | Who | What |
|---|---|---|---|
| GET | `/evenings` | both | teacher: own evenings, parent: upcoming evenings of their children's teachers |
| POST | `/evenings` | teacher | create evening and generate slots |
| GET | `/evenings/{id}/overview` | teacher | per slot: which child, which parent |
| DELETE | `/evenings/{id}/slots/{slotId}` | teacher | remove slot (e.g. a break) |
| GET | `/evenings/{id}/slots` | parent | slots with free places (counts only, no names) |
| POST | `/evenings/{id}/bookings` | parent | book `{childId, slotId}` |
| PUT | `/evenings/{id}/bookings/{bookingId}` | parent | move to `{slotId}` |
| DELETE | `/evenings/{id}/bookings/{bookingId}` | parent | cancel |
| GET | `/bookings` | parent | all bookings of my children |

Errors: `400` invalid input, `403` not your child or evening, `404` not found, `409` rule broken.
The body carries a code, for example `{"code": "SLOT_FULL", "message": "..."}`.

## Edge cases and pitfalls

| # | Case | Decision |
|---|---|---|
| 1 | Two parents book the last place in a slot at the same time | Pessimistic lock on the evening row. The second request waits, then sees the slot is full: `409 SLOT_FULL`. No client retry. |
| 2 | Both parents of one child book at the same time (or double click) | Same lock, plus rule 3: `409 CHILD_ALREADY_BOOKED`. A unique index `(evening_id, child_id)` is the safety net. |
| 3 | Teacher removes a slot that has bookings | `409`: "move or cancel the bookings first". |
| 4 | Teacher removes a slot while a parent books it | Same lock: they run one after the other, so the outcome is always consistent. |
| 5 | Move to a full slot | `409`. The original booking stays unchanged, because a move is one atomic change. |
| 6 | Move to the same slot | No-op, `200`. |
| 7 | Slot or booking belongs to another evening | `404`. |
| 8 | Booking for someone else's child, for a child of another teacher, or cancelling someone else's booking | `403`. |
| 9 | Invalid evening: end ≤ start, conversation length ≤ 0 or longer than the evening, capacity < 1, date in the past | `400`. |
| 10 | Booking, moving or cancelling after the evening's end time | `409`. The `Clock` is injected, so this is testable. |
| 11 | Time zones | Store `LocalDate` + `LocalTime` as the school's local time. The `Clock` is fixed to `Europe/Amsterdam`, because the container runs in UTC. |
| 12 | Deleting a whole evening that has bookings | Not built (not in the assignment). Same rule as #3 if it is added. |
| 13 | Privacy: parents see other children in a slot | The parent's slot list shows free places only, no names. |
| 14 | A child booked at 19:00 with teacher A and at 19:00 with teacher B | `409 CHILD_DOUBLE_BOOKED`. Lock on the child row, so two parents doing this at the same time cannot both succeed. |
| 15 | A teacher creates overlapping evenings, or double clicks "create" | `409 EVENING_OVERLAPS`. Lock on the teacher row. |
| 16 | A teacher changes or views another teacher's evening | `403`. |
| 17 | The second parent moves a booking | The overview shows the parent who moved it (`bookedBy` is updated). |

## Tests

Aim for good coverage of the rules and edge cases, with a logical pyramid: most at the bottom, few at the top.

- **Unit:** slot generation (#9, trailing part), capacity, rule 3, move (#5, #6), cancel, remove slot (#3), past evening (#10).
- **Integration:** the repository saves and loads the full aggregate (including removed bookings); the views return the expected rows (including empty slots). Concurrency tests run through the **command**, not the repository, because the lock lives in the command's transaction: last place (#1), same child at two teachers (#14).
- **E2E:** teacher creates → parent books → second parent sees the booking → teacher overview. Error mapping (`403`, `409`).

## Workflow

- One phase = one feature, built as a vertical slice: domain → database → HTTP.
- Per phase: a branch `phase-N-<name>` from `master`, and one PR against `master` that Cas reviews and merges.
- Per todo in `TODO.md`, TDD shows in the history as a pair of commits:
  1. `test:` the failing test (red). This commit does not compile or does not pass, on purpose.
  2. `feat:` the code that makes it pass (green), plus any refactor. **This commit also ticks the todo**, never a separate commit afterwards.
- Setup and acceptance (e2e) tests that pass straight away get a single `test:` or `chore:` commit.
- The next phase starts from the updated `master`.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org): `feat`, `test`, `fix`, `refactor`, `docs`, `chore`, with an optional scope (`evening`, `slot`, `booking`). Imperative, lowercase, no period, about 50 characters.

## Phases

| Phase | Feature | Assignment items |
|---|---|---|
| 0 | Setup: Gradle, Spring Boot, Docker Compose, Flyway schema + seed data, Testcontainers | — |
| 1 | Teacher creates an evening, slots are generated | 1, 2 |
| 2 | Parent sees free slots and books a conversation; every parent sees it | 3, 5 |
| 3 | Parent moves or cancels a booking | 4 |
| 4 | Teacher sees the overview and removes a slot | 6, 7 |
| 5 | README and DECISIONS.md | — |
| 6 | Simulation: random and concurrent runs against the real API to find logic errors (see `SIMULATION.md`) | 1–7 (checked) |
| 7 | Simulation improvements: test more on open evenings, cover the gaps the final review left, clearer reports | — |
| 8 | Fixes from an independent review: no `500` on a huge conversation length, no booking into a slot that has started | 1, 3, 4 |

The todos per phase are in `TODO.md`.

## Phase 7: Simulation improvements

Phase 6 found no logic errors in the app. This phase makes that result stronger: it closes the gaps the runs do not reach yet, and makes a finding easier to read. Only `src/test` changes; `src/main` stays as it is. Same workflow: a failing `test:` commit, then the commit that makes it pass and ticks the todo.

**Coverage**
- **Open evenings.** The clock moves forward during a run, so about a quarter of the steps hit evenings that have already ended. The picker mostly picks evenings that are still open (for example 4 in 5), and sometimes an ended one, so `EVENING_HAS_ENDED` stays covered.
- **Ending during a burst.** Concurrent bursts never pass an evening's end time, so the "nothing changes after the end" invariant never runs there. One wave per burst moves the clock past the end of an evening with bookings, then sends book, move and cancel requests for it. All must be refused.
- **Capacity 0 and below.** The picker never sends it. It sometimes sends 0 or -1 when creating an evening; the app must answer `400`.
- **S8 tests "slot full".** Its last check passes through `403` (Lisa is not Jansen's child). An extra check: Sara moves Daan's booking into the full 19:00 slot and gets `409 SLOT_FULL`. The reviewer's line stays as written.
- **No silent pass.** The removed-slot check passes when the parent cannot read `/slots`. A non-`200` reply becomes a finding ("could not check").

**Reports**
- Outcomes are counted per action and code (`book SLOT_FULL`), not per code only.
- Concurrent log lines name the parent, child and slot by position. A finding that lasts is reported once per run, not once per wave.
- A violation that disappears and comes back on the same row is reported again.

**Docs**
- README: how to run `./gradlew simulation`, with `-Pseed`, `-Pseeds`, `-Psteps` and `-Pbursts`.

**Done when** `./gradlew test` and `./gradlew simulation` pass, the report shows the new outcomes (`EVENING_HAS_ENDED` in concurrent mode, `400` for capacity), and `SIMULATION.md` Results is updated with a new run.

## Phase 8: Fixes from an independent review

A fresh reviewer, who only saw the assignment and the repository, checked a clean clone. All seven items work and `docker compose up -d` is enough. It found two bugs and two undocumented choices. Same workflow: a failing `test:` commit, then the commit that makes it pass and ticks the todo.

**Bugs**
- **`500` on a huge conversation length.** `"conversationMinutes": 9223372036854775807` overflows in `Duration.ofMinutes` before the domain can check it. A length longer than a day is now `400 INVALID_EVENING`, like any other invalid evening.
- **A slot that has started can still be booked.** While an evening runs, a parent can book or move into a slot whose start time has passed. Booking or moving into such a slot now gives `409 SLOT_HAS_STARTED`. The parent's free-slot list leaves those slots out, so it never offers what cannot be booked. Cancelling stays allowed until the evening ends.

**The simulation follows the new rule:** `SLOT_HAS_STARTED` becomes a known code with a reason the refusal checker can verify (the slot's start ≤ now), and "free slots are exactly the slots with room" also leaves out started slots.

**Docs:** `DECISIONS.md` names the two choices in "Rules I chose where the assignment was open": leftover time shorter than one conversation is dropped, and a slot can no longer be booked once it has started.

**Left out on purpose** (minor): `"capacity": 1.5` read as 1, Spring's default body for unknown paths and methods, slow saves on evenings with more than a thousand slots.

## Deliverables

- `README.md`: how to start the app and try it out. Curl examples use a date far in the future, so they do not expire.
- `DECISIONS.md` (max 2 pages, so 2–3 lines per decision):
  - choices made and why: DDD-lite, CQRS with views, pessimistic over optimistic locking, JdbcTemplate over JPA
  - the CQRS trade-off: commands check rules across evenings with a query. Strict CQRS avoids that, because a read model can lag behind. Here it does not lag: it is the same database and the same transaction, and the lock covers the race.
  - what was left out: see below
  - what breaks first at 100 schools: no school concept (mitigated: parents only see evenings of their children's teachers), lock waits if one evening gets a burst of bookings, no notifications, no real auth
  - how AI was used

## Out of scope

Real login, schools as a concept, managing teachers/parents/children and their links, notifications, per-slot capacity changes, UI, deleting an evening.
