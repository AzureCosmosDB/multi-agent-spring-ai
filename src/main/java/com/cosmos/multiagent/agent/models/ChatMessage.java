// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.cosmos.multiagent.agent.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage implements Message {

    private String role;

    @JsonProperty("text")
    private String content;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    @JsonProperty("role")
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getText() {
        return content;
    }

    public String getContent() {
        return content;
    }

    @Override
    public Map<String, Object> getMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        if (role != null && !role.isEmpty()) {
            metadata.put("agent", role);
        }
        return metadata;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.USER;
    }
}
