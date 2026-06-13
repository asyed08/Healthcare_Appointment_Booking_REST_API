# Healthcare Appointment Booking API - Troubleshooting Guide

## Build & Compilation Issues

### Issue: Maven Build Fails with Compilation Errors

**Symptoms:**
```
[ERROR] COMPILATION ERROR
[ERROR] .../src/main/java/com/ameen/healthcare/entity/Doctor.java:[line] error:
```

**Root Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| Lombok annotations not removed | Verify `@Getter`, `@Setter`, `@Builder` are NOT in entity files. All 5 entities (Doctor, Patient, Appointment, Slot, Availability) must use explicit accessors. |
| Missing import statement | Run `mvn compile` and check error line; add missing import (e.g., `import java.time.LocalDateTime;`) |
| Syntax error in Java file | Use IDE's "Find & Replace" or `grep` to locate typos. Check for missing semicolons, brackets. |
| Cached build artifacts | Run `mvn clean` before `mvn compile` to clear target/ directory. |
| Wrong Java version | Verify `java -version` returns 17.x.x. Update pom.xml <source> and <target> to 17 if needed. |

**Quick Fix:**
```bash
# Full clean rebuild
mvn clean compile -DskipTests

# If still failing, check specific file
mvn compile -e 2>&1 | grep -A 5 "\[ERROR\]"
```

---

### Issue: JAR File Not Generated

**Symptoms:**
```
target/ directory exists but no .jar file inside
```

**Root Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| Package phase not run | Run `mvn package -DskipTests` instead of just `mvn compile` |
| Build failed silently | Check console output for warnings; resolve all compiler warnings first. |
| Wrong pom.xml packaging | Verify `<packaging>jar</packaging>` in pom.xml (default is JAR for Spring Boot) |
| Main class not found | Check `<start-class>com.ameen.healthcare.HealthcareApplication</start-class>` in pom.xml |

**Quick Fix:**
```bash
mvn clean package -DskipTests 2>&1 | tail -15
# Should show: "BUILD SUCCESS" and "healthcare-appointment-*.jar"
```

---

## Application Startup Issues

### Issue: App Fails to Start - Port Already in Use

**Symptoms:**
```
Caused by: java.net.BindException: Address already in use
```

**Solutions:**

```bash
# Find process using port 8080
lsof -i :8080
# Kill the process
kill -9 <PID>

# Or change port in application.yml
server:
  port: 8081
```

---

### Issue: Database Connection Failed

**Symptoms:**
```
Caused by: org.postgresql.util.PSQLException: Connection to localhost:5432 refused
```

**Solutions:**

**For Local Development:**
```bash
# Start PostgreSQL via docker-compose
docker-compose up -d postgres

# Verify connection
psql -h localhost -U healthcare_user -d healthcare_db
# Password: healthcare_password

# Check logs
docker-compose logs postgres
```

**For Azure Deployment:**
```bash
# Verify connection string in App Service Configuration
az webapp config appsettings list --resource-group healthcare-api-rg --name healthcare-api-app

# Test connection from app logs
az webapp log tail --resource-group healthcare-api-rg --name healthcare-api-app

# Check PostgreSQL firewall rules
az postgres flexible-server firewall-rule list \
  --resource-group healthcare-api-rg \
  --server-name healthcare-api-db
```

---

### Issue: Kafka Connection Failed

**Symptoms:**
```
Caused by: org.apache.kafka.common.errors.TimeoutException: Topic appointment.created not found
```

**Solutions:**

**For Local Development:**
```bash
# Start Kafka via docker-compose
docker-compose up -d kafka

# Verify topics exist
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Create topics if missing
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 \
  --create --topic appointment.created --partitions 3 --replication-factor 1 --if-not-exists
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 \
  --create --topic appointment.cancelled --partitions 3 --replication-factor 1 --if-not-exists

# Check consumer group
docker-compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list
```

**For Confluent Cloud (Production):**
```bash
# Verify bootstrap servers in application-prod.yml
# Format: pkc-xxxxx.region.provider.confluent.cloud:9092

# Test connection
kafka-broker-api-versions --bootstrap-servers pkc-xxxxx.region.provider.confluent.cloud:9092 \
  --command-config client.properties

# Check topics
kafka-topics --bootstrap-servers pkc-xxxxx.region.provider.confluent.cloud:9092 \
  --command-config client.properties --list
```

---

### Issue: JWT Token Validation Fails

**Symptoms:**
```
401 Unauthorized: JWT validation failed / JWT signature does not match
```

