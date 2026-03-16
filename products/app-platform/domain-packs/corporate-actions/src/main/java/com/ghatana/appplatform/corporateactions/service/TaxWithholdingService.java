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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Tax withholding on CA entitlement payments. K-03 T2 sandboxed tax rules
 *              provide per-holder-type rates. Default rates: 5% for resident, 15% for
 *              non-resident (Nepal TDS). Fires TaxWithheld event per holder.
 *              Net payable = gross − tax. Satisfies STORY-D12-007.
 * @doc.layer   Domain
 * @doc.pattern Tax withholding; K-03 T2 tax engine; resident/non-resident TDS; event publish.
 */
public class TaxWithholdingService {

    private static final BigDecimal RESIDENT_RATE     = new BigDecimal("0.05");
    private static final BigDecimal NON_RESIDENT_RATE = new BigDecimal("0.15");

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final TaxRulePort      taxRulePort;
    private final EventPort        eventPort;
    private final Counter          taxWithheldCounter;

    public TaxWithholdingService(HikariDataSource dataSource, Executor executor,
                                  TaxRulePort taxRulePort, EventPort eventPort,
                                  MeterRegistry registry) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.taxRulePort       = taxRulePort;
        this.eventPort         = eventPort;
        this.taxWithheldCounter = Counter.builder("ca.tax.withheld_events_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-03 T2 sandboxed tax rule engine. */
    public interface TaxRulePort {
        BigDecimal getRate(String clientId, String jurisdiction, String caType);
        boolean isResident(String clientId);
    }

    public interface EventPort { void publish(String topic, Object payload); }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record TaxRecord(String taxId, String caId, String clientId, BigDecimal grossAmount,
                             BigDecimal taxRate, BigDecimal taxAmount, BigDecimal netPayable,
                             String jurisdiction, LocalDateTime calculatedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<TaxRecord>> calculateWithholding(String caId, String caType,
                                                          String jurisdiction) {
        return Promise.ofBlocking(executor, () -> {
            List<EntitlementRow> entitlements = loadEntitlements(caId);
            List<TaxRecord> records = new ArrayList<>();
            for (EntitlementRow ent : entitlements) {
                BigDecimal rate = taxRulePort.getRate(ent.clientId(), jurisdiction, caType);
                if (rate == null) {
                    rate = taxRulePort.isResident(ent.clientId()) ? RESIDENT_RATE : NON_RESIDENT_RATE;
                }
                BigDecimal tax    = ent.grossAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP);
                BigDecimal netPay = ent.grossAmount().subtract(tax);

                TaxRecord rec = persistTaxRecord(caId, ent.clientId(), ent.grossAmount(),
                        rate, tax, netPay, jurisdiction);
                eventPort.publish("ca.tax.withheld", rec);
                taxWithheldCounter.increment();
                records.add(rec);
            }
            return records;
        });
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private TaxRecord persistTaxRecord(String caId, String clientId, BigDecimal grossAmount,
                                        BigDecimal rate, BigDecimal taxAmount, BigDecimal net,
                                        String jurisdiction) throws SQLException {
        String taxId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO ca_tax_withholdings
                    (tax_id, ca_id, client_id, gross_amount, tax_rate, tax_amount, net_payable,
                     jurisdiction, calculated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (ca_id, client_id) DO UPDATE
                SET tax_rate=EXCLUDED.tax_rate, tax_amount=EXCLUDED.tax_amount,
                    net_payable=EXCLUDED.net_payable, calculated_at=NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taxId); ps.setString(2, caId); ps.setString(3, clientId);
            ps.setBigDecimal(4, grossAmount); ps.setBigDecimal(5, rate);
            ps.setBigDecimal(6, taxAmount); ps.setBigDecimal(7, net);
            ps.setString(8, jurisdiction);
            ps.executeUpdate();
        }
        return new TaxRecord(taxId, caId, clientId, grossAmount, rate, taxAmount, net,
                jurisdiction, LocalDateTime.now());
    }

    record EntitlementRow(String clientId, BigDecimal grossAmount) {}

    private List<EntitlementRow> loadEntitlements(String caId) throws SQLException {
        String sql = "SELECT client_id, gross_amount FROM ca_cash_entitlements WHERE ca_id=?";
        List<EntitlementRow> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new EntitlementRow(rs.getString("client_id"),
                        rs.getBigDecimal("gross_amount")));
            }
        }
        return result;
    }
}
