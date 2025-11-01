// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.cosmos.multiagent.agent.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.cosmos.multiagent.agent.Agent;
import com.cosmos.multiagent.agent.memory.CosmosChatSession;
import com.cosmos.multiagent.agent.models.ChatMessage;
import com.cosmos.multiagent.agent.memory.CosmosChatMemory;
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
    private final CosmosChatMemory chatMemory;
    private final CosmosChatSession chatSession;
    private final ChatModel chatModel;

    @Autowired
    private ChatClient chatClient;
    AgentRouting agentRouting;

    public AgentOrchestrator(CosmosChatSession chatSession, CosmosChatMemory chatMemory, ChatModel chatModel) {
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
        // Use builder pattern for MessageChatMemoryAdvisor in 1.0.3
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

        // Check if the agent has changed during the call
        String checkActiveAgent = chatSession.getActiveAgent(sessionId, userId, tenantId);

        // Only add the user message once, in the top-level call
        if (saveChatMemory) {
            responseMessages.add(new ChatMessage("user", input));
        }

        responseMessages.add(new ChatMessage(activeAgent, response));

        // If an agent transfer occurred during processing
        if (!checkActiveAgent.equals(activeAgent)) {
            logger.info("Agent transfer during processing. New agent: {}", checkActiveAgent);
            List<Message> recursiveMessages = handleUserInput(input, sessionId, userId, tenantId, false);
            responseMessages.addAll(recursiveMessages);
        }

        // Deduplicate user messages: keep only the first
        boolean userMessageSeen = false;
        Iterator<Message> iterator = responseMessages.iterator();
        while (iterator.hasNext()) {
            Message message = iterator.next();
            if (message instanceof ChatMessage) {
                ChatMessage chatMessage = (ChatMessage) message;
                if ("user".equals(chatMessage.getRole())) {
                    if (!userMessageSeen) {
                        userMessageSeen = true;
                    } else {
                        iterator.remove();
                    }
                }
            }
        }

        // Only save memory at the top-level call
        if (saveChatMemory) {
            logger.info("Saving chat memory for session: {}", sessionId);
            chatMemory.add(sessionId, responseMessages);
        }

        return responseMessages;
    }

    public String summarize(String userMessage) {
        String promptText = "Summarize this message in 4-6 words as a session title: " + userMessage;
        String response = chatClient.prompt(promptText).call().content();
        return response.replaceAll("[\"\n]", "").trim();
    }

}