# Deployment Guide

Complete guide for deploying the Healthcare API to Azure environments.

## Quick Start

### Prerequisites
1. Azure CLI installed: `az --version`
2. Terraform installed: `terraform -version`
3. Service Principal with credentials
4. GitHub repository access

### 5-Minute Setup

```bash
# 1. Clone and navigate
cd infrastructure

# 2. Create terraform.tfvars from example
cp terraform.tfvars.example terraform.tfvars

# 3. Edit variables (minimum: db_admin_password, jwt_secret_key)
nano terraform.tfvars

# 4. Initialize Terraform (requires Azure CLI login)
terraform init

# 5. Plan deployment
terraform plan -out=tfplan

# 6. Apply deployment
terraform apply tfplan

# 7. View outputs
terraform output
```

## Detailed Deployment Steps

### 1. Azure Account Setup

```bash
# Login
az login

# Set subscription
az account set --subscription "YOUR_SUBSCRIPTION_ID"

# Verify
az account show
```

### 2. Prepare Terraform State Backend

```bash
# Variables
RESOURCE_GROUP="healthcare-api-rg"
LOCATION="eastus"
STORAGE_ACCOUNT="healthcareapitfstate"
CONTAINER_NAME="tfstate"

# Create RG
az group create --name $RESOURCE_GROUP --location $LOCATION

# Create storage account
az storage account create \
  --name $STORAGE_ACCOUNT \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Standard_LRS

# Get storage key
STORAGE_KEY=$(az storage account keys list \
  --account-name $STORAGE_ACCOUNT \
  --resource-group $RESOURCE_GROUP \
  --query "[0].value" -o tsv)

# Create container
az storage container create \
  --name $CONTAINER_NAME \
  --account-name $STORAGE_ACCOUNT \
  --account-key $STORAGE_KEY
```

### 3. Configure Terraform Backend

```bash
cd infrastructure

# Option A: Interactive init
terraform init
# When prompted for backend config, enter:
# - Resource Group: healthcare-api-rg
# - Storage Account: healthcareapitfstate
# - Container: tfstate
# - Key: healthcare-api.tfstate

# Option B: Config file
cat > backend-config.hcl << EOF
storage_account_name = "healthcareapitfstate"
container_name       = "tfstate"
key                  = "healthcare-api.tfstate"
resource_group_name  = "healthcare-api-rg"
access_key           = "$STORAGE_KEY"
EOF

terraform init -backend-config=backend-config.hcl
```

### 4. Prepare Variables

```bash
# Copy template
cp terraform.tfvars.example terraform.tfvars

# Required edits
nano terraform.tfvars
```

**Minimum Required**:
```hcl
azure_region = "East US"
environment  = "dev"

# Database - Generate strong password
db_admin_password = "MySecureP@ssw0rd123!"

# JWT - Generate 32+ char random key
jwt_secret_key = "$(openssl rand -base64 32)"

# Kafka (if not using Confluent Cloud, use localhost)
kafka_bootstrap_servers = "localhost:9092"
```

### 5. Plan Infrastructure

```bash
# Generate plan
terraform plan -out=tfplan

# Review changes
terraform show tfplan

# For specific environment
terraform plan -var="environment=prod" -out=tfplan
```

### 6. Deploy Infrastructure

```bash
# Apply plan
terraform apply tfplan

# Or directly (requires confirmation)
terraform apply

# Auto-approve (CI/CD use)
terraform apply -auto-approve
```

**Deployment time**: ~10-15 minutes for first deployment

### 7. Verify Deployment

```bash
# Get outputs
terraform output

# Test database connection
DB_HOST=$(terraform output -raw database_server_fqdn)
DB_USER=$(terraform output -raw database_admin_username)

psql -h $DB_HOST -U $DB_USER -d healthcaredb -c "SELECT VERSION();"

# Test App Service (may take 2-3 minutes to start)
APP_HOST=$(terraform output -raw app_service_default_hostname)
curl https://$APP_HOST/health
```

## Environment-Specific Deployments

### Development Environment

```bash
# Create terraform.tfvars for dev
cat > terraform.dev.tfvars << EOF
environment       = "dev"
app_service_sku   = "B2"
db_admin_password = "DevP@ssw0rd123!"
jwt_secret_key    = "dev-jwt-secret-key-minimum-32-characters"
kafka_bootstrap_servers = "localhost:9092"
EOF

# Deploy
terraform plan -var-file="terraform.dev.tfvars" -out=tfplan.dev
terraform apply tfplan.dev
```

### Staging Environment

