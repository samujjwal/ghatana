package com.ghatana.products.finance.domains.oms.service;

import com.ghatana.platform.core.event.EventBusPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for order routing to EMS with idempotency and rejection handling
 * @doc.layer Test
 * @doc.pattern Unit Test with Mocks
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Order Routing Tests")
class OrderRoutingTest {

    @Mock
    private OrderRoutingService.EmsPort emsPort;

    @Mock
    private DataSource dataSource;

    @Mock
    private EventBusPort eventBusPort;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private OrderRoutingService routingService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() throws Exception {
        meterRegistry = new SimpleMeterRegistry();
        routingService = new OrderRoutingService(emsPort, dataSource, eventBusPort, meterRegistry);

        // Setup default database mocks
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false); // No existing routing by default
    }

    @Test
    @DisplayName("Should successfully route order to EMS")
    void shouldSuccessfullyRouteOrderToEms() throws Exception {
        // GIVEN: Valid order and EMS returns routing ID
        String orderId = "ORD-001";
        String expectedRoutingId = "EMS-ROUTE-001";

        when(emsPort.route(eq(orderId), anyString(), anyString(), anyString(),
                anyString(), anyLong(), any(), anyString(), anyString()))
            .thenReturn(expectedRoutingId);

        // WHEN: Route order to EMS
        String routingId = routingService.routeToEms(
            orderId,
            "client-1",
            "INST-001",
            "NASDAQ",
            "BUY",
            100L,
            BigDecimal.valueOf(150.00),
            "LIMIT",
            "DAY"
        );

        // THEN: Routing ID returned and metrics updated
        assertThat(routingId).isEqualTo(expectedRoutingId);

        Counter ordersRouted = meterRegistry.find("oms.orders.routed").counter();
        assertThat(ordersRouted).isNotNull();
        assertThat(ordersRouted.count()).isEqualTo(1.0);

        verify(eventBusPort).publish(any(OrderRoutingService.OrderRoutedEvent.class));
    }

    @Test
    @DisplayName("Should handle EMS rejection")
    void shouldHandleEmsRejection() throws Exception {
        // GIVEN: EMS rejects the order
        String orderId = "ORD-002";
        String rejectionReason = "Insufficient margin";

        when(emsPort.route(eq(orderId), anyString(), anyString(), anyString(),
                anyString(), anyLong(), any(), anyString(), anyString()))
            .thenThrow(new OrderRoutingService.EmsPort.EmsRejectException(rejectionReason));

        // WHEN: Route order to EMS
        String routingId = routingService.routeToEms(
            orderId,
            "client-1",
            "INST-001",
            "NASDAQ",
            "BUY",
            100L,
            BigDecimal.valueOf(150.00),
            "LIMIT",
            "DAY"
        );

        // THEN: Returns null and metrics updated
        assertThat(routingId).isNull();

        Counter emsRejections = meterRegistry.find("oms.ems.rejections").counter();
        assertThat(emsRejections).isNotNull();
        assertThat(emsRejections.count()).isEqualTo(1.0);

        verify(eventBusPort).publish(any(OrderRoutingService.OrderEmsRejectedEvent.class));
    }

    @Test
    @DisplayName("Should handle idempotent routing requests")
    void shouldHandleIdempotentRoutingRequests() throws Exception {
        // GIVEN: Order already routed
        String orderId = "ORD-003";
        String existingRoutingId = "EMS-ROUTE-003";

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("routing_id")).thenReturn(existingRoutingId);

        // WHEN: Route same order again
        String routingId = routingService.routeToEms(
            orderId,
            "client-1",
            "INST-001",
            "NASDAQ",
            "BUY",
            100L,
            BigDecimal.valueOf(150.00),
            "LIMIT",
            "DAY"
        );

        // THEN: Returns existing routing ID without calling EMS
        assertThat(routingId).isEqualTo(existingRoutingId);
        verify(emsPort, never()).route(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should route MARKET order without price")
    void shouldRouteMarketOrderWithoutPrice() throws Exception {
        // GIVEN: MARKET order (no limit price)
        String orderId = "ORD-004";
        String expectedRoutingId = "EMS-ROUTE-004";

        when(emsPort.route(eq(orderId), anyString(), anyString(), anyString(),
                eq("SELL"), anyLong(), isNull(), eq("MARKET"), anyString()))
            .thenReturn(expectedRoutingId);

        // WHEN: Route MARKET order
        String routingId = routingService.routeToEms(
            orderId,
            "client-1",
            "INST-001",
            "NASDAQ",
            "SELL",
            200L,
            null,  // no price for MARKET
            "MARKET",
            "IOC"
        );

        // THEN: Successfully routed
        assertThat(routingId).isEqualTo(expectedRoutingId);
        verify(emsPort).route(eq(orderId), anyString(), anyString(), anyString(),
                eq("SELL"), eq(200L), isNull(), eq("MARKET"), eq("IOC"));
    }

    @Test
    @DisplayName("Should route to correct exchange")
    void shouldRouteToCorrectExchange() throws Exception {
        // GIVEN: Order for specific exchange
        String orderId = "ORD-005";
        String expectedRoutingId = "EMS-ROUTE-005";
        String targetExchange = "NYSE";

        when(emsPort.route(eq(orderId), anyString(), anyString(), eq(targetExchange),
                anyString(), anyLong(), any(), anyString(), anyString()))
            .thenReturn(expectedRoutingId);

        // WHEN: Route order to NYSE
        String routingId = routingService.routeToEms(
            orderId,
            "client-1",
            "INST-001",
            targetExchange,
            "BUY",
            100L,
            BigDecimal.valueOf(150.00),
            "LIMIT",
            "DAY"
        );

        // THEN: Routed to correct exchange
        assertThat(routingId).isEqualTo(expectedRoutingId);
        verify(emsPort).route(anyString(), anyString(), anyString(), eq(targetExchange),
                anyString(), anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle large order quantities")
    void shouldHandleLargeOrderQuantities() throws Exception {
        // GIVEN: Large order quantity
        String orderId = "ORD-006";
        String expectedRoutingId = "EMS-ROUTE-006";
        long largeQuantity = 1_000_000L;

        when(emsPort.route(eq(orderId), anyString(), anyString(), anyString(),
                anyString(), eq(largeQuantity), any(), anyString(), anyString()))
            .thenReturn(expectedRoutingId);

        // WHEN: Route large order
        String routingId = routingService.routeToEms(
            orderId,
            "client-1",
            "INST-001",
            "NASDAQ",
            "BUY",
            largeQuantity,
            BigDecimal.valueOf(150.00),
            "LIMIT",
            "DAY"
        );

        // THEN: Successfully routed
        assertThat(routingId).isEqualTo(expectedRoutingId);
        verify(emsPort).route(anyString(), anyString(), anyString(), anyString(),
                anyString(), eq(largeQuantity), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle different time-in-force options")
    void shouldHandleDifferentTimeInForceOptions() throws Exception {
        // Test GTC (Good Till Cancel)
        String orderId1 = "ORD-007";
        when(emsPort.route(eq(orderId1), anyString(), anyString(), anyString(),
                anyString(), anyLong(), any(), anyString(), eq("GTC")))
            .thenReturn("EMS-ROUTE-007");

        String routingId1 = routingService.routeToEms(
            orderId1, "client-1", "INST-001", "NASDAQ", "BUY",
            100L, BigDecimal.valueOf(150.00), "LIMIT", "GTC"
        );

        assertThat(routingId1).isNotNull();
        verify(emsPort).route(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyLong(), any(), anyString(), eq("GTC"));

        // Test IOC (Immediate or Cancel)
        String orderId2 = "ORD-008";
        when(emsPort.route(eq(orderId2), anyString(), anyString(), anyString(),
                anyString(), anyLong(), any(), anyString(), eq("IOC")))
            .thenReturn("EMS-ROUTE-008");

        String routingId2 = routingService.routeToEms(
            orderId2, "client-1", "INST-001", "NASDAQ", "SELL",
            200L, BigDecimal.valueOf(150.00), "LIMIT", "IOC"
        );

        assertThat(routingId2).isNotNull();
        verify(emsPort).route(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyLong(), any(), anyString(), eq("IOC"));
    }
}
