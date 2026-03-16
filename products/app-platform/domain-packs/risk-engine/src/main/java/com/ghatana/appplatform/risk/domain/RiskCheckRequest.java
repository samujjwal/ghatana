package com.ghatana.appplatform.risk.domain;

import java.math.BigDecimal;

/**
 * @doc.type    Domain Object (Record)
 * @doc.purpose Immutable input structure for a risk check request (D06-001, D06-002, D06-003).
 *              Carries all fields needed to evaluate margin, position limits, and concentration.
 * @doc.layer   Domain
 * @doc.pattern Value Object
 *
 * @param orderId       Correlation identifier for the order being checked.
 * @param clientId      The client submitting the order.
 * @param accountId     Specific account within the client's portfolio.
 * @param instrumentId  The instrument being traded.
 * @param marginType    Instrument class used to look up margin rate.
 * @param side          "BUY" or "SELL".
 * @param quantity      Number of units to trade.
 * @param price         Limit/estimated price per unit.
 * @param orderValue    Pre-computed {@code quantity × price}.
 */
public record RiskCheckRequest(
        String orderId,
        String clientId,
        String accountId,
        String instrumentId,
        MarginType marginType,
        String side,
        long quantity,
        BigDecimal price,
        BigDecimal orderValue
) {}
