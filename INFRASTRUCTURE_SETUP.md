# Infrastructure and Deployment Setup Guide

Complete step-by-step guide for setting up and deploying the Healthcare API infrastructure using Terraform and GitHub Actions CI/CD.

## Table of Contents

1. [Project Structure](#project-structure)
2. [Quick Start](#quick-start)
3. [Prerequisites](#prerequisites)
4. [Azure Setup](#azure-setup)
5. [Terraform Configuration](#terraform-configuration)
6. [GitHub Actions Setup](#github-actions-setup)
7. [Deployment](#deployment)
8. [Post-Deployment](#post-deployment)
9. [Troubleshooting](#troubleshooting)

## Project Structure

```
Healthcare_Appointment_Booking_REST_API/
├── .github/
│   └── workflows/
│       └── ci-cd.yml              # GitHub Actions pipeline
├── infrastructure/
│   ├── main.tf                    # Main Terraform configuration
│   ├── variables.tf               # Input variables
│   ├── outputs.tf                 # Output values
│   ├── locals.tf                  # Local values
│   ├── backend.tf                 # State backend configuration
│   ├── terraform.tfvars.example   # Example variables
│   └── README.md                  # Terraform documentation
├── src/
│   └── main/
│       ├── java/                  # Application source code
│       └── resources/
│           └── db/migration/      # Flyway migrations
├── docker-compose.yml             # Local development
├── Dockerfile                     # Container image
├── DEPLOYMENT.md                  # Deployment procedures
├── CI_CD.md                       # CI/CD documentation
└── README.md                      # Project README
```

## Quick Start

### For Local Development

```bash
# Start local environment with Docker Compose
docker-compose up -d

# Application available at http://localhost:8080
# PostgreSQL: localhost:5432
# Kafka: localhost:9092

# Verify health
curl http://localhost:8080/health

# Stop services
docker-compose down
```

### For Azure Deployment

```bash
# 1. Navigate to infrastructure
cd infrastructure

# 2. Copy and configure variables
cp terraform.tfvars.example terraform.tfvars
nano terraform.tfvars

# 3. Deploy
terraform init
terraform plan -out=tfplan
terraform apply tfplan

# 4. Get outputs
terraform output
```

## Prerequisites

### Local Development
- **Docker & Docker Compose**: v4.0+
- **Java**: 17 or later (for local IDE development)
- **Maven**: 3.8.1+

### Azure Deployment
- **Azure Subscription**: Active account
- **Azure CLI**: v2.50.0+
- **Terraform**: v1.5.0+
- **Service Principal**: For CI/CD authentication
- **GitHub Account**: For Actions workflow

### Installation

```bash
# macOS (using Homebrew)
brew install azure-cli terraform docker

# Verify installations
az --version
terraform -version
docker --version
docker-compose --version
```

## Azure Setup

### Step 1: Create Azure Subscription

```bash
# List subscriptions
az account list

# Set active subscription
az account set --subscription "YOUR_SUBSCRIPTION_ID"

# Show current context
az account show
```

### Step 2: Create Resource Group for Terraform State

```bash
# Variables
RESOURCE_GROUP="healthcare-api-rg"
LOCATION="eastus"
STORAGE_ACCOUNT="healthcareapitfstate"  # Must be globally unique
CONTAINER_NAME="tfstate"

# Create resource group
az group create \
  --name $RESOURCE_GROUP \
  --location $LOCATION

echo "✓ Resource group created"
```

### Step 3: Create Storage Account for State Backend

```bash
# Create storage account
az storage account create \
  --name $STORAGE_ACCOUNT \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Standard_LRS \
  --encryption-services blob

echo "✓ Storage account created"

# Get storage account access key
STORAGE_KEY=$(az storage account keys list \
  --account-name $STORAGE_ACCOUNT \
  --resource-group $RESOURCE_GROUP \
  --query "[0].value" -o tsv)

echo "Storage key: $STORAGE_KEY"

# Create blob container
az storage container create \
  --name $CONTAINER_NAME \
  --account-name $STORAGE_ACCOUNT \
  --account-key $STORAGE_KEY

echo "✓ Storage container created"
```

### Step 4: Create Service Principal for CI/CD

```bash
# Create Service Principal with Contributor role
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

# Save these values:
# appId → AZURE_CLIENT_ID
# password → AZURE_CLIENT_SECRET
# tenant → AZURE_TENANT_ID
```

## Terraform Configuration

### Step 1: Initialize Terraform Working Directory

```bash
cd infrastructure

# Option A: Interactive initialization
terraform init

# Follow prompts to enter:
# - Resource Group: healthcare-api-rg
# - Storage Account: healthcareapitfstate
# - Container Name: tfstate
# - Key: healthcare-api.tfstate

# Option B: Non-interactive (using backend config)
cat > backend-config.hcl << EOF
storage_account_name = "healthcareapitfstate"
container_name       = "tfstate"
key                  = "healthcare-api.tfstate"
resource_group_name  = "healthcare-api-rg"
access_key           = "$STORAGE_KEY"
EOF

terraform init -backend-config=backend-config.hcl

echo "✓ Terraform initialized"
```

### Step 2: Configure Variables

```bash
# Copy example variables file
cp terraform.tfvars.example terraform.tfvars

# Edit with your values
nano terraform.tfvars

# Minimum required configuration:
cat >> terraform.tfvars << EOF

# Override defaults
azure_region = "East US"
environment  = "dev"

# Generate strong database password
# Requirements: 8+ chars, uppercase, lowercase, number, special char
db_admin_password = "MySecureP@ssw0rd123"

# Generate JWT secret (minimum 32 characters)
jwt_secret_key = "your-very-secure-jwt-secret-key-of-at-least-32-characters"

# Kafka bootstrap servers
kafka_bootstrap_servers = "localhost:9092"
EOF
```

### Step 3: Validate Configuration

```bash
# Validate Terraform files
terraform validate

# Format check
terraform fmt -check -recursive

# Auto-format
terraform fmt -recursive

echo "✓ Configuration validated"
```

## GitHub Actions Setup

### Step 1: Add GitHub Secrets

```bash
# Initialize GitHub CLI (if not done)
gh auth login

# Add secrets (requires repo context)
gh secret set AZURE_CLIENT_ID --body "YOUR_CLIENT_ID"
gh secret set AZURE_CLIENT_SECRET --body "YOUR_CLIENT_SECRET"
gh secret set AZURE_SUBSCRIPTION_ID --body "YOUR_SUBSCRIPTION_ID"
gh secret set AZURE_TENANT_ID --body "YOUR_TENANT_ID"
gh secret set TF_VAR_DB_ADMIN_PASSWORD --body "YOUR_DB_PASSWORD"
gh secret set TF_VAR_JWT_SECRET_KEY --body "YOUR_JWT_KEY"
gh secret set TF_VAR_KAFKA_BOOTSTRAP_SERVERS --body "localhost:9092"
gh secret set APP_SERVICE_NAME --body "healthcare-api-prod-app"
gh secret set DB_HOST --body "healthcare-api-prod-db.postgres.database.azure.com"
gh secret set DB_USERNAME --body "psqladmin"
gh secret set DB_PASSWORD --body "YOUR_DB_PASSWORD"
gh secret set AZURE_RESOURCE_GROUP --body "healthcare-api-prod-rg"

# Verify secrets (does not show values)
gh secret list

echo "✓ GitHub secrets configured"
```

### Step 2: Commit and Push Infrastructure Code

```bash
# Navigate to project root
cd /path/to/Healthcare_Appointment_Booking_REST_API

# Add infrastructure files
git add infrastructure/ .github/workflows/ci-cd.yml
git add DEPLOYMENT.md CI_CD.md INFRASTRUCTURE_SETUP.md

# Commit
git commit -m "feat: add terraform infrastructure and github actions ci/cd pipeline"

# Push to main (will trigger workflow)
git push origin main

echo "✓ Infrastructure code pushed to GitHub"
```

## Deployment

### Option 1: Manual Terraform Deployment (Development)

```bash
cd infrastructure

# Plan deployment
terraform plan -out=tfplan

# Review plan output
terraform show tfplan

# Apply plan
terraform apply tfplan

# View deployment results
terraform output

# Get App Service URL
APP_URL=$(terraform output -raw app_service_default_hostname)
echo "Application URL: https://$APP_URL"
```

### Option 2: Automated GitHub Actions Deployment (Production)

1. **Push to main branch** (automatically triggers deployment)
   ```bash
   git push origin main
   ```

2. **Monitor workflow** in GitHub Actions tab
   ```bash
   gh run list --workflow=ci-cd.yml --limit=5
   gh run view  # Watch latest run
   ```

3. **View deployment logs**
   ```bash
   gh run view --log
   ```

### Option 3: Terraform Cloud/Enterprise (Enterprise)

```bash
# Create Terraform Cloud account
# https://app.terraform.io

# Authenticate
terraform login

# Follow prompts to generate API token
# Configure terraform block:

cat > infrastructure/terraform.tf << EOF
terraform {
  cloud {
    organization = "YOUR_ORGANIZATION"

    workspaces {
      name = "healthcare-api"
    }
  }
}
EOF

# Run remote operations
terraform plan
terraform apply
```

## Post-Deployment

### Step 1: Verify Deployment

```bash
# Get outputs
cd infrastructure
terraform output

# Test database connectivity
DB_HOST=$(terraform output -raw database_server_fqdn)
DB_USER=$(terraform output -raw database_admin_username)

psql -h "$DB_HOST" -U "$DB_USER" -d healthcaredb -c "SELECT version();"

# Test application endpoint (wait 2-3 minutes for app to start)
APP_HOST=$(terraform output -raw app_service_default_hostname)

# Health check
curl https://"$APP_HOST"/health

# Test API
curl -X POST https://"$APP_HOST"/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "TestP@ss123!",
    "firstName": "Test",
    "lastName": "User",
    "role": "PATIENT"
  }' | jq .
```

### Step 2: Run Database Migrations

```bash
# Get database credentials from Terraform outputs
export DB_HOST=$(terraform output -raw database_server_fqdn)
export DB_USER=$(terraform output -raw database_admin_username)
export DB_NAME="healthcaredb"
export DB_PORT="5432"

# Run Flyway migrations
cd /path/to/Healthcare_Appointment_Booking_REST_API

mvn flyway:migrate \
  -Dflyway.url="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME?sslmode=require" \
  -Dflyway.user="$DB_USER" \
  -Dflyway.password="$DB_PASSWORD" \
  -Dflyway.locations="filesystem:src/main/resources/db/migration"

# Verify migrations
psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" << EOF
SELECT version, description, success FROM public.flyway_schema_history;
EOF
```

### Step 3: Configure Monitoring

```bash
# Enable Application Insights
APP_INSIGHTS_ID=$(terraform output -raw application_insights_id)
APP_NAME=$(terraform output -raw app_service_name)
RG=$(terraform output -raw resource_group_name)

az webapp config set \
  --resource-group "$RG" \
  --name "$APP_NAME" \
  --linux-fx-version "JAVA|17-java17"

# Set up alerts
az monitor metrics alert create \
  --resource-group "$RG" \
  --name "HighErrorRate" \
  --scopes "$APP_INSIGHTS_ID" \
  --condition "avg RequestsFailed > 10" \
  --window-size 5m \
  --evaluation-frequency 1m \
  --action email

echo "✓ Monitoring configured"
```

### Step 4: Configure Custom Domain (Optional)

```bash
# Verify domain ownership in Azure DNS
# Add CNAME record pointing to your App Service

# Configure HTTPS certificate
az webapp config ssl bind \
  --resource-group "$RG" \
  --name "$APP_NAME" \
  --certificate-thumbprint "THUMBPRINT" \
  --ssl-type SNI
```

## Troubleshooting

### Terraform Issues

```bash
# Validate configuration
terraform validate

# Check state
terraform state list
terraform state show azurerm_linux_web_app.main

# Debug output
terraform plan -var-file=terraform.tfvars -out=tfplan
terraform show tfplan

# Refresh state
terraform refresh

# Force unlock (if state is locked)
terraform force-unlock LOCK_ID
```

### Azure CLI Issues

```bash
# Verify authentication
az account show

# Re-authenticate if needed
az login --use-device-code

# Check subscription
az account list

# Set correct subscription
az account set --subscription "YOUR_SUBSCRIPTION_ID"
```

### Deployment Issues

```bash
# Check App Service logs
az webapp log tail --resource-group "$RG" --name "$APP_NAME"

# Stream live logs
az webapp log stream --resource-group "$RG" --name "$APP_NAME"

# Check deployment status
az webapp deployment list --resource-group "$RG" --name "$APP_NAME"

# Restart app
az webapp restart --resource-group "$RG" --name "$APP_NAME"
```

### Database Connection Issues

```bash
# Test connection directly
psql -h "$(terraform output -raw database_server_fqdn)" \
     -U "$(terraform output -raw database_admin_username)" \
     -d healthcaredb \
     -c "SELECT 1;"

# Check firewall rules
az postgres flexible-server firewall-rule list \
  --resource-group "$(terraform output -raw resource_group_name)" \
  --name "$(terraform output -raw database_server_name)"

# Add firewall rule if needed
az postgres flexible-server firewall-rule create \
  --resource-group "$(terraform output -raw resource_group_name)" \
  --name "$(terraform output -raw database_server_name)" \
  --rule-name "AllowMyIP" \
  --start-ip-address "YOUR.IP.ADDRESS" \
  --end-ip-address "YOUR.IP.ADDRESS"
```

## Cleanup

### Destroy Infrastructure

```bash
cd infrastructure

# Plan destruction
terraform plan -destroy

# Apply destruction
terraform destroy

# Confirm when prompted
```

### Remove Local State Files

```bash
# WARNING: Only do this after terraform destroy succeeds
rm -rf .terraform
rm terraform.tfstate*
rm backend-config.hcl
rm tfplan*
```

### Clean Up Azure Resources Manually

```bash
# List all resource groups
az group list --query "[].name" -o tsv

# Delete specific resource group
az group delete --name healthcare-api-rg --yes

# Delete storage account
az storage account delete \
  --name healthcareapitfstate \
  --resource-group healthcare-api-rg \
  --yes
```

## Useful Commands Summary

```bash
# Terraform
terraform init                    # Initialize
terraform validate               # Validate configuration
terraform plan                   # Show what will be created
terraform apply                  # Create/update resources
terraform destroy                # Delete all resources
terraform output                 # Show outputs
terraform state list             # List resources
terraform import RESOURCE ID     # Import existing resource
terraform refresh                # Sync state

# Azure CLI
az login                          # Authenticate
az account show                   # Current account
az account set --subscription ID  # Switch subscription
az group create                   # Create resource group
az provider show --namespace NAME # Check provider

# GitHub CLI
gh secret list                    # List secrets
gh secret set NAME                # Add/update secret
gh run list                       # List workflow runs
gh run view RUN_ID                # View run details
gh run watch                      # Watch current run
```

## Next Steps

1. ✅ Complete all prerequisites
2. ✅ Set up Azure subscription and Service Principal
3. ✅ Configure Terraform backend storage
4. ✅ Add GitHub secrets
5. ✅ Deploy infrastructure using Terraform
6. ✅ Run database migrations
7. ✅ Configure monitoring and alerting
8. ✅ Set up custom domain (optional)
9. 📖 Review monitoring dashboards
10. 🔄 Implement automated backups

## Support Documentation

- **Terraform Documentation**: `infrastructure/README.md`
- **CI/CD Pipeline**: `CI_CD.md`
- **Deployment Procedures**: `DEPLOYMENT.md`
- **Project README**: `README.md`

## Additional Resources

- [Terraform Azure Provider](https://registry.terraform.io/providers/hashicorp/azurerm/latest)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Azure CLI Reference](https://learn.microsoft.com/cli/azure/)
- [Spring Boot on Azure](https://learn.microsoft.com/en-us/azure/developer/java/spring-framework/)
