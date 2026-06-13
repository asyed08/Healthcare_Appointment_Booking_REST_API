# Backend configuration for remote state management
# Note: You must create the storage account and container before running terraform init
# 
# Create storage account with:
# az storage account create --name healthcareapitfstate --resource-group healthcare-api-rg --location eastus --sku Standard_LRS
# az storage container create --account-name healthcareapitfstate --name tfstate
#
# Then configure backend:
# terraform init -backend-config="storage_account_name=healthcareapitfstate" \
#                 -backend-config="container_name=tfstate" \
#                 -backend-config="key=healthcare-api.tfstate" \
#                 -backend-config="resource_group_name=healthcare-api-rg"
#
# Or use terraform backend config file (backend-config.hcl):
# storage_account_name = "healthcareapitfstate"
# container_name       = "tfstate"
# key                  = "healthcare-api.tfstate"
# resource_group_name  = "healthcare-api-rg"
# access_key           = "your_storage_account_access_key"
#
# terraform init -backend-config=backend-config.hcl
