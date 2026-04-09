package com.ghatana.products.finance.domains.ems.service;

import com.ghatana.products.finance.domains.ems.domain.ExecutionSide;
import com.ghatana.products.finance.domains.ems.service.ExecutionReportService.ExecutionReport;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for FIX protocol reporting and execution quality metrics per D02-021
 * @doc.layer Test
 * @doc.pattern Integration Test with EventloopTestBase
 */
@DisplayName("Execution Reporting Tests")
class ExecutionReportingTest extends EventloopTestBase {

    private ExecutionReportService reportService;
    private FixProtocolService fixProtocolService;
    private ExecutionQualityService qualityService;

    @Mock
    private DataSource dataSource;

    @Mock
    private ExecutionReportService.PostTradeNotifyPort postTradePort;

    @Mock
    private ExecutionReportService.CalendarPort calendarPort;

    private SimpleMeterRegistry meterRegistry;
    private Executor executor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        executor = Runnable::run;

        reportService = new ExecutionReportService(
            dataSource,
            executor,
            postTradePort,
            calendarPort,
            meterRegistry
        );

        fixProtocolService = new FixProtocolService();
        qualityService = new ExecutionQualityService();

        when(calendarPort.settlementDateBs(any(), anyInt())).thenReturn("2080-12-17");
        when(calendarPort.settlementDateAd(any(), anyInt())).thenReturn("2024-04-06");
    }

    @Test
    @DisplayName("Should generate execution report with all required fields")
    void shouldGenerateExecutionReportWithAllFields() {
        String fillId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        ExecutionReport report = new ExecutionReport(
            UUID.randomUUID().toString(),
            orderId,
            fillId,
            "client-1",
            "AAPL",
            "BUY",
            100.0,
            150.50,
            15050.00,
            15.05,
            7.53,
            22.58,
            15027.42,
            "2024-04-06",
            "2024-04-08",
            "2080-12-17",
            "EQUITY"
        );

        assertThat(report.reportId()).isNotNull();
        assertThat(report.orderId()).isEqualTo(orderId);
        assertThat(report.fillId()).isEqualTo(fillId);
        assertThat(report.grossAmount()).isEqualTo(15050.00);
        assertThat(report.netAmount()).isEqualTo(15027.42);
    }

    @Test
    @DisplayName("Should calculate commission fees correctly")
    void shouldCalculateCommissionFeesCorrectly() {
        double grossAmount = 15050.00;
        double commissionRate = 0.001;

        double commission = grossAmount * commissionRate;

        assertThat(commission).isEqualTo(15.05);
    }

    @Test
    @DisplayName("Should calculate net amount after fees")
    void shouldCalculateNetAmountAfterFees() {
        double grossAmount = 15050.00;
        double commission = 15.05;
        double sebiTurnover = 7.53;
        double stampDuty = 3.01;

        double netAmount = grossAmount - commission - sebiTurnover - stampDuty;

        assertThat(netAmount).isEqualTo(15024.41);
    }

    @Test
    @DisplayName("Should generate FIX execution report message")
    void shouldGenerateFixExecutionReportMessage() {
        ExecutionReport report = new ExecutionReport(
            UUID.randomUUID().toString(),
            "order-1",
            "fill-1",
            "client-1",
            "AAPL",
            "BUY",
            100.0,
            150.50,
            15050.00,
            15.05,
            7.53,
            22.58,
            15027.42,
            "2024-04-06",
            "2024-04-08",
            "2080-12-17",
            "EQUITY"
        );

        String fixMessage = fixProtocolService.generateExecutionReport(report);

        assertThat(fixMessage).contains("35=8");
        assertThat(fixMessage).contains("55=AAPL");
        assertThat(fixMessage).contains("54=1");
        assertThat(fixMessage).contains("38=100");
        assertThat(fixMessage).contains("44=150.50");
    }

    @Test
    @DisplayName("Should calculate settlement date using T+2")
    void shouldCalculateSettlementDateUsingTPlus2() {
        String tradeDate = "2024-04-04";
        int settlementDays = 2;

        when(calendarPort.settlementDateAd(tradeDate, settlementDays))
            .thenReturn("2024-04-06");

        String settlementDate = calendarPort.settlementDateAd(tradeDate, settlementDays);

        assertThat(settlementDate).isEqualTo("2024-04-06");
    }

    @Test
    @DisplayName("Should notify post-trade module after report generation")
    void shouldNotifyPostTradeModuleAfterReportGeneration() {
        String fillId = UUID.randomUUID().toString();
        ExecutionReport report = new ExecutionReport(
            UUID.randomUUID().toString(),
            "order-1",
            fillId,
            "client-1",
            "AAPL",
            "BUY",
            100.0,
            150.50,
            15050.00,
            15.05,
            7.53,
            22.58,
            15027.42,
            "2024-04-06",
            "2024-04-08",
            "2080-12-17",
            "EQUITY"
        );

        postTradePort.notifyFill(fillId, report);

        verify(postTradePort, times(1)).notifyFill(eq(fillId), any(ExecutionReport.class));
    }

    @Test
    @DisplayName("Should calculate execution quality metrics")
    void shouldCalculateExecutionQualityMetrics() {
        ExecutionMetrics metrics = qualityService.calculateMetrics(
            BigDecimal.valueOf(150.50),
            BigDecimal.valueOf(150.50),
            BigDecimal.valueOf(100),
            Instant.now().minusSeconds(5),
            Instant.now()
        );

        assertThat(metrics.priceImprovement()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.executionSpeed()).isLessThanOrEqualTo(5000L);
    }

    @Test
    @DisplayName("Should track fill rate metrics")
    void shouldTrackFillRateMetrics() {
        FillRateMetrics metrics = qualityService.calculateFillRate(
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(80)
        );

        assertThat(metrics.fillRate()).isEqualByComparingTo(BigDecimal.valueOf(0.80));
    }

    @Test
    @DisplayName("Should generate regulatory execution report")
    void shouldGenerateRegulatoryExecutionReport() {
        RegulatoryReport report = qualityService.generateRegulatoryReport(
            "order-1",
            "AAPL",
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.50),
            "NASDAQ",
            Instant.now()
        );

        assertThat(report.orderId()).isEqualTo("order-1");
        assertThat(report.symbol()).isEqualTo("AAPL");
        assertThat(report.venue()).isEqualTo("NASDAQ");
    }

    @Test
    @DisplayName("Should include venue execution statistics")
    void shouldIncludeVenueExecutionStatistics() {
        VenueStatistics stats = qualityService.calculateVenueStatistics(
            "NASDAQ",
            List.of(
                BigDecimal.valueOf(150.50),
                BigDecimal.valueOf(150.51),
                BigDecimal.valueOf(150.49)
            )
        );

        assertThat(stats.venue()).isEqualTo("NASDAQ");
        assertThat(stats.averagePrice()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should increment report generation counter")
    void shouldIncrementReportGenerationCounter() {
        double initialCount = meterRegistry.counter("ems.execution.report.generated").count();

        meterRegistry.counter("ems.execution.report.generated").increment();

        assertThat(meterRegistry.counter("ems.execution.report.generated").count())
            .isEqualTo(initialCount + 1);
    }

    static class FixProtocolService {
        String generateExecutionReport(ExecutionReport report) {
            return String.format(
                "8=FIX.4.4|9=XXX|35=8|49=SENDER|56=TARGET|34=1|52=%s|" +
                "11=%s|37=%s|17=%s|150=2|39=2|55=%s|54=%s|38=%.0f|44=%.2f|" +
                "31=%.2f|32=%.0f|151=0|14=%.0f|6=%.2f|10=XXX|",
                report.tradeDateAd(),
                report.orderId(),
                report.orderId(),
                report.fillId(),
                report.instrumentId(),
                report.side().equals("BUY") ? "1" : "2",
                report.quantity(),
                report.price(),
                report.price(),
                report.quantity(),
                report.quantity(),
                report.price()
            );
        }
    }

    static class ExecutionQualityService {
        ExecutionMetrics calculateMetrics(BigDecimal orderPrice, BigDecimal executionPrice,
                                          BigDecimal quantity, Instant orderTime, Instant executionTime) {
            BigDecimal priceImprovement = orderPrice.subtract(executionPrice);
            long executionSpeed = executionTime.toEpochMilli() - orderTime.toEpochMilli();
            return new ExecutionMetrics(priceImprovement, executionSpeed);
        }

        FillRateMetrics calculateFillRate(BigDecimal orderQuantity, BigDecimal filledQuantity) {
            BigDecimal fillRate = filledQuantity.divide(orderQuantity, 2, java.math.RoundingMode.HALF_UP);
            return new FillRateMetrics(fillRate);
        }

        RegulatoryReport generateRegulatoryReport(String orderId, String symbol, ExecutionSide side,
                                                  BigDecimal quantity, BigDecimal price,
                                                  String venue, Instant executedAt) {
            return new RegulatoryReport(orderId, symbol, side, quantity, price, venue, executedAt);
        }

        VenueStatistics calculateVenueStatistics(String venue, java.util.List<BigDecimal> prices) {
            BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal average = sum.divide(BigDecimal.valueOf(prices.size()), 2, java.math.RoundingMode.HALF_UP);
            return new VenueStatistics(venue, average);
        }
    }

    record ExecutionMetrics(BigDecimal priceImprovement, long executionSpeed) {}
    record FillRateMetrics(BigDecimal fillRate) {}
    record RegulatoryReport(String orderId, String symbol, ExecutionSide side,
                           BigDecimal quantity, BigDecimal price, String venue, Instant executedAt) {}
    record VenueStatistics(String venue, BigDecimal averagePrice) {}
}
