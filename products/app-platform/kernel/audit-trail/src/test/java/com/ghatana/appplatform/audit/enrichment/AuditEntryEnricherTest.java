package com.ghatana.appplatform.audit.enrichment;

import com.ghatana.appplatform.audit.domain.AuditEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuditEntryEnricher Tests")
class AuditEntryEnricherTest {

    private final AuditEntryEnricher enricher = new AuditEntryEnricher();

    private AuditEntry minimal() {
        return AuditEntry.builder()
                .action("TEST_ACTION")
                .actor(AuditEntry.Actor.of("user-1", "admin"))
                .resource(AuditEntry.Resource.of("Account", "acc-1"))
                .outcome(AuditEntry.Outcome.SUCCESS)
                .tenantId("tenant-1")
                .build();
    }

    @Test
    void shouldPreserveId() {
        AuditEntry input = minimal();
        AuditEntry enriched = enricher.enrich(input);
        assertThat(enriched.id()).isEqualTo(input.id());
    }

    @Test
    void shouldDefaultTimestampGregorianToNowWhenNull() {
        // AuditEntry auto-sets a timestamp in the constructor, so we verify it's non-null
        Instant before = Instant.now().minusSeconds(1);
        AuditEntry enriched = enricher.enrich(minimal());
        assertThat(enriched.timestampGregorian()).isAfter(before);
    }

    @Test
    void shouldPreserveExistingTimestampGregorian() {
        Instant fixed = Instant.parse("2026-03-13T00:00:00Z");
        AuditEntry input = AuditEntry.builder()
                .action("TEST_ACTION")
                .actor(AuditEntry.Actor.of("user-1", "admin"))
                .resource(AuditEntry.Resource.of("Account", "acc-1"))
                .outcome(AuditEntry.Outcome.SUCCESS)
                .tenantId("tenant-1")
                .timestampGregorian(fixed)
                .build();

        AuditEntry enriched = enricher.enrich(input);

        assertThat(enriched.timestampGregorian()).isEqualTo(fixed);
    }

    @Test
    void shouldSetTimestampBsToEmptyInSprint1DegradationMode() {
        AuditEntry enriched = enricher.enrich(minimal());
        assertThat(enriched.timestampBs()).isEqualTo("");
    }

    @Test
    void shouldPreserveAllOtherFields() {
        AuditEntry input = AuditEntry.builder()
                .action("TRANSFER")
                .actor(AuditEntry.Actor.of("user-99", "teller"))
                .resource(AuditEntry.Resource.of("Transaction", "txn-42"))
                .outcome(AuditEntry.Outcome.FAILURE)
                .tenantId("tenant-finance")
                .traceId("trace-abc")
                .build();

        AuditEntry enriched = enricher.enrich(input);

        assertThat(enriched.action()).isEqualTo("TRANSFER");
        assertThat(enriched.actor().userId()).isEqualTo("user-99");
        assertThat(enriched.resource().type()).isEqualTo("Transaction");
        assertThat(enriched.outcome()).isEqualTo(AuditEntry.Outcome.FAILURE);
        assertThat(enriched.tenantId()).isEqualTo("tenant-finance");
        assertThat(enriched.traceId()).isEqualTo("trace-abc");
    }

    @Test
    void shouldThrowWhenEntryIsNull() {
        assertThatThrownBy(() -> enricher.enrich(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void shouldAllowSubclassToOverrideCalendarResolution() {
        AuditEntryEnricher withK15 = new AuditEntryEnricher() {
            @Override
            protected String resolveCalendarDate(java.time.LocalDate gregorianDate) {
                // Stub K-15: 2026-03-13 → BS 2082-11-29
                return "2082-11-29";
            }
        };

        Instant fixed = Instant.parse("2026-03-13T00:00:00Z");
        AuditEntry input = AuditEntry.builder()
                .action("TEST")
                .actor(AuditEntry.Actor.of("u", "r"))
                .resource(AuditEntry.Resource.of("X", "1"))
                .outcome(AuditEntry.Outcome.SUCCESS)
                .tenantId("t")
                .timestampGregorian(fixed)
                .build();

        AuditEntry enriched = withK15.enrich(input);

        assertThat(enriched.timestampBs()).isEqualTo("2082-11-29");
    }
}
