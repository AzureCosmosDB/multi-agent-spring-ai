# Multi-agent Java sample with Spring AI and Azure Cosmos DB

Inspired by OpenAI Swarm and LangGraph - a sample personal shopping AI Chatbot that can help with product enquiries, making sales, and refunding orders by transferring to different agents for those tasks.

Features:
- **Multi-agent**: the sample implements a custom multi-agent orchestration framework using [Spring AI](https://docs.spring.io/spring-ai/reference/) and [Azure OpenAI](https://learn.microsoft.com/azure/ai-services/openai/overview) as building blocks. 
- **Transactional data management**: agents interact with the planet scale [Azure Cosmos DB database service](https://learn.microsoft.com/azure/cosmos-db/introduction) using [Spring AI tool calling](https://docs.spring.io/spring-ai/reference/api/tools-migration.html) to store transactional user and product operational data, implemented via [Spring Data](https://spring.io/projects/spring-data). 
- **Retrieval Augmented Generation (RAG)**: the sample uses [vector search](https://learn.microsoft.com/azure/cosmos-db/nosql/vector-search) in Azure Cosmos DB with powerful [DiskANN index](https://www.microsoft.com/en-us/research/publication/diskann-fast-accurate-billion-point-nearest-neighbor-search-on-a-single-node/?msockid=091c323873cd6bd6392120ac72e46a98) to serve product enquiries from the same database that is used for transactions. Implemented via the [Spring AI vector store plugin for Azure Cosmos DB](https://docs.spring.io/spring-ai/reference/api/vectordbs/azure-cosmos-db.html).
- **Long term chat memory persistence**: the sample implements Spring AI's `ChatMemory` interface to store and manage long term chat memory for each user session in Azure Cosmos DB.
- **Multi-tenant/user session storage**: [Hierarchical Partitioning](https://learn.microsoft.com/azure/cosmos-db/hierarchical-partition-keys) is used in Azure Cosmos DB to store and manage each user session, this is integrated into the UI. A "default" `tenantId` is used, and the user's local IP address is capatured as the `userId`.
- **UI**: front end is built as a single-page application (SPA) using HTML, CSS, and JavaScript located in the resources/static folder. The UI talks to REST API endpoints exposed by the backend Spring Boot application.


## UI demo

![Demo](./media/demo.gif)

## Overview

The personal shopper example includes 3 agents to handle various customer service requests, and an orchestrator for initial routing. The agents are implemented using [Spring AI](https://docs.spring.io/spring-ai/reference/) framework to interact with the Azure OpenAI API. The agents are designed to be modular and can be easily extended or replaced with other implementations.

1. **Product Agent**: Answers customer queries from the products container using [Retrieval Augmented Generation (RAG)](https://learn.microsoft.com/azure/cosmos-db/gen-ai/rag).
2. **Refund Agent**: Manages customer refunds, requiring both user ID and item ID to initiate a refund.
3. **Sales Agent**: Handles actions related to placing orders, requiring both user ID and product ID to complete a purchase.


## Prerequisites

- [Azure Developer CLI (azd)](https://learn.microsoft.com/azure/developer/azure-developer-cli/install-azd) **(recommended for deployment)**
- [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli) (required by azd)
- [Maven](https://maven.apache.org/install.html) 3.8.1 or later
- [Java 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html) or later

> **Note:** All Azure resources (Cosmos DB, OpenAI, Managed Identity, etc.) are provisioned automatically using Bicep via `azd up`. No manual portal setup required.


## Setup & Deployment (ACD/azd)

Clone the repository:

```shell
git clone https://github.com/TheovanKraay/multi-agent-spring-ai.git
cd multi-agent-spring-ai
```

### 1. Authenticate to Azure

```shell
az login
azd auth login
```

### 2. Provision all Azure resources (ACD/azd)

Run the following to deploy all infrastructure (Cosmos DB, OpenAI, Managed Identity, etc.) to a new environment:

```shell
azd up
```

You will be prompted for a location (e.g. eastus) and an environment name (e.g. dev, test, prod, etc). This will:

- Create a new resource group and all required Azure resources using Bicep (see `infra/`)
- Deploy Cosmos DB with vector search enabled, OpenAI with correct models, and assign RBAC
- Auto-detect your Azure user and set the OWNER tag
- **Generate `src/main/resources/application.properties` with the correct endpoints and deployment names for your environment**

> **Tip:** You can deploy to multiple environments (e.g. dev, test, prod) by running `azd up` with different environment names.

### 3. Build the application

```shell
mvn clean package -DskipTests
```

### 4. Run the application

```shell
java -jar target/multi-agent-spring-ai-cosmos-1.0-exec.jar
```

### 5. Load sample data

```shell
java -jar target/multiagent-dataloader.jar
```

### 6. Test the app

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Web UI: http://localhost:8080
- CLI: `java -jar target/multiagent-cli.jar`

---

## About `application.properties`

- The file `src/main/resources/application.properties` is **auto-generated** by the `azd up` postprovision hook with the correct values for your environment.
- The file is **gitignored**. Do not commit secrets or environment-specific values.
- An example template is provided as `application.properties.example`.
- To run locally without azd, copy the example and fill in your own values.

---

## Azure Credential Delegation (ACD) and RBAC

This sample uses [DefaultAzureCredential](https://learn.microsoft.com/java/api/overview/azure/identity-readme?view=azure-java-stable#authenticate-a-user-assigned-managed-identity-with-defaultazurecredential) for all Azure SDK authentication. The Bicep and azd deployment will:

- Assign a managed identity to the app
- Grant RBAC data plane access to Cosmos DB and OpenAI
- Tag all resources with the OWNER (auto-detected from your Azure account)

**No connection strings or keys are required.**

If you need to run locally as a different user, ensure you have the correct RBAC roles assigned in the Azure Portal or via CLI.