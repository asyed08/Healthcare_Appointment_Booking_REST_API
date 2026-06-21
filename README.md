# Healthcare Appointment Booking REST API

A Spring Boot REST API for managing healthcare appointments, doctor availability, and patient profiles — with JWT authentication, Kafka-driven notifications, and optimistic locking for concurrent booking safety.

**Live demo:** [Swagger UI](https://healthcare-booking-eycvcuchd0g0gzb2.centralus-01.azurewebsites.net/swagger-ui/index.html) — deployed on Azure App Service (Free tier; allow ~30s on first load if the instance is sleeping)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Security | Spring Security + JWT (JJWT 0.12) |
| Database | PostgreSQL (Neon Tech) |
| Migrations | Flyway |
| Messaging | Apache Kafka (Confluent Cloud) |
| Email | Spring Mail (SMTP / JavaMailSender) |
| API Docs | Springdoc OpenAPI (Swagger UI) |
| Build | Maven |
| Container | Docker |
| IaC | Terraform (Azure) |
| CI/CD | GitHub Actions |

## Features

- **JWT Authentication** — register/login with role-based access (`DOCTOR`, `PATIENT`)
- **Doctor Profiles** — CRUD, specialization, weekly availability windows
- **Automatic Slot Generation** — 30-minute slots generated 30 days ahead
- **Patient Profiles** — CRUD with ownership and doctor-override access
- **Appointment Booking** — optimistic locking (`@Version`) prevents double-bookings under concurrent load
- **Idempotency** — SHA-256 request deduplication (24 h TTL) for safe client retries
- **Pagination** — all list endpoints return `Page<T>` with configurable page size, number, and sort order
- **Event-Driven Notifications** — Kafka events on booking/cancellation; HTML confirmation emails via `JavaMailSender`
- **OpenAPI / Swagger UI** — interactive docs at `/swagger-ui/index.html`

## Project Structure

```
src/main/java/com/ameen/healthcare/
├── controller/       # REST controllers (Auth, Doctor, Patient, Appointment)
├── service/          # Business logic, Kafka consumer, NotificationService
├── repository/       # Spring Data JPA repositories
├── entity/           # JPA entities
├── dto/
│   ├── request/      # Inbound DTOs
│   ├── response/     # Outbound DTOs
│   └── event/        # Kafka event payloads
├── config/           # JPA, Kafka producer/consumer configuration
├── exception/        # Custom exceptions + GlobalExceptionHandler
├── enums/            # Role, AppointmentStatus, SlotStatus
└── security/         # JWT filter, UserDetailsService, SecurityConfig
```

## Local Development

### Prerequisites

- Java 17
- Maven 3.8+
- A [Neon](https://neon.tech) PostgreSQL database
- A [Confluent Cloud](https://confluent.io) Kafka cluster

### 1. Create `application-local.yml`

This file holds your local credentials and is gitignored — never commit it.

Create `src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://<your-neon-host>/neondb?sslmode=require
    username: neondb_owner
    password: <your-neon-password>

  kafka:
    bootstrap-servers: <your-confluent-bootstrap-server>:9092

kafka:
  security:
    protocol: SASL_SSL
  sasl:
    mechanism: PLAIN
    jaas:
      config: 'org.apache.kafka.common.security.plain.PlainLoginModule required username="<API_KEY>" password="<API_SECRET>";'
```

### 2. Run the app

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

API: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Environment Variables

These are read at runtime. In Azure they are set as App Service application settings; locally they are provided via `application-local.yml`.

| Variable | Description |
|----------|-------------|
| `SPRING_DATASOURCE_URL` | JDBC URL — `jdbc:postgresql://<host>/neondb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Confluent Cloud bootstrap server |
| `KAFKA_SECURITY_PROTOCOL` | `SASL_SSL` for Confluent Cloud |
| `KAFKA_SASL_MECHANISM` | `PLAIN` |
| `KAFKA_SASL_JAAS_CONFIG` | Full JAAS login string with API key/secret |
| `JWT_SECRET_KEY` | Base64-encoded JWT signing key (min 32 chars) |
| `MAIL_USERNAME` | Gmail sender address |
| `MAIL_PASSWORD` | Gmail app password |

## API Reference

All endpoints are prefixed with `/api/v1`. Protected endpoints require:
```
Authorization: Bearer <jwt_token>
```

### Auth
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `POST` | `/auth/register` | Public | Register (`role`: `DOCTOR` or `PATIENT`) |
| `POST` | `/auth/login` | Public | Login and receive a JWT |

### Doctors
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `POST` | `/doctors` | `DOCTOR` | Create doctor profile |
| `GET` | `/doctors` | Public | List all doctors — paginated (`?specialization=` filter) |
| `GET` | `/doctors/{id}` | Public | Get a doctor by ID |
| `PUT` | `/doctors/{id}` | `DOCTOR` (owner) | Update doctor profile |
| `POST` | `/doctors/{id}/availability` | `DOCTOR` (owner) | Add availability window |
| `DELETE` | `/doctors/{id}/availability/{avid}` | `DOCTOR` (owner) | Remove availability window |
| `GET` | `/doctors/{id}/slots` | Authenticated | List available booking slots |

### Patients
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `POST` | `/patients` | `PATIENT` | Create patient profile |
| `GET` | `/patients/me` | `PATIENT` | Get own profile |
| `PUT` | `/patients/me` | `PATIENT` | Update own profile |
| `GET` | `/patients/{id}` | `DOCTOR` or owner | Get patient by ID |

### Appointments
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `POST` | `/appointments` | `PATIENT` | Book an appointment (requires `Idempotency-Key` header) |
| `DELETE` | `/appointments/{id}` | `PATIENT`/`DOCTOR` (owner) | Cancel an appointment |
| `GET` | `/appointments/my/patient` | `PATIENT` | List own appointments — paginated |
| `GET` | `/appointments/my/doctor` | `DOCTOR` | List appointments for own patients — paginated |
| `GET` | `/appointments/{id}` | Owner | Get a single appointment |

### Pagination

All paginated endpoints accept standard Spring pagination query parameters:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `page` | `0` | Zero-based page index |
| `size` | `20` | Number of items per page |
| `sort` | — | Field and direction, e.g. `appointmentDate,desc` |

**Example:**
```
GET /api/v1/appointments/my/patient?page=0&size=10&sort=appointmentDate,desc
```

**Response envelope:**
```json
{
  "content": [ ... ],
  "totalElements": 42,
  "totalPages": 5,
  "size": 10,
  "number": 0,
  "first": true,
  "last": false
}
```

Pagination parameters are visible and testable directly in Swagger UI.

### Idempotency

Append an `Idempotency-Key` header to `POST /appointments` to make booking safe to retry:

```
POST /api/v1/appointments
Idempotency-Key: <any-unique-uuid>
```

If the same key is received again with the same payload, the original response is returned without re-processing. Sending the same key with a different payload returns `422 Unprocessable Entity`. Keys expire after 24 hours.

## Testing

The project has two layers of automated tests, run together with `mvn test`.

### Unit Tests (27 tests)

Located in `src/test/java/com/ameen/healthcare/service/`, these use Mockito to test business logic in isolation with no external dependencies.

| Test class | What it covers |
|------------|----------------|
| `AppointmentServiceTest` | Happy-path booking, idempotency hit, idempotency key reuse conflict, slot/patient not found, slot already booked, cancel by patient, cancel by doctor, cancel by non-owner, already-cancelled guard, appointment not found, pagination of patient appointments |
| `PatientServiceTest` | Create profile, duplicate profile guard, patient not found, update own profile, doctor-override access, unauthorized access |
| `AuthServiceTest` | Register new user, duplicate email guard, login with correct password |

Run unit tests only:
```bash
mvn test -Dtest="*ServiceTest,*AuthServiceTest"
```

### Integration Tests (7 tests)

Located in `src/test/java/com/ameen/healthcare/AppointmentIT.java`, these boot the full Spring context against a real PostgreSQL instance managed by [Testcontainers](https://testcontainers.com). No external database or Kafka broker is needed — Docker must be running locally.

| Test | What it verifies |
|------|-----------------|
| `bookAppointment_happyPath` | Full booking flow end-to-end: register → create profiles → add availability → book slot → assert `201` and response body |
| `bookAppointment_idempotency` | Second request with same `Idempotency-Key` returns `201` without creating a duplicate appointment |
| `bookAppointment_doubleBooking` | Concurrent booking of the same slot returns `409 Conflict` |
| `cancelAppointment_byPatient` | Patient can cancel own appointment; slot status resets to `AVAILABLE` |
| `cancelAppointment_byDoctor` | Assigned doctor can cancel the appointment |
| `noAuth_returns401` | Unauthenticated requests to protected endpoints return `4xx` |
| `doctorRole_cannotBook` | A `DOCTOR` user attempting to book receives `403 Forbidden` |

Run integration tests only:
```bash
mvn test -Dtest="AppointmentIT"
```

Run all tests:
```bash
mvn test
```

> **Note:** Integration tests require Docker. Kafka is mocked out (`@MockBean AppointmentEventPublisher`) so no Confluent Cloud credentials are needed for tests.

## Database Migrations

Managed by Flyway, run automatically on startup.

| Version | Description |
|---------|-------------|
| `V1` | Initial schema — users, doctors, patients, availability, appointments |
| `V2` | Slots table with `version` column for optimistic locking |
| `V3` | `idempotency_keys` table for deduplication |

## Infrastructure & Deployment

Terraform in `infrastructure/` provisions the Azure App Service.  
See [`infrastructure/README.md`](infrastructure/README.md) for setup instructions.

The CI/CD pipeline (`.github/workflows/ci-cd.yml`) runs on every push to `main`:

1. **Build and test** — all 34 tests run against a real Postgres container (Testcontainers); no external services needed
2. **Trivy security scan** — filesystem vulnerability scan; results uploaded to GitHub Security tab
3. **Build and push Docker image** — tagged `main` and `sha-<commit>`, pushed to GitHub Container Registry (GHCR)
4. **Terraform apply** — provisions or updates Azure infrastructure
5. **Deploy to Azure App Service** — pulls the new image and performs a health check against `/api-docs`

On pull requests, only steps 1–2 run plus a Terraform plan comment on the PR.

## License

[Apache 2.0](LICENSE)
