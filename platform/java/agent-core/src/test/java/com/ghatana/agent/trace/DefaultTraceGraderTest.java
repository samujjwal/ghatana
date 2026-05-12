/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for DefaultTraceGrader
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DefaultTraceGrader Tests")
class DefaultTraceGraderTest {

    private final DefaultTraceGrader grader = new DefaultTraceGrader();

    @Test
    @DisplayName("Empty trace is invalid")
    void emptyTraceIsInvalid() {
        Trace trace = new Trace(
                "trace-1",
                "agent-1",
                "turn-1",
                "input",
                "output",
                List.of(),
                Map.of(),
                Instant.now()
        );

        TraceGrade grade = grader.grade(trace);

        assertThat(grade).isEqualTo(TraceGrade.INVALID);
    }

    @Test
    @DisplayName("Complete trace with good causality gets good grade")
    void completeTraceGetsGoodGrade() {
        Trace trace = new Trace(
                "trace-1",
                "agent-1",
                "turn-1",
                "input",
                "output",
                List.of(
                        new Trace.TraceStep("step-1", "action A", "observed A", Map.of()),
                        new Trace.TraceStep("step-2", "action B", "action B completed", Map.of())
                ),
                Map.of("metadata", "value"),
                Instant.now()
        );

        TraceGrade grade = grader.grade(trace);

        assertThat(grade).isNotEqualTo(TraceGrade.INVALID);
        assertThat(grade).isNotEqualTo(TraceGrade.POOR);
    }

    @Test
    @DisplayName("Grade score mapping")
    void gradeScoreMapping() {
        assertThat(grader.score(TraceGrade.EXCELLENT)).isEqualTo(1.0);
        assertThat(grader.score(TraceGrade.GOOD)).isEqualTo(0.8);
        assertThat(grader.score(TraceGrade.FAIR)).isEqualTo(0.6);
        assertThat(grader.score(TraceGrade.POOR)).isEqualTo(0.4);
        assertThat(grader.score(TraceGrade.INVALID)).isEqualTo(0.0);
    }
}
