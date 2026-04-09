/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.service;

import java.util.Map;
import java.util.HashMap;

/**
 * Result object for asynchronous operations
 *
 * @doc.type class
 * @doc.purpose Result object for asynchronous operations
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public class TransactionResult {

    private final String status;
    private final String message;
    private final Map<String, Object> metadata;

    TransactionResult(String status, String message, Map<String, Object> metadata) {
        this.status = status;
        this.message = message;
        this.metadata = metadata;
    }

    public static TransactionResult approved() {
        return new TransactionResult("APPROVED", "Transaction approved", new HashMap<>());
    }

    public static TransactionResult approved(Map<String, Object> metadata) {
        return new TransactionResult("APPROVED", "Transaction approved", metadata);
    }

    public static TransactionResult rejected(String reason) {
        return new TransactionResult("REJECTED", reason, new HashMap<>());
    }

    public static TransactionResult rejected(String reason, Map<String, Object> metadata) {
        return new TransactionResult("REJECTED", reason, metadata);
    }

    public static TransactionResult pendingReview(String message, Map<String, Object> metadata) {
        return new TransactionResult("PENDING_REVIEW", message, metadata);
    }

    public static TransactionResult error(String message) {
        return new TransactionResult("ERROR", message, new HashMap<>());
    }

    public static TransactionResult error(String message, Map<String, Object> metadata) {
        return new TransactionResult("ERROR", message, metadata);
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
