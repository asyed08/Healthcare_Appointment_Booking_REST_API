# CI/CD Pipeline Documentation

## Overview

The Healthcare API uses GitHub Actions for continuous integration and continuous deployment (CI/CD). The pipeline automates testing, building, security scanning, and deployment to Azure.

## Pipeline Workflow

```
┌─────────────────────────────────────────────────────────────┐
│                    Trigger Events                            │
│  (Push to main/develop, Pull Request to main/develop)      │
└────────────────────┬────────────────────────────────────────┘
                     │
         ┌───────────┴────────────┐
         │                        │
         ▼                        ▼
    ┌─────────────┐         ┌─────────────┐
    │   Build     │         │  Build      │
    │   & Test    │         │  & Test     │
    │   (Ubuntu)  │         │  (Ubuntu)   │
    └─────────────┘         └─────────────┘
         │                        │
         └───────────┬────────────┘
                     │
         ┌───────────▼────────────┐
         │  Security Scanning     │
         │  (Trivy CVE scan)      │
         └───────────┬────────────┘
                     │
         ┌───────────▼────────────┐
         │  Docker Build & Push   │
         │  (main branch only)    │
         └───────────┬────────────┘
                     │
    ┌────────────────┼────────────────┐
    │                │                │
    ▼                ▼                ▼
┌─────────────┐ ┌────────────┐  ┌──────────────┐
│ Terraform   │ │  Deploy to │  │  Run Health  │
│   Plan      │ │   Azure    │  │    Check     │
│   (PR)      │ │ (main only)│  │ (main only)  │
└─────────────┘ └────────────┘  └──────────────┘
```

## Jobs and Stages

### 1. Build Job

**Trigger**: Every push and PR  
**Duration**: ~5-10 minutes

**Steps**:
1. Checkout code
2. Setup JDK 17 (Temurin)
3. Start PostgreSQL 15 and Kafka services
4. Run Maven tests
5. Build application package
6. Upload test results and coverage

**Services Started**:
- PostgreSQL 15 on port 5432
- Apache Kafka on port 9092
- Apache ZooKeeper on port 2181

**Environment Variables**:
```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/healthcaredb_test
SPRING_DATASOURCE_USERNAME: testuser
SPRING_DATASOURCE_PASSWORD: testpass123
KAFKA_BOOTSTRAP_SERVERS: localhost:9092
JWT_SECRET_KEY: test-secret-key-for-ci-cd-pipeline-minimum-32-chars
```

### 2. Security Scanning Job

**Trigger**: After successful build  
**Duration**: ~2-3 minutes

**Steps**:
1. Run Trivy vulnerability scanner
2. Generate SARIF report
3. Upload results to GitHub Security tab

