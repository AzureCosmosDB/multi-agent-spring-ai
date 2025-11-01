// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.cosmos.multiagent.api.tools;

import com.azure.cosmos.models.PartitionKey;
import com.cosmos.multiagent.repository.UsersRepository;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

public class NotifyCustomer {

    private final UsersRepository usersRepository;

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NotifyCustomer.class);

    public NotifyCustomer(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Tool(description = "Notify a customer via email or phone")
    public String notifyCustomer(Integer userId, String method) {
        logger.info("Calling notifyCustomer() tool");
        try {
            return usersRepository.findById(userId.toString(), new PartitionKey(userId.toString()))
                    .map(user -> {
                        String email = user.getEmail();
                        String phone = user.getPhone();

                        if ("email".equalsIgnoreCase(method) && email != null && !email.isEmpty()) {
                            return String.format("Emailed customer %s a notification.", email);
                        } else if ("phone".equalsIgnoreCase(method) && phone != null && !phone.isEmpty()) {
                            return String.format("Texted customer %s a notification.", phone);
                        } else {
                            return String.format("No %s contact available for user ID %d.", method, userId);
                        }
                    })
                    .orElse(String.format("User ID %d not found.", userId));

        } catch (Exception e) {
            return String.format("An error occurred during notification: %s", e.getMessage());
        }
    }

}
