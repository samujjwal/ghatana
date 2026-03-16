package com.ghatana.appplatform.oms.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @doc.type    Record (Immutable Value Object)
 * @doc.purpose Represents a single execution fill received from the EMS (D01-014).
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public record Fill(
        String fillId,
        String orderId,
        String execId,           // Exchange-assigned execution ID (idempotency key)
        BigDecimal fillQuantity,
        BigDecimal fillPrice,
        BigDecimal fees,
        Instant filledAt
) {}
