// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.cosmos.multiagent.api;

import com.azure.core.credential.TokenCredential;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.spring.ai.chat.memory.repository.cosmosdb.CosmosDBChatMemoryRepository;
import com.azure.spring.ai.chat.memory.repository.cosmosdb.CosmosDBChatMemoryRepositoryConfig;
import com.azure.spring.ai.vectorstore.cosmosdb.CosmosDBVectorStore;
import com.azure.spring.data.cosmos.config.AbstractCosmosConfiguration;
import com.azure.spring.data.cosmos.config.CosmosConfig;
import com.azure.spring.data.cosmos.repository.config.EnableCosmosRepositories;
import com.azure.spring.data.cosmos.core.ResponseDiagnostics;
import com.azure.spring.data.cosmos.core.ResponseDiagnosticsProcessor;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.util.List;

@Configuration
@EnableCosmosRepositories(basePackages = "com.cosmos.multiagent.repository")
public class MultiAgentConfig extends AbstractCosmosConfiguration {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MultiAgentConfig.class);

    @Value("${spring.cloud.azure.cosmos.endpoint}")
    private String cosmosEndpoint;

    @Value("${spring.cloud.azure.cosmos.database.name}")
    private String databaseName;

    @Value("${spring.cloud.azure.cosmos.queryMetricsEnabled}")
    private boolean queryMetricsEnabled;

    @Value("${spring.cloud.azure.cosmos.responseDiagnosticsEnabled}")
    private static boolean responseDiagnosticsEnabled;

    @Bean
    public TokenCredential tokenCredential() {
        return new DefaultAzureCredentialBuilder().build();
    }

    // Single CosmosAsyncClient shared between Spring Data Cosmos and Spring AI
    @Bean
    public CosmosAsyncClient cosmosAsyncClient() {
        return new CosmosClientBuilder()
                .endpoint(cosmosEndpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .contentResponseOnWriteEnabled(true)
                .buildAsyncClient();
    }

    @Bean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }

    @Bean
    public CosmosConfig cosmosConfig() {
        return CosmosConfig.builder()
                .responseDiagnosticsProcessor(new ResponseDiagnosticsProcessorImplementation())
                .enableQueryMetrics(queryMetricsEnabled)
                .build();
    }

    @Override
    protected String getDatabaseName() {
        return this.databaseName;
    }

    @Bean
    public ChatMemoryRepository chatMemoryRepository(CosmosAsyncClient cosmosAsyncClient) {
        CosmosDBChatMemoryRepositoryConfig config = CosmosDBChatMemoryRepositoryConfig.builder()
                .withCosmosClient(cosmosAsyncClient)
                .withDatabaseName(databaseName)
                .withContainerName("ChatMemory")
                .withPartitionKeyPath("/conversationId")
                .build();
        return CosmosDBChatMemoryRepository.create(config);
    }

    @Bean
    public VectorStore vectorStore(
            ObservationRegistry observationRegistry,
            CosmosAsyncClient cosmosAsyncClient,
            EmbeddingModel embeddingModel) {

        return CosmosDBVectorStore.builder(cosmosAsyncClient, embeddingModel)
                .databaseName("MultiAgentDB")
                .containerName("Product")
                .metadataFields(List.of("product_id"))
                .partitionKeyPath("/id")
                .vectorDimensions(1536)
                .vectorStoreThroughput(1000)
                .batchingStrategy(new TokenCountBatchingStrategy())
                .observationRegistry(observationRegistry)
                .build();
    }

    private static class ResponseDiagnosticsProcessorImplementation implements ResponseDiagnosticsProcessor {

        @Override
        public void processResponseDiagnostics(@Nullable ResponseDiagnostics responseDiagnostics) {
            if (responseDiagnosticsEnabled == true) {
                logger.info("Response Diagnostics {}", responseDiagnostics);
            }
        }
    }
}
