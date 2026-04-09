package com.ghatana.products.finance.domains.oms.service;

import com.ghatana.products.finance.domains.oms.domain.OrderSide;
import com.ghatana.products.finance.domains.oms.domain.OrderType;
import com.ghatana.products.finance.domains.oms.domain.TimeInForce;
import com.ghatana.products.finance.domains.oms.service.OrderCaptureService.OrderCaptureRequest;
import com.ghatana.products.finance.domains.referencedata.domain.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Comprehensive validation tests for order field validation rules per D01-002
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Order Validation Tests")
class OrderValidationTest {

    private OrderValidationService validationService;
    private Instrument testInstrument;

    @BeforeEach
    void setUp() {
        validationService = new OrderValidationService();

        // Create test instrument with lot size 100 and tick size 0.01
        testInstrument = new Instrument(
            java.util.UUID.randomUUID(),
            "AAPL",
            "NASDAQ",
            "US0378331005",  // ISIN
            "Apple Inc.",
            com.ghatana.products.finance.domains.referencedata.domain.InstrumentType.EQUITY,
            com.ghatana.products.finance.domains.referencedata.domain.InstrumentStatus.ACTIVE,
            "Technology",
            100,  // lot size
            BigDecimal.valueOf(0.01),  // tick size
            "USD",
            java.time.LocalDate.now(),
            null,  // effectiveTo (current version)
            java.time.Instant.now(),
            "2080-12-15",  // Bikram Sambat date
            java.util.Map.of()
        );
    }

    @Test
    @DisplayName("Should validate valid LIMIT order")
    void shouldValidateValidLimitOrder() {
        // GIVEN: Valid LIMIT order
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            BigDecimal.valueOf(100),  // quantity (multiple of lot size)
            BigDecimal.valueOf(150.50),  // price (on tick grid)
            null,  // no stop price for LIMIT
            "idempotency-key-1"
        );

        // WHEN/THEN: Should not throw
        assertThatCode(() -> validationService.validateFields(request, testInstrument))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject LIMIT order without price")
    void shouldRejectLimitOrderWithoutPrice() {
        // GIVEN: LIMIT order without price
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            BigDecimal.valueOf(100),
            null,  // missing price
            null,
            "idempotency-key-1"
        );

