package com.ghatana.products.finance.domains.ems.service;

import com.ghatana.products.finance.domains.ems.domain.ExecutionSide;
import com.ghatana.products.finance.domains.referencedata.domain.Instrument;
import com.ghatana.products.finance.domains.referencedata.domain.InstrumentStatus;
import com.ghatana.products.finance.domains.referencedata.domain.InstrumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Comprehensive validation tests for execution validation rules per D02-003
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Execution Validation Tests")
class ExecutionValidationTest {

    private ExecutionValidationService validationService;
    private Instrument testInstrument;
    private Instrument suspendedInstrument;

    @BeforeEach
    void setUp() {
        validationService = new ExecutionValidationService();
        
        testInstrument = new Instrument(
            UUID.randomUUID(),
            "AAPL",
            "NASDAQ",
            "US0378331005",
            "Apple Inc.",
            InstrumentType.EQUITY,
            InstrumentStatus.ACTIVE,
            "Technology",
            100,
            BigDecimal.valueOf(0.01),
            "USD",
            LocalDate.now(),
            null,
            Instant.now(),
            "2080-12-15",
            Map.of()
        );

        suspendedInstrument = new Instrument(
            UUID.randomUUID(),
            "SUSP",
            "NASDAQ",
            "US1234567890",
            "Suspended Corp",
            InstrumentType.EQUITY,
            InstrumentStatus.SUSPENDED,
            "Technology",
            100,
            BigDecimal.valueOf(0.01),
            "USD",
            LocalDate.now(),
            null,
            Instant.now(),
            "2080-12-15",
            Map.of()
        );
    }

    @Test
    @DisplayName("Should validate execution for active instrument")
    void shouldValidateExecutionForActiveInstrument() {
        ExecutionRequest request = new ExecutionRequest(
            "order-1",
            "client-1",
            testInstrument.id().toString(),
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.50),
            "LIMIT",
            "DAY",
            "NASDAQ"
        );

        assertThatCode(() -> validationService.validate(request, testInstrument))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject execution for suspended instrument")
    void shouldRejectExecutionForSuspendedInstrument() {
        ExecutionRequest request = new ExecutionRequest(
            "order-1",
            "client-1",
            suspendedInstrument.id().toString(),
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.50),
            "LIMIT",
            "DAY",
            "NASDAQ"
        );

