# MediFlow
Clinic platform: patients book doctors, doctors write notes, AI drafts discharge summaries (later).
Stack: Java 21, Spring Boot, Maven, PostgreSQL, React (later), AWS (later).

## Repo layout
- `api/` — Spring Boot backend
- `web/` — React frontend (later)
- `infra/` — AWS infrastructure (later)

## Rules
- Package: com.mediflow.api, layered: controller / service / repository / dto / entity
- Use DTOs in controllers, never expose entities
- Validation with jakarta.validation, global exception handler returning JSON errors
- FHIR-style field names (givenName, familyName, birthDate)
- Every endpoint needs a test (JUnit 5 + MockMvc)
- Small commits, one feature per commit