        // WHEN/THEN: Should throw validation exception
        assertThatThrownBy(() -> validationService.validateFields(request, testInstrument))
            .isInstanceOf(OrderValidationService.OrderValidationException.class)
            .hasMessageContaining("LIMIT order requires price");
    }

    @Test
    @DisplayName("Should validate valid MARKET order")
    void shouldValidateValidMarketOrder() {
        // GIVEN: Valid MARKET order (no price required)
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.SELL,
            OrderType.MARKET,
            TimeInForce.IOC,
            BigDecimal.valueOf(200),
            null,  // no price for MARKET
            null,
            "idempotency-key-2"
        );

        // WHEN/THEN: Should not throw
        assertThatCode(() -> validationService.validateFields(request, testInstrument))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject STOP order without stop price")
    void shouldRejectStopOrderWithoutStopPrice() {
        // GIVEN: STOP order without stop price
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.STOP,
            TimeInForce.DAY,
            BigDecimal.valueOf(100),
            null,
            null,  // missing stop price
            "idempotency-key-3"
        );

        // WHEN/THEN: Should throw validation exception
        assertThatThrownBy(() -> validationService.validateFields(request, testInstrument))
            .isInstanceOf(OrderValidationService.OrderValidationException.class)
            .hasMessageContaining("STOP order requires stop_price");
    }

    @Test
    @DisplayName("Should validate valid STOP_LIMIT order")
    void shouldValidateValidStopLimitOrder() {
        // GIVEN: Valid STOP_LIMIT order with both price and stop price
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.STOP_LIMIT,
            TimeInForce.DAY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.00),  // limit price
            BigDecimal.valueOf(149.00),  // stop price
            "idempotency-key-4"
        );

        // WHEN/THEN: Should not throw
        assertThatCode(() -> validationService.validateFields(request, testInstrument))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject STOP_LIMIT order without price")
    void shouldRejectStopLimitOrderWithoutPrice() {
        // GIVEN: STOP_LIMIT order without limit price
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.STOP_LIMIT,
            TimeInForce.DAY,
            BigDecimal.valueOf(100),
            null,  // missing limit price
            BigDecimal.valueOf(149.00),
            "idempotency-key-5"
        );

        // WHEN/THEN: Should throw validation exception
        assertThatThrownBy(() -> validationService.validateFields(request, testInstrument))
            .isInstanceOf(OrderValidationService.OrderValidationException.class)
            .hasMessageContaining("STOP_LIMIT order requires both price and stop_price");
    }

    @Test
    @DisplayName("Should reject order with zero quantity")
    void shouldRejectOrderWithZeroQuantity() {
        // GIVEN: Order with zero quantity
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            BigDecimal.ZERO,  // invalid quantity
            BigDecimal.valueOf(150.00),
            null,
            "idempotency-key-6"
        );

        // WHEN/THEN: Should throw validation exception
        assertThatThrownBy(() -> validationService.validateFields(request, testInstrument))
            .isInstanceOf(OrderValidationService.OrderValidationException.class)
            .hasMessageContaining("quantity must be greater than zero");
    }

    @Test
    @DisplayName("Should reject order with negative quantity")
    void shouldRejectOrderWithNegativeQuantity() {
        // GIVEN: Order with negative quantity
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            BigDecimal.valueOf(-100),  // invalid quantity
            BigDecimal.valueOf(150.00),
            null,
            "idempotency-key-7"
        );

        // WHEN/THEN: Should throw validation exception
        assertThatThrownBy(() -> validationService.validateFields(request, testInstrument))
            .isInstanceOf(OrderValidationService.OrderValidationException.class)
            .hasMessageContaining("quantity must be greater than zero");
    }

    @Test
    @DisplayName("Should reject order with zero price")
    void shouldRejectOrderWithZeroPrice() {
        // GIVEN: Order with zero price
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            BigDecimal.valueOf(100),
            BigDecimal.ZERO,  // invalid price
            null,
            "idempotency-key-8"
        );

        // WHEN/THEN: Should throw validation exception
        assertThatThrownBy(() -> validationService.validateFields(request, testInstrument))
            .isInstanceOf(OrderValidationService.OrderValidationException.class)
            .hasMessageContaining("price must be greater than zero");
    }

    @Test
    @DisplayName("Should reject order with negative price")
    void shouldRejectOrderWithNegativePrice() {
        // GIVEN: Order with negative price
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(-150.00),  // invalid price
            null,
            "idempotency-key-9"
        );

        // WHEN/THEN: Should throw validation exception
        assertThatThrownBy(() -> validationService.validateFields(request, testInstrument))
            .isInstanceOf(OrderValidationService.OrderValidationException.class)
            .hasMessageContaining("price must be greater than zero");
    }

    @Test
    @DisplayName("Should reject order with quantity not multiple of lot size")
    void shouldRejectOrderWithInvalidLotSize() {
        // GIVEN: Order with quantity not multiple of lot size (100)
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            BigDecimal.valueOf(150),  // not multiple of 100
            BigDecimal.valueOf(150.00),
            null,
            "idempotency-key-10"
        );

        // WHEN/THEN: Should throw validation exception
        assertThatThrownBy(() -> validationService.validateFields(request, testInstrument))
            .isInstanceOf(OrderValidationService.OrderValidationException.class)
            .hasMessageContaining("not a multiple of lot size");
    }

    @Test
    @DisplayName("Should accept order with quantity multiple of lot size")
    void shouldAcceptOrderWithValidLotSize() {
        // GIVEN: Order with quantity multiple of lot size (100)
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            BigDecimal.valueOf(300),  // multiple of 100
            BigDecimal.valueOf(150.00),
            null,
            "idempotency-key-11"
        );

        // WHEN/THEN: Should not throw
        assertThatCode(() -> validationService.validateFields(request, testInstrument))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject order with price not on tick size grid")
    void shouldRejectOrderWithInvalidTickSize() {
        // GIVEN: Order with price not on tick size grid (0.01)
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.555),  // not on 0.01 grid
            null,
            "idempotency-key-12"
        );

        // WHEN/THEN: Should throw validation exception
        assertThatThrownBy(() -> validationService.validateFields(request, testInstrument))
            .isInstanceOf(OrderValidationService.OrderValidationException.class)
            .hasMessageContaining("not on tick size grid");
    }

    @Test
    @DisplayName("Should accept order with price on tick size grid")
    void shouldAcceptOrderWithValidTickSize() {
        // GIVEN: Order with price on tick size grid (0.01)
        OrderCaptureRequest request = new OrderCaptureRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.55),  // on 0.01 grid
            null,
            "idempotency-key-13"
        );

        // WHEN/THEN: Should not throw
        assertThatCode(() -> validationService.validateFields(request, testInstrument))
            .doesNotThrowAnyException();
    }
}
