package com.ghatana.appplatform.oms.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @doc.type    Record (Immutable Value Object)
 * @doc.purpose Captures details of an order amendment request (D01-006).
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public record OrderAmendment(
        String amendmentId,
        String orderId,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        BigDecimal oldQuantity,
        BigDecimal newQuantity,
        TimeInForce oldTimeInForce,
        TimeInForce newTimeInForce,
        String requestedBy,
        Instant requestedAt,
        boolean requiresMakerChecker   // true when new value > 150% of old
) {}
