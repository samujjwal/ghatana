package com.ghatana.appplatform.config.bundle;

import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.domain.ConfigSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConfigBundleExporter}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for air-gap config bundle export (K02-012)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConfigBundleExporter — Unit Tests")
class ConfigBundleExporterTest {

    @Mock DataSource dataSource;
    @Mock Connection connection;
    @Mock PreparedStatement schemaStmt;
    @Mock PreparedStatement entryStmt;
    @Mock ResultSet schemaRs;
    @Mock ResultSet entryRs;

    private ConfigBundleExporter exporter;

    @BeforeEach
    void setUp() throws Exception {
        exporter = new ConfigBundleExporter(dataSource, "production", "test-user");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(schemaStmt, entryStmt);
        when(schemaStmt.executeQuery()).thenReturn(schemaRs);
        when(entryStmt.executeQuery()).thenReturn(entryRs);
    }

    @Test
    @DisplayName("build() with one schema and one entry produces well-formed bundle")
    void buildProducesWellFormedBundle() throws Exception {
        // GIVEN — schema row
        when(schemaRs.next()).thenReturn(true, false);
        when(schemaRs.getString("namespace")).thenReturn("payments");
        when(schemaRs.getString("version")).thenReturn("1.0.0");
        when(schemaRs.getString("json_schema")).thenReturn("{\"type\":\"object\"}");
        when(schemaRs.getString("description")).thenReturn("Payment config");
        when(schemaRs.getString("defaults")).thenReturn("{}");

        // GIVEN — entry row
        when(entryRs.next()).thenReturn(true, false);
        when(entryRs.getString("namespace")).thenReturn("payments");
        when(entryRs.getString("key")).thenReturn("max-retries");
        when(entryRs.getString("value")).thenReturn("3");
        when(entryRs.getString("level")).thenReturn("TENANT");
        when(entryRs.getString("level_id")).thenReturn("tenant-abc");
        when(entryRs.getString("schema_namespace")).thenReturn("payments");

        // WHEN
        ConfigBundle bundle = exporter.build();

        // THEN
        assertThat(bundle.manifest().environment()).isEqualTo("production");
        assertThat(bundle.manifest().generatedBy()).isEqualTo("test-user");
        assertThat(bundle.manifest().formatVersion()).isEqualTo("1.0");
        assertThat(bundle.manifest().schemaCount()).isEqualTo(1);
        assertThat(bundle.manifest().entryCount()).isEqualTo(1);
        assertThat(bundle.manifest().contentHash()).hasSize(64);  // SHA-256 hex
        assertThat(bundle.manifest().isSigned()).isFalse();

        assertThat(bundle.schemas()).hasSize(1);
        assertThat(bundle.schemas().get(0).namespace()).isEqualTo("payments");

        assertThat(bundle.entries()).hasSize(1);
        assertThat(bundle.entries().get(0).key()).isEqualTo("max-retries");
        assertThat(bundle.entries().get(0).level()).isEqualTo(ConfigHierarchyLevel.TENANT);
    }

    @Test
    @DisplayName("build() with empty DB produces bundle with zero counts")
    void buildEmptyDb() throws Exception {
        when(schemaRs.next()).thenReturn(false);
        when(entryRs.next()).thenReturn(false);

        ConfigBundle bundle = exporter.build();

        assertThat(bundle.manifest().schemaCount()).isEqualTo(0);
        assertThat(bundle.manifest().entryCount()).isEqualTo(0);
        assertThat(bundle.schemas()).isEmpty();
        assertThat(bundle.entries()).isEmpty();
        assertThat(bundle.manifest().contentHash()).hasSize(64);
    }

    @Test
    @DisplayName("contentHash is stable across two build() calls with same data")
    void contentHashIsStable() throws Exception {
        // First call mock
        when(schemaRs.next()).thenReturn(true, false, true, false);
        when(schemaRs.getString("namespace")).thenReturn("payments");
        when(schemaRs.getString("version")).thenReturn("1.0.0");
        when(schemaRs.getString("json_schema")).thenReturn("{\"type\":\"object\"}");
        when(schemaRs.getString("description")).thenReturn("desc");
        when(schemaRs.getString("defaults")).thenReturn("{}");

        when(entryRs.next()).thenReturn(false, false);

        // Need separate PreparedStatement + ResultSet stubs for the second build() call
        PreparedStatement schemaStmt2 = mock(PreparedStatement.class);
        PreparedStatement entryStmt2  = mock(PreparedStatement.class);
        ResultSet         schemaRs2   = mock(ResultSet.class);
        ResultSet         entryRs2    = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
            .thenReturn(schemaStmt, entryStmt, schemaStmt2, entryStmt2);
        when(schemaStmt2.executeQuery()).thenReturn(schemaRs2);
        when(entryStmt2.executeQuery()).thenReturn(entryRs2);

        when(schemaRs2.next()).thenReturn(true, false);
        when(schemaRs2.getString("namespace")).thenReturn("payments");
        when(schemaRs2.getString("version")).thenReturn("1.0.0");
        when(schemaRs2.getString("json_schema")).thenReturn("{\"type\":\"object\"}");
        when(schemaRs2.getString("description")).thenReturn("desc");
        when(schemaRs2.getString("defaults")).thenReturn("{}");
        when(entryRs2.next()).thenReturn(false);

        ConfigBundle bundle1 = exporter.build();
        ConfigBundle bundle2 = exporter.build();

        assertThat(bundle1.manifest().contentHash())
            .isEqualTo(bundle2.manifest().contentHash());
    }

    @Test
    @DisplayName("write() produces valid gzip output that round-trips")
    void writeProducesValidGzip() throws Exception {
        when(schemaRs.next()).thenReturn(false);
        when(entryRs.next()).thenReturn(false);

        ConfigBundle bundle = exporter.build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exporter.write(bundle, baos);

        byte[] gzipBytes = baos.toByteArray();
        assertThat(gzipBytes).isNotEmpty();

        // Verify GZIP magic bytes
        assertThat(gzipBytes[0] & 0xFF).isEqualTo(0x1F);
        assertThat(gzipBytes[1] & 0xFF).isEqualTo(0x8B);

        // Verify decompressible
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(gzipBytes))) {
            byte[] raw = gz.readAllBytes();
            assertThat(raw).isNotEmpty();
            String json = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            assertThat(json).contains("\"manifest\"");
            assertThat(json).contains("\"schemas\"");
            assertThat(json).contains("\"entries\"");
        }
    }

    @Test
    @DisplayName("constructor rejects blank environment")
    void constructorRejectsBlankEnvironment() {
        assertThatThrownBy(() -> new ConfigBundleExporter(dataSource, "  ", "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("environment");
    }

    @Test
    @DisplayName("constructor rejects blank generatedBy")
    void constructorRejectsBlankGeneratedBy() {
        assertThatThrownBy(() -> new ConfigBundleExporter(dataSource, "env", "  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("generatedBy");
    }
}
