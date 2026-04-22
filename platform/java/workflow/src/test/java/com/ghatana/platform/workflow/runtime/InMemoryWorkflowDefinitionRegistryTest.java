/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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

@DisplayName("InMemoryWorkflowDefinitionRegistry Tests [GH-90000]")
class InMemoryWorkflowDefinitionRegistryTest extends EventloopTestBase {

    private InMemoryWorkflowDefinitionRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new InMemoryWorkflowDefinitionRegistry(); // GH-90000
    }

    private WorkflowDefinition defV(String id, int version) { // GH-90000
        return WorkflowDefinition.builder(id, "WF-" + id) // GH-90000
            .version(version) // GH-90000
            .addStep(WorkflowStepDefinition.action("s1", "Step", "op")) // GH-90000
            .build(); // GH-90000
    }

    @Test
    void shouldRegisterAndFindLatest() { // GH-90000
        runPromise(() -> registry.register(defV("wf-1", 1)) // GH-90000
            .then(v -> registry.findLatest("wf-1 [GH-90000]"))
            .whenResult(opt -> { // GH-90000
                assertThat(opt).isPresent(); // GH-90000
                assertThat(opt.get().version()).isEqualTo(1); // GH-90000
            }));
    }

    @Test
    void shouldFindLatestVersion() { // GH-90000
        runPromise(() -> registry.register(defV("wf-1", 1)) // GH-90000
            .then(v -> registry.register(defV("wf-1", 2))) // GH-90000
            .then(v -> registry.register(defV("wf-1", 3))) // GH-90000
            .then(v -> registry.findLatest("wf-1 [GH-90000]"))
            .whenResult(opt -> assertThat(opt.get().version()).isEqualTo(3))); // GH-90000
    }

    @Test
    void shouldFindBySpecificVersion() { // GH-90000
        runPromise(() -> registry.register(defV("wf-1", 1)) // GH-90000
            .then(v -> registry.register(defV("wf-1", 2))) // GH-90000
            .then(v -> registry.findByVersion("wf-1", 1)) // GH-90000
            .whenResult(opt -> assertThat(opt.get().version()).isEqualTo(1))); // GH-90000
    }

    @Test
    void shouldReturnEmptyForMissing() { // GH-90000
        Optional<WorkflowDefinition> result = runPromise(() -> registry.findLatest("nope [GH-90000]"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    void shouldListAllLatest() { // GH-90000
        runPromise(() -> registry.register(defV("wf-1", 1)) // GH-90000
            .then(v -> registry.register(defV("wf-1", 2))) // GH-90000
            .then(v -> registry.register(defV("wf-2", 1)))); // GH-90000

        List<WorkflowDefinition> all = runPromise(() -> registry.listAll()); // GH-90000
        assertThat(all).hasSize(2); // GH-90000
    }

    @Test
    void shouldRemove() { // GH-90000
        runPromise(() -> registry.register(defV("wf-1", 1)) // GH-90000
            .then(v -> registry.remove("wf-1 [GH-90000]")));

        Optional<WorkflowDefinition> result = runPromise(() -> registry.findLatest("wf-1 [GH-90000]"));
        assertThat(result).isEmpty(); // GH-90000
        assertThat(registry.size()).isZero(); // GH-90000
    }

    @Test
    void shouldTrackSizeAndClear() { // GH-90000
        runPromise(() -> registry.register(defV("wf-1", 1)) // GH-90000
            .then(v -> registry.register(defV("wf-2", 1)))); // GH-90000

        assertThat(registry.size()).isEqualTo(2); // GH-90000

        registry.clear(); // GH-90000
        assertThat(registry.size()).isZero(); // GH-90000
    }
}
