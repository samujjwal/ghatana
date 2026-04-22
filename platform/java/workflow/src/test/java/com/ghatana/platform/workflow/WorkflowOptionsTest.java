/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WorkflowOptions Tests [GH-90000]")
class WorkflowOptionsTest {

    @Test
    void shouldCreateEphemeralDefaults() { // GH-90000
        WorkflowOptions opts = WorkflowOptions.ephemeral(); // GH-90000

        assertThat(opts.kind()).isEqualTo(WorkflowKind.EPHEMERAL); // GH-90000
        assertThat(opts.timeout()).isEqualTo(Duration.ofMinutes(5)); // GH-90000
        assertThat(opts.maxRetries()).isZero(); // GH-90000
        assertThat(opts.sagaPolicy()).isEqualTo(SagaPolicy.NONE); // GH-90000
    }

    @Test
    void shouldCreateDurableDefaults() { // GH-90000
        WorkflowOptions opts = WorkflowOptions.durable(); // GH-90000

        assertThat(opts.kind()).isEqualTo(WorkflowKind.DURABLE); // GH-90000
        assertThat(opts.timeout()).isNull(); // GH-90000
        assertThat(opts.maxRetries()).isEqualTo(3); // GH-90000
        assertThat(opts.sagaPolicy()).isEqualTo(SagaPolicy.BACKWARD_COMPENSATION); // GH-90000
    }

    @Test
    void shouldCreateCustomOptions() { // GH-90000
        WorkflowOptions opts = new WorkflowOptions( // GH-90000
            WorkflowKind.DURABLE, Duration.ofHours(1), 10, SagaPolicy.FORWARD_RECOVERY); // GH-90000

        assertThat(opts.kind()).isEqualTo(WorkflowKind.DURABLE); // GH-90000
        assertThat(opts.timeout()).isEqualTo(Duration.ofHours(1)); // GH-90000
        assertThat(opts.maxRetries()).isEqualTo(10); // GH-90000
        assertThat(opts.sagaPolicy()).isEqualTo(SagaPolicy.FORWARD_RECOVERY); // GH-90000
    }

    @Test
    void shouldCopyWithTimeout() { // GH-90000
        WorkflowOptions base = WorkflowOptions.ephemeral(); // GH-90000
        WorkflowOptions custom = base.withTimeout(Duration.ofMinutes(15)); // GH-90000

        assertThat(custom.timeout()).isEqualTo(Duration.ofMinutes(15)); // GH-90000
        assertThat(custom.kind()).isEqualTo(base.kind()); // GH-90000
    }

    @Test
    void shouldCopyWithMaxRetries() { // GH-90000
        WorkflowOptions base = WorkflowOptions.ephemeral(); // GH-90000
        WorkflowOptions custom = base.withMaxRetries(7); // GH-90000

        assertThat(custom.maxRetries()).isEqualTo(7); // GH-90000
        assertThat(custom.kind()).isEqualTo(base.kind()); // GH-90000
    }

    @Test
    void shouldCopyWithSagaPolicy() { // GH-90000
        WorkflowOptions base = WorkflowOptions.ephemeral(); // GH-90000
        WorkflowOptions custom = base.withSagaPolicy(SagaPolicy.BACKWARD_COMPENSATION); // GH-90000

        assertThat(custom.sagaPolicy()).isEqualTo(SagaPolicy.BACKWARD_COMPENSATION); // GH-90000
        assertThat(custom.kind()).isEqualTo(base.kind()); // GH-90000
    }

    @Test
    void shouldRejectNullKind() { // GH-90000
        assertThatThrownBy(() -> new WorkflowOptions(null, Duration.ZERO, 0, SagaPolicy.NONE)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    void shouldAllowNullTimeout() { // GH-90000
        WorkflowOptions opts = new WorkflowOptions(WorkflowKind.EPHEMERAL, null, 0, SagaPolicy.NONE); // GH-90000
        assertThat(opts.timeout()).isNull(); // GH-90000
    }
}
