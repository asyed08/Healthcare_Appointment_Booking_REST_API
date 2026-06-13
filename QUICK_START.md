# Quick Start Guide

Fast-track setup for deploying Healthcare API to Azure.

## 5-Minute Setup (Local Development)

```bash
# 1. Start local services (PostgreSQL, Kafka)
docker-compose up -d

# 2. Run the application
mvn spring-boot:run

# 3. Test API
curl http://localhost:8080/health
```

## 15-Minute Setup (Azure Development Deployment)

```bash
# 1. Login to Azure
az login
az account set --subscription "YOUR_SUBSCRIPTION_ID"

# 2. Create backend storage (one-time)
RESOURCE_GROUP="healthcare-api-rg"
LOCATION="eastus"
STORAGE_ACCOUNT="healthcareapitfstate"

az group create --name $RESOURCE_GROUP --location $LOCATION
az storage account create \
  --name $STORAGE_ACCOUNT \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION --sku Standard_LRS

STORAGE_KEY=$(az storage account keys list \
  --account-name $STORAGE_ACCOUNT \
  --resource-group $RESOURCE_GROUP \
  --query "[0].value" -o tsv)

az storage container create \
  --name tfstate \
  --account-name $STORAGE_ACCOUNT \
  --account-key $STORAGE_KEY

# 3. Initialize Terraform
cd infrastructure
terraform init \
  -backend-config="storage_account_name=$STORAGE_ACCOUNT" \
  -backend-config="container_name=tfstate" \
  -backend-config="key=healthcare-api.tfstate" \
  -backend-config="resource_group_name=$RESOURCE_GROUP" \
  -backend-config="access_key=$STORAGE_KEY"

# 4. Configure variables
cp terraform.tfvars.example terraform.tfvars
nano terraform.tfvars
# Edit:
#   db_admin_password = "YourSecureP@ss123"
#   jwt_secret_key = "your-32-plus-character-secret-key"

# 5. Deploy
terraform plan -out=tfplan
terraform apply tfplan

# 6. Get outputs
terraform output
echo "App URL: $(terraform output -raw app_service_default_hostname)"
```

## GitHub Actions Automated Deployment (Production)

### One-Time Setup

```bash
# 1. Create Service Principal
az ad sp create-for-rbac \
  --name healthcare-api-ci-cd \
  --role Contributor \
  --scopes /subscriptions/YOUR_SUBSCRIPTION_ID

# Copy: appId, password, tenant

# 2. Add GitHub Secrets
gh secret set AZURE_CLIENT_ID --body "appId_value"
gh secret set AZURE_CLIENT_SECRET --body "password_value"
gh secret set AZURE_SUBSCRIPTION_ID --body "YOUR_SUBSCRIPTION_ID"
gh secret set AZURE_TENANT_ID --body "tenant_value"
gh secret set TF_VAR_DB_ADMIN_PASSWORD --body "YourSecureP@ss123"
gh secret set TF_VAR_JWT_SECRET_KEY --body "your-32-plus-character-secret-key"
gh secret set TF_VAR_KAFKA_BOOTSTRAP_SERVERS --body "localhost:9092"
gh secret set APP_SERVICE_NAME --body "healthcare-api-prod-app"
gh secret set DB_HOST --body "healthcare-api-prod-db.postgres.database.azure.com"
gh secret set DB_USERNAME --body "psqladmin"
gh secret set DB_PASSWORD --body "YourSecureP@ss123"
gh secret set AZURE_RESOURCE_GROUP --body "healthcare-api-prod-rg"
```

### Automatic Deployment

```bash
# Just push to main!
git push origin main

# Watch deployment
gh run watch
```

---

## Useful Commands

### Local Development
```bash
# Start services
docker-compose up -d

# Stop services
docker-compose down

# View logs
docker-compose logs -f app

# Run application
mvn spring-boot:run

# Run tests
mvn clean test

# Build JAR
mvn clean package -DskipTests
```

### Terraform
```bash
cd infrastructure

# Initialize
terraform init

# Plan changes
terraform plan -out=tfplan

# Apply changes
terraform apply tfplan

# View outputs
terraform output

# Destroy infrastructure
terraform destroy
```