**Solutions:**

```bash
# 1. Verify JWT secret in application.yml
cat src/main/resources/application.yml | grep -A 2 "jwt:"

# 2. Check token expiry (default 24 hours)
# Token format: header.payload.footer
# Decode payload (base64): https://jwt.io
# Verify "exp" claim is in future

# 3. For Azure: verify JWT secret in Key Vault
az keyvault secret show --vault-name healthcare-api-kv --name jwt-secret

# 4. Regenerate token: use /auth/login endpoint again
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"doctor@example.com","password":"password123"}'
```

---

## Database Issues

### Issue: Flyway Migration Fails

**Symptoms:**
```
Caused by: org.flywaydb.core.api.FlywayException: Validate failed: Version of schema history table
```

**Solutions:**

```bash
# 1. Check migration files exist
ls -la src/main/resources/db/migration/

# 2. View Flyway history in database
docker-compose exec postgres psql -U healthcare_user -d healthcare_db -c \
  "SELECT * FROM flyway_schema_history;"

# 3. Reset migrations (dev only, loses data)
docker-compose exec postgres psql -U healthcare_user -d healthcare_db -c \
  "DROP TABLE IF EXISTS flyway_schema_history;"

# 4. Re-run migrations
mvn flyway:clean flyway:migrate

# 5. For Azure, check migration logs
az webapp log tail --resource-group healthcare-api-rg --name healthcare-api-app | grep -i "flyway"
```

---

### Issue: Optimistic Locking Fails - "Version Conflict"

**Symptoms:**
```
409 Conflict: Slot unavailable / Version mismatch
```

**This is expected behavior!** It means:
- Two users tried to book the same slot simultaneously
- The first user's transaction succeeded
- The second user's transaction failed (version number mismatch)

**Solution:**
```bash
# Client should:
# 1. Retry the booking with a different slot (user can query available slots)
# 2. Use exponential backoff: wait 100ms, 200ms, 400ms
# 3. Max retries: 3 (after 3 failures, suggest manual retry)

# To verify: check Appointment table version
docker-compose exec postgres psql -U healthcare_user -d healthcare_db -c \
  "SELECT id, slot_id, version FROM appointments ORDER BY created_at DESC LIMIT 10;"
```

---

### Issue: Idempotency Key Dedup Not Working

**Symptoms:**
```
POST /appointments with same Idempotency-Key returns different response
```

**Solutions:**

```bash
# 1. Verify idempotency_keys table exists
docker-compose exec postgres psql -U healthcare_user -d healthcare_db -c \
  "SELECT * FROM idempotency_keys LIMIT 5;"

# 2. Check SHA-256 hash generation in AppointmentService
# Log should show: "Idempotency key hash: sha256=abcd1234..."

# 3. Verify TTL is working (24 hours)
docker-compose exec postgres psql -U healthcare_user -d healthcare_db -c \
  "SELECT id, idem_key, expires_at FROM idempotency_keys WHERE expires_at < NOW();"

# 4. Clean expired keys manually (if cleanup job fails)
docker-compose exec postgres psql -U healthcare_user -d healthcare_db -c \
  "DELETE FROM idempotency_keys WHERE expires_at < NOW();"
```

---

## API & Endpoint Issues

### Issue: 403 Forbidden on Endpoint

**Symptoms:**
```
403 Forbidden: Access Denied / Insufficient permissions
```

**Causes & Solutions:**

| Endpoint | Required Role | Solution |
|----------|---------------|----------|
| `POST /doctors` | ROLE_DOCTOR | User must have DOCTOR role. Use `/auth/register?role=DOCTOR` |
| `POST /patients` | ROLE_PATIENT | User must have PATIENT role. Use `/auth/register?role=PATIENT` |
| `DELETE /appointments/{id}` | ROLE_DOCTOR or ROLE_PATIENT (owner) | User must be appointment owner or DOCTOR. Check userId in token. |
| `GET /patients/{id}` | ROLE_DOCTOR or owner | PATIENT can only access own profile; DOCTOR can access any patient. |

**Quick Fix:**
```bash
# 1. Decode JWT token (at jwt.io or use jq)
JWT_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
echo "$JWT_TOKEN" | cut -d. -f2 | base64 -D

# 2. Verify "roles" claim includes expected role
# Expected: ["ROLE_DOCTOR"] or ["ROLE_PATIENT"]

# 3. If wrong role, re-authenticate with correct role
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"doctor@example.com","password":"password123"}'
```