**Tools**:
- [Trivy](https://github.com/aquasecurity/trivy): Container/filesystem vulnerability scanner
- Scans dependencies for known CVEs

### 3. Docker Build Job

**Trigger**: After security scan, main branch only  
**Duration**: ~5-8 minutes

**Steps**:
1. Setup Docker Buildx
2. Login to GitHub Container Registry (GHCR)
3. Build multi-platform images (linux/amd64, linux/arm64)
4. Push to GHCR with tags:
   - `main` (latest)
   - `sha-<commit>` (commit hash)
   - Semantic versioning (if tagged)

**Registry**: `ghcr.io/asyed08/healthcare_appointment_booking_rest_api`

### 4. Terraform Plan Job

**Trigger**: Pull requests only  
**Duration**: ~3-5 minutes

**Steps**:
1. Validate Terraform files
2. Plan infrastructure changes
3. Comment on PR with plan summary
4. No infrastructure changes applied

**Validates**:
- Terraform syntax and format
- Azure provider configuration
- Variable validation

### 5. Deploy Job

**Trigger**: Main branch push only, after Docker build  
**Duration**: ~10-15 minutes
**Environment**: Production

**Steps**:
1. Initialize Terraform
2. Apply infrastructure changes (create/update Azure resources)
3. Deploy application to App Service
4. Run Flyway database migrations
5. Perform health checks (30 attempts, 10s intervals)
6. Notify deployment success/failure

**Health Check**:
```bash
curl https://{app-name}.azurewebsites.net/health
```

### 6. Rollback Job

**Trigger**: Deploy job failure  
**Duration**: ~5 minutes

**Steps**:
1. Notify deployment failure
2. Swap staging and production slots
3. Restore previous working version

## GitHub Secrets Configuration

Required secrets for CI/CD to work:

| Secret | Description | Example |
|--------|-------------|---------|
| `AZURE_CLIENT_ID` | Service Principal Client ID | `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` |
| `AZURE_CLIENT_SECRET` | Service Principal password | `xxxxxxxxxxxxxxxxxx` |
| `AZURE_SUBSCRIPTION_ID` | Azure Subscription ID | `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` |
| `AZURE_TENANT_ID` | Azure AD Tenant ID | `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` |
| `TF_VAR_DB_ADMIN_PASSWORD` | PostgreSQL admin password | `MySecureP@ssw0rd123` |
| `TF_VAR_KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `broker1:9092,broker2:9092` |
| `TF_VAR_JWT_SECRET_KEY` | JWT signing key (min 32 chars) | `your-very-secure-jwt-secret-key...` |
| `APP_SERVICE_NAME` | App Service resource name | `healthcare-api-prod-app` |
| `DB_HOST` | Database FQDN | `healthcare-api-prod-db.postgres.database.azure.com` |
| `DB_USERNAME` | Database username | `psqladmin` |
| `DB_PASSWORD` | Database password | `MySecureP@ssw0rd123` |
| `AZURE_PUBLISH_PROFILE` | App Service publish profile XML | `<PublishProfile>...</PublishProfile>` |
| `AZURE_RESOURCE_GROUP` | Resource group name | `healthcare-api-prod-rg` |

### Setting Up Secrets

```bash
# Via GitHub CLI
gh secret set AZURE_CLIENT_ID --body "YOUR_CLIENT_ID"
gh secret set AZURE_CLIENT_SECRET --body "YOUR_CLIENT_SECRET"
# ... repeat for all secrets

# Or via GitHub UI:
# 1. Go to Settings → Secrets and variables → Actions
# 2. Click "New repository secret"
# 3. Add each secret
```

## Service Principal Setup

Create a Service Principal for CI/CD authentication:

```bash
# Create Service Principal
az ad sp create-for-rbac \
  --name healthcare-api-ci-cd \
  --role Contributor \
  --scopes /subscriptions/YOUR_SUBSCRIPTION_ID

# Output example:
# {
#   "appId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
#   "displayName": "healthcare-api-ci-cd",
#   "password": "xxxxxxxxxxxxxxxxxx",
#   "tenant": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
# }

# Map to GitHub Secrets:
# appId → AZURE_CLIENT_ID
# password → AZURE_CLIENT_SECRET
# tenant → AZURE_TENANT_ID
```

## Running Pipelines

### Manual Trigger (Workflow Dispatch)

Add to `.github/workflows/ci-cd.yml`:
```yaml
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]
  workflow_dispatch:  # Manual trigger
```

Then trigger via GitHub CLI:
```bash
gh workflow run ci-cd.yml --ref main
```

### Viewing Pipeline Runs

```bash
# List recent runs
gh run list --workflow=ci-cd.yml --limit=10

# View specific run
gh run view RUN_ID

# View logs
gh run view RUN_ID --log

# Stream live logs
gh run watch RUN_ID

