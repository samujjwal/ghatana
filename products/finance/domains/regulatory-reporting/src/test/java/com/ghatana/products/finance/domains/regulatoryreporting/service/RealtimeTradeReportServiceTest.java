package com.ghatana.products.finance.domains.regulatoryreporting.service;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies realtime trade reporting persistence and T+15 window handling
 * @doc.layer product
 * @doc.pattern Test
 */
class RealtimeTradeReportServiceTest extends EventloopTestBase {

    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:trade-report-test;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS trade_reports (
                    trade_report_id VARCHAR(128) PRIMARY KEY,
                    trade_id VARCHAR(128) NOT NULL,
                    submission_id VARCHAR(128) NOT NULL,
                    within_window BOOLEAN NOT NULL,
                    reported_at TIMESTAMP NOT NULL
                )
                """);
        }
    }

    @Test
    void onOrderFilledPersistsWithinWindowSubmission() throws Exception {
        RealtimeTradeReportService.TradeReportBuilderPort reportBuilder = mock(RealtimeTradeReportService.TradeReportBuilderPort.class);
        RegulatorSubmissionAdapterService submissionAdapter = mock(RegulatorSubmissionAdapterService.class);
        when(reportBuilder.buildTradeReport("trade-1", "TRADE_REPORT")).thenReturn("<trade />".getBytes());
        when(submissionAdapter.submit(anyString(), eq("SEBON"), eq("TRADE_REPORT"), any(), eq("application/xml")))
            .thenReturn(Promise.of(new RegulatorSubmissionAdapterService.Submission(
                "submission-1",
                "trade-1",
                "SEBON",
                "TRADE_REPORT",
                RegulatorSubmissionAdapterService.SubmissionStatus.SUBMITTED,
                "ref-1",
                0,
                LocalDateTime.now(),
                LocalDateTime.now()
            )));

        RealtimeTradeReportService service = new RealtimeTradeReportService(
            dataSource,
            Runnable::run,
            reportBuilder,
            submissionAdapter,
            new SimpleMeterRegistry()
        );

        RealtimeTradeReportService.TradeReportRecord record = runPromise(() -> service.onOrderFilled(
            new RealtimeTradeReportService.OrderFilledEvent(
                "trade-1",
                "client-1",
                "NABIL",
                "BUY",
                10,
                1500.0,
                LocalDateTime.now().minusMinutes(3)
            )
        ));

        assertTrue(record.withinWindow());
        assertTrue(isWithinWindowStored("trade-1"));
    }

    @Test
    void onOrderFilledMarksBreachedWindowWhenTradeIsLate() throws Exception {
        RealtimeTradeReportService.TradeReportBuilderPort reportBuilder = mock(RealtimeTradeReportService.TradeReportBuilderPort.class);
        RegulatorSubmissionAdapterService submissionAdapter = mock(RegulatorSubmissionAdapterService.class);
        when(reportBuilder.buildTradeReport("trade-2", "TRADE_REPORT")).thenReturn("<trade />".getBytes());
        when(submissionAdapter.submit(anyString(), eq("SEBON"), eq("TRADE_REPORT"), any(), eq("application/xml")))
            .thenReturn(Promise.of(new RegulatorSubmissionAdapterService.Submission(
                "submission-2",
                "trade-2",
                "SEBON",
                "TRADE_REPORT",
                RegulatorSubmissionAdapterService.SubmissionStatus.SUBMITTED,
                "ref-2",
                0,
                LocalDateTime.now(),
                LocalDateTime.now()
            )));

        RealtimeTradeReportService service = new RealtimeTradeReportService(
            dataSource,
            Runnable::run,
            reportBuilder,
            submissionAdapter,
            new SimpleMeterRegistry()
        );

        RealtimeTradeReportService.TradeReportRecord record = runPromise(() -> service.onOrderFilled(
            new RealtimeTradeReportService.OrderFilledEvent(
                "trade-2",
                "client-2",
                "NTC",
                "SELL",
                25,
                910.0,
                LocalDateTime.now().minusMinutes(20)
            )
        ));

        assertFalse(record.withinWindow());
        assertFalse(isWithinWindowStored("trade-2"));
    }

    private boolean isWithinWindowStored(String tradeId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT within_window FROM trade_reports WHERE trade_id = ?")) {
            statement.setString(1, tradeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }
}
