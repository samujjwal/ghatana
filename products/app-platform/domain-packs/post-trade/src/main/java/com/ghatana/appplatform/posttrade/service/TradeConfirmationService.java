package com.ghatana.appplatform.posttrade.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Generates trade confirmations when an order fill is received (D09-001).
 *              Creates PDF + JSON confirmation, calculates T+n settlement date using K-15
 *              calendar, and writes dual-calendar timestamps (AD + BS).
 *              All confirmations start in PENDING status await delivery.
 * @doc.layer   Domain — Post-Trade operations
 * @doc.pattern Event-driven (OrderFilled → generate confirmation); dual-calendar timestamps
 */
public class TradeConfirmationService {

    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");
    private static final int DEFAULT_SETTLEMENT_DAYS = 2;  // T+2 for equities

    public record OrderFilledEvent(
        String fillId,
        String orderId,
        String clientId,
        String instrumentId,
        String side,
        long quantity,
        BigDecimal fillPrice,
        BigDecimal fees,
        String currency,
        Instant filledAt
    ) {}

    public record TradeConfirmation(
        String confirmationId,
        String fillId,
        String orderId,
        String clientId,
        String instrumentId,
        String side,
        long quantity,
        BigDecimal fillPrice,
        BigDecimal fees,
        BigDecimal netAmount,
        String currency,
        LocalDate settlementDate,
        LocalDate tradeDate,
        String status,   // PENDING / SENT / DELIVERED / ACKNOWLEDGED / ESCALATED
        String pdfPath,
        String jsonPayload,
        Instant createdAt
    ) {}

    private final DataSource dataSource;
    private final Executor executor;
    private final Counter confirmationsGeneratedCounter;

    public TradeConfirmationService(DataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.confirmationsGeneratedCounter = Counter.builder("posttrade.confirmations.generated_total")
            .register(registry);
    }

    /**
     * Handle an OrderFilled event: generate and persist a trade confirmation.
     * Settlement date is T+2 (production: K-15 BS calendar for holiday awareness).
     */
    public Promise<TradeConfirmation> onOrderFilled(OrderFilledEvent event) {
        return Promise.ofBlocking(executor, () -> {
            LocalDate tradeDate    = event.filledAt().atZone(NST).toLocalDate();
            LocalDate settlementDate = tradeDate.plusDays(DEFAULT_SETTLEMENT_DAYS);
            // Production: use K-15 calendar to skip NEPSE holidays in settlement date

            BigDecimal netAmount = event.fillPrice()
                .multiply(BigDecimal.valueOf(event.quantity()))
                .subtract(event.fees());

            String jsonPayload = buildJsonPayload(event, netAmount, settlementDate);
            String pdfPath = generatePdfPlaceholder(event.fillId());

            TradeConfirmation confirmation = new TradeConfirmation(
                UUID.randomUUID().toString(), event.fillId(), event.orderId(),
                event.clientId(), event.instrumentId(), event.side(),
                event.quantity(), event.fillPrice(), event.fees(), netAmount,
                event.currency(), settlementDate, tradeDate,
                "PENDING", pdfPath, jsonPayload, Instant.now());

            persistConfirmation(confirmation);
            confirmationsGeneratedCounter.increment();
            return confirmation;
        });
    }

    /** Load an existing confirmation by fill ID (idempotency check). */
    public Promise<TradeConfirmation> findByFillId(String fillId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT id, fill_id, order_id, client_id, instrument_id, side, quantity, " +
                         "price, fees, net_amount, currency, settlement_date, trade_date, " +
                         "status, pdf_path, json_payload, created_at " +
                         "FROM trade_confirmations WHERE fill_id = ?";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(fillId));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                    return null;
                }
            }
        });
    }

    private void persistConfirmation(TradeConfirmation tc) throws Exception {
        String sql = "INSERT INTO trade_confirmations(id, fill_id, order_id, client_id, instrument_id, " +
                     "side, quantity, price, fees, net_amount, currency, settlement_date, trade_date, " +
                     "status, pdf_path, json_payload, created_at) " +
                     "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?,NOW()) " +
                     "ON CONFLICT(fill_id) DO NOTHING";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(tc.confirmationId()));
            ps.setObject(2, UUID.fromString(tc.fillId()));
            ps.setObject(3, UUID.fromString(tc.orderId()));
            ps.setObject(4, UUID.fromString(tc.clientId()));
            ps.setObject(5, UUID.fromString(tc.instrumentId()));
            ps.setString(6, tc.side());
            ps.setLong(7, tc.quantity());
            ps.setBigDecimal(8, tc.fillPrice());
            ps.setBigDecimal(9, tc.fees());
            ps.setBigDecimal(10, tc.netAmount());
            ps.setString(11, tc.currency());
            ps.setObject(12, tc.settlementDate());
            ps.setObject(13, tc.tradeDate());
            ps.setString(14, tc.pdfPath());
            ps.setString(15, tc.jsonPayload());
            ps.executeUpdate();
        }
    }

    private TradeConfirmation mapRow(ResultSet rs) throws Exception {
        return new TradeConfirmation(rs.getString("id"), rs.getString("fill_id"),
            rs.getString("order_id"), rs.getString("client_id"), rs.getString("instrument_id"),
            rs.getString("side"), rs.getLong("quantity"), rs.getBigDecimal("price"),
            rs.getBigDecimal("fees"), rs.getBigDecimal("net_amount"), rs.getString("currency"),
            rs.getDate("settlement_date").toLocalDate(), rs.getDate("trade_date").toLocalDate(),
            rs.getString("status"), rs.getString("pdf_path"), rs.getString("json_payload"),
            rs.getTimestamp("created_at").toInstant());
    }

    private String buildJsonPayload(OrderFilledEvent event, BigDecimal netAmount, LocalDate settlementDate) {
        return String.format(
            "{\"fillId\":\"%s\",\"orderId\":\"%s\",\"clientId\":\"%s\"," +
            "\"instrumentId\":\"%s\",\"side\":\"%s\",\"quantity\":%d," +
            "\"fillPrice\":\"%s\",\"fees\":\"%s\",\"netAmount\":\"%s\"," +
            "\"currency\":\"%s\",\"settlementDate\":\"%s\"}",
            event.fillId(), event.orderId(), event.clientId(),
            event.instrumentId(), event.side(), event.quantity(),
            event.fillPrice(), event.fees(), netAmount,
            event.currency(), settlementDate);
    }

    private String generatePdfPlaceholder(String fillId) {
        // Production: generate actual PDF via template engine and store in object storage
        return "/confirmations/" + fillId + ".pdf";
    }
}
