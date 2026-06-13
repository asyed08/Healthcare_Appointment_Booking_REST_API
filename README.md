# Healthcare Appointment Booking REST API

A production-grade Spring Boot REST API for managing healthcare appointments, doctor availability, and patient profiles — with JWT authentication, Kafka-driven notifications, and optimistic locking for concurrent booking safety.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Security | Spring Security + JWT (JJWT 0.12) |
| Database | PostgreSQL 15 |
| Migrations | Flyway |
| Messaging | Apache Kafka |
| Email | Spring Mail (SMTP / JavaMailSender) |
| API Docs | Springdoc OpenAPI (Swagger UI) |
| Build | Maven |
| Container | Docker + docker-compose |
| IaC | Terraform (Azure) |
| CI/CD | GitHub Actions |

---

## Features

- **JWT Authentication** — register/login with role-based access (`DOCTOR`, `PATIENT`)
- **Doctor Profiles** — CRUD, specialization, weekly availability windows
- **Automatic Slot Generation** — 30-minute slots generated 30 days ahead; regenerated every Sunday at 01:00 UTC
- **Patient Profiles** — CRUD with ownership and doctor-override access
- **Appointment Booking** — optimistic locking (`@Version`) prevents double-bookings under concurrent load
- **Idempotency** — SHA-256 request deduplication (24 h TTL) for safe client retries
- **Event-Driven Notifications** — Kafka events on booking/cancellation; HTML confirmation emails sent via `JavaMailSender`; SMS (Twilio) and calendar (Google Calendar / Microsoft Graph) stubs ready to wire up
- **OpenAPI / Swagger UI** — interactive docs at `/swagger-ui.html`

---

## Quick Start (Local)

### Prerequisites
- Java 17+, Maven 3.8+
- Docker & docker-compose

### 1. Start backing services
```bash
docker-compose up -d
```
Starts PostgreSQL (port 5432), Zookeeper, and Kafka (port 9092).

### 2. Run the application
```bash
mvn spring-boot:run
```
The API is available at `http://localhost:8080`.  
Swagger UI: `http://localhost:8080/swagger-ui.html`

### 3. Or build and run the JAR
```bash
mvn clean package -DskipTests
java -jar target/healthcare-0.0.1-SNAPSHOT.jar
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/healthcare_db` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `healthcare_user` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `healthcare_pass` | DB password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `JWT_SECRET_KEY` | *(hex key in application.yml)* | 256-bit JWT signing key |
| `MAIL_HOST` | `smtp.gmail.com` | SMTP host |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_USERNAME` | `noreply@healthcare.com` | Sender address |
| `MAIL_PASSWORD` | *(empty)* | SMTP password / app password |

---

## API Reference

All endpoints are prefixed with `/api/v1`. Protected endpoints require:
```
Authorization: Bearer <jwt_token>
```

### Auth
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `POST` | `/auth/register` | Public | Register a new user (`role`: `DOCTOR` or `PATIENT`) |
| `POST` | `/auth/login` | Public | Login and receive a JWT token |

### Doctors
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `POST` | `/doctors` | `DOCTOR` | Create doctor profile |
| `GET` | `/doctors` | Public | List all doctors (filter by `?specialization=`) |
| `GET` | `/doctors/{id}` | Public | Get a doctor by ID |
| `PUT` | `/doctors/{id}` | `DOCTOR` (owner) | Update doctor profile |
| `POST` | `/doctors/{id}/availability` | `DOCTOR` (owner) | Add an availability window |
| `DELETE` | `/doctors/{id}/availability/{avid}` | `DOCTOR` (owner) | Remove an availability window |
| `GET` | `/doctors/{id}/slots` | Authenticated | List available booking slots |

### Patients
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `POST` | `/patients` | `PATIENT` | Create patient profile |
| `GET` | `/patients/me` | `PATIENT` | Get own profile |
| `PUT` | `/patients/me` | `PATIENT` | Update own profile |
| `GET` | `/patients/{id}` | `DOCTOR` or owner | Get patient profile by ID |

### Appointments
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| `POST` | `/appointments` | `PATIENT` | Book an appointment (requires `Idempotency-Key` header) |
| `DELETE` | `/appointments/{id}` | `PATIENT`/`DOCTOR` (owner) | Cancel an appointment |
| `GET` | `/appointments/my/patient` | `PATIENT` | List own appointments |
| `GET` | `/appointments/my/doctor` | `DOCTOR` | List appointments for own patients |
| `GET` | `/appointments/{id}` | Owner | Get a single appointment |

---

## Database Migrations

Managed by Flyway. Run automatically on startup.

| Version | Description |
|---------|-------------|
| `V1` | Initial schema — users, doctors, patients, availability, appointments |
| `V2` | Slots table with `version` column for optimistic locking |
| `V3` | `idempotency_keys` table for deduplication |

---

## Project Structure

```
src/main/java/com/ameen/healthcare/
├── controller/       # REST controllers (Auth, Doctor, Patient, Appointment)
├── service/          # Business logic + Kafka consumer + NotificationService
├── repository/       # Spring Data JPA repositories
├── entity/           # JPA entities
├── dto/
│   ├── request/      # Inbound DTOs
│   ├── response/     # Outbound DTOs
│   └── event/        # Kafka event payloads
├── config/           # Security, JPA, Kafka configuration
├── exception/        # Custom exceptions + GlobalExceptionHandler
├── enums/            # Role, AppointmentStatus, SlotStatus
└── security/         # JWT filter + UserDetailsService
```

---

## Infrastructure & Deployment

Terraform configuration for Azure is in `infrastructure/`.  
Provisions: App Service, PostgreSQL Flexible Server, Container Registry, Key Vault, Log Analytics.

GitHub Actions CI/CD pipeline (`.github/workflows/`) runs on push to `main`:
1. Maven build + tests
2. Docker image → Azure Container Registry
3. Terraform plan / apply
4. App Service deploy + health check

See [`DEPLOYMENT_GUIDE.md`](DEPLOYMENT_GUIDE.md) for step-by-step instructions.

---

## License

[Apache 2.0](LICENSE)