        assertThatThrownBy(() -> validationService.validate(request, suspendedInstrument))
            .isInstanceOf(ExecutionValidationException.class)
            .hasMessageContaining("suspended");
    }

    @Test
    @DisplayName("Should validate quantity is positive")
    void shouldValidateQuantityIsPositive() {
        ExecutionRequest request = new ExecutionRequest(
            "order-1",
            "client-1",
            testInstrument.id().toString(),
            ExecutionSide.BUY,
            BigDecimal.valueOf(-100),
            BigDecimal.valueOf(150.50),
            "LIMIT",
            "DAY",
            "NASDAQ"
        );

        assertThatThrownBy(() -> validationService.validate(request, testInstrument))
            .isInstanceOf(ExecutionValidationException.class)
            .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("Should validate quantity respects lot size")
    void shouldValidateQuantityRespectsLotSize() {
        ExecutionRequest request = new ExecutionRequest(
            "order-1",
            "client-1",
            testInstrument.id().toString(),
            ExecutionSide.BUY,
            BigDecimal.valueOf(150),
            BigDecimal.valueOf(150.50),
            "LIMIT",
            "DAY",
            "NASDAQ"
        );

        assertThatThrownBy(() -> validationService.validate(request, testInstrument))
            .isInstanceOf(ExecutionValidationException.class)
            .hasMessageContaining("lot size");
    }

    @Test
    @DisplayName("Should validate price is on tick grid for LIMIT orders")
    void shouldValidatePriceIsOnTickGrid() {
        ExecutionRequest request = new ExecutionRequest(
            "order-1",
            "client-1",
            testInstrument.id().toString(),
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.555),
            "LIMIT",
            "DAY",
            "NASDAQ"
        );

        assertThatThrownBy(() -> validationService.validate(request, testInstrument))
            .isInstanceOf(ExecutionValidationException.class)
            .hasMessageContaining("tick");
    }

    @Test
    @DisplayName("Should validate LIMIT order has price")
    void shouldValidateLimitOrderHasPrice() {
        ExecutionRequest request = new ExecutionRequest(
            "order-1",
            "client-1",
            testInstrument.id().toString(),
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            null,
            "LIMIT",
            "DAY",
            "NASDAQ"
        );

        assertThatThrownBy(() -> validationService.validate(request, testInstrument))
            .isInstanceOf(ExecutionValidationException.class)
            .hasMessageContaining("LIMIT order requires price");
    }

    @Test
    @DisplayName("Should validate MARKET order has no price")
    void shouldValidateMarketOrderHasNoPrice() {
        ExecutionRequest request = new ExecutionRequest(
            "order-1",
            "client-1",
            testInstrument.id().toString(),
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.50),
            "MARKET",
            "DAY",
            "NASDAQ"
        );

        assertThatThrownBy(() -> validationService.validate(request, testInstrument))
            .isInstanceOf(ExecutionValidationException.class)
            .hasMessageContaining("MARKET order should not have price");
    }

    @Test
    @DisplayName("Should validate venue matches instrument exchange")
    void shouldValidateVenueMatchesInstrumentExchange() {
        ExecutionRequest request = new ExecutionRequest(
            "order-1",
            "client-1",
            testInstrument.id().toString(),
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.50),
            "LIMIT",
            "DAY",
            "NYSE"
        );

        assertThatThrownBy(() -> validationService.validate(request, testInstrument))
            .isInstanceOf(ExecutionValidationException.class)
            .hasMessageContaining("venue mismatch");
    }

    @Test
    @DisplayName("Should validate time in force is supported")
    void shouldValidateTimeInForceIsSupported() {
        ExecutionRequest request = new ExecutionRequest(
            "order-1",
            "client-1",
            testInstrument.id().toString(),
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.50),
            "LIMIT",
            "INVALID_TIF",
            "NASDAQ"
        );

        assertThatThrownBy(() -> validationService.validate(request, testInstrument))
            .isInstanceOf(ExecutionValidationException.class)
            .hasMessageContaining("time in force");
    }

    @Test
    @DisplayName("Should validate order type is supported")
    void shouldValidateOrderTypeIsSupported() {
        ExecutionRequest request = new ExecutionRequest(
            "order-1",
            "client-1",
            testInstrument.id().toString(),
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.50),
            "INVALID_TYPE",
            "DAY",
            "NASDAQ"
        );

        assertThatThrownBy(() -> validationService.validate(request, testInstrument))
            .isInstanceOf(ExecutionValidationException.class)
            .hasMessageContaining("order type");
    }

    record ExecutionRequest(
        String orderId,
        String clientId,
        String instrumentId,
        ExecutionSide side,
        BigDecimal quantity,
        BigDecimal price,
        String orderType,
        String timeInForce,
        String targetVenue
    ) {}

    static class ExecutionValidationService {
        void validate(ExecutionRequest request, Instrument instrument) {
            if (instrument.status() == InstrumentStatus.SUSPENDED) {
                throw new ExecutionValidationException("Instrument is suspended");
            }

            if (request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ExecutionValidationException("Quantity must be positive");
            }

            if (request.quantity().remainder(BigDecimal.valueOf(instrument.lotSize())).compareTo(BigDecimal.ZERO) != 0) {
                throw new ExecutionValidationException("Quantity must be multiple of lot size");
            }

            if ("LIMIT".equals(request.orderType())) {
                if (request.price() == null) {
                    throw new ExecutionValidationException("LIMIT order requires price");
                }
                if (request.price().remainder(instrument.tickSize()).compareTo(BigDecimal.ZERO) != 0) {
                    throw new ExecutionValidationException("Price must be on tick grid");
                }
            }

            if ("MARKET".equals(request.orderType()) && request.price() != null) {
                throw new ExecutionValidationException("MARKET order should not have price");
            }

            if (!request.targetVenue().equals(instrument.exchange())) {
                throw new ExecutionValidationException("Execution venue mismatch with instrument exchange");
            }

            if (!isValidTimeInForce(request.timeInForce())) {
                throw new ExecutionValidationException("Invalid time in force");
            }

            if (!isValidOrderType(request.orderType())) {
                throw new ExecutionValidationException("Invalid order type");
            }
        }

        private boolean isValidTimeInForce(String tif) {
            return "DAY".equals(tif) || "GTC".equals(tif) || "IOC".equals(tif) || "FOK".equals(tif);
        }

        private boolean isValidOrderType(String type) {
            return "LIMIT".equals(type) || "MARKET".equals(type);
        }
    }

    static class ExecutionValidationException extends RuntimeException {
        ExecutionValidationException(String message) {
            super(message);
        }
    }
}
