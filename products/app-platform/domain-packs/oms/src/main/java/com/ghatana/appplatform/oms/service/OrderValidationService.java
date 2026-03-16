package com.ghatana.appplatform.oms.service;

import com.ghatana.appplatform.oms.domain.OrderType;
import com.ghatana.appplatform.oms.service.OrderCaptureService.OrderCaptureRequest;
import com.ghatana.appplatform.refdata.domain.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Validates order fields per D01-002: type-specific required fields,
 *              price > 0, quantity > 0, lot-size compliance, tick-size grid.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — Application Service
 */
public class OrderValidationService {

    private static final Logger log = LoggerFactory.getLogger(OrderValidationService.class);

    /**
     * Throws {@link OrderValidationException} if any constraint is violated.
     */
    public void validateFields(OrderCaptureRequest request, Instrument instrument) {
        // Order type-specific required fields
        if (request.orderType() == OrderType.LIMIT && request.price() == null) {
            throw new OrderValidationException("LIMIT order requires price");
        }
        if (request.orderType() == OrderType.STOP && request.stopPrice() == null) {
            throw new OrderValidationException("STOP order requires stop_price");
        }
        if (request.orderType() == OrderType.STOP_LIMIT
                && (request.price() == null || request.stopPrice() == null)) {
            throw new OrderValidationException("STOP_LIMIT order requires both price and stop_price");
        }

        // Quantity > 0
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderValidationException("quantity must be greater than zero");
        }

        // Price > 0 (if specified)
        if (request.price() != null && request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderValidationException("price must be greater than zero");
        }

        // Lot size validation: quantity % lotSize == 0
        // lotSize comes from instrument config (simplified: treat as 1 if not set)
        BigDecimal lotSize = instrument.lotSize() != null ? instrument.lotSize() : BigDecimal.ONE;
        if (request.quantity().remainder(lotSize).compareTo(BigDecimal.ZERO) != 0) {
            throw new OrderValidationException(
                    "quantity " + request.quantity() + " is not a multiple of lot size " + lotSize);
        }

        // Tick size validation: price % tickSize == 0 (if price specified)
        if (request.price() != null && instrument.tickSize() != null
                && instrument.tickSize().compareTo(BigDecimal.ZERO) > 0) {
            if (request.price().remainder(instrument.tickSize()).compareTo(BigDecimal.ZERO) != 0) {
                throw new OrderValidationException(
                        "price " + request.price() + " is not on tick size grid " + instrument.tickSize());
            }
        }

        log.debug("Order validation passed: instrumentId={} qty={} price={}",
                request.instrumentId(), request.quantity(), request.price());
    }

    public static class OrderValidationException extends RuntimeException {
        public OrderValidationException(String message) {
            super(message);
        }
    }
}
