// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.cosmos.multiagent.api.tools;

import com.cosmos.multiagent.repository.PurchaseHistory;
import com.cosmos.multiagent.repository.PurchaseHistoryRepository;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class RefundItem {

    private final PurchaseHistoryRepository purchaseHistoryRepository;

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RefundItem.class);

    public RefundItem(PurchaseHistoryRepository purchaseHistoryRepository) {
        this.purchaseHistoryRepository = purchaseHistoryRepository;
    }

    @Tool(description = "Refund an item")
    public String refundItem(String userId, String itemId) {
        logger.info("Called refundItem() tool");
        try {
            List<PurchaseHistory> results = purchaseHistoryRepository.findByItemId(itemId);
            PurchaseHistory purchasedItem = results.isEmpty() ? null : results.get(0);
            if (results != null) {
                String dateOfRefund = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                        .withZone(ZoneOffset.UTC)
                        .format(Instant.now());
                purchasedItem.setDateOfRefund(dateOfRefund);
                purchaseHistoryRepository.save(purchasedItem);
                return String.format(
                        "Refund processed for product %s for user ID %s. Item ID: %s.",
                        purchasedItem.getItemId(), userId, itemId);
            } else {
                return String.format("Item ID %s not found in purchase history.", itemId);
            }

        } catch (Exception e) {
            logger.error("Error ordering item: ", e);
            return "An error occurred during order placement: " + e.getMessage();
        }
    }
}
