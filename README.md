# MediFlow

A clinic platform API: patients book appointments with doctors, with double-booking protection
enforced by the database. Coming later: clinical notes, AI-drafted discharge summaries, a React
frontend and AWS deployment.

> Personal learning project, built with synthetic data.

## Tech stack

- Java 21, Spring Boot 4.2 (Web MVC, Validation, Data JPA, Actuator), Maven wrapper
- PostgreSQL 16 with Flyway migrations
- JUnit 5 + MockMvc tests
- FHIR-style field names and codes (`givenName`, `birthDate`, `"gender": "female"`, `"status": "booked"`)

## Getting started

### Prerequisites

- JDK 21 (`java -version` should report 21; if you have several JDKs on macOS, run
  `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` first)
- Docker (Docker Desktop, Colima or similar)

### 1. Start PostgreSQL

```bash
docker run -d --name mediflow-db -p 5432:5432 \
  -e POSTGRES_DB=mediflow -e POSTGRES_USER=mediflow -e POSTGRES_PASSWORD=mediflow \
  postgres:16
```

Port 5432 must be free; stop any locally installed PostgreSQL first. Next time, just run
`docker start mediflow-db`.

### 2. Run the tests

```bash
cd api
./mvnw test
```

The integration tests run against the database above (each test rolls back its changes).

### 3. Run the API

```bash
./mvnw spring-boot:run
curl localhost:8080/actuator/health   # {"status":"UP", ...}
```

Flyway creates the schema on startup. Connection settings default to the container above and can
be overridden with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/actuator/health` | Health check |
| `GET` | `/api/patients` | List patients |
| `GET` | `/api/patients/{id}` | Get a patient |
| `POST` | `/api/patients` | Create a patient |
| `PUT` | `/api/patients/{id}` | Replace a patient |
| `DELETE` | `/api/patients/{id}` | Delete a patient (409 if they have appointments) |
| `GET` | `/api/doctors` | List doctors |
| `GET` | `/api/doctors/{id}` | Get a doctor |
| `POST` | `/api/doctors` | Create a doctor |
| `PUT` | `/api/doctors/{id}` | Replace a doctor |
| `DELETE` | `/api/doctors/{id}` | Delete a doctor (409 if they have appointments) |
| `GET` | `/api/appointments?doctorId=&patientId=&status=` | List appointments (all filters optional), ordered by start |
| `GET` | `/api/appointments/{id}` | Get an appointment |
| `POST` | `/api/appointments` | Book an appointment (409 if the slot overlaps) |
| `PUT` | `/api/appointments/{id}` | Reschedule a booked appointment (new time and/or doctor; the patient cannot change) |
| `POST` | `/api/appointments/{id}/cancel` | Cancel (optional body: `{"cancellationReason": "..."}`) |
| `POST` | `/api/appointments/{id}/fulfill` | Mark as fulfilled (only once it has started) |

### Booking rules

- An appointment is the half-open interval `[start, end)`, so back-to-back slots are allowed.
- A new or rescheduled appointment is rejected with **409 Conflict** if it overlaps another
  **booked** appointment for the same doctor, or for the same patient.
- Cancelled and fulfilled appointments do not block a slot, and are final: re-booking means
  creating a new appointment. Appointments are never deleted.
- `start` must be in the future, `end` after `start`, and the duration at most 8 hours.
  Times are ISO-8601 with an offset (`2026-12-01T10:00:00Z` or `...+05:30`) and returned in UTC.
- Unknown patient or doctor, or an inactive doctor: **422 Unprocessable Content**.

The overlap rule is checked in the service for a clear error message, and enforced by PostgreSQL
exclusion constraints (`btree_gist`) so two simultaneous requests can never both succeed.

### Errors

Errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem JSON:

```json
{
  "status": 400,
  "title": "Validation failed",
  "detail": "One or more fields are invalid",
  "errors": [{ "field": "givenName", "message": "must not be blank" }]
}
```

## Try it with curl

```bash
# Create a patient and a doctor
curl -s -X POST localhost:8080/api/patients -H 'Content-Type: application/json' -d '{
  "givenName": "Ada", "familyName": "Lovelace", "birthDate": "1990-12-10",
  "gender": "female", "email": "ada@example.com"
}'

curl -s -X POST localhost:8080/api/doctors -H 'Content-Type: application/json' -d '{
  "givenName": "Gregory", "familyName": "House",
  "specialty": "Diagnostic Medicine", "email": "house@example.com"
}'

# Book a slot (use the ids returned above and a future date)
curl -s -X POST localhost:8080/api/appointments -H 'Content-Type: application/json' -d '{
  "patientId": 1, "doctorId": 1,
  "start": "2030-01-15T10:00:00Z", "end": "2030-01-15T10:30:00Z",
  "reason": "Annual check-up"
}'

# Book the same slot again -> 409 Conflict
curl -s -i -X POST localhost:8080/api/appointments -H 'Content-Type: application/json' -d '{
  "patientId": 1, "doctorId": 1,
  "start": "2030-01-15T10:00:00Z", "end": "2030-01-15T10:30:00Z"
}'

# List a doctor's booked appointments
curl -s 'localhost:8080/api/appointments?doctorId=1&status=booked'

# Cancel
curl -s -X POST localhost:8080/api/appointments/1/cancel \
  -H 'Content-Type: application/json' -d '{"cancellationReason": "Patient request"}'
```

## Project layout

```
api/                 Spring Boot backend
  src/main/java/com/mediflow/api/
    controller/      REST controllers (DTOs in, DTOs out)
    service/         Business rules and transactions
    repository/      Spring Data JPA repositories
    dto/             Request/response records
    entity/          JPA entities
    exception/       Exceptions and the global problem-JSON handler
  src/main/resources/db/migration/   Flyway migrations
web/                 React frontend (later)
infra/               AWS infrastructure (later)
```
