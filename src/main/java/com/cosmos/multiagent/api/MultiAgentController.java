// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.cosmos.multiagent.api;

import com.cosmos.multiagent.agent.models.ChatMessage;
import com.cosmos.multiagent.agent.models.ChatSession;
import com.cosmos.multiagent.agent.orchestrator.AgentOrchestrator;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class MultiAgentController {

    @Autowired
    private MultiAgentService multiAgentService;

    @PostMapping("/tenant/{tenantId}/user/{userId}/session/{sessionId}/completion")
    public List<Message> completion(@RequestParam String input, @PathVariable String userId, @PathVariable String tenantId, @PathVariable String sessionId) {
        AgentOrchestrator orchestrator = multiAgentService.getOrchestrator();
        return orchestrator.handleUserInput(input, sessionId, userId, tenantId, true);
    }

    @PostMapping("/tenant/{tenantId}/user/{userId}/session")
    public String createSession(@PathVariable String userId, @PathVariable String tenantId) {
        return multiAgentService.getChatSessionId(userId, tenantId);
    }

    @GetMapping("/tenant/{tenantId}/user/{userId}/sessions")
    public List<ChatSession> getSessions(@PathVariable String userId, @PathVariable String tenantId) {
        return multiAgentService.getChatSessions(userId, tenantId);
    }

    @GetMapping("/tenant/{tenantId}/user/{userId}/session/{sessionId}/messages{lastN}")
    public List<ChatMessage> getSession(@PathVariable String userId, @PathVariable String tenantId, @PathVariable String sessionId, @RequestParam int lastN) {
        List<Message> messages = multiAgentService.getChatSession(sessionId, lastN);
        List<ChatMessage> chatMessages = new java.util.ArrayList<>();
        
        for (Message msg : messages) {
            String role = "user";
            if (msg.getMetadata() != null && msg.getMetadata().containsKey("agent")) {
                role = (String) msg.getMetadata().get("agent");
            }
            chatMessages.add(new ChatMessage(role, msg.getText()));
        }
        
        return chatMessages;
    }

    @DeleteMapping("/tenant/{tenantId}/user/{userId}/session/{sessionId}")
    public void deleteSession(@PathVariable String userId, @PathVariable String tenantId, @PathVariable String sessionId) {
        // Clearing the chat session record will be a single record and synchronous
        multiAgentService.getChatSession().deleteSession(sessionId, userId, tenantId);
        // Clearing the chat memory will be asynchronous (subscribe)
        multiAgentService.getChatMemory().clear(sessionId);
    }

    @PutMapping("/data")
    public void dataLoad() throws IOException {
        multiAgentService.dataLoad();
    }

}