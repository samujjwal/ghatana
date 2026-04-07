package com.ghatana.finance.service;

/**
 * @doc.type interface
 * @doc.purpose Abstraction for transaction-processing idempotency storage
 * @doc.layer product
 * @doc.pattern Port
 */
public interface TransactionIdempotencyStore {

    TransactionResult get(String transactionId, String fingerprint);

    TransactionResult putIfAbsent(String transactionId, String fingerprint, TransactionResult result);
}