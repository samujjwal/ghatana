/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("InMemoryWorkflowDefinitionRegistry Tests")
class InMemoryWorkflowDefinitionRegistryTest extends EventloopTestBase {

    private InMemoryWorkflowDefinitionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryWorkflowDefinitionRegistry();
    }

    private WorkflowDefinition defV(String id, int version) {
        return WorkflowDefinition.builder(id, "WF-" + id)
            .version(version)
            .addStep(WorkflowStepDefinition.action("s1", "Step", "op"))
            .build();
    }

    @Test
    void shouldRegisterAndFindLatest() {
        runPromise(() -> registry.register(defV("wf-1", 1))
            .then(v -> registry.findLatest("wf-1"))
            .whenResult(opt -> {
                assertThat(opt).isPresent();
                assertThat(opt.get().version()).isEqualTo(1);
            }));
    }

    @Test
    void shouldFindLatestVersion() {
        runPromise(() -> registry.register(defV("wf-1", 1))
            .then(v -> registry.register(defV("wf-1", 2)))
            .then(v -> registry.register(defV("wf-1", 3)))
            .then(v -> registry.findLatest("wf-1"))
            .whenResult(opt -> assertThat(opt.get().version()).isEqualTo(3)));
    }

    @Test
    void shouldFindBySpecificVersion() {
        runPromise(() -> registry.register(defV("wf-1", 1))
            .then(v -> registry.register(defV("wf-1", 2)))
            .then(v -> registry.findByVersion("wf-1", 1))
            .whenResult(opt -> assertThat(opt.get().version()).isEqualTo(1)));
    }

    @Test
    void shouldReturnEmptyForMissing() {
        Optional<WorkflowDefinition> result = runPromise(() -> registry.findLatest("nope"));
        assertThat(result).isEmpty();
    }

    @Test
    void shouldListAllLatest() {
        runPromise(() -> registry.register(defV("wf-1", 1))
            .then(v -> registry.register(defV("wf-1", 2)))
            .then(v -> registry.register(defV("wf-2", 1))));

        List<WorkflowDefinition> all = runPromise(() -> registry.listAll());
        assertThat(all).hasSize(2);
    }

    @Test
    void shouldRemove() {
        runPromise(() -> registry.register(defV("wf-1", 1))
            .then(v -> registry.remove("wf-1")));

        Optional<WorkflowDefinition> result = runPromise(() -> registry.findLatest("wf-1"));
        assertThat(result).isEmpty();
        assertThat(registry.size()).isZero();
    }

    @Test
    void shouldTrackSizeAndClear() {
        runPromise(() -> registry.register(defV("wf-1", 1))
            .then(v -> registry.register(defV("wf-2", 1))));

        assertThat(registry.size()).isEqualTo(2);

        registry.clear();
        assertThat(registry.size()).isZero();
    }
}
