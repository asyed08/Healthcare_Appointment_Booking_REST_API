variable "azure_region" {
  description = "Azure region"
  type        = string
  default     = "East US"
}

variable "environment" {
  description = "Environment (dev, staging, prod)"
  type        = string
  default     = "prod"
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Must be dev, staging, or prod."
  }
}

variable "project_name" {
  description = "Project name used as a prefix for all resources"
  type        = string
  default     = "healthcare-api"
}

variable "app_service_sku" {
  description = "App Service Plan SKU (B1 = cheapest, S1 = standard)"
  type        = string
  default     = "B1"
}

# ---------------------------------------------------------------------------
# Database (Neon Tech)
# ---------------------------------------------------------------------------
variable "spring_datasource_url" {
  description = "JDBC URL for Neon Postgres — e.g. jdbc:postgresql://ep-xxx.neon.tech/neondb?sslmode=require"
  type        = string
  sensitive   = true
}

variable "spring_datasource_username" {
  description = "Neon Postgres username"
  type        = string
  sensitive   = true
}

variable "spring_datasource_password" {
  description = "Neon Postgres password"
  type        = string
  sensitive   = true
}

# ---------------------------------------------------------------------------
# Kafka (Confluent Cloud)
# ---------------------------------------------------------------------------
variable "kafka_bootstrap_servers" {
  description = "Confluent Cloud bootstrap server — e.g. pkc-xxx.eastus.azure.confluent.cloud:9092"
  type        = string
  sensitive   = true
}

variable "kafka_sasl_jaas_config" {
  description = "Full JAAS config string for Confluent Cloud SASL/PLAIN auth"
  type        = string
  sensitive   = true
}

# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------
variable "jwt_secret_key" {
  description = "Base64-encoded JWT signing key (min 32 chars)"
  type        = string
  sensitive   = true
  validation {
    condition     = length(var.jwt_secret_key) >= 32
    error_message = "JWT secret key must be at least 32 characters."
  }
}

# ---------------------------------------------------------------------------
# Email
# ---------------------------------------------------------------------------
variable "mail_username" {
  description = "Gmail address used as the sender"
  type        = string
}

variable "mail_password" {
  description = "Gmail app password (not your account password)"
  type        = string
  sensitive   = true
}

# ---------------------------------------------------------------------------
# GitHub Container Registry (GHCR)
# ---------------------------------------------------------------------------
variable "ghcr_username" {
  description = "GitHub username (used to pull the Docker image from GHCR)"
  type        = string
}

variable "ghcr_token" {
  description = "GitHub PAT with read:packages scope (to pull from GHCR)"
  type        = string
  sensitive   = true
}

variable "ghcr_image" {
  description = "Full GHCR image path without tag — e.g. ghcr.io/your-username/healthcare_appointment_booking_rest_api"
  type        = string
}

# ---------------------------------------------------------------------------
# Misc
# ---------------------------------------------------------------------------
variable "tags" {
  description = "Extra tags to add to all resources"
  type        = map(string)
  default     = {}
}