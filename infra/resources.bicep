metadata description = 'Creates the Azure resources for the multi-agent-spring-ai application.'

targetScope = 'resourceGroup'

@description('Primary location for all resources.')
param location string

@description('Resource token for unique naming.')
param resourceToken string

@description('Tags to apply to resources.')
param tags object

@description('Id of the principal to assign database and application roles.')
param deploymentUserPrincipalId string

@description('Environment name for GUID generation.')
param environmentName string

resource managedIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: 'managed-identity-${resourceToken}'
  location: location
  tags: tags
}

resource cosmosDbAccount 'Microsoft.DocumentDB/databaseAccounts@2024-11-15' = {
  name: 'cosmos-db-nosql-${resourceToken}'
  location: location
  kind: 'GlobalDocumentDB'
  properties: {
    databaseAccountOfferType: 'Standard'
    locations: [
      {
        locationName: location
        failoverPriority: 0
        isZoneRedundant: false
      }
    ]
    disableLocalAuth: true
    publicNetworkAccess: 'Enabled'
    ipRules: []
    virtualNetworkRules: []
    capabilities: [
      {
        name: 'EnableNoSQLVectorSearch'
      }
    ]
  }
  tags: tags
}

resource sqlRoleDefinition 'Microsoft.DocumentDB/databaseAccounts/sqlRoleDefinitions@2024-11-15' = {
  name: guid('nosql-data-plane-contributor', environmentName)
  parent: cosmosDbAccount
  properties: {
    roleName: 'nosql-data-plane-contributor'
    type: 'CustomRole'
    assignableScopes: [
      cosmosDbAccount.id
    ]
    permissions: [
      {
        dataActions: [
          'Microsoft.DocumentDB/databaseAccounts/readMetadata'
          'Microsoft.DocumentDB/databaseAccounts/sqlDatabases/containers/items/*'
          'Microsoft.DocumentDB/databaseAccounts/sqlDatabases/containers/*'
        ]
      }
    ]
  }
}

resource sqlRoleAssignmentMI 'Microsoft.DocumentDB/databaseAccounts/sqlRoleAssignments@2024-11-15' = {
  parent: cosmosDbAccount
  name: guid('nosql-data-plane-contributor-mi', environmentName)
  properties: {
    roleDefinitionId: sqlRoleDefinition.id
    principalId: managedIdentity.properties.principalId
    scope: cosmosDbAccount.id
  }
}

resource sqlRoleAssignmentSP 'Microsoft.DocumentDB/databaseAccounts/sqlRoleAssignments@2024-11-15' = {
  parent: cosmosDbAccount
  name: guid('nosql-data-plane-contributor-sp', environmentName)
  properties: {
    roleDefinitionId: sqlRoleDefinition.id
    principalId: deploymentUserPrincipalId
    scope: cosmosDbAccount.id
  }
}

resource sqlDatabase 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases@2024-11-15' = {
  parent: cosmosDbAccount
  name: 'MultiAgentDB'
  properties: {
    resource: {
      id: 'MultiAgentDB'
    }
  }
}

resource openAI 'Microsoft.CognitiveServices/accounts@2023-05-01' = {
  name: 'open-ai-${resourceToken}'
  location: location
  kind: 'OpenAI'
  properties: {
    disableLocalAuth: true
    customSubDomainName: 'open-ai-${resourceToken}'
    publicNetworkAccess: 'Enabled'
  }
  sku: {
    name: 'S0'
  }
  tags: tags
}

// Define deployments array for sequential deployment
var deployments = [
  {
    name: 'gpt-4o-mini'
    skuName: 'GlobalStandard'
    skuCapacity: 40
    modelName: 'gpt-4o-mini'
    modelVersion: '2024-07-18'
  }
  {
    name: 'text-embedding-3-large'
    skuName: 'GlobalStandard'
    skuCapacity: 120
    modelName: 'text-embedding-3-large'
    modelVersion: '1'
  }
]

// Deploy models sequentially using batchSize
@batchSize(1)
resource openAIDeployments 'Microsoft.CognitiveServices/accounts/deployments@2023-05-01' = [for deployment in deployments: {
  parent: openAI
  name: deployment.name
  sku: {
    name: deployment.skuName
    capacity: deployment.skuCapacity
  }
  properties: {
    model: {
      name: deployment.modelName
      format: 'OpenAI'
      version: deployment.modelVersion
    }
  }
}]

resource openAIassignmentUser 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid('open-ai-assignment-user', environmentName)
  scope: resourceGroup()
  properties: {
    principalId: deploymentUserPrincipalId
    // Cognitive Services OpenAI User built-in role
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '5e0bd9bd-7b93-4f28-af87-19fc36ad61bd')
    principalType: 'User'
  }
}

resource openAIassignmentMI 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid('open-ai-assignment-mi', environmentName)
  scope: resourceGroup()
  properties: {
    principalId: managedIdentity.properties.principalId
    // Cognitive Services OpenAI User built-in role
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '5e0bd9bd-7b93-4f28-af87-19fc36ad61bd')
    principalType: 'ServicePrincipal'
  }
}

// Outputs
output AZURE_COSMOSDB_ENDPOINT string = cosmosDbAccount.properties.documentEndpoint
output AZURE_OPENAI_ENDPOINT string = openAI.properties.endpoint
output AZURE_OPENAI_EMBEDDING_DEPLOYMENT string = openAIDeployments[1].name
output AZURE_OPENAI_GPT_DEPLOYMENT string = openAIDeployments[0].name
