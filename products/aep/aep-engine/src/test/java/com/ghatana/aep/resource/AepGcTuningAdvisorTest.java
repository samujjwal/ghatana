/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.resource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.GarbageCollectorMXBean;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AepGcTuningAdvisor} (AEP-005.3).
 */
@DisplayName("AepGcTuningAdvisor — AEP-005.3")
class AepGcTuningAdvisorTest {

    @Test
    @DisplayName("analyze returns meetsTarget=true when avg pause is below threshold")
    void meetsTargetWhenAvgPauseLow() {
        GarbageCollectorMXBean gc = mockGcBean("G1 Young", 100, 500); // avg 5ms
        AepGcTuningAdvisor advisor = AepGcTuningAdvisor.builder()
                .gcBeans(List.of(gc))
                .pauseTargetMs(10.0)
                .build();

        AepGcTuningAdvisor.GcAnalysis analysis = advisor.analyze();
        assertThat(analysis.avgPauseMs()).isEqualTo(5.0);
        assertThat(analysis.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("analyze returns meetsTarget=false when avg pause exceeds threshold")
    void doesNotMeetTargetWhenAvgPauseHigh() {
        GarbageCollectorMXBean gc = mockGcBean("G1 Young", 10, 500); // avg 50ms
        AepGcTuningAdvisor advisor = AepGcTuningAdvisor.builder()
                .gcBeans(List.of(gc))
                .pauseTargetMs(10.0)
                .build();

        AepGcTuningAdvisor.GcAnalysis analysis = advisor.analyze();
        assertThat(analysis.meetsTarget()).isFalse();
        assertThat(analysis.recommendation()).containsIgnoringCase("consider");
    }

    @Test
    @DisplayName("analyze with no collections returns meetsTarget=true and 0 avg pause")
    void noCollectionsReturnsZeroAvgAndMeetsTarget() {
        GarbageCollectorMXBean gc = mockGcBean("G1", 0, 0);
        AepGcTuningAdvisor advisor = AepGcTuningAdvisor.builder()
                .gcBeans(List.of(gc))
                .build();

        AepGcTuningAdvisor.GcAnalysis analysis = advisor.analyze();
        assertThat(analysis.totalCollections()).isEqualTo(0);
        assertThat(analysis.avgPauseMs()).isEqualTo(0.0);
        assertThat(analysis.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("analyze recommendation includes ZGC hint for very high pause times")
    void recommendationIncludesZgcForVeryHighPause() {
        GarbageCollectorMXBean gc = mockGcBean("G1", 10, 1_000); // avg 100ms = 10× target
        AepGcTuningAdvisor advisor = AepGcTuningAdvisor.builder()
                .gcBeans(List.of(gc))
                .pauseTargetMs(10.0)
                .build();

        AepGcTuningAdvisor.GcAnalysis analysis = advisor.analyze();
        assertThat(analysis.recommendation()).containsIgnoringCase("ZGC");
    }

    @Test
    @DisplayName("Builder rejects non-positive pauseTargetMs")
    void builderRejectsNonPositiveTarget() {
        assertThatThrownBy(() -> AepGcTuningAdvisor.builder().pauseTargetMs(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Helper ────────────────────────────────────────────────────────────────

    private GarbageCollectorMXBean mockGcBean(String name, long count, long timeMs) {
        GarbageCollectorMXBean bean = mock(GarbageCollectorMXBean.class);
        when(bean.getName()).thenReturn(name);
        when(bean.getCollectionCount()).thenReturn(count);
        when(bean.getCollectionTime()).thenReturn(timeMs);
        return bean;
    }
}

