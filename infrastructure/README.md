# Infrastructure — Terraform on Azure

Terraform configuration that provisions the Azure resources needed to run the Healthcare API.

## What Gets Provisioned

```
Azure
├── Resource Group
├── App Service Plan (Linux)
├── Linux Web App  ← runs the Docker image from GHCR
└── Application Insights  ← optional APM / monitoring
```

External services (managed outside Terraform):
- **Database** — [Neon Tech](https://neon.tech) PostgreSQL (serverless, free tier available)
- **Kafka** — [Confluent Cloud](https://confluent.io) (free tier available)
- **Docker registry** — GitHub Container Registry (GHCR, free for public repos)

## Prerequisites

- [Terraform](https://developer.hashicorp.com/terraform/install) v1.5+  
  `brew install terraform`
- [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli)  
  `brew install azure-cli`
- An active Azure subscription

## One-time Setup

### 1. Create the Terraform state backend

Terraform stores its state remotely in Azure Blob Storage so the CI/CD pipeline and your local machine share the same state.

```bash
az login

RESOURCE_GROUP="healthcare-api-rg"
STORAGE_ACCOUNT="healthcareapitfstate"   # must be globally unique

az group create -n $RESOURCE_GROUP -l eastus

az storage account create \
  -n $STORAGE_ACCOUNT \
  -g $RESOURCE_GROUP \
  -l eastus \
  --sku Standard_LRS

az storage container create \
  -n tfstate \
  --account-name $STORAGE_ACCOUNT
```

### 2. Create a Service Principal for CI/CD

```bash
az ad sp create-for-rbac \
  --name healthcare-api-sp \
  --role Contributor \
  --scopes /subscriptions/$(az account show --query id -o tsv)
```

Save the output — you'll need `appId`, `password`, and `tenant` as GitHub secrets.

### 3. Configure variables

```bash
cd infrastructure
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your real values
```

Required values in `terraform.tfvars`:

| Variable | Where to find it |
|----------|-----------------|
| `spring_datasource_url` | Neon dashboard → Connection string (convert to JDBC format: `jdbc:postgresql://...`) |
| `spring_datasource_username` | Neon dashboard |
| `spring_datasource_password` | Neon dashboard |
| `kafka_bootstrap_servers` | Confluent Cloud → Cluster → Endpoints |
| `kafka_sasl_jaas_config` | Confluent Cloud → API Keys (wrap in JAAS format — see example file) |
| `jwt_secret_key` | Any Base64 string ≥ 32 chars |
| `mail_username` | Your Gmail address |
| `mail_password` | Gmail → Security → App Passwords |
| `ghcr_username` | Your GitHub username |
| `ghcr_token` | GitHub → Settings → Developer settings → Personal access tokens (need `read:packages`) |
| `ghcr_image` | `ghcr.io/<github-username>/<repo-name>` |

### 4. Initialise and deploy

```bash
cd infrastructure

terraform init
terraform plan      # review what will be created
terraform apply     # deploy
```

## Day-to-day Commands

```bash
# See what's currently deployed
terraform show

# Preview changes without applying
terraform plan

# Apply changes
terraform apply

# Destroy everything (careful in production!)
terraform destroy
```

## Outputs

After `terraform apply`:

```bash
terraform output app_service_url        # public URL of your app
terraform output app_service_name       # name of the App Service resource
terraform output resource_group_name    # resource group

# Sensitive — requires -json flag
terraform output -json application_insights_instrumentation_key
```

## CI/CD Pipeline

The pipeline lives at `.github/workflows/ci-cd.yml` in the **repo root** — GitHub requires that exact path and won't pick up workflows from anywhere else.

On every push to `main` the pipeline:
1. Runs tests (H2 in-memory, no external services needed)
2. Runs a Trivy security scan
3. Builds and pushes a Docker image to GHCR
4. Runs `terraform apply` (using the secrets below)
5. Deploys the new image to App Service + health check

On pull requests it runs steps 1–2 plus `terraform plan`, and posts the plan output as a PR comment so you can review infrastructure changes before merging.

## GitHub Secrets for CI/CD

Add these in your repo → Settings → Secrets and variables → Actions:

| Secret | Description |
|--------|-------------|
| `AZURE_CLIENT_ID` | Service principal `appId` |
| `AZURE_CLIENT_SECRET` | Service principal `password` |
| `AZURE_SUBSCRIPTION_ID` | Your Azure subscription ID |
| `AZURE_TENANT_ID` | Service principal `tenant` |
| `AZURE_PUBLISH_PROFILE` | App Service → Get publish profile (XML file contents) |
| `APP_SERVICE_NAME` | Name of the App Service resource (e.g. `healthcare-api-prod-app`) |
| `TF_VAR_SPRING_DATASOURCE_URL` | JDBC URL for Neon |
| `TF_VAR_SPRING_DATASOURCE_USERNAME` | Neon username |
| `TF_VAR_SPRING_DATASOURCE_PASSWORD` | Neon password |
| `TF_VAR_KAFKA_BOOTSTRAP_SERVERS` | Confluent bootstrap server |
| `TF_VAR_KAFKA_SASL_JAAS_CONFIG` | Full JAAS config string |
| `TF_VAR_JWT_SECRET_KEY` | JWT signing key |
| `MAIL_USERNAME` | Gmail sender address |
| `MAIL_PASSWORD` | Gmail app password |

## Monitoring

Application Insights is provisioned automatically. To connect it to your app, add the instrumentation key as an app setting:

```bash
KEY=$(terraform output -json application_insights_instrumentation_key | jq -r '.value')

az webapp config appsettings set \
  -g $(terraform output -raw resource_group_name) \
  -n $(terraform output -raw app_service_name) \
  --settings APPINSIGHTS_INSTRUMENTATIONKEY=$KEY
```

## Viewing App Service Logs

```bash
az webapp log tail \
  -g $(terraform output -raw resource_group_name) \
  -n $(terraform output -raw app_service_name)
```

## Troubleshooting

**`terraform init` fails** — Make sure the storage account and container exist (step 1 above).

**`terraform apply` fails with auth error** — Run `az login` and confirm the right subscription is active: `az account show`.

**App won't start after deploy** — Check logs with the command above. Common causes: wrong env var values (especially the JDBC URL format), or the Docker image wasn't pushed before deploy ran.