# Check job status
gh run view RUN_ID --json jobs
```

## Environment Variables

### Build Environment

```yaml
REGISTRY: ghcr.io
IMAGE_NAME: asyed08/healthcare_appointment_booking_rest_api
JAVA_VERSION: '17'
JAVA_DISTRIBUTION: 'temurin'
```

### Database Test Service

```yaml
POSTGRES_USER: testuser
POSTGRES_PASSWORD: testpass123
POSTGRES_DB: healthcaredb_test
```

### Application Configuration

Set in `terraform.tfvars` or GitHub secrets:
```
SPRING_PROFILES_ACTIVE=prod
LOG_LEVEL=INFO
```

## Testing Strategy

### Unit Tests
- Run with `mvn test`
- JUnit 5 with Mockito
- Service layer testing
- DTO validation

### Integration Tests
- PostgreSQL test container
- Kafka test broker
- Controller integration tests
- End-to-end API flows

### Coverage Reports
- Uploaded to Codecov
- Minimum 80% coverage required (configure in codecov.yml)

## Artifact Management

### Build Artifacts

Generated and uploaded:
- `surefire-reports/` - Test execution reports
- `target/*.jar` - Packaged application
- Docker image pushed to GHCR

### Retention Policies

```yaml
# GitHub Actions artifacts
days-to-retain: 30
```

## Deployment Strategy

### Blue-Green Deployment

Uses App Service deployment slots:
1. Deploy to staging slot
2. Run smoke tests
3. Swap slots (staging → production)
4. Rollback by swapping back if needed

### Database Migrations

```bash
mvn flyway:migrate \
  -Dflyway.url="jdbc:postgresql://..." \
  -Dflyway.user="..." \
  -Dflyway.password="..."
```

Flyway ensures:
- Sequential migration ordering
- Failed migrations prevent deployment
- Automatic versioning
- Rollback capability

## Monitoring and Alerts

### GitHub Notifications

- Failed workflow: Email notification
- PR checks: Inline comments
- Deployment status: Environment page

### Azure Monitoring

- Application Insights: Performance metrics
- Alert Rules: High error rates, slowness
- Log Analytics: Detailed diagnostics

### Slack Integration (Optional)

Add to workflow:
```yaml
- name: Notify Slack
  if: always()
  uses: slackapi/slack-github-action@v1
  with:
    webhook-url: ${{ secrets.SLACK_WEBHOOK }}
    payload: |
      {
        "text": "Deployment ${{ job.status }}"
      }
```

## Troubleshooting

### Build Failures

```bash
# View logs
gh run view RUN_ID --log

# Check specific job
gh run view RUN_ID --json jobs --jq '.jobs[] | select(.name=="Build")'

# Debug locally
mvn clean test
mvn clean package -DskipTests
```

### Deployment Issues

```bash
# Check App Service logs
az webapp log tail --resource-group RG_NAME --name APP_NAME

# View Terraform apply logs
gh run view RUN_ID --log | grep -A 50 "Terraform Apply"

# Rollback if needed
az webapp deployment slot swap \
  --resource-group RG_NAME \
  --name APP_NAME \
  --slot staging
```

### Secret Issues

```bash
# Verify secret exists
gh secret list

# Update secret
gh secret set SECRET_NAME --body "new_value"

# Check if secret is being used
grep -r "secrets\." .github/workflows/
```

## Cost Optimization

### CI/CD Costs

- GitHub Actions: First 3000 minutes/month free
- Self-hosted runners: For high-volume builds
- Caching: Maven dependencies cached between runs
- Artifact retention: Limited to 30 days

### Recommendations

1. Use caching for Maven dependencies
2. Run security scans only on main branch
3. Parallel job execution
4. Self-hosted runners for frequent deployments

## Security Best Practices

1. **Least Privilege**: Service Principal with Contributor role (production: Owner)
2. **Secret Rotation**: Rotate keys quarterly
3. **Audit Logging**: Enable Azure audit logs
4. **Code Review**: PR reviews before merge
5. **Dependency Scanning**: Trivy for CVEs
6. **Branch Protection**: Require passing CI checks

## Integration with IDE

### Pre-commit Hooks

Install to validate before commit:
```bash
# Create .githooks/pre-commit
#!/bin/bash
mvn clean test
if [ $? -ne 0 ]; then
  exit 1
fi

# Configure git to use hooks
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit
```

### Local Testing

Simulate CI pipeline locally:
```bash
# Build Docker image
docker build -t healthcare-api:local .

# Run tests
mvn clean test

# Package application
mvn clean package -DskipTests
```

## Documentation

- Full pipeline file: `.github/workflows/ci-cd.yml`
- Infrastructure code: `infrastructure/`
- Deployment guide: `DEPLOYMENT.md`
- Local development: `README.md`