---

### Issue: 409 Conflict on POST /appointments

**Symptoms:**
```
409 Conflict: Slot unavailable
```

**Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| Slot status is BOOKED, not AVAILABLE | User must select a different slot. Query `GET /doctors/{id}/slots` to see available slots. |
| Optimistic lock failed (version conflict) | Another user booked same slot. Retry with different slot or exponential backoff. |
| Slot already deleted | Slot generation may have been reset. Wait for next cron job (Sunday 01:00) or manually trigger. |

**Debug:**
```bash
# Check slot status in database
docker-compose exec postgres psql -U healthcare_user -d healthcare_db -c \
  "SELECT id, slot_date, start_time, status FROM slots WHERE doctor_id = 1 LIMIT 10;"

# Check appointment count
docker-compose exec postgres psql -U healthcare_user -d healthcare_db -c \
  "SELECT COUNT(*) FROM appointments WHERE status = 'PENDING';"
```

---

### Issue: 422 Unprocessable Entity - Validation Error

**Symptoms:**
```
422 Unprocessable Entity: Validation failed
```

**Solutions:**

```bash
# Common validation errors:
# 1. Missing required field in request body
# 2. Invalid format (e.g., email not RFC 5322 compliant)
# 3. Date/time format incorrect (expected: ISO 8601)
# 4. Idempotency key invalid (must be UUID v4)

# Example: Create appointment with invalid slot ID
curl -X POST http://localhost:8080/appointments \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{"slotId":"invalid-uuid","notes":"Check-up"}'

# Response will detail which field failed validation
```

---

## Kafka & Event Issues

### Issue: Kafka Consumer Lag > 1 second

**Symptoms:**
```
App logs show: "Consumer lag: 1500ms"
Email/SMS notifications delayed
```

**Solutions:**

```bash
# 1. Check consumer group lag
docker-compose exec kafka kafka-consumer-groups \
  --bootstrap-servers localhost:9092 \
  --group appointment-notifications \
  --describe

# 2. Increase consumer concurrency (in application.yml)
spring:
  kafka:
    listener:
      concurrency: 5  # default 1, increase to 5-10

# 3. Restart app
mvn spring-boot:run

# 4. Monitor lag in Application Insights
# Query: customMetrics | where name == "kafka.consumer.lag"
```

---

### Issue: Events Not Published to Kafka

**Symptoms:**
```
Appointment created but event not in topic
Kafka consumer receives no messages
```

**Solutions:**

```bash
# 1. Check event publishing in logs
mvn spring-boot:run | grep -i "publishing\|kafka"

# 2. Verify KafkaTemplate bean is loaded
curl http://localhost:8080/actuator/beans | grep -i "kafkaTemplate"

# 3. Check topic configuration (appointment.created topic must exist)
docker-compose exec kafka kafka-topics --bootstrap-servers localhost:9092 --describe

# 4. Manually publish test event
docker-compose exec kafka kafka-console-producer \
  --bootstrap-servers localhost:9092 \
  --topic appointment.created \
  --property "parse.key=true" \
  --property "key.separator=:"
# Then type: 1:{"patientId":1,"doctorId":2,"appointmentId":3}

# 5. Consume and verify
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-servers localhost:9092 \
  --topic appointment.created \
  --from-beginning
```

---

## Docker & Container Issues

### Issue: Docker Container Fails to Start

**Symptoms:**
```
docker: Error response from daemon: oci runtime error
Container exits immediately with code 1
```

**Solutions:**

```bash
# 1. View logs
docker-compose logs healthcare-api

# 2. Check resource limits
docker stats

# 3. Verify environment variables
docker-compose exec healthcare-api env | grep -i "db\|kafka\|jwt"

# 4. Test connection from container
docker-compose exec healthcare-api nc -zv postgres 5432
docker-compose exec healthcare-api nc -zv kafka 9092

# 5. Rebuild image
docker-compose build --no-cache healthcare-api
docker-compose up -d
```

---

### Issue: Dockerfile Build Fails

**Symptoms:**
```
Step 5/8 : RUN mvn clean package -DskipTests
ERROR: failed to solve with frontend dockerfile.v0
```

**Solutions:**

```bash
# 1. Verify JAR file exists locally
ls -lh target/healthcare-appointment-*-SNAPSHOT.jar

# 2. Check Docker build context
docker build --progress=plain -t healthcare-api .

# 3. Verify Dockerfile syntax
docker run --rm -i hadolint/hadolint < Dockerfile

# 4. Use multi-stage build (already in provided Dockerfile)
# Check if base images are reachable:
docker pull openjdk:17-jdk-slim
docker pull openjdk:17-jre-slim
```

