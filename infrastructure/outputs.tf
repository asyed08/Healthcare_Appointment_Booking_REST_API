output "app_service_name" {
  description = "Azure App Service name"
  value       = azurerm_linux_web_app.main.name
}

output "app_service_url" {
  description = "Public URL of the deployed app"
  value       = "https://${azurerm_linux_web_app.main.default_hostname}"
}

output "application_insights_instrumentation_key" {
  description = "Application Insights key — add as APPINSIGHTS_INSTRUMENTATIONKEY app setting if you want APM"
  value       = azurerm_application_insights.main.instrumentation_key
  sensitive   = true
}

output "resource_group_name" {
  description = "Resource group that contains all resources"
  value       = azurerm_resource_group.main.name
}