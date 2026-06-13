terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.80"
    }
  }

  backend "azurerm" {
    resource_group_name  = "healthcare-api-rg"
    storage_account_name = "healthcareapitfstate"
    container_name       = "tfstate"
    key                  = "healthcare-api.tfstate"
  }
}

provider "azurerm" {
  features {
    key_vault {
      purge_soft_delete_on_destroy    = true
      recover_soft_deleted_key_vaults = true
    }
  }
}

locals {
  environment = var.environment
  project     = var.project_name
  location    = var.azure_region
  common_tags = {
    Environment = local.environment
    Project     = local.project
    ManagedBy   = "Terraform"
    CreatedAt   = timestamp()
  }
}

# Resource Group
resource "azurerm_resource_group" "main" {
  name       = "${local.project}-${local.environment}-rg"
  location   = local.location
  tags       = local.common_tags
}

# App Service Plan
resource "azurerm_service_plan" "main" {
  name                = "${local.project}-${local.environment}-asp"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  os_type             = "Linux"
  sku_name            = var.app_service_sku

  tags = local.common_tags
}

# App Service
resource "azurerm_linux_web_app" "main" {
  name                = "${local.project}-${local.environment}-app"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  service_plan_id     = azurerm_service_plan.main.id

  identity {
    type = "SystemAssigned"
  }

  app_settings = {
    WEBSITES_ENABLE_APP_SERVICE_STORAGE = false
    DOCKER_REGISTRY_SERVER_URL          = "https://index.docker.io"
    WEBSITES_PORT                       = "8080"
    JAVA_OPTS                           = "-Dserver.port=8080"
  }

  site_config {
    always_on                = true
    minimum_tls_version      = "1.2"
    http2_enabled            = true
    remote_debugging_enabled = false

    application_stack {
      java_version              = "17"
      java_server               = "TOMCAT"
      java_server_version       = "10.0"
    }
  }

  https_only = true
  tags       = local.common_tags

  depends_on = [azurerm_service_plan.main]
}

# PostgreSQL Database Server
resource "azurerm_postgresql_flexible_server" "main" {
  name                   = "${local.project}-${local.environment}-db"
  location               = azurerm_resource_group.main.location
  resource_group_name    = azurerm_resource_group.main.name
  admin_login            = var.db_admin_username
  admin_password         = var.db_admin_password
  database_charset       = "UTF8"
  database_collation     = "en_US.utf8"
  sku_name               = var.db_sku
  storage_mb             = var.db_storage_mb
  backup_retention_days  = 7
  geo_redundant_backup   = var.db_geo_redundant_backup

  version = "15"

  high_availability {
    mode = var.db_ha_enabled ? "ZoneRedundant" : null
  }

  tags = local.common_tags
}

# PostgreSQL Database
resource "azurerm_postgresql_flexible_server_database" "main" {
  name       = var.db_name
  server_id  = azurerm_postgresql_flexible_server.main.id
  charset    = "UTF8"
  collation  = "en_US.utf8"
}

# PostgreSQL Firewall Rule for Azure Services
resource "azurerm_postgresql_flexible_server_firewall_rule" "allow_azure_services" {
  name             = "allow-azure-services"
  server_id        = azurerm_postgresql_flexible_server.main.id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}

# PostgreSQL Firewall Rule for App Service
resource "azurerm_postgresql_flexible_server_firewall_rule" "allow_app_service" {
  name             = "allow-app-service"
  server_id        = azurerm_postgresql_flexible_server.main.id
  start_ip_address = azurerm_linux_web_app.main.outbound_ip_addresses[0]
  end_ip_address   = azurerm_linux_web_app.main.outbound_ip_addresses[0]
}

# Key Vault for Secrets Management
resource "azurerm_key_vault" "main" {
  name                        = "${replace(local.project, "-", "")}${local.environment}kv"
  location                    = azurerm_resource_group.main.location
  resource_group_name         = azurerm_resource_group.main.name
  enabled_for_disk_encryption = true
  tenant_id                   = data.azurerm_client_config.current.tenant_id
  sku_name                    = "standard"
  purge_protection_enabled    = var.key_vault_purge_protection

  tags = local.common_tags
}

# Key Vault Access Policy for App Service Managed Identity
resource "azurerm_key_vault_access_policy" "app_service" {
  key_vault_id       = azurerm_key_vault.main.id
  tenant_id          = data.azurerm_client_config.current.tenant_id
  object_id          = azurerm_linux_web_app.main.identity[0].principal_id

  secret_permissions = [
    "Get",
    "List"
  ]
}

# Key Vault Access Policy for Terraform Execution
resource "azurerm_key_vault_access_policy" "terraform" {
  key_vault_id       = azurerm_key_vault.main.id
  tenant_id          = data.azurerm_client_config.current.tenant_id
  object_id          = data.azurerm_client_config.current.object_id

  secret_permissions = [
    "Create",
    "Delete",
    "Get",
    "List",
    "Purge",
    "Recover",
    "Restore",
    "Set"
  ]
}

# Store Database Password in Key Vault
resource "azurerm_key_vault_secret" "db_password" {
  name         = "database-password"
  value        = var.db_admin_password
  key_vault_id = azurerm_key_vault.main.id
}

# Store Database Connection String in Key Vault
resource "azurerm_key_vault_secret" "db_connection_string" {
  name  = "database-connection-string"
  value = "postgresql://${var.db_admin_username}:${var.db_admin_password}@${azurerm_postgresql_flexible_server.main.fqdn}:5432/${var.db_name}?sslmode=require"
  key_vault_id = azurerm_key_vault.main.id
}

# Store Kafka Bootstrap Servers in Key Vault
resource "azurerm_key_vault_secret" "kafka_bootstrap_servers" {
  name         = "kafka-bootstrap-servers"
  value        = var.kafka_bootstrap_servers
  key_vault_id = azurerm_key_vault.main.id
}

# Store JWT Secret in Key Vault
resource "azurerm_key_vault_secret" "jwt_secret" {
  name         = "jwt-secret-key"
  value        = var.jwt_secret_key
  key_vault_id = azurerm_key_vault.main.id
}

# Container Registry (optional, for custom Docker images)
resource "azurerm_container_registry" "main" {
  name                = "${replace(local.project, "-", "")}${local.environment}acr"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "Basic"
  admin_enabled       = false

  tags = local.common_tags
}

# Container Registry Access for App Service
resource "azurerm_role_assignment" "app_service_acr" {
  scope              = azurerm_container_registry.main.id
  role_definition_name = "AcrPull"
  principal_id       = azurerm_linux_web_app.main.identity[0].principal_id
}

# Application Insights
resource "azurerm_application_insights" "main" {
  name                = "${local.project}-${local.environment}-ai"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  application_type    = "web"
  retention_in_days   = 30

  tags = local.common_tags
}

# Log Analytics Workspace
resource "azurerm_log_analytics_workspace" "main" {
  name                = "${local.project}-${local.environment}-law"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = 30

  tags = local.common_tags
}

# Diagnostic Settings for App Service
resource "azurerm_monitor_diagnostic_setting" "app_service" {
  name                       = "${local.project}-${local.environment}-app-diag"
  target_resource_id         = azurerm_linux_web_app.main.id
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id

  enabled_log {
    category = "AppServiceAppLogs"
  }

  enabled_log {
    category = "AppServiceAuditLogs"
  }

  enabled_log {
    category = "AppServiceConsoleLogs"
  }

  enabled_log {
    category = "AppServiceHTTPLogs"
  }

  metric {
    category = "AllMetrics"
  }
}

# Data source for current Azure context
data "azurerm_client_config" "current" {}
