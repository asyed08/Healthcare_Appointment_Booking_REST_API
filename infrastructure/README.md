# Healthcare API - Terraform Infrastructure

This directory contains the Infrastructure as Code (IaC) for deploying the Healthcare Appointment Booking REST API to Azure using Terraform.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      Azure Resources                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────────────────────────────────────────┐     │
│  │          App Service (Linux - Java 17)           │     │
│  │  - Tomcat 10.0 runtime                           │     │
│  │  - Managed Identity (System Assigned)            │     │
│  │  - HTTPS only                                    │     │
│  └──────────────────────────────────────────────────┘     │
│                           ↓                                │
│  ┌──────────────────────────────────────────────────┐     │
│  │    PostgreSQL Flexible Server (v15)             │     │
│  │  - Automated backups (7+ days)                   │     │
│  │  - Optional HA (Zone-Redundant)                  │     │
│  │  - Firewall rules (App Service integration)      │     │
│  └──────────────────────────────────────────────────┘     │
│                           ↓                                │
│  ┌──────────────────────────────────────────────────┐     │
│  │         Azure Key Vault (Standard)               │     │
│  │  - Secrets: DB credentials, JWT key, etc.       │     │
│  │  - Managed Identity access policy                │     │
│  │  - Purge protection enabled                      │     │
│  └──────────────────────────────────────────────────┘     │
│                                                             │
│  ┌──────────────────────────────────────────────────┐     │
│  │    Application Insights + Log Analytics          │     │
│  │  - Application monitoring & diagnostics          │     │
│  │  - Performance tracking & alerting               │     │
│  └──────────────────────────────────────────────────┘     │
│                                                             │
│  ┌──────────────────────────────────────────────────┐     │
│  │   Container Registry (optional, for images)     │     │
│  │  - Private Docker image repository               │     │
│  └──────────────────────────────────────────────────┘     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Prerequisites

1. **Azure Subscription**: Active Azure account with appropriate permissions
2. **Terraform**: v1.5.0+
   ```bash
   brew install terraform  # macOS
   ```
3. **Azure CLI**: v2.50.0+
   ```bash
   brew install azure-cli  # macOS
   ```
4. **Service Principal**: For CI/CD authentication
5. **Storage Account**: For Terraform state backend

## Initial Setup

### 1. Create Resource Group and Backend Storage

```bash
# Set variables
RESOURCE_GROUP="healthcare-api-rg"
LOCATION="eastus"
STORAGE_ACCOUNT="healthcareapitfstate"
CONTAINER_NAME="tfstate"

# Create resource group
az group create \
  --name $RESOURCE_GROUP \
  --location $LOCATION

# Create storage account
az storage account create \
  --name $STORAGE_ACCOUNT \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Standard_LRS

# Get storage account access key
STORAGE_KEY=$(az storage account keys list \
  --account-name $STORAGE_ACCOUNT \
  --resource-group $RESOURCE_GROUP \
  --query "[0].value" -o tsv)

# Create container for Terraform state
az storage container create \
  --name $CONTAINER_NAME \
  --account-name $STORAGE_ACCOUNT \
  --account-key $STORAGE_KEY
```

### 2. Configure Azure CLI Authentication

```bash
# Login to Azure
az login

# Set default subscription
az account set --subscription "YOUR_SUBSCRIPTION_ID"

# Verify authentication
az account show
```

### 3. Initialize Terraform

```bash
cd infrastructure

# Create backend config file
cat > backend-config.hcl << EOF
storage_account_name = "healthcareapitfstate"
container_name       = "tfstate"
key                  = "healthcare-api.tfstate"
resource_group_name  = "healthcare-api-rg"
access_key           = "$STORAGE_KEY"
EOF

# Initialize with backend
terraform init -backend-config=backend-config.hcl
```

### 4. Configure Variables

```bash
# Copy example file
cp terraform.tfvars.example terraform.tfvars

# Edit with your values
nano terraform.tfvars
```

**Required Variables:**
- `db_admin_password`: PostgreSQL password (min 8 chars, uppercase, lowercase, number, special char)
- `jwt_secret_key`: JWT signing key (min 32 chars)
- `kafka_bootstrap_servers`: Kafka broker addresses

## Deployment

### Plan Deployment

```bash
# Review what Terraform will create
terraform plan -out=tfplan

# For specific environment
terraform plan -var="environment=prod" -out=tfplan
```

### Apply Deployment

```bash
# Deploy to Azure
terraform apply tfplan

# Or directly (non-interactive)
terraform apply -auto-approve
```

### Destroy Infrastructure

```bash
# Remove all resources (careful in production!)
terraform destroy

# With confirmation
terraform destroy -auto-approve
```

## Environment Configurations

### Development (Default)
```hcl
environment     = "dev"
app_service_sku = "B2"
db_sku          = "B_Standard_B1ms"
db_ha_enabled   = false
```

### Staging
```hcl
environment     = "staging"
app_service_sku = "S1"
db_sku          = "D_Standard_D2s_v3"
db_ha_enabled   = true
```

### Production
```hcl
environment     = "prod"
app_service_sku = "S2"
db_sku          = "D_Standard_D4s_v3"
db_ha_enabled   = true
```

## Outputs

After successful deployment, retrieve outputs:

```bash
# Show all outputs
terraform output

# Get specific output
terraform output app_service_default_hostname

# Get sensitive outputs
terraform output -json database_connection_string | jq -r '.value'
```

## Managing Secrets

### Add Secret to Key Vault

```bash
# Via Terraform
terraform apply -var='new_secret_key=new_value'

# Via Azure CLI
az keyvault secret set \
  --vault-name $(terraform output -raw key_vault_name) \
  --name my-secret \
  --value my-secret-value
```

