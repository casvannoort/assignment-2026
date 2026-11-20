# Simulation: looking for logic errors

## Why

The 80 tests check examples we thought of ourselves. This simulation uses the app the way a school would, at a larger scale and with random and concurrent actions, to find logic errors: places where the app does something that does not follow the original assignment, or that breaks one of our own rules.

## Goals, non-goals, anti-goals

- **Goal:** a list of logic errors, each with a reproducible scenario (seed plus the actions that led to it).
- **Goal:** tell apart errors against the **assignment** from errors against **our own decisions**.
- **Non-goal:** fixing the errors. Fixes come later, one by one, after Cas has chosen.
- **Non-goal:** performance benchmarking.
- **Anti-goal:** a checker that re-implements the app's rules. If the same person writes a rule twice, the same misunderstanding passes both times. Checks are declarative ("never more bookings than capacity"), or come from an independent reviewer.
- **Anti-goal:** results that cannot be reproduced. Every run has a seed.

## Approach

**Chosen:** one random-walk simulator that drives the real API (Spring app plus a real Postgres from Testcontainers), with a two-layer checker. It runs in two modes, sequential for logic and concurrent for races.

**Considered and not chosen:**
- **Model-based testing on the domain with jqwik.** It is fast and shrinks failures automatically. But the aggregate does not know about permissions, overlap across evenings, views or HTTP codes, so it misses half the rules. jqwik combined with Spring and a database adds friction.
- **Only a load test over HTTP.** It finds races, but random load alone does not find logic errors in sequential flows.

## Design

### Where it lives
- Branch `simulation`. Nothing goes to `master` until Cas has seen the results.
- Code in `src/test/kotlin/.../simulation/`, with JUnit tag `simulation`. `./gradlew test` excludes the tag, and a new task `./gradlew simulation` runs only that tag.
- Parameters as Gradle properties: `-Pseed=…`, `-Psteps=…`.

### School generator
- Inserts a generated school with JDBC into the test database, next to the existing seed:
  - 20 teachers, 300 children and about 450 parents;
  - some children have two parents, some parents have two or three children;
  - each child has 1 to 4 teachers.
- Deterministic from the seed.

### Controllable clock
- A test-only `MutableClock` bean (`@Primary`) replaces the real clock, so a run can move time forward, for example past the end of an evening.

