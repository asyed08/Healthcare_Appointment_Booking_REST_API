variable "azure_region" {
  description = "Azure region for resources"
  type        = string
  default     = "East US"

  validation {
    condition     = contains(["East US", "West US", "East US 2", "West US 2", "North Europe", "West Europe"], var.azure_region)
    error_message = "Please specify a valid Azure region."
  }
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod"
  }
}

variable "project_name" {
  description = "Project name"
  type        = string
  default     = "healthcare-api"
}

variable "app_service_sku" {
  description = "App Service Plan SKU"
  type        = string
  default     = "B2"

  validation {
    condition     = contains(["B1", "B2", "B3", "S1", "S2", "S3"], var.app_service_sku)
    error_message = "App Service SKU must be one of: B1, B2, B3, S1, S2, S3"
  }
}

variable "db_admin_username" {
  description = "PostgreSQL admin username"
  type        = string
  sensitive   = true
  default     = "psqladmin"
}

variable "db_admin_password" {
  description = "PostgreSQL admin password (minimum 8 characters, must contain uppercase, lowercase, number, special character)"
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.db_admin_password) >= 8 && can(regex("[A-Z]", var.db_admin_password)) && can(regex("[a-z]", var.db_admin_password)) && can(regex("[0-9]", var.db_admin_password)) && can(regex("[!@#$%^&*]", var.db_admin_password))
    error_message = "Database password must be at least 8 characters and contain uppercase, lowercase, number, and special character."
  }
}

variable "db_name" {
  description = "PostgreSQL database name"
  type        = string
  default     = "healthcaredb"
}

variable "db_sku" {
  description = "PostgreSQL Flexible Server SKU"
  type        = string
  default     = "B_Standard_B1ms"

  validation {
    condition     = can(regex("^(B_|D_|E_)", var.db_sku))
    error_message = "Database SKU must be a valid PostgreSQL Flexible Server SKU."
  }
}

variable "db_storage_mb" {
  description = "PostgreSQL storage in MB"
  type        = number
  default     = 32768 # 32 GB

  validation {
    condition     = var.db_storage_mb >= 32768 && var.db_storage_mb <= 2097152
    error_message = "Storage must be between 32 GB and 2 TB."
  }
}

variable "db_ha_enabled" {
  description = "Enable PostgreSQL High Availability"
  type        = bool
  default     = false
}

variable "db_geo_redundant_backup" {
  description = "Enable geo-redundant backup for PostgreSQL"
  type        = bool
  default     = false
}

variable "key_vault_purge_protection" {
  description = "Enable purge protection on Key Vault"
  type        = bool
  default     = true
}

variable "kafka_bootstrap_servers" {
  description = "Kafka bootstrap servers (comma-separated)"
  type        = string
  sensitive   = true
  default     = "localhost:9092"
}

variable "jwt_secret_key" {
  description = "JWT secret key for token signing"
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.jwt_secret_key) >= 32
    error_message = "JWT secret key must be at least 32 characters long."
  }
}

variable "app_settings" {
  description = "Additional app settings for App Service"
  type        = map(string)
  default     = {}
}

variable "tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}
