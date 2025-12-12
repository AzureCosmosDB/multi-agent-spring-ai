// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.cosmos.multiagent.agent.orchestrator;

import com.cosmos.multiagent.agent.Agent;
import com.cosmos.multiagent.agent.memory.CosmosChatSession;
import com.cosmos.multiagent.agent.models.ChatMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AgentOrchestrator {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AgentOrchestrator.class);
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final ChatMemory chatMemory;
    private final CosmosChatSession chatSession;
    private final ChatModel chatModel;

    @Autowired
    private ChatClient chatClient;
    AgentRouting agentRouting;

    public AgentOrchestrator(CosmosChatSession chatSession, ChatMemory chatMemory, ChatModel chatModel) {
        this.chatSession = chatSession;
        this.chatMemory = chatMemory;
        this.chatModel = chatModel;
        this.chatClient = ChatClient.create(chatModel);
        this.agentRouting = new AgentRouting(this.chatClient);
    }

    public void registerAgent(Agent agent) {
        agents.put(agent.name(), agent);
    }

    public List<Message> handleUserInput(String input, String sessionId, String userId, String tenantId,
            boolean saveChatMemory) {
        List<Message> responseMessages = new ArrayList<>();
        logger.info("session id: {}", sessionId);

        String activeAgent = chatSession.getActiveAgent(sessionId, userId, tenantId);

        // needs to be a local instance to patch the active agent record in a
        // thread-safe manner
        AgentTransfer agentTransfer = new AgentTransfer(chatSession, sessionId, userId, tenantId);

        logger.info("Active agent: {}", activeAgent);

        // Route if agent unknown
        if (activeAgent.equals("unknown")) {
            // if activeAgent is unknown, this is the first user message, so update session
            // name using summarize
            chatSession.patchSessionName(sessionId, userId, tenantId, summarize(input));
            Map<String, String> routes = new HashMap<>();
            for (Agent agent : agents.values()) {
                routes.put(agent.name(), agent.systemPrompt());
            }
            activeAgent = agentRouting.route(input, routes);
            agentTransfer.transferAgent(activeAgent);
        }

        logger.info("Agent to use: {}", activeAgent);
        Agent agent = agents.get(activeAgent);
        agentTransfer.setRoutableAgents(agent.routableAgents());

        List<Object> tools = new ArrayList<>();
        for (Object tool : agent.tools()) {
            tools.add(tool);
        }
        tools.add(agentTransfer);

        // Build and call the chat client with memory advisor
        String response = ChatClient.builder(chatModel)
                .build()
                .prompt(agent.systemPrompt())
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                        .conversationId(sessionId)
                        .build())
                .user(input)
                .tools(tools.toArray())
                .call()
                .content();

        // Update the last assistant message in memory to add agent metadata
        List<Message> messages = chatMemory.get(sessionId);
        if (!messages.isEmpty()) {
            Message lastMessage = messages.get(messages.size() - 1);
            if (lastMessage.getMetadata() != null) {
                lastMessage.getMetadata().put("agent", activeAgent);
                // Clear and re-add all messages to persist the metadata update
                chatMemory.clear(sessionId);
                chatMemory.add(sessionId, messages);
            }
        }

        // Check if the agent has changed during the call
        String checkActiveAgent = chatSession.getActiveAgent(sessionId, userId, tenantId);

        // If an agent transfer occurred during processing, recursively handle with new agent
        if (!checkActiveAgent.equals(activeAgent)) {
            logger.info("Agent transfer during processing. New agent: {}", checkActiveAgent);            
            
            // Remove the user message to prevent duplicate storage
            // Current state: [..., USER, TRANSFER]
            // After removing USER: [..., TRANSFER]
            // After recursive call: [..., TRANSFER, USER, SALES] (wrong order)
            // Need to swap to: [..., USER, TRANSFER, SALES]
            
            List<Message> allMessages = chatMemory.get(sessionId);
            if (allMessages.size() >= 2 && 
                allMessages.get(allMessages.size() - 2).getMessageType() == org.springframework.ai.chat.messages.MessageType.USER) {
                allMessages.remove(allMessages.size() - 2);
                chatMemory.clear(sessionId);
                allMessages.forEach(msg -> chatMemory.add(sessionId, msg));
            }
            
            // Recursive call - MessageChatMemoryAdvisor will add USER message again
            handleUserInput(input, sessionId, userId, tenantId, false);
            
            // After recursive call, fix the order: swap TRANSFER and USER messages
            // Current: [..., TRANSFER, USER, SALES]
            // Want: [..., USER, TRANSFER, SALES]
            allMessages = chatMemory.get(sessionId);
            if (allMessages.size() >= 3) {
                // Swap the transfer message (now at -3) with user message (now at -2)
                Message transferMsg = allMessages.get(allMessages.size() - 3);
                Message userMsg = allMessages.get(allMessages.size() - 2);
                
                // Only swap if they're in the wrong order
                if (transferMsg.getMessageType() == org.springframework.ai.chat.messages.MessageType.ASSISTANT &&
                    userMsg.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER) {
                    
                    allMessages.set(allMessages.size() - 3, userMsg);
                    allMessages.set(allMessages.size() - 2, transferMsg);
                    
                    chatMemory.clear(sessionId);
                    allMessages.forEach(msg -> chatMemory.add(sessionId, msg));
                }
            }
        }

        return new ArrayList<>();
    }

    public String summarize(String userMessage) {
        String promptText = "Summarize this message in 4-6 words as a session title: " + userMessage;
        String response = chatClient.prompt(promptText).call().content();
        return response.replaceAll("[\"\n]", "").trim();
    }

}