### Azure CLI
```bash
# View deployment logs
az webapp log tail --resource-group healthcare-api-prod-rg --name healthcare-api-prod-app

# Stream live logs
az webapp log stream --resource-group healthcare-api-prod-rg --name healthcare-api-prod-app

# Connect to database
psql -h $(terraform output -raw database_server_fqdn) \
     -U psqladmin -d healthcaredb

# Restart app
az webapp restart --resource-group healthcare-api-prod-rg --name healthcare-api-prod-app
```

### GitHub Actions
```bash
# List recent runs
gh run list --workflow=ci-cd.yml --limit=10

# View run details
gh run view RUN_ID

# View logs
gh run view RUN_ID --log

# Watch live run
gh run watch
```

---

## Environment Configuration

### Development
```hcl
environment       = "dev"
azure_region     = "East US"
app_service_sku  = "B2"
db_sku           = "B_Standard_B1ms"
db_ha_enabled    = false
```

### Staging
```hcl
environment      = "staging"
azure_region     = "East US"
app_service_sku  = "S1"
db_sku           = "D_Standard_D2s_v3"
db_ha_enabled    = true
```

### Production
```hcl
environment           = "prod"
azure_region         = "East US 2"
app_service_sku      = "S2"
db_sku               = "D_Standard_D4s_v3"
db_ha_enabled        = true
db_geo_redundant_backup = true
```

---

## Verify Deployment

```bash
# Get application URL
APP_URL=$(terraform output -raw app_service_default_hostname)

# Test health endpoint
curl https://$APP_URL/health

# Test API (register user)
curl -X POST https://$APP_URL/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "TestP@ss123!",
    "firstName": "John",
    "lastName": "Doe",
    "role": "PATIENT"
  }'
```

---

## Troubleshooting

### App Won't Start
```bash
# Check logs
az webapp log tail --resource-group RG --name APP_NAME

# Restart app
az webapp restart --resource-group RG --name APP_NAME
```

### Database Connection Failed
```bash
# Test connection
psql -h DATABASE_HOST -U psqladmin -d healthcaredb

# Check firewall rules
az postgres flexible-server firewall-rule list \
  --resource-group RG --name DB_NAME
```

### Workflow Failed
```bash
# View logs
gh run view RUN_ID --log

# Check secrets
gh secret list
```

### Terraform State Issues
```bash
# Refresh state
terraform refresh

# Show current state
terraform show

# Force unlock (if locked)
terraform force-unlock LOCK_ID
```

---

## Key Files

| File | Purpose |
|------|---------|
| `infrastructure/main.tf` | Azure resources definition |
| `infrastructure/variables.tf` | Configuration variables |
| `infrastructure/terraform.tfvars` | Your custom configuration |
| `.github/workflows/ci-cd.yml` | GitHub Actions pipeline |
| `docker-compose.yml` | Local development setup |
| `Dockerfile` | Container image |
| `INFRASTRUCTURE_SETUP.md` | Detailed setup guide |
| `DEPLOYMENT.md` | Deployment procedures |
| `CI_CD.md` | Pipeline documentation |

---

## Essential Secrets (GitHub)

```
AZURE_CLIENT_ID
AZURE_CLIENT_SECRET
AZURE_SUBSCRIPTION_ID
AZURE_TENANT_ID
TF_VAR_DB_ADMIN_PASSWORD
TF_VAR_JWT_SECRET_KEY
TF_VAR_KAFKA_BOOTSTRAP_SERVERS
APP_SERVICE_NAME
DB_HOST
DB_USERNAME
DB_PASSWORD
AZURE_RESOURCE_GROUP
```

---

## Next Steps

1. **For Local Development**: Run `docker-compose up -d`
2. **For Azure Deployment**: Follow "15-Minute Setup" above
3. **For CI/CD**: Add GitHub secrets and push to main
4. **For Details**: See relevant documentation file

---

## Status

✅ **Terraform**: Configured and ready to deploy  
✅ **GitHub Actions**: Configured and ready to use  
✅ **Docker**: Local development environment ready  
✅ **Documentation**: Complete and comprehensive  
✅ **Build**: Verified and passing  

**Ready for deployment!** 🚀

---

**For more details, see:**
- `INFRASTRUCTURE_SETUP.md` - Complete setup guide
- `infrastructure/README.md` - Terraform details
- `DEPLOYMENT.md` - Deployment procedures
- `CI_CD.md` - Pipeline details
