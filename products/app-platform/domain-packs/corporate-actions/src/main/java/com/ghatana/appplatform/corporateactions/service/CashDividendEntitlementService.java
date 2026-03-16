package com.ghatana.appplatform.corporateactions.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Calculates cash dividend entitlements for all eligible holders in the CA
 *              holder snapshot. Entitlement = quantity × dividend_per_share (from CA ratio).
 *              Fractional entitlements are rounded down; fractional residuals aggregated
 *              into a pool for distribution per company policy. Multi-currency support:
 *              reads dividend currency from the CA definition. Fires EntitlementCalculated
 *              event per holder. Satisfies STORY-D12-004.
 * @doc.layer   Domain
 * @doc.pattern Entitlement calculation; fractional handling; multi-currency; event publish.
 */
public class CashDividendEntitlementService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final EventPort        eventPort;
    private final Counter          entitlementCalculatedCounter;
    private final Counter          fractionalPoolCounter;

    public CashDividendEntitlementService(HikariDataSource dataSource, Executor executor,
                                           EventPort eventPort, MeterRegistry registry) {
        this.dataSource                   = dataSource;
        this.executor                     = executor;
        this.eventPort                    = eventPort;
        this.entitlementCalculatedCounter = Counter.builder("ca.cash_dividend.entitlements_total").register(registry);
        this.fractionalPoolCounter        = Counter.builder("ca.cash_dividend.fractional_pool_events").register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface EventPort { void publish(String topic, Object payload); }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record CashEntitlement(String entitlementId, String caId, String clientId,
                                   double quantity, double dividendPerShare,
                                   BigDecimal grossAmount, BigDecimal fractionalResidue,
                                   String currency, LocalDate paymentDate,
                                   LocalDateTime calculatedAt) {}

    public record EntitlementRunSummary(String caId, int holderCount, BigDecimal totalGross,
                                         BigDecimal totalFractionalPool, String currency) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<EntitlementRunSummary> calculateEntitlements(String caId) {
        return Promise.ofBlocking(executor, () -> {
            CaDetails ca = loadCaDetails(caId);
            List<HolderRow> holders = loadSnapshot(caId);
            BigDecimal totalGross      = BigDecimal.ZERO;
            BigDecimal totalFractional = BigDecimal.ZERO;

            for (HolderRow holder : holders) {
                BigDecimal raw       = BigDecimal.valueOf(holder.quantity())
                        .multiply(BigDecimal.valueOf(ca.dividendPerShare()));
                BigDecimal gross     = raw.setScale(2, RoundingMode.DOWN);
                BigDecimal fractional = raw.subtract(gross);

                CashEntitlement ent = persistEntitlement(caId, holder.clientId(), holder.quantity(),
                        ca.dividendPerShare(), gross, fractional, ca.currency(), ca.paymentDate());
                eventPort.publish("ca.entitlement.calculated", ent);
                entitlementCalculatedCounter.increment();
                if (fractional.compareTo(BigDecimal.ZERO) > 0) fractionalPoolCounter.increment();

                totalGross      = totalGross.add(gross);
                totalFractional = totalFractional.add(fractional);
            }
            return new EntitlementRunSummary(caId, holders.size(), totalGross, totalFractional, ca.currency());
        });
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private CashEntitlement persistEntitlement(String caId, String clientId, double qty,
                                                double divPerShare, BigDecimal gross,
                                                BigDecimal fractional, String currency,
                                                LocalDate paymentDate) throws SQLException {
        String entId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO ca_cash_entitlements
                    (entitlement_id, ca_id, client_id, quantity, dividend_per_share,
                     gross_amount, fractional_residue, currency, payment_date, calculated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (ca_id, client_id) DO UPDATE
                SET gross_amount=EXCLUDED.gross_amount, fractional_residue=EXCLUDED.fractional_residue,
                    calculated_at=NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entId); ps.setString(2, caId); ps.setString(3, clientId);
            ps.setDouble(4, qty); ps.setDouble(5, divPerShare);
            ps.setBigDecimal(6, gross); ps.setBigDecimal(7, fractional);
            ps.setString(8, currency); ps.setObject(9, paymentDate);
            ps.executeUpdate();
        }
        return new CashEntitlement(entId, caId, clientId, qty, divPerShare, gross, fractional,
                currency, paymentDate, LocalDateTime.now());
    }

    record CaDetails(double dividendPerShare, String currency, LocalDate paymentDate) {}

    private CaDetails loadCaDetails(String caId) throws SQLException {
        String sql = "SELECT ratio, currency, payment_date FROM corporate_actions WHERE ca_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("CA not found: " + caId);
                return new CaDetails(rs.getDouble("ratio"), rs.getString("currency"),
                        rs.getObject("payment_date", LocalDate.class));
            }
        }
    }

    record HolderRow(String clientId, double quantity) {}

    private List<HolderRow> loadSnapshot(String caId) throws SQLException {
        String sql = "SELECT client_id, quantity FROM ca_holder_snapshots WHERE ca_id=?";
        List<HolderRow> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new HolderRow(rs.getString("client_id"), rs.getDouble("quantity")));
            }
        }
        return result;
    }
}
