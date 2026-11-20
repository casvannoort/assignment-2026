# TODO

Tick a todo in the same commit that does the work. See the workflow in `PLAN.md`.

## Phase 0: Setup

- [x] Gradle Kotlin project with Spring Boot (web, jdbc), Flyway and the Postgres driver
- [x] `Dockerfile` and `docker-compose.yml` that run the app and Postgres
- [x] Flyway V1: schema (teacher, parent, child, parent_child, teacher_child, evening, slot, booking, unique index `(evening_id, child_id)`)
- [x] Flyway V2: seed data (2 teachers, 4 parents, 3 children; one child with two parents, one parent with two children, one parent unrelated to the two-parent child; teacher–child links)
- [x] Testcontainers base test: the app starts against a real Postgres

## Phase 1: Teacher creates an evening

- [x] Domain: ids as value classes and `TimeRange`
- [x] Domain: generate slots from start, end and conversation length; a trailing part that is too short is dropped
- [x] Domain: reject an invalid evening (end ≤ start, conversation length ≤ 0 or longer than the evening, capacity < 1, date in the past)
- [x] `Clock` bean fixed to `Europe/Amsterdam`
- [x] Repository: save and load an evening with its slots (integration)
- [x] Identity: `X-User-Id` resolves to a teacher or a parent; an unknown id gives `401`
- [x] Command: create an evening; lock the teacher and reject an overlapping evening (`409 EVENING_OVERLAPS`)
- [x] `POST /evenings` (teacher only) with error mapping `400` / `403` / `409`
- [x] `GET /evenings`: a teacher sees their own evenings, a parent sees upcoming evenings of their children's teachers (date from the `Clock`)
- [x] E2E: a teacher creates an evening and sees it in the list

## Phase 2: Parent books a conversation

- [x] Domain: book a slot; a full slot gives `SlotFull`
- [x] Domain: one booking per child per evening (`ChildAlreadyBooked`)
- [x] Domain: no booking after the evening's end time
- [x] Repository: lock on load (`FOR UPDATE`), upsert bookings
- [x] Command: book, in one transaction; a parent can only book for their own child, and only at that child's teacher (`403`)
- [x] Command: lock the child and reject an overlapping booking at another evening (`409 CHILD_DOUBLE_BOOKED`)
- [x] Integration (through the command): two concurrent bookings on the last place give one success and one `SLOT_FULL`
- [x] Integration (through the command): the same child booked at two teachers at the same time gives one success and one `CHILD_DOUBLE_BOOKED`
- [x] View `slot_availability` + `GET /evenings/{id}/slots`
- [x] `POST /evenings/{id}/bookings`
- [x] View `child_bookings` + `GET /bookings`
- [x] E2E: a parent books, the second parent of the child sees the booking

## Phase 3: Parent moves or cancels a booking

- [x] Domain: move to another slot; a full slot gives `SlotFull` and the original booking stays unchanged
- [x] Domain: moving to the same slot is a no-op
- [x] Domain: moving sets `bookedBy` to the parent who moved it
- [x] Domain: cancel a booking
- [x] Repository: deletes bookings that are no longer in the aggregate (integration)
- [x] Command: move checks for an overlapping booking at another evening, like booking does
- [x] `PUT` and `DELETE /evenings/{id}/bookings/{bookingId}`: any parent of the child (`403` otherwise, `404` for another evening)
- [x] E2E: the second parent moves the booking, then cancels it

## Phase 4: Teacher sees the overview and removes a slot

- [x] Domain: remove a slot; a slot with bookings gives `SlotHasBookings`
- [x] `DELETE /evenings/{id}/slots/{slotId}` (own evenings only, `403` otherwise)
- [x] View `evening_overview` with empty slots (`LEFT JOIN`) + `GET /evenings/{id}/overview` (own evenings only)
- [x] E2E: a teacher removes a slot for a break and sees the overview

## Phase 5: Docs

- [x] README: how to start, the seed users, curl examples with a date far in the future
- [x] DECISIONS.md: choices, the CQRS trade-off, what was left out, what breaks first at 100 schools, AI use (max 2 pages)

## Simulation (branch `simulation`, see `SIMULATION.md`)

- [x] Harness: `simulation` Gradle task and tag, `MutableClock`, copy of the assignment
- [x] School generator: deterministic school, insert and reset
- [ ] API client and consistency checker (before/after per request)
- [ ] Layer 2 invariants, each proven by a test that breaks the database on purpose
- [ ] Layer 1 assignment checks, one per assignment item
- [ ] Sequential simulator with outcome counts and report
- [ ] Independent scenarios from a fresh reviewer, implemented word for word
- [ ] Concurrent simulator with bursts and a capacity row
- [ ] Run the full simulation and fill in the results
