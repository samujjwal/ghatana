package com.ghatana.appplatform.audit.export;

import com.ghatana.appplatform.audit.chain.HashChainService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditEvidencePdfGenerator}.
 *
 * <p>Mocks the JDBC layer to avoid requiring a live database. Verifies that the
 * generated output is a valid, loadable PDF with the expected page structure.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AuditEvidencePdfGenerator PDF structure and correctness
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditEvidencePdfGenerator — Unit Tests")
class AuditEvidencePdfGeneratorTest {

    @Mock DataSource dataSource;
    @Mock Connection connection;
    @Mock PreparedStatement preparedStatement;
    @Mock ResultSet resultSet;

    private static final Instant FROM = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant TO   = Instant.parse("2025-01-31T23:59:59Z");

    @Test
    @DisplayName("generate — empty result set produces valid 3-page PDF")
    void generate_emptyResultSet_produces3PagePdf() throws Exception {
        givenJdbcReturns(/* no rows */ false);
        AuditEvidencePdfGenerator gen = new AuditEvidencePdfGenerator(dataSource, new HashChainService());

        byte[] pdf = generatePdf(gen);

        assertThat(pdf).startsWith('%', 'P', 'D', 'F');
        try (PDDocument doc = PDDocument.load(pdf)) {
            // Cover + empty data placeholder + hash summary = 3 pages
            assertThat(doc.getNumberOfPages()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("generate — 3 rows fit on a single data page, total 3 pages")
    void generate_threeRows_singleDataPage() throws Exception {
        givenJdbcReturns(true, true, true, false);
        AuditEvidencePdfGenerator gen = new AuditEvidencePdfGenerator(dataSource, new HashChainService());

        byte[] pdf = generatePdf(gen);

        try (PDDocument doc = PDDocument.load(pdf)) {
            // Cover + 1 data page + hash summary = 3 pages
            assertThat(doc.getNumberOfPages()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("generate — PDF metadata title includes tenant ID")
    void generate_pdfMetadata_containsTenantId() throws Exception {
        givenJdbcReturns(false);
        AuditEvidencePdfGenerator gen = new AuditEvidencePdfGenerator(dataSource, new HashChainService());

        byte[] pdf = generatePdf(gen);

        try (PDDocument doc = PDDocument.load(pdf)) {
            assertThat(doc.getDocumentInformation().getTitle())
                .contains("tenant-abc");
        }
    }

    @Test
    @DisplayName("generate — PDF metadata author is the requestedBy value")
    void generate_pdfMetadata_authorMatchesRequestedBy() throws Exception {
        givenJdbcReturns(false);
        AuditEvidencePdfGenerator gen = new AuditEvidencePdfGenerator(dataSource, new HashChainService());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        gen.generate("tenant-abc", FROM, TO, "auditor@ghatana.com", "Compliance review", baos);

        try (PDDocument doc = PDDocument.load(baos.toByteArray())) {
            assertThat(doc.getDocumentInformation().getAuthor())
                .isEqualTo("auditor@ghatana.com");
        }
    }

    @Test
    @DisplayName("generate — row limit breach throws IllegalStateException")
    void generate_exceedsRowLimit_throwsException() throws Exception {
        // Make ResultSet return rows past the limit
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // Simulate always returning true (infinite rows)
        when(resultSet.next()).thenReturn(true);
        stubRow(resultSet, 999L);

        AuditEvidencePdfGenerator gen = new AuditEvidencePdfGenerator(dataSource, new HashChainService());

        assertThatThrownBy(() -> generatePdf(gen))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("row limit");
    }

    @Test
    @DisplayName("generate — output stream is written with non-trivial content")
    void generate_outputStreamHasContent() throws Exception {
        givenJdbcReturns(true, false);
        AuditEvidencePdfGenerator gen = new AuditEvidencePdfGenerator(dataSource, new HashChainService());

        byte[] pdf = generatePdf(gen);

        // PDF should be at least 10 KB for a real multi-page document
        assertThat(pdf.length).isGreaterThan(10_000);
    }

    @Test
    @DisplayName("constant MAX_EXPORT_ROWS is 50000")
    void constant_maxExportRows() {
        assertThat(AuditEvidencePdfGenerator.MAX_EXPORT_ROWS).isEqualTo(50_000);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Configures JDBC mocks to return rows where each boolean in {@code rowHasNext}
     * is the return value of the successive {@code ResultSet.next()} calls.
     */
    private void givenJdbcReturns(boolean... rowHasNext) throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        Boolean[] args = new Boolean[rowHasNext.length];
        for (int i = 0; i < rowHasNext.length; i++) args[i] = rowHasNext[i];

        if (args.length == 1) {
            when(resultSet.next()).thenReturn(args[0]);
        } else if (args.length >= 2) {
            Boolean[] rest = new Boolean[args.length - 2];
            System.arraycopy(args, 2, rest, 0, rest.length);
            when(resultSet.next()).thenReturn(args[0], args[1], rest);
        }

        stubRow(resultSet, 1L);
    }

    private static void stubRow(ResultSet rs, long seqNum) throws Exception {
        when(rs.getLong("sequence_number")).thenReturn(seqNum);
        when(rs.getTimestamp("timestamp_gregorian"))
            .thenReturn(Timestamp.from(Instant.parse("2025-01-15T09:30:00Z")));
        when(rs.getString("timestamp_bs")).thenReturn("2081-10-01");
        when(rs.getString("action")).thenReturn("ORDER_CREATED");
        when(rs.getString("outcome")).thenReturn("SUCCESS");
        when(rs.getString("current_hash")).thenReturn(
            "a3f1c2e4b5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0a1b2");
        when(rs.getString("actor"))
            .thenReturn("{\"user_id\":\"u-001\",\"role\":\"ADMIN\"}");
        when(rs.getString("resource"))
            .thenReturn("{\"type\":\"Order\",\"id\":\"ord-123\"}");
    }

    private byte[] generatePdf(AuditEvidencePdfGenerator gen) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        gen.generate("tenant-abc", FROM, TO, "tester", "Unit test run", baos);
        return baos.toByteArray();
    }
}
