// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.cosmos.multiagent.api;

import com.azure.cosmos.CosmosAsyncClient;
import com.cosmos.multiagent.agent.Agent;
import com.cosmos.multiagent.agent.memory.CosmosChatSession;
import com.cosmos.multiagent.agent.models.ChatSession;
import com.cosmos.multiagent.agent.orchestrator.AgentOrchestrator;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.repository.cosmosdb.CosmosDBChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import com.cosmos.multiagent.api.tools.OrderItem;
import com.cosmos.multiagent.api.tools.RefundItem;
import com.cosmos.multiagent.repository.ProductRepository;
import com.cosmos.multiagent.repository.PurchaseHistoryRepository;
import com.cosmos.multiagent.repository.User;
import com.cosmos.multiagent.repository.UsersRepository;
import com.cosmos.multiagent.api.tools.NotifyCustomer;
import com.cosmos.multiagent.api.tools.ProductSearch;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class MultiAgentService {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MultiAgentService.class);

    @Value("${spring.cloud.azure.cosmos.database.name}")
    private String databaseName;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private CosmosAsyncClient cosmosAsyncClient;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PurchaseHistoryRepository purchaseHistoryRepository;

    @Autowired
    private ProductRepository productsRepository;

    @Autowired
    private CosmosDBChatMemoryRepository chatMemoryRepository;

    private AgentOrchestrator orchestrator;
    private CosmosChatSession chatSession;
    private ChatMemory chatMemory;

    @PostConstruct
    public void initialize() {
        chatSession = new CosmosChatSession(cosmosAsyncClient, databaseName);
        chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
        orchestrator = new AgentOrchestrator(chatSession, chatMemory, chatModel);

        List<String> allAgents = List.of("Sales", "Product", "Refunds");

        Agent productAgent = new Agent("Product",
                "You are a product agent that can help the user search for products. " +
                        "Ask for what products the user is interested in. Call productSearch() and pass in the user's "
                        +
                        "question as an argument. Make sure you output the Product ID that comes back for each product."
                        +
                        "If the user wants to order one of the products, transfer to Sales." +
                        "You can also transfer the user to another agent by calling getRoutableAgents() to" +
                        "determine which agents you can call, then call transferAgent() tool passing the appropriate agent"
                        +
                        "for the question being asked.",
                List.of(new ProductSearch(vectorStore)),
                agentTransfersAllowed("Product", allAgents));

        Agent salesAgent = new Agent("Sales",
                "You are a sales agent." +
                        "For now all you can do is order an item, or transfer the user to another agent by calling " +
                        "getRoutableAgents() to determine which agents you can call, then call transferAgent() passing "
                        +
                        "the appropriate agent for the question being asked." +
                        "If the user wants to order an item, you must ask for the user id and item id. " +
                        "If the context information shows that the user has asked for product information," +
                        "Then get the item id from there (it will be the Product ID) but confirm with the user." +
                        "Then call the orderItem() tool passing userId and itemId to it, and return the result. " +
                        "If the user wants to be notified of the sale, ask for the notification method (email or phone) and user Id "
                        +
                        "(unless user id is already present in context information, then get it from there)." +
                        "Then call the notifyCustomer() method passing userId and method values to it. " +
                        "If the user wants a refund, transfer to Refunds. ",
                List.of(new NotifyCustomer(usersRepository),
                        new OrderItem(purchaseHistoryRepository, productsRepository)),
                agentTransfersAllowed("Sales", allAgents));

        Agent refundsAgent = new Agent("Refunds",
                "You are a refund agent." +
                        "For now all you can do is arrange a refund or transfer the user to another agent by calling " +
                        "getRoutableAgents() to determine which agents you can call, then call transferAgent() " +
                        "passing the appropriate agent for the question being asked." +
                        "If the user wants a refund, you must ask for the user id and item id." +
                        "If the context information shows that the user has asked for product information," +
                        "then get the item id from there (it will be the Product ID) but confirm with the user." +
                        "Then call the refundItem() tool passing userId and itemId to it, and return the result. " +
                        "If the user wants to be notified of the refund, ask for the notification method (email or phone) and user Id "
                        +
                        "(unless user id is already present in context information, then get it from there)." +
                        "Then call the notifyCustomer() method passing userId and method values to it. " +
                        "You must return the response from refundItem() to the user, and notifyCustomer() method if called.",
                List.of(new NotifyCustomer(usersRepository), new RefundItem(purchaseHistoryRepository)),
                agentTransfersAllowed("Refunds", allAgents));

        orchestrator.registerAgent(productAgent);
        orchestrator.registerAgent(salesAgent);
        orchestrator.registerAgent(refundsAgent);

    }

    /**
     * This method returns a list of agents that the current agent can transfer to.
     *
     * @param currentAgent The name of the current agent.
     * @param allAgents    A list of all available agents.
     * @return A list of agent names that the current agent can transfer to.
     */
    private List<String> agentTransfersAllowed(String currentAgent, List<String> allAgents) {
        return allAgents.stream().filter(a -> !a.equals(currentAgent)).toList();
    }

    public AgentOrchestrator getOrchestrator() {
        return orchestrator;
    }

    public CosmosChatSession getChatSession() {
        return chatSession;
    }

    public ChatMemory getChatMemory() {
        return chatMemory;
    }

    public List<ChatSession> getChatSessions(String userId, String tenantId) {
        return chatSession.getSessions(userId, tenantId);
    }

    public List<Message> getChatSession(String sessionId, int lastN) {
        List<Message> messages = chatMemory.get(sessionId);
        // Return last N messages if requested
        if (lastN > 0 && messages.size() > lastN) {
            return messages.subList(messages.size() - lastN, messages.size());
        }
        return messages;
    }

    public String getChatSessionId(String userId, String tenantId) {
        return chatSession.createSessionId(userId, tenantId);
    }

    public void dataLoad() throws IOException {
        logger.info("Loading fake data....");
        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = new ClassPathResource("data/products.json").getInputStream();

        List<Map<String, Object>> products = mapper.readValue(inputStream, new TypeReference<>() {
        });
        List<Document> allDocuments = new ArrayList<>();

        for (Map<String, Object> product : products) {
            StringBuilder content = new StringBuilder();
            content.append("Product ID: ").append(product.get("product_id")).append("\n");
            content.append("Product Name: ").append(product.get("product_name")).append("\n");
            content.append("Category: ").append(product.get("category")).append("\n");
            content.append("Description: ").append(product.get("product_description")).append("\n");
            content.append("Price: ").append(product.get("price"));

            Document doc = new Document(content.toString());
            doc.getMetadata().put("productId", product.get("product_id").toString());
            doc.getMetadata().put("productName", product.get("product_name").toString());
            doc.getMetadata().put("category", product.get("category").toString());
            doc.getMetadata().put("price", product.get("price").toString());
            allDocuments.add(doc);
        }

        // Process in batches of 50 to avoid rate limiting with 5K TPM embedding
        // capacity
        int batchSize = 50;
        int totalBatches = (int) Math.ceil((double) allDocuments.size() / batchSize);
        logger.info("Processing {} documents in {} batches of {}", allDocuments.size(), totalBatches, batchSize);

        for (int i = 0; i < allDocuments.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allDocuments.size());
            List<Document> batch = allDocuments.subList(i, end);
            int batchNum = (i / batchSize) + 1;

            logger.info("Processing batch {}/{} ({} documents)", batchNum, totalBatches, batch.size());
            vectorStore.add(batch);
            logger.info("Completed batch {}/{}", batchNum, totalBatches);

            // Add delay between batches to avoid rate limiting (except for last batch)
            if (end < allDocuments.size()) {
                try {
                    logger.info("Waiting 5 seconds before next batch to avoid rate limiting...");
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Data loading interrupted", e);
                }
            }
        }

        User user = new User();
        user.setId("1");
        user.setUserId("1");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("email@somebody.com");
        user.setPhone("990-00090000");
        usersRepository.save(user);
        logger.info("Data loaded into vector store and Users container.");
    }
}