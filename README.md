# Parent-teacher conferences

A backend that lets a teacher set up a conference evening and lets parents book a conversation.
Kotlin, Spring Boot, PostgreSQL. Choices and trade-offs are in [DECISIONS.md](DECISIONS.md).

## Start

Requires Docker.

```bash
docker compose up --build
```

The API runs on http://localhost:8080. Flyway creates the schema and the seed data on startup.
`docker compose down -v` stops it and removes the data.

## Who you are

There is no login. Every request says who you are with the `X-User-Id` header. The id decides whether you are a teacher or a parent.

| Id | Name | Role | Children / teaches |
|---|---|---|---|
| `00000000-0000-0000-0000-000000000101` | Ms. Jansen | teacher | Emma, Daan |
| `00000000-0000-0000-0000-000000000102` | Mr. de Vries | teacher | Emma, Lisa |
| `00000000-0000-0000-0000-000000000201` | Anna Bakker | parent | Emma |
| `00000000-0000-0000-0000-000000000202` | Mark Bakker | parent | Emma |
| `00000000-0000-0000-0000-000000000203` | Sara Visser | parent | Daan, Lisa |
| `00000000-0000-0000-0000-000000000204` | Kees Visser | parent | Lisa |

Children: Emma `…0301`, Daan `…0302`, Lisa `…0303` (same prefix as above).

## Try it out

The examples use `jq` to pick ids from the responses.

```bash
API=http://localhost:8080
JANSEN=00000000-0000-0000-0000-000000000101
ANNA=00000000-0000-0000-0000-000000000201
MARK=00000000-0000-0000-0000-000000000202
EMMA=00000000-0000-0000-0000-000000000301

# 1. Ms. Jansen creates an evening: 19:00-20:00, conversations of 15 minutes, one conversation per slot
EVENING=$(curl -s -X POST $API/evenings -H "X-User-Id: $JANSEN" -H 'Content-Type: application/json' \
  -d '{"date": "2035-03-12", "start": "19:00", "end": "20:00", "conversationMinutes": 15, "capacity": 1}' | jq -r .id)

# 2. She removes the 19:30 slot for a break
BREAK=$(curl -s $API/evenings/$EVENING/overview -H "X-User-Id: $JANSEN" | jq -r '.[2].slotId')
curl -s -X DELETE $API/evenings/$EVENING/slots/$BREAK -H "X-User-Id: $JANSEN" -w '%{http_code}\n'

# 3. Anna sees the free slots and books one for Emma
curl -s $API/evenings/$EVENING/slots -H "X-User-Id: $ANNA" | jq
SLOT=$(curl -s $API/evenings/$EVENING/slots -H "X-User-Id: $ANNA" | jq -r '.[0].id')
BOOKING=$(curl -s -X POST $API/evenings/$EVENING/bookings -H "X-User-Id: $ANNA" -H 'Content-Type: application/json' \
  -d "{\"childId\": \"$EMMA\", \"slotId\": \"$SLOT\"}" | jq -r .id)

# 4. Mark, Emma's other parent, sees the booking and moves it to 19:45
curl -s $API/bookings -H "X-User-Id: $MARK" | jq
LATER=$(curl -s $API/evenings/$EVENING/slots -H "X-User-Id: $MARK" | jq -r '.[-1].id')
curl -s -X PUT $API/evenings/$EVENING/bookings/$BOOKING -H "X-User-Id: $MARK" -H 'Content-Type: application/json' \
  -d "{\"slotId\": \"$LATER\"}" | jq

# 5. Ms. Jansen sees who comes when, and with which parent
curl -s $API/evenings/$EVENING/overview -H "X-User-Id: $JANSEN" | jq

# 6. Mark cancels the booking
curl -s -X DELETE $API/evenings/$EVENING/bookings/$BOOKING -H "X-User-Id: $MARK" -w '%{http_code}\n'
```

## API

| Method | Path | Who | What |
|---|---|---|---|
| GET | `/evenings` | both | teacher: own evenings, parent: upcoming evenings of their children's teachers |
| POST | `/evenings` | teacher | create an evening; slots are generated |
| GET | `/evenings/{id}/overview` | teacher | every slot, with which child comes with which parent |
| DELETE | `/evenings/{id}/slots/{slotId}` | teacher | remove an empty slot, for example for a break |
| GET | `/evenings/{id}/slots` | parent | slots with free places |
| POST | `/evenings/{id}/bookings` | parent | book `{childId, slotId}` |
| PUT | `/evenings/{id}/bookings/{bookingId}` | parent | move to `{slotId}` |
| DELETE | `/evenings/{id}/bookings/{bookingId}` | parent | cancel |
| GET | `/bookings` | parent | all bookings of my children |

Errors return `{"code": "...", "message": "..."}`:
`400` invalid input, `401` unknown user, `403` not allowed, `404` not found, `409` a rule is broken (for example `SLOT_FULL`, `CHILD_ALREADY_BOOKED`, `CHILD_DOUBLE_BOOKED`, `SLOT_HAS_BOOKINGS`, `EVENING_HAS_ENDED`).

## Tests

Requires Java 25 and Docker. Testcontainers starts a real PostgreSQL for the tests.

```bash
./gradlew test
```

With [mise](https://mise.jdx.dev), `mise install` gets the right Java version from `mise.toml`.

## Simulation

A separate run that uses the app the way a school would, through the real API, to look for logic errors. It is not part of `./gradlew test`. How it works and what it found: [SIMULATION.md](SIMULATION.md).

```bash
./gradlew simulation                                    # 5 seeds × 300 steps, 1 concurrent burst
./gradlew simulation -Pseed=42 -Pseeds=20 -Psteps=1000 -Pbursts=5
```

| Option | Default | What |
|---|---|---|
| `-Pseed` | 1 | first seed; the same seed gives the same sequential run |
| `-Pseeds` | 5 | number of seeds, each a new school |
| `-Psteps` | 300 | random steps per seed |
| `-Pbursts` | 1 | concurrent bursts of parents booking at once |

The report and the step logs are in `build/simulation/`. The run fails on any finding, or when a key outcome (for example `SLOT_FULL`) never happened.
