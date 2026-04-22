/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Unit tests for {@link AepGcTuningAdvisor} (AEP-005.3). // GH-90000
 */
@DisplayName("AepGcTuningAdvisor — AEP-005.3 [GH-90000]")
class AepGcTuningAdvisorTest {

    @Test
    @DisplayName("analyze returns meetsTarget=true when avg pause is below threshold [GH-90000]")
    void meetsTargetWhenAvgPauseLow() { // GH-90000
        GarbageCollectorMXBean gc = mockGcBean("G1 Young", 100, 500); // avg 5ms // GH-90000
        AepGcTuningAdvisor advisor = AepGcTuningAdvisor.builder() // GH-90000
                .gcBeans(List.of(gc)) // GH-90000
                .pauseTargetMs(10.0) // GH-90000
                .build(); // GH-90000

        AepGcTuningAdvisor.GcAnalysis analysis = advisor.analyze(); // GH-90000
        assertThat(analysis.avgPauseMs()).isEqualTo(5.0); // GH-90000
        assertThat(analysis.meetsTarget()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("analyze returns meetsTarget=false when avg pause exceeds threshold [GH-90000]")
    void doesNotMeetTargetWhenAvgPauseHigh() { // GH-90000
        GarbageCollectorMXBean gc = mockGcBean("G1 Young", 10, 500); // avg 50ms // GH-90000
        AepGcTuningAdvisor advisor = AepGcTuningAdvisor.builder() // GH-90000
                .gcBeans(List.of(gc)) // GH-90000
                .pauseTargetMs(10.0) // GH-90000
                .build(); // GH-90000

        AepGcTuningAdvisor.GcAnalysis analysis = advisor.analyze(); // GH-90000
        assertThat(analysis.meetsTarget()).isFalse(); // GH-90000
        assertThat(analysis.recommendation()).containsIgnoringCase("consider [GH-90000]");
    }

    @Test
    @DisplayName("analyze with no collections returns meetsTarget=true and 0 avg pause [GH-90000]")
    void noCollectionsReturnsZeroAvgAndMeetsTarget() { // GH-90000
        GarbageCollectorMXBean gc = mockGcBean("G1", 0, 0); // GH-90000
        AepGcTuningAdvisor advisor = AepGcTuningAdvisor.builder() // GH-90000
                .gcBeans(List.of(gc)) // GH-90000
                .build(); // GH-90000

        AepGcTuningAdvisor.GcAnalysis analysis = advisor.analyze(); // GH-90000
        assertThat(analysis.totalCollections()).isEqualTo(0); // GH-90000
        assertThat(analysis.avgPauseMs()).isEqualTo(0.0); // GH-90000
        assertThat(analysis.meetsTarget()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("analyze recommendation includes ZGC hint for very high pause times [GH-90000]")
    void recommendationIncludesZgcForVeryHighPause() { // GH-90000
        GarbageCollectorMXBean gc = mockGcBean("G1", 10, 1_000); // avg 100ms = 10× target // GH-90000
        AepGcTuningAdvisor advisor = AepGcTuningAdvisor.builder() // GH-90000
                .gcBeans(List.of(gc)) // GH-90000
                .pauseTargetMs(10.0) // GH-90000
                .build(); // GH-90000

        AepGcTuningAdvisor.GcAnalysis analysis = advisor.analyze(); // GH-90000
        assertThat(analysis.recommendation()).containsIgnoringCase("ZGC [GH-90000]");
    }

    @Test
    @DisplayName("Builder rejects non-positive pauseTargetMs [GH-90000]")
    void builderRejectsNonPositiveTarget() { // GH-90000
        assertThatThrownBy(() -> AepGcTuningAdvisor.builder().pauseTargetMs(0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // ─── Helper ────────────────────────────────────────────────────────────────

    private GarbageCollectorMXBean mockGcBean(String name, long count, long timeMs) { // GH-90000
        GarbageCollectorMXBean bean = mock(GarbageCollectorMXBean.class); // GH-90000
        when(bean.getName()).thenReturn(name); // GH-90000
        when(bean.getCollectionCount()).thenReturn(count); // GH-90000
        when(bean.getCollectionTime()).thenReturn(timeMs); // GH-90000
        return bean;
    }
}
