package com.ghatana.appplatform.corporateactions.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

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
 * @doc.purpose Issues PDF TDS certificates for each holder's tax withheld on a CA.
 *              K-08 ensures 7-year retention of all generated certificates.
 *              Supports annual consolidated statements covering all CAs in a fiscal year.
 *              Dual-calendar BS/AD dates from K-15 CalendarPort applied to all dates.
 *              Satisfies STORY-D12-008.
 * @doc.layer   Domain
 * @doc.pattern TDS certificates; K-08 7-year retention; annual statement; K-15 dual calendar.
 */
public class TaxCertificateService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final PdfRenderPort    pdfRenderPort;
    private final StoragePort      storagePort;
    private final CalendarPort     calendarPort;
    private final Counter          certificateIssuedCounter;

    public TaxCertificateService(HikariDataSource dataSource, Executor executor,
                                  PdfRenderPort pdfRenderPort, StoragePort storagePort,
                                  CalendarPort calendarPort, MeterRegistry registry) {
        this.dataSource                = dataSource;
        this.executor                  = executor;
        this.pdfRenderPort             = pdfRenderPort;
        this.storagePort               = storagePort;
        this.calendarPort              = calendarPort;
        this.certificateIssuedCounter  = Counter.builder("ca.tax_cert.issued_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface PdfRenderPort { byte[] render(String htmlContent); }

    public interface StoragePort {
        String store(String bucket, String key, byte[] data, String contentType);
        /** K-08: mark for 7-year retention. */
        void setRetentionPolicy(String bucket, String key, int retentionYears);
    }

    public interface CalendarPort { String toNepaliDate(LocalDate adDate); }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record TaxCertificate(String certId, String caId, String clientId,
                                  String taxId, String storageKey, LocalDate issuedDate,
                                  String issuedDateBs, LocalDateTime createdAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<TaxCertificate>> issueCertificates(String caId) {
        return Promise.ofBlocking(executor, () -> {
            List<TaxRow> taxes = loadTaxRecords(caId);
            List<TaxCertificate> issued = new ArrayList<>();
            for (TaxRow tax : taxes) {
                LocalDate today = LocalDate.now();
                String    todayBs = calendarPort.toNepaliDate(today);
                String    html = buildCertificateHtml(tax, today, todayBs);
                byte[]    pdf  = pdfRenderPort.render(html);
                String    key  = "tax-certs/" + caId + "/" + tax.clientId() + "/" + UUID.randomUUID() + ".pdf";
                storagePort.store("ca-documents", key, pdf, "application/pdf");
                storagePort.setRetentionPolicy("ca-documents", key, 7);
                TaxCertificate cert = persistCertificate(caId, tax.clientId(), tax.taxId(), key, today, todayBs);
                certificateIssuedCounter.increment();
                issued.add(cert);
            }
            return issued;
        });
    }

    public Promise<byte[]> issueAnnualStatement(String clientId, int fiscalYear) {
        return Promise.ofBlocking(executor, () -> {
            List<TaxRow> allTaxes = loadClientAnnualTax(clientId, fiscalYear);
            String html = buildAnnualStatementHtml(clientId, fiscalYear, allTaxes);
            byte[] pdf  = pdfRenderPort.render(html);
            String key  = "tax-certs/annual/" + clientId + "/" + fiscalYear + ".pdf";
            storagePort.store("ca-documents", key, pdf, "application/pdf");
            storagePort.setRetentionPolicy("ca-documents", key, 7);
            return pdf;
        });
    }

    // ─── HTML builders ───────────────────────────────────────────────────────

    private String buildCertificateHtml(TaxRow tax, LocalDate today, String todayBs) {
        return "<html><body><h1>TDS Certificate</h1>" +
               "<p>Client: " + tax.clientId() + "</p>" +
               "<p>CA: " + tax.caId() + "</p>" +
               "<p>Gross Amount: " + tax.grossAmount() + " " + tax.currency() + "</p>" +
               "<p>Tax Rate: " + tax.taxRate() + "</p>" +
               "<p>Tax Withheld: " + tax.taxAmount() + "</p>" +
               "<p>Net Payable: " + tax.netPayable() + "</p>" +
               "<p>Issue Date (AD): " + today + " (BS): " + todayBs + "</p>" +
               "</body></html>";
    }

    private String buildAnnualStatementHtml(String clientId, int year, List<TaxRow> taxes) {
        StringBuilder sb = new StringBuilder("<html><body><h1>Annual TDS Statement FY ")
                .append(year).append("</h1><p>Client: ").append(clientId).append("</p><table>");
        sb.append("<tr><th>CA</th><th>Gross</th><th>Tax Rate</th><th>Tax</th><th>Net</th></tr>");
        for (TaxRow t : taxes) {
            sb.append("<tr><td>").append(t.caId()).append("</td><td>").append(t.grossAmount())
              .append("</td><td>").append(t.taxRate()).append("</td><td>").append(t.taxAmount())
              .append("</td><td>").append(t.netPayable()).append("</td></tr>");
        }
        return sb.append("</table></body></html>").toString();
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private TaxCertificate persistCertificate(String caId, String clientId, String taxId,
                                               String key, LocalDate issuedDate,
                                               String issuedDateBs) throws SQLException {
        String certId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO ca_tax_certificates
                    (cert_id, ca_id, client_id, tax_id, storage_key, issued_date, issued_date_bs, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW()) ON CONFLICT DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, certId); ps.setString(2, caId); ps.setString(3, clientId);
            ps.setString(4, taxId); ps.setString(5, key); ps.setObject(6, issuedDate);
            ps.setString(7, issuedDateBs);
            ps.executeUpdate();
        }
        return new TaxCertificate(certId, caId, clientId, taxId, key, issuedDate, issuedDateBs, LocalDateTime.now());
    }

    record TaxRow(String taxId, String caId, String clientId, java.math.BigDecimal grossAmount,
                  java.math.BigDecimal taxRate, java.math.BigDecimal taxAmount,
                  java.math.BigDecimal netPayable, String currency) {}

    private List<TaxRow> loadTaxRecords(String caId) throws SQLException {
        String sql = "SELECT tax_id, ca_id, client_id, gross_amount, tax_rate, tax_amount, net_payable, 'NPR' AS currency FROM ca_tax_withholdings WHERE ca_id=?";
        List<TaxRow> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new TaxRow(rs.getString("tax_id"), rs.getString("ca_id"),
                        rs.getString("client_id"), rs.getBigDecimal("gross_amount"),
                        rs.getBigDecimal("tax_rate"), rs.getBigDecimal("tax_amount"),
                        rs.getBigDecimal("net_payable"), rs.getString("currency")));
            }
        }
        return result;
    }

    private List<TaxRow> loadClientAnnualTax(String clientId, int year) throws SQLException {
        String sql = """
                SELECT tw.tax_id, tw.ca_id, tw.client_id, tw.gross_amount, tw.tax_rate,
                       tw.tax_amount, tw.net_payable, 'NPR' AS currency
                FROM ca_tax_withholdings tw
                JOIN corporate_actions ca ON ca.ca_id = tw.ca_id
                WHERE tw.client_id=? AND EXTRACT(YEAR FROM ca.payment_date)=?
                """;
        List<TaxRow> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId); ps.setInt(2, year);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new TaxRow(rs.getString("tax_id"), rs.getString("ca_id"),
                        rs.getString("client_id"), rs.getBigDecimal("gross_amount"),
                        rs.getBigDecimal("tax_rate"), rs.getBigDecimal("tax_amount"),
                        rs.getBigDecimal("net_payable"), rs.getString("currency")));
            }
        }
        return result;
    }
}