### Retrieve Secret from App Service

Application can access secrets via Managed Identity:

```java
@Configuration
public class KeyVaultConfig {
    
    @Value("@{@com.azure.identity.credential#keyVaultClient}")
    private String jwtSecretKey;
    
    // Or use Azure SDK:
    // SecretClient secretClient = new SecretClientBuilder()
    //     .vaultUrl(keyVaultUri)
    //     .credential(new DefaultAzureCredential())
    //     .buildClient();
    // String secret = secretClient.getSecret("jwt-secret-key").getValue();
}
```

## Monitoring and Logging

### Application Insights

View application metrics and logs:
```bash
# Get instrumentation key
terraform output application_insights_instrumentation_key

# View logs
az monitor app-insights events show \
  --resource-group $(terraform output -raw resource_group_name) \
  --app $(terraform output -raw resource_group_name)-app
```

### Log Analytics

Query logs using KQL:
```bash
az monitor log-analytics query \
  --workspace $(terraform output -raw log_analytics_workspace_id) \
  --analytics-query "AppRequests | where DurationMs > 1000"
```

## Database Management

### Connect to PostgreSQL

```bash
# Get connection string
DB_HOST=$(terraform output -raw database_server_fqdn)
DB_USER=$(terraform output -raw database_admin_username)

# Connect with psql
psql -h $DB_HOST -U $DB_USER -d healthcaredb
```

### Run Migrations

```bash
mvn flyway:migrate \
  -Dflyway.url="jdbc:postgresql://$DB_HOST/healthcaredb" \
  -Dflyway.user="$DB_USER" \
  -Dflyway.password="$DB_PASSWORD"
```

## CI/CD Integration

### GitHub Secrets Required

Add these secrets to your GitHub repository settings:

```
AZURE_CLIENT_ID              - Service Principal Client ID
AZURE_CLIENT_SECRET          - Service Principal Client Secret
AZURE_SUBSCRIPTION_ID        - Azure Subscription ID
AZURE_TENANT_ID              - Azure Tenant ID
TF_VAR_DB_ADMIN_PASSWORD     - PostgreSQL admin password
TF_VAR_KAFKA_BOOTSTRAP_SERVERS - Kafka brokers
TF_VAR_JWT_SECRET_KEY        - JWT signing key
APP_SERVICE_NAME             - App Service resource name
DB_HOST                      - Database FQDN
DB_USERNAME                  - Database username
DB_PASSWORD                  - Database password
AZURE_PUBLISH_PROFILE        - App Service publish profile
AZURE_RESOURCE_GROUP         - Resource group name
```

### Service Principal Setup

```bash
# Create service principal
az ad sp create-for-rbac \
  --name healthcare-api-sp \
  --role Contributor \
  --scopes /subscriptions/YOUR_SUBSCRIPTION_ID

# Output will show:
# - appId (AZURE_CLIENT_ID)
# - password (AZURE_CLIENT_SECRET)
# - tenant (AZURE_TENANT_ID)
```

## Troubleshooting

### Terraform State Issues

```bash
# Refresh state
terraform refresh

# Show current state
terraform show

# Import existing resource
terraform import azurerm_resource_group.main /subscriptions/SUB_ID/resourceGroups/RG_NAME
```

### Database Connectivity

```bash
# Test connection
psql -h $(terraform output -raw database_server_fqdn) \
     -U psqladmin \
     -d healthcaredb \
     -c "SELECT version();"

# Check firewall rules
az postgres flexible-server firewall-rule list \
  --resource-group $(terraform output -raw resource_group_name) \
  --name $(terraform output -raw database_server_name)
```

### App Service Issues

```bash
# Check deployment logs
az webapp log tail \
  --resource-group $(terraform output -raw resource_group_name) \
  --name $(terraform output -raw app_service_name)

# Stream live logs
az webapp log stream \
  --resource-group $(terraform output -raw resource_group_name) \
  --name $(terraform output -raw app_service_name)
```

## Cost Management

### Estimate Costs

```bash
terraform plan -json | jq '.resource_changes[] | select(.type=="azurerm_*") | .change.after'
```

### Cost Reduction

1. **Development**: Use smaller SKUs (B1/B2, Basic database)
2. **Auto-scaling**: Configure App Service auto-scale rules
3. **Reserved Instances**: For production workloads
4. **Spot Instances**: For non-critical environments

## Security Best Practices

1. **Managed Identity**: App Service uses system-assigned identity
2. **Key Vault**: Secrets never stored in code or config
3. **Firewall**: Database accessible only from App Service
4. **TLS/HTTPS**: All communication encrypted
5. **Azure Policy**: Enforce compliance rules

## Maintenance

### Regular Tasks

- **Weekly**: Monitor Application Insights dashboards
- **Monthly**: Review and rotate secrets
- **Quarterly**: Update Terraform provider versions
- **Annually**: Review and optimize resource SKUs

### Update Terraform

```bash
# Update providers
terraform init -upgrade

# Update local Terraform
terraform version  # Check version
brew upgrade terraform  # macOS
```

## Support and Documentation

- [Terraform Azure Provider Docs](https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs)
- [Azure App Service Best Practices](https://learn.microsoft.com/en-us/azure/app-service/)
- [PostgreSQL Flexible Server Guide](https://learn.microsoft.com/en-us/azure/postgresql/)
- [Azure Key Vault Best Practices](https://learn.microsoft.com/en-us/azure/key-vault/general/best-practices)

## Related Documentation

- See [../CI_CD.md](../CI_CD.md) for GitHub Actions pipeline details
- See [../DEPLOYMENT.md](../DEPLOYMENT.md) for deployment procedures
