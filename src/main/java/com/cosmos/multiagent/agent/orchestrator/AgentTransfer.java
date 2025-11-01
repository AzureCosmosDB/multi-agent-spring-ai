// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.cosmos.multiagent.agent.orchestrator;

import com.cosmos.multiagent.agent.memory.CosmosChatSession;
import org.springframework.ai.tool.annotation.Tool;
import java.util.ArrayList;
import java.util.List;

public class AgentTransfer {

    private CosmosChatSession chatSession;

    private String sessionId;
    private String userId;
    private String tenantId;

    @Tool(description = "Get routable agents")
    public List<String> getRoutableAgents() {
        return routableAgents;
    }

    public Object setRoutableAgents(List<String> routableAgents) {
        this.routableAgents = routableAgents;
        return null;
    }

    private List<String> routableAgents = new ArrayList<>();

    public AgentTransfer(CosmosChatSession chatSession, String sessionId, String userId, String tenantId) {
        this.chatSession = chatSession;
        this.sessionId = sessionId;
        this.userId = userId;
        this.tenantId = tenantId;
    }

    @Tool(description = "Transfer agent to another agent")
    String transferAgent(String agentName) {
        chatSession.patchActiveAgent(this.sessionId, this.userId, this.tenantId, agentName);
        return "Agent transferred to " + agentName;
    }

}
