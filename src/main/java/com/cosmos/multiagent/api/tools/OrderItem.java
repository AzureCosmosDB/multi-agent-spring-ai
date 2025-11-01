// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.cosmos.multiagent.api.tools;

import com.cosmos.multiagent.repository.Product;
import com.cosmos.multiagent.repository.ProductRepository;
import com.cosmos.multiagent.repository.PurchaseHistory;
import com.cosmos.multiagent.repository.PurchaseHistoryRepository;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OrderItem {

    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final ProductRepository productsRepository;

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OrderItem.class);

    public OrderItem(PurchaseHistoryRepository purchaseHistoryRepository,
            ProductRepository productsRepository) {
        this.purchaseHistoryRepository = purchaseHistoryRepository;
        this.productsRepository = productsRepository;
    }

    @Tool(description = "Order an item")
    public String orderItem(String userId, String itemId) {
        logger.info("Called orderItem() tool");
        try {
            List<Product> results = productsRepository.findFirstByMetadata_ProductId(itemId);
            Optional<Product> productOpt = results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));

            if (productOpt.isPresent()) {
                Product product = productOpt.get();

                String dateOfPurchase = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                        .withZone(ZoneOffset.UTC)
                        .format(Instant.now());

                PurchaseHistory purchase = new PurchaseHistory();
                purchase.setId(UUID.randomUUID().toString());
                purchase.setUserId(userId);
                purchase.setAmount(product.getMetadata().getPrice());
                purchase.setDateOfPurchase(dateOfPurchase);
                purchase.setItemId(itemId);

                purchaseHistoryRepository.save(purchase);

                return String.format(
                        "Order placed for product %s for user ID %s. Item ID: %s.",
                        product.getMetadata().getProductId(), userId, itemId);

            } else {
                return String.format("Product %s not found.", itemId);
            }

        } catch (Exception e) {
            logger.error("Error ordering item: ", e);
            return "An error occurred during order placement: " + e.getMessage();
        }
    }
}
