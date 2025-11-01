// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.cosmos.multiagent.api;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.TokenCredential;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.spring.data.cosmos.config.AbstractCosmosConfiguration;
import com.azure.spring.data.cosmos.config.CosmosConfig;
import com.azure.spring.data.cosmos.repository.config.EnableCosmosRepositories;
import com.azure.spring.data.cosmos.core.ResponseDiagnostics;
import com.azure.spring.data.cosmos.core.ResponseDiagnosticsProcessor;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.cosmosdb.CosmosDBVectorStore;
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

    @Value("${spring.ai.azure.openai.endpoint}")
    private String azureOpenAIEndpoint;

    @Value("${spring.ai.azure.openai.chat.options.deployment-name}")
    private String chatDeploymentName;

    @Value("${spring.ai.azure.openai.chat.options.temperature}")
    private double chatTemperature;

    @Value("${spring.ai.azure.openai.embedding.options.deployment-name}")
    private String embeddingDeploymentName;

    @Bean
    public TokenCredential tokenCredential() {
        return new DefaultAzureCredentialBuilder().build();
    }

    @Bean
    public OpenAIClient azureOpenAIClient(TokenCredential tokenCredential) {
        return new OpenAIClientBuilder()
                .credential(tokenCredential)
                .endpoint(azureOpenAIEndpoint)
                .buildClient();
    }

    @Bean
    public OpenAIClientBuilder azureOpenAIClientBuilder(TokenCredential tokenCredential) {
        return new OpenAIClientBuilder()
                .credential(tokenCredential)
                .endpoint(azureOpenAIEndpoint);
    }

    @Bean
    public ChatModel azureOpenAiChatModel(
            OpenAIClientBuilder openAIClientBuilder,
            ObservationRegistry observationRegistry) {
        AzureOpenAiChatOptions options = AzureOpenAiChatOptions.builder()
                .deploymentName(chatDeploymentName)
                .temperature(chatTemperature)
                .build();
        // Use builder pattern which should handle toolCallingManager internally
        return AzureOpenAiChatModel.builder()
                .openAIClientBuilder(openAIClientBuilder)
                .defaultOptions(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    @Bean
    public EmbeddingModel azureOpenAiEmbeddingModel(OpenAIClient openAIClient) {
        AzureOpenAiEmbeddingOptions options = AzureOpenAiEmbeddingOptions.builder()
                .deploymentName(embeddingDeploymentName)
                .build();
        return new AzureOpenAiEmbeddingModel(openAIClient, MetadataMode.EMBED, options);
    }

    // Single CosmosAsyncClient shared between Spring Data Cosmos and Spring AI
    // Vector Store
    @Bean
    public CosmosAsyncClient cosmosAsyncClient() {
        return new CosmosClientBuilder()
                .endpoint(cosmosEndpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .contentResponseOnWriteEnabled(true)
                .buildAsyncClient();
    }

    @Bean
    public ChatClient chatClient(AzureOpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
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

    // Manual VectorStore bean using Spring AI 1.0.0-SNAPSHOT (has Builder fix)
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
