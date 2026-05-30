/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.registry;

import com.ghatana.aep.pattern.lifecycle.PatternLifecycleEvent;
import com.ghatana.aep.pattern.lifecycle.PatternLifecycleEventType;
import com.ghatana.aep.pattern.lifecycle.PatternLifecycleState;
import com.ghatana.aep.pattern.lifecycle.PatternLifecycleTransition;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DataCloudPatternLifecycleRepository}.
 */
@DisplayName("DataCloudPatternLifecycleRepository")
class DataCloudPatternLifecycleRepositoryTest extends EventloopTestBase {

    private EntityManagerFactory emf;
    private EntityManager em;
    private DataCloudPatternLifecycleRepository repository;

    @BeforeEach
    void setUp() {
        // Use H2 in-memory database for testing
        emf = Persistence.createEntityManagerFactory("test-persistence-unit");
        em = emf.createEntityManager();
        repository = new DataCloudPatternLifecycleRepository(em);
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @Test
    void shouldSaveAndRetrieveState() {
        // WHEN
        runPromise(() -> repository.saveState("tenant-1", "pattern-1", PatternLifecycleState.DRAFT));

        // THEN
        Optional<PatternLifecycleState> state = runPromise(() -> repository.getState("tenant-1", "pattern-1"));
        assertThat(state).isPresent();
        assertThat(state.get()).isEqualTo(PatternLifecycleState.DRAFT);
    }

    @Test
    void shouldUpdateExistingState() {
        // GIVEN
        runPromise(() -> repository.saveState("tenant-1", "pattern-1", PatternLifecycleState.DRAFT));

        // WHEN
        runPromise(() -> repository.saveState("tenant-1", "pattern-1", PatternLifecycleState.VALIDATED));

        // THEN
        Optional<PatternLifecycleState> state = runPromise(() -> repository.getState("tenant-1", "pattern-1"));
        assertThat(state).isPresent();
        assertThat(state.get()).isEqualTo(PatternLifecycleState.VALIDATED);
    }

    @Test
    void shouldReturnEmptyForNonExistentState() {
        // WHEN
        Optional<PatternLifecycleState> state = runPromise(() -> repository.getState("tenant-1", "pattern-1"));

        // THEN
        assertThat(state).isEmpty();
    }

    @Test
    void shouldSaveAndRetrieveEvent() {
        // GIVEN
        PatternLifecycleEvent event = new PatternLifecycleEvent(
            "event-1",
            "pattern-1",
            "tenant-1",
            PatternLifecycleState.DRAFT,
            PatternLifecycleState.VALIDATED,
            PatternLifecycleEventType.PROMOTION,
            "user-1",
            Instant.now(),
            Map.of("traceId", "trace-123", "policyDecision", "approved", "confidence", 0.95)
        );

        // WHEN
        runPromise(() -> repository.saveEvent(event));

        // THEN
        List<PatternLifecycleEvent> events = runPromise(() -> repository.getEvents("tenant-1", "pattern-1"));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventId()).isEqualTo("event-1");
        assertThat(events.get(0).evidence()).containsKey("traceId");
        assertThat(events.get(0).evidence()).containsKey("policyDecision");
        assertThat(events.get(0).evidence()).containsKey("confidence");
    }

    @Test
    void shouldRetrieveEventsInOrder() {
        // GIVEN
        Instant now = Instant.now();
        PatternLifecycleEvent event1 = new PatternLifecycleEvent(
            "event-1", "pattern-1", "tenant-1",
            PatternLifecycleState.DRAFT, PatternLifecycleState.VALIDATED,
            PatternLifecycleEventType.PROMOTION, "user-1", now.minusSeconds(2), Map.of()
        );
        PatternLifecycleEvent event2 = new PatternLifecycleEvent(
            "event-2", "pattern-1", "tenant-1",
            PatternLifecycleState.VALIDATED, PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PROMOTION, "user-1", now.minusSeconds(1), Map.of()
        );
        PatternLifecycleEvent event3 = new PatternLifecycleEvent(
            "event-3", "pattern-1", "tenant-1",
            PatternLifecycleState.ACTIVE, PatternLifecycleState.RETIRED,
            PatternLifecycleEventType.DEMOTION, "user-1", now, Map.of()
        );

        // WHEN
        runPromise(() -> Promise.complete()
            .then(() -> repository.saveEvent(event1))
            .then(() -> repository.saveEvent(event3))
            .then(() -> repository.saveEvent(event2)));

        // THEN
        List<PatternLifecycleEvent> events = runPromise(() -> repository.getEvents("tenant-1", "pattern-1"));
        assertThat(events).hasSize(3);
        assertThat(events.get(0).eventId()).isEqualTo("event-1");
        assertThat(events.get(1).eventId()).isEqualTo("event-2");
        assertThat(events.get(2).eventId()).isEqualTo("event-3");
    }

    @Test
    void shouldDeleteStateAndEvents() {
        // GIVEN
        runPromise(() -> repository.saveState("tenant-1", "pattern-1", PatternLifecycleState.DRAFT));
        PatternLifecycleEvent event = new PatternLifecycleEvent(
            "event-1", "pattern-1", "tenant-1",
            PatternLifecycleState.DRAFT, PatternLifecycleState.VALIDATED,
            PatternLifecycleEventType.PROMOTION, "user-1", Instant.now(), Map.of()
        );
        runPromise(() -> repository.saveEvent(event));

        // WHEN
        runPromise(() -> repository.delete("tenant-1", "pattern-1"));

        // THEN
        Optional<PatternLifecycleState> state = runPromise(() -> repository.getState("tenant-1", "pattern-1"));
        assertThat(state).isEmpty();

        List<PatternLifecycleEvent> events = runPromise(() -> repository.getEvents("tenant-1", "pattern-1"));
        assertThat(events).isEmpty();
    }

    @Test
    void shouldGetPatternsByState() {
        // GIVEN
        runPromise(() -> Promise.complete()
            .then(() -> repository.saveState("tenant-1", "pattern-1", PatternLifecycleState.ACTIVE))
            .then(() -> repository.saveState("tenant-1", "pattern-2", PatternLifecycleState.ACTIVE))
            .then(() -> repository.saveState("tenant-1", "pattern-3", PatternLifecycleState.DRAFT))
            .then(() -> repository.saveState("tenant-2", "pattern-4", PatternLifecycleState.ACTIVE)));

        // WHEN
        List<String> activePatterns = runPromise(() -> repository.getPatternsByState("tenant-1", PatternLifecycleState.ACTIVE));

        // THEN
        assertThat(activePatterns).hasSize(2);
        assertThat(activePatterns).containsExactlyInAnyOrder("pattern-1", "pattern-2");
    }

    @Test
    void shouldEnforceTenantIsolation() {
        // GIVEN
        runPromise(() -> Promise.complete()
            .then(() -> repository.saveState("tenant-a", "pattern-1", PatternLifecycleState.DRAFT))
            .then(() -> repository.saveState("tenant-b", "pattern-1", PatternLifecycleState.ACTIVE)));

        // WHEN
        Optional<PatternLifecycleState> stateA = runPromise(() -> repository.getState("tenant-a", "pattern-1"));
        Optional<PatternLifecycleState> stateB = runPromise(() -> repository.getState("tenant-b", "pattern-1"));

        // THEN
        assertThat(stateA).isPresent();
        assertThat(stateA.get()).isEqualTo(PatternLifecycleState.DRAFT);
        assertThat(stateB).isPresent();
        assertThat(stateB.get()).isEqualTo(PatternLifecycleState.ACTIVE);
    }
}
