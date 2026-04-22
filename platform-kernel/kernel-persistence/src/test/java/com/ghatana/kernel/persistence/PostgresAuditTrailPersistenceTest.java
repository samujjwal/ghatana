package com.ghatana.kernel.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.DefaultAuditTrailService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @doc.type class
 * @doc.purpose Tests PostgreSQL-style audit persistence adapter using H2 in PostgreSQL mode
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PostgresAuditTrailPersistence [GH-90000]")
class PostgresAuditTrailPersistenceTest {

    private PostgresAuditTrailPersistence persistence;

    @BeforeEach
    void setUp() { // GH-90000
        JdbcDataSource dataSource = new JdbcDataSource(); // GH-90000
        dataSource.setURL("jdbc:h2:mem:audit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 [GH-90000]");
        dataSource.setUser("sa [GH-90000]");
        dataSource.setPassword("sa [GH-90000]");

        persistence = new PostgresAuditTrailPersistence(dataSource, new ObjectMapper()); // GH-90000
        persistence.ensureSchema(); // GH-90000
    }

    @Test
    @DisplayName("persists and loads audit events with payload and hash [GH-90000]")
    void persistsAndLoadsEvents() { // GH-90000
        AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder() // GH-90000
            .eventId("evt-1 [GH-90000]")
            .eventType("PATIENT_READ [GH-90000]")
            .entityId("patient-1 [GH-90000]")
            .userId("doctor-1 [GH-90000]")
            .tenantId("tenant-1 [GH-90000]")
            .action("READ [GH-90000]")
            .data(Map.of("resource", "Patient", "field", "allergies")) // GH-90000
            .timestamp(12345L) // GH-90000
            .previousHash("genesis [GH-90000]")
            .build(); // GH-90000

        persistence.persist(new DefaultAuditTrailService.StoredAuditEvent(event, "hash-1")); // GH-90000

        List<DefaultAuditTrailService.StoredAuditEvent> loaded = persistence.loadAll(); // GH-90000
        assertEquals(1, loaded.size()); // GH-90000
        assertEquals("evt-1", loaded.getFirst().event().getEventId()); // GH-90000
        assertEquals("hash-1", loaded.getFirst().hash()); // GH-90000
        assertFalse(loaded.getFirst().event().getData().isEmpty()); // GH-90000
    }
}
