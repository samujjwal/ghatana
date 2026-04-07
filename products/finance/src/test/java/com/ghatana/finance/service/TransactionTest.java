package com.ghatana.finance.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @doc.type class
 * @doc.purpose Tests transaction mapping for finance fraud inference inputs
 * @doc.layer product
 * @doc.pattern Test
 */
class TransactionTest {

    @Test
    void includesFraudRelevantFieldsInMap() {
        Transaction transaction = new Transaction();
        transaction.setId("txn-55");
        transaction.setTenantId("tenant-55");
        transaction.setAmount(1250.0);
        transaction.setCurrency("USD");
        transaction.setLocation("US-NY");
        transaction.setMerchantCategory("CRYPTO_EXCHANGE");
        transaction.setCounterpartyCountry("RU");
        transaction.setPaymentMethod("WIRE_TRANSFER");
        transaction.setVelocity(4.0);
        transaction.setTimestamp(Instant.parse("2026-04-06T12:00:00Z"));
        transaction.setStatus("PENDING");

        Map<String, Object> map = transaction.toMap();

        assertEquals("txn-55", map.get("id"));
        assertEquals("tenant-55", map.get("tenant_id"));
        assertEquals("CRYPTO_EXCHANGE", map.get("merchant_category"));
        assertEquals("RU", map.get("counterparty_country"));
        assertEquals("WIRE_TRANSFER", map.get("payment_method"));
        assertEquals(4.0, map.get("velocity"));
    }
}