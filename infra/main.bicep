metadata description = 'Provisions resources for the multi-agent-spring-ai sample application that uses Azure Cosmos DB for NoSQL and Azure OpenAI.'

targetScope = 'subscription'

@minLength(1)
@maxLength(64)
@description('Name of the environment that can be used as part of naming resource convention.')
param environmentName string

@minLength(1)
@description('Primary location for all resources.')
param location string

@description('Id of the principal to assign database and application roles.')
param deploymentUserPrincipalId string = ''

@description('Owner tag for resource tagging')
param owner string = ''

var resourceToken = toLower(uniqueString(subscription().id, environmentName, location))
var tags = {
  'azd-env-name': environmentName
  owner: owner
  repo: 'https://github.com/azurecosmosdb/multi-agent-spring-ai'
}

resource resourceGroup 'Microsoft.Resources/resourceGroups@2021-04-01' = {
  name: 'rg-${environmentName}'
  location: location
  tags: tags
}

module resources 'resources.bicep' = {
  name: 'resources'
  scope: resourceGroup
  params: {
    location: location
    resourceToken: resourceToken
    tags: tags
    deploymentUserPrincipalId: deploymentUserPrincipalId
    environmentName: environmentName
  }
}

// Environment file outputs
output AZURE_COSMOSDB_ENDPOINT string = resources.outputs.AZURE_COSMOSDB_ENDPOINT
output AZURE_OPENAI_ENDPOINT string = resources.outputs.AZURE_OPENAI_ENDPOINT
output AZURE_OPENAI_EMBEDDING_DEPLOYMENT string = resources.outputs.AZURE_OPENAI_EMBEDDING_DEPLOYMENT
output AZURE_OPENAI_GPT_DEPLOYMENT string = resources.outputs.AZURE_OPENAI_GPT_DEPLOYMENT