```bash
cat > terraform.staging.tfvars << EOF
environment              = "staging"
azure_region            = "East US"
app_service_sku         = "S1"
db_sku                  = "D_Standard_D2s_v3"
db_ha_enabled           = true
db_admin_password       = "StagingP@ssw0rd123!"
jwt_secret_key          = "staging-jwt-secret-key-minimum-32-characters"
kafka_bootstrap_servers = "kafka1:9092,kafka2:9092"
EOF

terraform plan -var-file="terraform.staging.tfvars" -out=tfplan.staging
terraform apply tfplan.staging
```

### Production Environment

```bash
cat > terraform.prod.tfvars << EOF
environment              = "prod"
azure_region            = "East US 2"
app_service_sku         = "S2"
db_sku                  = "D_Standard_D4s_v3"
db_ha_enabled           = true
db_geo_redundant_backup = true
db_admin_password       = "$(openssl rand -base64 32)"
jwt_secret_key          = "$(openssl rand -base64 48)"
kafka_bootstrap_servers = "kafka1:9092,kafka2:9092,kafka3:9092"
EOF

terraform plan -var-file="terraform.prod.tfvars" -out=tfplan.prod
terraform apply tfplan.prod
```

## Post-Deployment Configuration

### 1. Database Initialization

```bash
# Set connection parameters
export DB_HOST=$(terraform output -raw database_server_fqdn)
export DB_USER=$(terraform output -raw database_admin_username)
export DB_PASS="YOUR_PASSWORD"
export DB_NAME="healthcaredb"

# Run migrations
mvn flyway:migrate \
  -Dflyway.url="jdbc:postgresql://$DB_HOST:5432/$DB_NAME?sslmode=require" \
  -Dflyway.user="$DB_USER" \
  -Dflyway.password="$DB_PASS"
```

### 2. Configure Application Secrets

Store secrets in Key Vault:

```bash
KEY_VAULT=$(terraform output -raw key_vault_name)

# Already set by Terraform, but verify:
az keyvault secret show --vault-name $KEY_VAULT --name "jwt-secret-key"

# Set additional secrets if needed
az keyvault secret set \
  --vault-name $KEY_VAULT \
  --name "sendgrid-api-key" \
  --value "YOUR_SENDGRID_KEY"
```

### 3. Configure App Service

```bash
APP_NAME=$(terraform output -raw app_service_name)
RG=$(terraform output -raw resource_group_name)

# Set app settings
az webapp config appsettings set \
  --resource-group $RG \
  --name $APP_NAME \
  --settings \
    SPRING_PROFILES_ACTIVE=prod \
    LOG_LEVEL=INFO \
    SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

### 4. Test Application

```bash
APP_HOST=$(terraform output -raw app_service_default_hostname)

# Wait for app to start (may take 2-3 minutes)
for i in {1..30}; do
  if curl -f https://$APP_HOST/health 2>/dev/null; then
    echo "✓ Application is healthy"
    break
  fi
  echo "Waiting... ($i/30)"
  sleep 10
done

# Test API
curl -X POST https://$APP_HOST/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "TestP@ss123!",
    "firstName": "John",
    "lastName": "Doe",
    "role": "PATIENT"
  }'
```

## CI/CD Integration

### GitHub Actions Deployment

1. **Add Secrets to GitHub**:
   ```bash
   gh secret set AZURE_CLIENT_ID --body "YOUR_CLIENT_ID"
   gh secret set AZURE_CLIENT_SECRET --body "YOUR_CLIENT_SECRET"
   gh secret set AZURE_SUBSCRIPTION_ID --body "YOUR_SUBSCRIPTION_ID"
   gh secret set AZURE_TENANT_ID --body "YOUR_TENANT_ID"
   gh secret set TF_VAR_DB_ADMIN_PASSWORD --body "YOUR_DB_PASSWORD"
   gh secret set TF_VAR_JWT_SECRET_KEY --body "YOUR_JWT_KEY"
   gh secret set TF_VAR_KAFKA_BOOTSTRAP_SERVERS --body "YOUR_KAFKA_BROKERS"
   gh secret set APP_SERVICE_NAME --body "healthcare-api-prod-app"
   gh secret set DB_HOST --body "healthcare-api-prod-db.postgres.database.azure.com"
   gh secret set DB_USERNAME --body "psqladmin"
   gh secret set DB_PASSWORD --body "YOUR_DB_PASSWORD"
   gh secret set AZURE_RESOURCE_GROUP --body "healthcare-api-prod-rg"
   ```

2. **Push to Main**:
   ```bash
   git add infrastructure/ .github/workflows/ci-cd.yml
   git commit -m "feat: add terraform infrastructure and CI/CD pipeline"
   git push origin main
   ```

3. **Monitor Deployment**:
   ```bash
   # Watch workflow execution
   gh run watch
   
   # View logs
   gh run view --log
   ```

## Updating Infrastructure

### Add New Resource

```bash
# Edit main.tf to add resource
nano infrastructure/main.tf

