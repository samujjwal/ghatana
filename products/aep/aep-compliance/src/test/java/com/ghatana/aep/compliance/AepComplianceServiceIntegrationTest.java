/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void checkComplianceSucceedsWhenAllChecksPass() { // GH-90000
        AtomicBoolean brokerCalled = new AtomicBoolean(false); // GH-90000
        DataAccessBroker broker = (tenantId, subjectId, dataId, purpose) -> { // GH-90000
            brokerCalled.set(true); // GH-90000
            return Promise.complete(); // GH-90000
        };
        InMemoryRetentionPolicyEnforcer retention = new InMemoryRetentionPolicyEnforcer(); // GH-90000
        ComplianceService service = new ComplianceService(broker, retention); // GH-90000

        runPromise(() -> retention.registerRetention("tenant-a", "data-1", Duration.ofMinutes(5))); // GH-90000
        runPromise(() -> service.checkCompliance("tenant-a", "subject-1", "data-1", "analytics")); // GH-90000

        assertThat(brokerCalled).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("checkCompliance fails when retention has expired after access approval")
    void checkComplianceFailsWhenRetentionExpired() { // GH-90000
        AtomicBoolean brokerCalled = new AtomicBoolean(false); // GH-90000
        DataAccessBroker broker = (tenantId, subjectId, dataId, purpose) -> { // GH-90000
            brokerCalled.set(true); // GH-90000
            return Promise.complete(); // GH-90000
        };
        InMemoryRetentionPolicyEnforcer retention = new InMemoryRetentionPolicyEnforcer(); // GH-90000
        ComplianceService service = new ComplianceService(broker, retention); // GH-90000

        runPromise(() -> retention.registerRetention("tenant-a", "data-2", Duration.ZERO)); // GH-90000

        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> service.checkCompliance("tenant-a", "subject-2", "data-2", "analytics"))) // GH-90000
            .isInstanceOf(RetentionExpiredException.class); // GH-90000
        assertThat(brokerCalled).isTrue(); // GH-90000
    }
}
