package com.ghatana.appplatform.compliance.domain;

import java.math.BigDecimal;

/**
 * @doc.type    Record (Immutable Value Object)
 * @doc.purpose Input to the compliance rule pipeline (D07-001).
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public record ComplianceCheckRequest(
        String orderId,
        String clientId,
        String instrumentId,
        String accountId,
        String jurisdiction,        // derived from instrument→exchange→jurisdiction
        String orderSide,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal orderValue,
        String kycStatus,           // from K-01 client profile
        int amlRiskScore            // 0-100; higher = riskier
) {}
