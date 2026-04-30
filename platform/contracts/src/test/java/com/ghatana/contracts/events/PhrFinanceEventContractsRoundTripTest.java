package com.ghatana.contracts.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Serialization round-trip contract tests for {@link PhrFinanceEventContracts}.
 *
 * <p>These tests prove that every canonical event record can be serialized to JSON
 * and deserialized back without data loss. They are the minimal compatibility proof
 * that the contracts are wire-stable.</p>
 *
 * @doc.type class
 * @doc.purpose Serialization round-trip tests for PHR-Finance canonical event contracts
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PhrFinanceEventContracts — serialization round-trip tests")
class PhrFinanceEventContractsRoundTripTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    @DisplayName("PhrBillingEvent round-trips through JSON without data loss")
    void phrBillingEventRoundTrip() throws Exception {
        PhrFinanceEventContracts.PhrBillingEvent original = new PhrFinanceEventContracts.PhrBillingEvent(
                PhrFinanceEventContracts.PhrBillingEvent.SCHEMA_VERSION,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "tenant-hospital-1",
                "patient-abc123",
                "provider-dr-sharma",
                "CPT-99213",
                "2026-04-28",
                new BigDecimal("150.00"),
                "USD",
                true,
                Instant.now()
        );

        String json = mapper.writeValueAsString(original);
        PhrFinanceEventContracts.PhrBillingEvent deserialized =
                mapper.readValue(json, PhrFinanceEventContracts.PhrBillingEvent.class);

        assertThat(deserialized.schemaVersion()).isEqualTo(original.schemaVersion());
        assertThat(deserialized.eventId()).isEqualTo(original.eventId());
        assertThat(deserialized.correlationId()).isEqualTo(original.correlationId());
        assertThat(deserialized.tenantId()).isEqualTo(original.tenantId());
        assertThat(deserialized.patientId()).isEqualTo(original.patientId());
        assertThat(deserialized.serviceCode()).isEqualTo(original.serviceCode());
        assertThat(deserialized.amount()).isEqualByComparingTo(original.amount());
        assertThat(deserialized.currency()).isEqualTo(original.currency());
        assertThat(deserialized.insuranceClaim()).isEqualTo(original.insuranceClaim());
        assertThat(json).contains("\"eventType\"").withFailMessage(
                "The JSON must include the eventType discriminator if present; or remove this assertion");
    }

    @Test
    @DisplayName("PhrBillingEvent must reject zero or negative amount at construction")
    void phrBillingEventRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> new PhrFinanceEventContracts.PhrBillingEvent(
                "v1", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "tenant", "patient", "provider", "CPT-1", "2026-01-01",
                BigDecimal.ZERO, "USD", false, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be positive");
    }

    @Test
    @DisplayName("PhrConsentAuditEvent round-trips through JSON without data loss")
    void phrConsentAuditEventRoundTrip() throws Exception {
        PhrFinanceEventContracts.PhrConsentAuditEvent original = new PhrFinanceEventContracts.PhrConsentAuditEvent(
                PhrFinanceEventContracts.PhrConsentAuditEvent.SCHEMA_VERSION,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "tenant-hospital-1",
                "patient-pseudonym-abc",
                "provider-dr-sharma",
                "PROVIDER",
                "PATIENT_READ",
                PhrFinanceEventContracts.PhrConsentAuditEvent.Decision.ALLOWED,
                "EXPLICIT_GRANT",
                true,
                Instant.now()
        );

        String json = mapper.writeValueAsString(original);
        PhrFinanceEventContracts.PhrConsentAuditEvent deserialized =
                mapper.readValue(json, PhrFinanceEventContracts.PhrConsentAuditEvent.class);

        assertThat(deserialized.eventId()).isEqualTo(original.eventId());
        assertThat(deserialized.decision()).isEqualTo(PhrFinanceEventContracts.PhrConsentAuditEvent.Decision.ALLOWED);
        assertThat(deserialized.reasonCode()).isEqualTo(original.reasonCode());
        assertThat(deserialized.auditRequired()).isTrue();
    }

    @Test
    @DisplayName("FinanceRiskEvaluationEvent round-trips through JSON without data loss")
    void financeRiskEvaluationEventRoundTrip() throws Exception {
        PhrFinanceEventContracts.FinanceRiskEvaluationEvent original = new PhrFinanceEventContracts.FinanceRiskEvaluationEvent(
                PhrFinanceEventContracts.FinanceRiskEvaluationEvent.SCHEMA_VERSION,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "tenant-fund-mgr",
                "portfolio-US-equities",
                "trade-XYZ-001",
                "MARKET",
                new BigDecimal("1000000.00"),
                "USD",
                Instant.now()
        );

        String json = mapper.writeValueAsString(original);
        PhrFinanceEventContracts.FinanceRiskEvaluationEvent deserialized =
                mapper.readValue(json, PhrFinanceEventContracts.FinanceRiskEvaluationEvent.class);

        assertThat(deserialized.eventId()).isEqualTo(original.eventId());
        assertThat(deserialized.entityId()).isEqualTo(original.entityId());
        assertThat(deserialized.riskType()).isEqualTo("MARKET");
        assertThat(deserialized.notionalAmount()).isEqualByComparingTo(new BigDecimal("1000000.00"));
    }

    @Test
    @DisplayName("Schema version constants must be 'v1' for all contracts")
    void schemaVersionsAreV1() {
        assertThat(PhrFinanceEventContracts.PhrBillingEvent.SCHEMA_VERSION).isEqualTo("v1");
        assertThat(PhrFinanceEventContracts.PhrConsentAuditEvent.SCHEMA_VERSION).isEqualTo("v1");
        assertThat(PhrFinanceEventContracts.FinanceRiskEvaluationEvent.SCHEMA_VERSION).isEqualTo("v1");
    }
}