### Actors and actions
- **Teachers:** create an evening (sometimes overlapping or invalid), list evenings, view the overview, remove a slot.
- **Parents:**
  - list evenings and free slots;
  - book (sometimes for someone else's child, a full slot or a removed slot);
  - move;
  - cancel;
  - list their bookings.
- **Others:** an unknown user and wrong roles.
- **Time:** moves forward now and then.
- Each step picks an actor and an action at random from the seed. Each request and response is logged.

### Modes
- **Sequential:** for example 20 seeds × 300 steps. After every step, the full checker runs.
- **Concurrent:** "booking opens". About 200 parents act at once on virtual threads (book, double-click, move), with several bursts. The checker runs after each burst.

### Checker layer 1: the assignment
- **Scenarios from an independent reviewer.** A fresh subagent gets only the original assignment text (the README of github.com/infowijs/code-assessment-2026, commit `d51c39b`, copied to `src/test/resources/simulation/assignment.md`) and the API section of our README. It does not get our plan, our decisions or our code. It writes the behaviour it expects, in plain Given / When / Then. Those scenarios are turned into tests word for word. Where the app disagrees, the result is listed, not "fixed in the test".
- **Per-step checks for the seven assignment items:**
  1. slots match date, start, end and length;
  2. capacity defaults to 1 when it is not given;
  3. free slots are exactly the slots with room;
  4. a move or cancel takes effect;
  5. every parent of the child sees the booking;
  6. the overview shows every booking with child, time and parent;
  7. a removed slot is gone.

### Checker layer 2: our own decisions
- SQL straight on the database after each step or burst, so it does not go through the app:
  1. slots lie within the evening, each one conversation long and without overlap;
  2. bookings per slot ≤ capacity;
  3. no booking points to a missing slot;
  4. at most one booking per child per evening, and no overlapping bookings for a child across evenings;
  5. the booking parent is a parent of the child, and the evening's teacher teaches the child;
  6. no overlapping evenings per teacher;
  7. nothing changes on an evening after its end time.

### Response consistency
- **In sequential mode, the database is compared before and after every request:**
  - a `2xx` must have changed exactly what it says;
  - a `4xx` must have changed nothing.
- Every error body has a known code.
- The views (`/bookings`, `/overview`, `/slots`) must agree with the tables.

### Report
- `SIMULATION.md` gets a **Results** section. It has one row per check (layer 1, layer 2, consistency) with pass or fail.
- Each failure gets the seed, the mode and the shortest action log that reproduces it, cut down by hand.

### Calls made after the builder check

- **Which assignment text counts:** the README of the assignment repo at commit `d51c39b`. It is copied into the simulation resources so the reviewer and the report always use the same version. A failure counts as "against the assignment" only when a reviewer scenario or one of the seven items fails.
- **Reproducing concurrent failures:** a seed cannot replay the order in which threads run. So a failure found in concurrent mode is reported with its seed and burst and marked "intermittent", and then rebuilt by hand as a deterministic test with latches in test code. No changes to `src/main` for this. Sequential replays refer to entities by their position in the generated school, not by their random UUIDs, so the seed alone is enough there.
- **Connection pool in concurrent mode:** the simulation raises the pool size and timeout in its own test properties, so the report stays about logic. Any `500` or timeout is still reported, in a separate "capacity" row. DECISIONS.md already names that risk.

## Left to the builder

- Exact sizes and step counts, as long as a run stays under a few minutes.
- How a disagreement between the reviewer's scenarios and our decisions is recorded. It is listed in the report either way.

## Build plan

Same workflow as the phases: per task a failing `test:` commit, then the commit that makes it pass and ticks the todo in `TODO.md`. All code lives in `src/test/kotlin/nl/assessment/conferences/simulation/`. Nothing in `src/main` changes.

### Calls on sizes (left to the builder above)

- **Sequential mode uses a small, dense school:** 3 teachers, 12 children, about 18 parents. With 300 children, random actions would almost never collide, and logic errors hide in collisions.
- **Concurrent mode uses the large school** (20 teachers, 300 children, about 450 parents) for realistic load.

### Review focus (failure modes the tests below must pin)

1. **A quiet run is not a correct run.** If random actions never hit a full slot, an ended evening or a forbidden child, "no findings" means nothing. Every run reports how often each outcome occurred, and a test fails if a key outcome (`SLOT_FULL`, `CHILD_ALREADY_BOOKED`, `CHILD_DOUBLE_BOOKED`, `FORBIDDEN`, `EVENING_HAS_ENDED`, `SLOT_HAS_BOOKINGS`) never happened.
2. **A checker that never fires proves nothing.** Every layer 2 invariant gets a test that breaks the database on purpose and expects the invariant to report it.
3. **Random UUIDs break replay.** Actions refer to evenings, slots and bookings by their position in what the simulator has seen, never by id.
4. **Time must really pass the end of evenings.** Some steps must happen after an evening's end time, otherwise rule 10 is never exercised.
5. **Concurrent checks must not report false races.** The per-request before/after comparison only runs in sequential mode. In concurrent mode only the invariants run, after each burst.

### Tasks

**1. Harness.** `build.gradle.kts`: `test` excludes tag `simulation`, a new task `simulation` (type `Test`) includes only that tag and passes `seed` and `steps` as system properties. `MutableClock(start: Instant, zone: ZoneId) : Clock` with `advance(by: Duration)`. `SimulationConfig` (`@TestConfiguration`): a `@Primary` `MutableClock` bean starting at `2030-01-06T08:00` Europe/Amsterdam. Copy of the assignment in `src/test/resources/simulation/assignment.md`.
Test: `MutableClockTest`, where `advance(90 min)` moves `instant()` 90 minutes. Check: `./gradlew test` does not run tagged tests, and `./gradlew simulation` runs only them.

**2. School generator.** `School(teachers, parents, children, parentsOf: Map<UUID, List<UUID>>, teachersOf: Map<UUID, List<UUID>>)`. `SchoolGenerator.generate(seed: Long, teachers: Int, children: Int): School` and `insert(jdbc)` / `reset(jdbc)`. `reset` deletes all evenings, slots, bookings and generated people, and keeps the fixed seed rows.
Tests:
- the same seed gives the same school;
- after `insert`, the counts in the database match;
- some children have two parents, some parents have several children, and every child has 1 to 4 teachers.

**3. API client and consistency checker.** `Api(port: Int)` over Java `HttpClient`. It returns `Reply(status: Int, body: JsonNode?)`, with one method per endpoint. `Snapshot.take(jdbc)` holds the rows of `evening`, `slot` and `booking`. `Consistency.check(action, reply, before, after): List<Finding>`:
- a `4xx` means `before == after`;
- book adds exactly one booking with the returned id;
- move changes only that booking's `slot_id` and `booked_by`;
- cancel removes only that booking;
- remove slot removes only that slot;
- create adds one evening and its slots;
- every error body has a code from the known list.

Tests: a correct book, and a `409`, give no findings. A hand-made "after" with an extra row gives one.

**4. Layer 2 invariants.** `Invariants.check(jdbc, now: LocalDateTime, endedSnapshot): List<Finding>`, with one SQL query per invariant 1–7 from the design.
Tests: a clean database gives no findings. For each invariant, a test inserts a violating row directly with JDBC and expects exactly that invariant to be reported.

**5. Layer 1 assignment checks.** `AssignmentChecks`, with one function per assignment item 1–7. Each compares an API reply with what the assignment text says, computed from the request, not from the app.
- Example: `slotsMatchRequest(request, reply)` expects `floor((end - start) / length)` contiguous slots from `start`.

Tests: unit tests with hand-made replies, one passing and one failing per item.

**6. Sequential simulator and report.** `ActionPicker(random, school, seen)` picks an actor and one action:
- create (sometimes invalid or overlapping);
- book (sometimes for someone else's child or a full slot);
- move, cancel, remove slot;
- list, overview;
- advance the clock;
- an unknown user.

`SequentialSimulationTest` runs seeds × steps. After each step it runs the consistency, layer 1 and layer 2 checks, writes `build/simulation/report.md` with the outcome counts and findings, and fails on any finding or on a key outcome with count 0.
Tests:
- the same seed gives the same action log;
- a short run (1 seed × 50 steps) completes.

**7. Independent scenarios.** A fresh subagent gets only `assignment.md` and the API section of `README.md`, and writes Given / When / Then scenarios to `src/test/resources/simulation/scenarios.md`. `AssignmentScenariosTest` implements them word for word. Disagreements with our decisions are reported, not edited away.

**8. Concurrent simulator.** `ConcurrentSimulationTest`, with the large school and a raised pool (`spring.datasource.hikari.maximum-pool-size=50`, `connection-timeout=30000`). Each burst:
- every teacher creates an evening;
- about 200 parents book at the same time on virtual threads (with double clicks and moves).

After each burst the invariants run, and `500`s and timeouts are counted in a "capacity" row. Findings are marked intermittent and get a hand-made deterministic test.

**9. Run and report.** Run the full simulation (20 seeds × 300 steps, and 5 concurrent bursts), then fill **Results** below from `build/simulation/report.md`.

## Results

_To be filled in after task 9._
