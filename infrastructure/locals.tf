# Local values for Terraform
locals {
  # Naming convention
  resource_prefix = "${var.project_name}-${var.environment}"
  
  # Environment-specific settings
  environment_settings = {
    dev = {
      app_service_sku          = "B2"
      db_sku                   = "B_Standard_B1ms"
      db_storage_mb            = 32768
      db_backup_retention_days = 7
      db_ha_enabled            = false
      app_insights_retention   = 30
      law_retention            = 30
    }
    staging = {
      app_service_sku          = "S1"
      db_sku                   = "D_Standard_D2s_v3"
      db_storage_mb            = 65536
      db_backup_retention_days = 14
      db_ha_enabled            = true
      app_insights_retention   = 90
      law_retention            = 90
    }
    prod = {
      app_service_sku          = "S2"
      db_sku                   = "D_Standard_D4s_v3"
      db_storage_mb            = 131072
      db_backup_retention_days = 30
      db_ha_enabled            = true
      app_insights_retention   = 365
      law_retention            = 365
    }
  }

  current_env_settings = local.environment_settings[var.environment]

  # Common tags for all resources
  common_tags = merge(
    {
      Environment = var.environment
      Project     = var.project_name
      ManagedBy   = "Terraform"
    },
    var.tags
  )

  # Merge app settings with environment defaults
  final_app_settings = merge(
    {
      "SPRING_DATASOURCE_URL" = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/${var.db_name}?sslmode=require"
      "SPRING_JPA_DATABASE"   = "POSTGRESQL"
      "ENVIRONMENT"           = var.environment
      "REGION"                = var.azure_region
    },
    var.app_settings
  )
}
