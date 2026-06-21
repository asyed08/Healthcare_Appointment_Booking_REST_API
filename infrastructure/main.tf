terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.80"
    }
  }

  # Remote state in Azure Blob Storage.
  # Create the storage account once before running terraform init:
  #   az group create -n healthcare-api-rg -l eastus
  #   az storage account create -n healthcareapitfstate -g healthcare-api-rg -l eastus --sku Standard_LRS
  #   az storage container create -n tfstate --account-name healthcareapitfstate
  backend "azurerm" {
    resource_group_name  = "healthcare-api-rg"
    storage_account_name = "healthcareapitfstate"
    container_name       = "tfstate"
    key                  = "healthcare-api.tfstate"
  }
}

provider "azurerm" {
  features {}
}

locals {
  prefix = "${var.project_name}-${var.environment}"
  common_tags = merge(
    {
      Environment = var.environment
      Project     = var.project_name
      ManagedBy   = "Terraform"
    },
    var.tags
  )
}

# ---------------------------------------------------------------------------
# Resource Group
# ---------------------------------------------------------------------------
resource "azurerm_resource_group" "main" {
  name     = "healthcare-rg"
  location = var.azure_region
  tags     = local.common_tags
}

# ---------------------------------------------------------------------------
# App Service Plan
# ---------------------------------------------------------------------------
resource "azurerm_service_plan" "main" {
  name                = "ASP-healthcarerg-a501"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  os_type             = "Linux"
  sku_name            = var.app_service_sku
  tags                = local.common_tags
}

# ---------------------------------------------------------------------------
# Linux Web App  (runs Docker image from GHCR)
# ---------------------------------------------------------------------------
resource "azurerm_linux_web_app" "main" {
  name                = "Healthcare-Booking"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  service_plan_id     = azurerm_service_plan.main.id
  https_only          = true
  tags                = local.common_tags

  app_settings = {
    # Database (Neon Tech)
    SPRING_DATASOURCE_URL      = var.spring_datasource_url
    SPRING_DATASOURCE_USERNAME = var.spring_datasource_username
    SPRING_DATASOURCE_PASSWORD = var.spring_datasource_password

    # Kafka (Confluent Cloud)
    SPRING_KAFKA_BOOTSTRAP_SERVERS = var.kafka_bootstrap_servers
    KAFKA_SECURITY_PROTOCOL        = "SASL_SSL"
    KAFKA_SASL_MECHANISM           = "PLAIN"
    KAFKA_SASL_JAAS_CONFIG         = var.kafka_sasl_jaas_config

    # Auth
    JWT_SECRET_KEY = var.jwt_secret_key

    # Email
    MAIL_USERNAME = var.mail_username
    MAIL_PASSWORD = var.mail_password

    # Docker / Azure App Service
    WEBSITES_PORT                       = "8080"
    DOCKER_REGISTRY_SERVER_URL          = "https://ghcr.io"
    DOCKER_REGISTRY_SERVER_USERNAME     = var.ghcr_username
    DOCKER_REGISTRY_SERVER_PASSWORD     = var.ghcr_token
  }

  site_config {
    always_on           = true
    minimum_tls_version = "1.2"
    http2_enabled       = true

    application_stack {
      docker_image_name   = "${var.ghcr_image}:main"
      docker_registry_url = "https://ghcr.io"
    }
  }
}
