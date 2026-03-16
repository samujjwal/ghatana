/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WorkflowOptions Tests")
class WorkflowOptionsTest {

    @Test
    void shouldCreateEphemeralDefaults() {
        WorkflowOptions opts = WorkflowOptions.ephemeral();

        assertThat(opts.kind()).isEqualTo(WorkflowKind.EPHEMERAL);
        assertThat(opts.timeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(opts.maxRetries()).isZero();
        assertThat(opts.sagaPolicy()).isEqualTo(SagaPolicy.NONE);
    }

    @Test
    void shouldCreateDurableDefaults() {
        WorkflowOptions opts = WorkflowOptions.durable();

        assertThat(opts.kind()).isEqualTo(WorkflowKind.DURABLE);
        assertThat(opts.timeout()).isNull();
        assertThat(opts.maxRetries()).isEqualTo(3);
        assertThat(opts.sagaPolicy()).isEqualTo(SagaPolicy.BACKWARD_COMPENSATION);
    }

    @Test
    void shouldCreateCustomOptions() {
        WorkflowOptions opts = new WorkflowOptions(
            WorkflowKind.DURABLE, Duration.ofHours(1), 10, SagaPolicy.FORWARD_RECOVERY);

        assertThat(opts.kind()).isEqualTo(WorkflowKind.DURABLE);
        assertThat(opts.timeout()).isEqualTo(Duration.ofHours(1));
        assertThat(opts.maxRetries()).isEqualTo(10);
        assertThat(opts.sagaPolicy()).isEqualTo(SagaPolicy.FORWARD_RECOVERY);
    }

    @Test
    void shouldCopyWithTimeout() {
        WorkflowOptions base = WorkflowOptions.ephemeral();
        WorkflowOptions custom = base.withTimeout(Duration.ofMinutes(15));

        assertThat(custom.timeout()).isEqualTo(Duration.ofMinutes(15));
        assertThat(custom.kind()).isEqualTo(base.kind());
    }

    @Test
    void shouldCopyWithMaxRetries() {
        WorkflowOptions base = WorkflowOptions.ephemeral();
        WorkflowOptions custom = base.withMaxRetries(7);

        assertThat(custom.maxRetries()).isEqualTo(7);
        assertThat(custom.kind()).isEqualTo(base.kind());
    }

    @Test
    void shouldCopyWithSagaPolicy() {
        WorkflowOptions base = WorkflowOptions.ephemeral();
        WorkflowOptions custom = base.withSagaPolicy(SagaPolicy.BACKWARD_COMPENSATION);

        assertThat(custom.sagaPolicy()).isEqualTo(SagaPolicy.BACKWARD_COMPENSATION);
        assertThat(custom.kind()).isEqualTo(base.kind());
    }

    @Test
    void shouldRejectNullKind() {
        assertThatThrownBy(() -> new WorkflowOptions(null, Duration.ZERO, 0, SagaPolicy.NONE))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldAllowNullTimeout() {
        WorkflowOptions opts = new WorkflowOptions(WorkflowKind.EPHEMERAL, null, 0, SagaPolicy.NONE);
        assertThat(opts.timeout()).isNull();
    }
}