# Plan changes
terraform plan -out=tfplan

# Review and apply
terraform apply tfplan
```

### Update Existing Resource

```bash
# Modify variables or resource configuration
terraform plan -out=tfplan

# Apply changes
terraform apply tfplan

# Verify deployment
curl https://$(terraform output -raw app_service_default_hostname)/health
```

### Destroy Infrastructure

```bash
# Plan destruction
terraform plan -destroy

# Destroy (removes ALL resources)
terraform destroy

# Auto-approve (careful!)
terraform destroy -auto-approve
```

## Troubleshooting

### Common Issues

#### 1. Authentication Failed
```bash
# Re-authenticate
az login --use-device-code

# Set subscription
az account set --subscription "YOUR_SUBSCRIPTION_ID"
```

#### 2. Backend State Lock
```bash
# Check lock status
terraform force-unlock LOCK_ID

# Or:
az storage blob delete \
  --account-name healthcareapitfstate \
  --container-name tfstate \
  --name "healthcare-api.tfstate.lock"
```

#### 3. Database Connection Issues
```bash
# Check firewall rules
az postgres flexible-server firewall-rule list \
  --resource-group $(terraform output -raw resource_group_name) \
  --name $(terraform output -raw database_server_name)

# Test connection
psql -h $(terraform output -raw database_server_fqdn) \
     -U psqladmin -d healthcaredb -c "SELECT 1;"
```

#### 4. App Service Not Starting
```bash
# Check logs
az webapp log tail \
  --resource-group $(terraform output -raw resource_group_name) \
  --name $(terraform output -raw app_service_name)

# Restart app
az webapp restart \
  --resource-group $(terraform output -raw resource_group_name) \
  --name $(terraform output -raw app_service_name)
```

### Debug Commands

```bash
# Show Terraform state
terraform show

# Show specific resource
terraform state show azurerm_linux_web_app.main

# List all resources
terraform state list

# Export outputs as JSON
terraform output -json

# Refresh state
terraform refresh

# Validate configuration
terraform validate
```

## Monitoring After Deployment

### Application Insights

```bash
# Get instrumentation key
terraform output application_insights_instrumentation_key

# View recent requests
az monitor app-insights events show \
  --app $(terraform output -raw application_insights_instrumentation_key | jq -r .value)
```

### Logs

```bash
# Stream app logs
az webapp log stream \
  --resource-group $(terraform output -raw resource_group_name) \
  --name $(terraform output -raw app_service_name)

# View recent logs
az webapp log download \
  --resource-group $(terraform output -raw resource_group_name) \
  --name $(terraform output -raw app_service_name) \
  --log-file logs.zip
```

### Database

```bash
# Connect and query
psql -h $(terraform output -raw database_server_fqdn) \
     -U psqladmin \
     -d healthcaredb \
     -c "SELECT NOW(); SELECT COUNT(*) FROM public.users;"
```

## Rollback Procedure

### If Deployment Fails

```bash
# Check deployment status
az webapp deployment list \
  --resource-group $(terraform output -raw resource_group_name) \
  --name $(terraform output -raw app_service_name)

# Swap with staging slot
az webapp deployment slot swap \
  --resource-group $(terraform output -raw resource_group_name) \
  --name $(terraform output -raw app_service_name) \
  --slot staging
```

### If Infrastructure Update Fails

```bash
# View state
terraform show

# Rollback last state
terraform apply -destroy

# Reapply with fixes
terraform plan -out=tfplan
terraform apply tfplan
```

## Cost Optimization

```bash
# Estimate monthly costs
terraform plan -json | grep -i "daily_cost\|monthly_cost"

# Use smaller SKUs for dev
terraform apply -var="app_service_sku=B1" -var="db_sku=B_Standard_B1ms"

# Use auto-scaling for variable loads
# (Configure in Azure portal or ARM template)
```

## Cleanup

```bash
# Remove all Azure resources
terraform destroy -auto-approve

# Remove local Terraform state (after destroying)
rm -rf .terraform terraform.tfstate*

# Remove infrastructure directory
rm -rf infrastructure
```

## Support

For detailed information, see:
- `infrastructure/README.md` - Terraform documentation
- `CI_CD.md` - GitHub Actions pipeline details
- `.github/workflows/ci-cd.yml` - Workflow configuration
- `terraform.tfvars.example` - Configuration template