---

## Azure Deployment Issues

### Issue: Terraform Apply Fails

**Symptoms:**
```
Error: retrieving Authenticated Object (Azure AD) Details for the current user
Error: insufficient permissions to complete the operation
```

**Solutions:**

```bash
# 1. Verify Azure CLI login
az account show
az account list

# 2. Set correct subscription
az account set --subscription <SUBSCRIPTION_ID>

# 3. Check service principal credentials
echo $AZURE_SUBSCRIPTION_ID $AZURE_TENANT_ID $AZURE_CLIENT_ID

# 4. Re-authenticate
az login
az ad sp show --id <CLIENT_ID>

# 5. Validate Terraform files
cd infra/terraform
terraform validate
terraform fmt -check

# 6. Plan before apply
terraform plan -out=tfplan
terraform apply tfplan
```

---

### Issue: App Service Health Check Failing

**Symptoms:**
```
App Service Status: Unhealthy
Health probe returned 503
```

**Solutions:**

```bash
# 1. Check app logs
az webapp log tail --resource-group healthcare-api-rg --name healthcare-api-app

# 2. Verify health endpoint
curl https://healthcare-api-app.azurewebsites.net/health

# 3. Check environment variables in App Service
az webapp config appsettings list --resource-group healthcare-api-rg --name healthcare-api-app

# 4. Verify database connection from app
# Check logs for: "Connection successful"

# 5. Restart app
az webapp restart --resource-group healthcare-api-rg --name healthcare-api-app

# 6. Check provisioning state
az webapp show --resource-group healthcare-api-rg --name healthcare-api-app --query provisioningState
```

---

### Issue: GitHub Actions Workflow Fails

**Symptoms:**
```
Workflow: deploy.yml status: FAILED
Job: Build step fails with Maven error
```

**Solutions:**

```bash
# 1. Check workflow logs (GitHub UI: Actions tab > workflow run > step output)

# 2. Verify secrets are set
gh secret list

# 3. Common causes:
# - AZURE_CLIENT_SECRET expired (rotate every 90 days)
# - DB_ADMIN_PASSWORD incorrect
# - ACR credentials invalid
# - Terraform state file locked

# 4. Force unlock Terraform state
cd infra/terraform
terraform force-unlock <LOCK_ID>

# 5. Retry workflow from GitHub UI
# Click "Re-run failed jobs" button
```

---

## Performance & Optimization Issues

### Issue: Slow Database Queries

**Symptoms:**
```
Response time >1s for GET /doctors
Logs show: "Hibernate: select ... took 1234ms"
```

**Solutions:**

```bash
# 1. Enable query logging
# application.yml:
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true
logging:
  level:
    org.hibernate.stat: DEBUG

# 2. Find slow queries
az webapp log tail | grep -i "took\|query"

# 3. Add database indexes
# V4__add_indexes.sql:
CREATE INDEX idx_doctor_user_id ON doctors(user_id);
CREATE INDEX idx_availability_doctor_id ON availability(doctor_id);
CREATE INDEX idx_slot_doctor_id ON slots(doctor_id);
CREATE INDEX idx_appointment_patient_id ON appointments(patient_id);

# 4. Optimize N+1 queries
# Use @EntityGraph or explicit joins in repositories
@EntityGraph(attributePaths = {"doctor", "slot"})
List<Appointment> findByPatientId(Long patientId);

# 5. Monitor execution plan
EXPLAIN ANALYZE
SELECT * FROM appointments WHERE patient_id = 1 ORDER BY created_at DESC;
```

---

### Issue: High Memory Usage

**Symptoms:**
```
App Service memory usage >80%
OutOfMemoryError in logs
```

**Solutions:**

```bash
# 1. Check JVM heap size
# Dockerfile: ENV JAVA_OPTS="-Xmx512m -Xms256m"
# Increase if App Service SKU allows

# 2. Monitor memory in Application Insights
# Query: performanceCounters | where name == "% Processor Time"

# 3. Profile heap dump (if available)
# Via App Service portal: Tools > Diagnostic Tools > Memory Dump

# 4. Identify memory leaks
# Check logs for: "Full GC" frequency

# 5. Increase App Service tier
az appservice plan update --name healthcare-api-plan \
  --resource-group healthcare-api-rg \
  --sku B3  # from B2 to B3
```

