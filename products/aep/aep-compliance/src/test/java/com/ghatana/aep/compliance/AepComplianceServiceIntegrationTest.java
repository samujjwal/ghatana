/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import com.ghatana.data.governance.DataAccessBroker;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration-style tests for the AEP compliance orchestrator.
 *
 * @doc.type class
 * @doc.purpose Verify ComplianceService coordinates access brokerage and retention enforcement
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("AepComplianceServiceIntegrationTest")
class AepComplianceServiceIntegrationTest extends EventloopTestBase {

    @Test
    @DisplayName("checkCompliance succeeds when broker and retention checks both pass")
    void checkComplianceSucceedsWhenAllChecksPass() {
        AtomicBoolean brokerCalled = new AtomicBoolean(false);
        DataAccessBroker broker = (tenantId, subjectId, dataId, purpose) -> {
            brokerCalled.set(true);
            return Promise.complete();
        };
        InMemoryRetentionPolicyEnforcer retention = new InMemoryRetentionPolicyEnforcer();
        ComplianceService service = new ComplianceService(broker, retention);

        runPromise(() -> retention.registerRetention("tenant-a", "data-1", Duration.ofMinutes(5)));
        runPromise(() -> service.checkCompliance("tenant-a", "subject-1", "data-1", "analytics"));

        assertThat(brokerCalled).isTrue();
    }

    @Test
    @DisplayName("checkCompliance fails when retention has expired after access approval")
    void checkComplianceFailsWhenRetentionExpired() {
        AtomicBoolean brokerCalled = new AtomicBoolean(false);
        DataAccessBroker broker = (tenantId, subjectId, dataId, purpose) -> {
            brokerCalled.set(true);
            return Promise.complete();
        };
        InMemoryRetentionPolicyEnforcer retention = new InMemoryRetentionPolicyEnforcer();
        ComplianceService service = new ComplianceService(broker, retention);

        runPromise(() -> retention.registerRetention("tenant-a", "data-2", Duration.ZERO));

        assertThatThrownBy(() ->
            runPromise(() -> service.checkCompliance("tenant-a", "subject-2", "data-2", "analytics")))
            .isInstanceOf(RetentionExpiredException.class);
        assertThat(brokerCalled).isTrue();
    }
}
