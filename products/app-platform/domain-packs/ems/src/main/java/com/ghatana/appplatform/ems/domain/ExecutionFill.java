package com.ghatana.appplatform.ems.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @doc.type      Record
 * @doc.purpose   Represents a single execution fill received from an exchange.
 * @doc.layer     Domain
 * @doc.pattern   Immutable Value Object
 */
public record ExecutionFill(
        String fillId,
        String routingId,
        String execId,
        long filledQuantity,
        BigDecimal fillPrice,
        Instant filledAt,
        String exchange
) {}
