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
@DisplayName("PostgresAuditTrailPersistence")
class PostgresAuditTrailPersistenceTest {

    private PostgresAuditTrailPersistence persistence;

    @BeforeEach
    void setUp() { 
        JdbcDataSource dataSource = new JdbcDataSource(); 
        dataSource.setURL("jdbc:h2:mem:audit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");

        persistence = new PostgresAuditTrailPersistence(dataSource, new ObjectMapper()); 
        persistence.ensureSchema(); 
    }

    @Test
    @DisplayName("persists and loads audit events with payload and hash")
    void persistsAndLoadsEvents() { 
        AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder() 
            .eventId("evt-1")
            .eventType("PATIENT_READ")
            .entityId("patient-1")
            .userId("doctor-1")
            .tenantId("tenant-1")
            .action("READ")
            .data(Map.of("resource", "Patient", "field", "allergies"))
            .timestamp(12345L)
            .previousHash("genesis")
            .build(); 

        persistence.persist(new DefaultAuditTrailService.StoredAuditEvent(event, "hash-1")); 

        List<DefaultAuditTrailService.StoredAuditEvent> loaded = persistence.loadAll(); 
        assertEquals(1, loaded.size()); 
        assertEquals("evt-1", loaded.getFirst().event().getEventId()); 
        assertEquals("hash-1", loaded.getFirst().hash()); 
        assertFalse(loaded.getFirst().event().getData().isEmpty()); 
    }
}
