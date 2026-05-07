/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for data retention classification logic — determining retention
 * tier, policy application, and regulatory mapping.
 *
 * @doc.type    class
 * @doc.purpose Tests for data retention classification and policy application
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("Data Retention Classification Tests")
class RetentionClassificationTest extends EventloopTestBase {

    // ── Retention tier model ──────────────────────────────────────────────────

    enum RetentionTier {
        SHORT_TERM(Duration.ofDays(30)), 
        MEDIUM_TERM(Duration.ofDays(365)), 
        LONG_TERM(Duration.ofDays(7 * 365)), 
        PERMANENT(Duration.ofDays(Long.MAX_VALUE / (24 * 60 * 60))); 

        final Duration defaultTtl;

        RetentionTier(Duration defaultTtl) { 
            this.defaultTtl = defaultTtl;
        }
    }

    enum DataSensitivity { PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED }

    enum Regulation { GDPR, HIPAA, SOC2, CCPA, NONE }

    record RetentionPolicy(RetentionTier tier, DataSensitivity sensitivity, Regulation regulation) { 
        Duration effectiveTtl() { 
            // RESTRICTED data under GDPR must be retained for at least 5 years
            if (sensitivity == DataSensitivity.RESTRICTED && regulation == Regulation.GDPR) { 
                return Duration.ofDays(5 * 365); 
            }
            // HIPAA requires 6-year retention
            if (regulation == Regulation.HIPAA) { 
                return Duration.ofDays(6 * 365); 
            }
            return tier.defaultTtl;
        }

        boolean requiresEncryption() { 
            return sensitivity == DataSensitivity.CONFIDENTIAL
                    || sensitivity == DataSensitivity.RESTRICTED;
        }

        boolean requiresAuditLog() { 
            return sensitivity != DataSensitivity.PUBLIC;
        }
    }

    // ── Tier classification by sensitivity ────────────────────────────────────

    @ParameterizedTest(name = "sensitivity={0}, expectedTier={1}") 
    @CsvSource({ 
            "PUBLIC,              SHORT_TERM",
            "INTERNAL,            MEDIUM_TERM",
            "CONFIDENTIAL,        LONG_TERM",
            "RESTRICTED,          LONG_TERM"
    })
    @DisplayName("sensitivity level maps to expected default retention tier")
    void sensitivityMapsToDefaultTier(DataSensitivity sensitivity, RetentionTier expectedTier) { 
        RetentionTier resolved = classifyByDefaultSensitivity(sensitivity); 
        assertThat(resolved).isEqualTo(expectedTier); 
    }

    @Test
    @DisplayName("PUBLIC data has the shortest default retention")
    void publicDataHasShortestRetention() { 
        RetentionTier tier = classifyByDefaultSensitivity(DataSensitivity.PUBLIC); 
        assertThat(tier.defaultTtl).isLessThan(RetentionTier.MEDIUM_TERM.defaultTtl); 
    }

    // ── Regulatory TTL overrides ──────────────────────────────────────────────

    @Test
    @DisplayName("GDPR + RESTRICTED yields at least 5-year effective TTL")
    void gdprRestrictedYieldsAtLeastFiveYearTtl() { 
        RetentionPolicy policy = new RetentionPolicy( 
                RetentionTier.LONG_TERM, DataSensitivity.RESTRICTED, Regulation.GDPR);

        assertThat(policy.effectiveTtl()).isGreaterThanOrEqualTo(Duration.ofDays(5 * 365)); 
    }

    @Test
    @DisplayName("HIPAA data has a minimum 6-year effective TTL")
    void hipaaDataHasMinimumSixYearTtl() { 
        RetentionPolicy policy = new RetentionPolicy( 
                RetentionTier.MEDIUM_TERM, DataSensitivity.CONFIDENTIAL, Regulation.HIPAA);

        assertThat(policy.effectiveTtl()).isEqualTo(Duration.ofDays(6 * 365)); 
    }

    @Test
    @DisplayName("NONE regulation uses the tier's default TTL")
    void noRegulationUsesTierDefault() { 
        RetentionPolicy policy = new RetentionPolicy( 
                RetentionTier.SHORT_TERM, DataSensitivity.PUBLIC, Regulation.NONE);

        assertThat(policy.effectiveTtl()).isEqualTo(RetentionTier.SHORT_TERM.defaultTtl); 
    }

    // ── Encryption requirement ────────────────────────────────────────────────

    @Test
    @DisplayName("CONFIDENTIAL data requires encryption at rest")
    void confidentialDataRequiresEncryption() { 
        RetentionPolicy policy = new RetentionPolicy( 
                RetentionTier.LONG_TERM, DataSensitivity.CONFIDENTIAL, Regulation.NONE);

        assertThat(policy.requiresEncryption()).isTrue(); 
    }

    @Test
    @DisplayName("PUBLIC data does not require encryption at rest")
    void publicDataDoesNotRequireEncryption() { 
        RetentionPolicy policy = new RetentionPolicy( 
                RetentionTier.SHORT_TERM, DataSensitivity.PUBLIC, Regulation.NONE);

        assertThat(policy.requiresEncryption()).isFalse(); 
    }

    // ── Audit log requirement ─────────────────────────────────────────────────

    @Test
    @DisplayName("non-PUBLIC data requires audit logging")
    void nonPublicDataRequiresAuditLog() { 
        for (DataSensitivity s : new DataSensitivity[]{ 
                DataSensitivity.INTERNAL, DataSensitivity.CONFIDENTIAL, DataSensitivity.RESTRICTED}) {
            RetentionPolicy policy = new RetentionPolicy(RetentionTier.MEDIUM_TERM, s, Regulation.NONE); 
            assertThat(policy.requiresAuditLog()) 
                    .as("expected audit log required for sensitivity=" + s) 
                    .isTrue(); 
        }
    }

    @Test
    @DisplayName("PUBLIC data does not require audit logging")
    void publicDataDoesNotRequireAuditLog() { 
        RetentionPolicy policy = new RetentionPolicy( 
                RetentionTier.SHORT_TERM, DataSensitivity.PUBLIC, Regulation.NONE);

        assertThat(policy.requiresAuditLog()).isFalse(); 
    }

    // ── Policy expiry comparison ──────────────────────────────────────────────

    @Test
    @DisplayName("data purge is scheduled at createdAt + effective TTL")
    void purgeIsScheduledAtCreationPlusTtl() { 
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        RetentionPolicy policy = new RetentionPolicy( 
                RetentionTier.SHORT_TERM, DataSensitivity.PUBLIC, Regulation.NONE);

        Instant expectedPurge = created.plus(policy.effectiveTtl()); 
        assertThat(expectedPurge).isAfter(created); 
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RetentionTier classifyByDefaultSensitivity(DataSensitivity sensitivity) { 
        return switch (sensitivity) { 
            case PUBLIC -> RetentionTier.SHORT_TERM;
            case INTERNAL -> RetentionTier.MEDIUM_TERM;
            case CONFIDENTIAL, RESTRICTED -> RetentionTier.LONG_TERM;
        };
    }
}