---

## Monitoring & Logging Issues

### Issue: No Logs in Application Insights

**Symptoms:**
```
Application Insights blade is empty
No traces/exceptions/requests visible
```

**Solutions:**

```bash
# 1. Verify App Service has diagnostics enabled
az webapp diagnostic-settings list --resource-group healthcare-api-rg

# 2. Check Log Analytics workspace
az monitor log-analytics workspace list

# 3. Enable application logging
az webapp log config --resource-group healthcare-api-rg \
  --name healthcare-api-app \
  --application-logging true \
  --level information

# 4. Verify SDK is instrumented
# pom.xml should include: spring-boot-starter-actuator
mvn dependency:tree | grep -i "actuator"

# 5. Check log ingestion
az monitor log-analytics query \
  --workspace <WORKSPACE_ID> \
  --analytics-query "AppServiceHTTPLogs | take 10"
```

---

## Security Issues

### Issue: Unauthorized Access to Endpoints

**Symptoms:**
```
Requests without Authorization header succeed
401 not returned for invalid token
```

**Solutions:**

```bash
# 1. Verify @PreAuthorize is on controller methods
grep -r "@PreAuthorize" src/main/java/com/ameen/healthcare/controller/

# 2. Check JwtAuthenticationFilter is registered
grep -r "JwtAuthenticationFilter" src/main/java/com/ameen/healthcare/config/

# 3. Verify SecurityConfig has correct filter order
# Filter order should be: JwtAuthenticationFilter → AuthenticationManager → DispatcherServlet

# 4. Test with curl
# Without token (should fail)
curl http://localhost:8080/doctors/1/availability

# With valid token (should succeed)
curl -H "Authorization: Bearer $JWT_TOKEN" http://localhost:8080/doctors/1/availability

# With invalid token (should fail)
curl -H "Authorization: Bearer invalid.token.here" http://localhost:8080/doctors/1/availability
```

---

## Network & Connectivity Issues

### Issue: Cannot Reach App Service from Local Machine

**Symptoms:**
```
curl: (7) Failed to connect to healthcare-api-app.azurewebsites.net port 443
Connection timeout
```

**Solutions:**

```bash
# 1. Verify app service is running
az webapp show --resource-group healthcare-api-rg --name healthcare-api-app --query state

# 2. Check firewall rules
az network nsg rule list --resource-group healthcare-api-rg --nsg-name healthcare-api-nsg

# 3. Verify DNS resolves
nslookup healthcare-api-app.azurewebsites.net

# 4. Test via curl with verbose output
curl -v https://healthcare-api-app.azurewebsites.net/health

# 5. Check network policies (if using vnet)
az network vnet subnet show --resource-group healthcare-api-rg \
  --vnet-name healthcare-api-vnet \
  --name app-subnet

# 6. Temporarily allow all traffic to debug
# WARNING: Security risk, only for troubleshooting
az network nsg rule create --resource-group healthcare-api-rg \
  --nsg-name healthcare-api-nsg \
  --name AllowAll \
  --priority 100 \
  --source-address-prefixes '*' \
  --destination-address-prefixes '*' \
  --access Allow \
  --protocol '*'
```

---

## Quick Reference: Top 10 Commands

```bash
# 1. Build and run locally
mvn clean package -DskipTests && java -jar target/healthcare-appointment-*-SNAPSHOT.jar

# 2. View recent logs
az webapp log tail --resource-group healthcare-api-rg --name healthcare-api-app

# 3. Restart app service
az webapp restart --resource-group healthcare-api-rg --name healthcare-api-app

# 4. Check database connection
docker-compose exec postgres psql -U healthcare_user -d healthcare_db -c "SELECT 1;"

# 5. View Kafka topics
docker-compose exec kafka kafka-topics --bootstrap-servers localhost:9092 --list

# 6. Monitor Kafka consumer lag
docker-compose exec kafka kafka-consumer-groups --bootstrap-servers localhost:9092 --group appointment-notifications --describe

# 7. View Application Insights metrics
az monitor app-insights component show --app healthcare-api-insights --resource-group healthcare-api-rg

# 8. Validate Terraform
cd infra/terraform && terraform validate && terraform plan

# 9. Check resource group status
az group show --name healthcare-api-rg

# 10. Decode JWT token
echo "$JWT_TOKEN" | cut -d. -f2 | base64 -D | jq .
```

---

**Last Updated:** June 13, 2026  
**Maintainer:** @asyed08  
**Known Issues:** None currently identified
