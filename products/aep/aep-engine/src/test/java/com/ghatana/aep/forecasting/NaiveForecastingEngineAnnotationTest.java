/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.forecasting;

import com.ghatana.aep.annotation.DevelopmentOnly;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.Annotation;

/**
 * Unit tests to verify {@link DevelopmentOnly} annotation is applied.
 *
 * P2-13: Verify NaiveForecastingEngine is marked as development-only.
 */
class NaiveForecastingEngineAnnotationTest {

    @Test
    void shouldHaveDevelopmentOnlyAnnotation() {
        DevelopmentOnly annotation = NaiveForecastingEngine.class
            .getAnnotation(DevelopmentOnly.class);
        
        assertNotNull(annotation, "NaiveForecastingEngine should be marked with @DevelopmentOnly");
        assertFalse(annotation.reason().isEmpty(), 
            "@DevelopmentOnly should have a reason specified");
        assertTrue(annotation.reason().contains("time-series ML"),
            "Reason should mention replacement with ML model");
    }

    @Test
    void shouldIndicateProductionUnsuitability() {
        DevelopmentOnly annotation = NaiveForecastingEngine.class
            .getAnnotation(DevelopmentOnly.class);
        
        assertNotNull(annotation);
        String reason = annotation.reason().toLowerCase();
        
        assertTrue(reason.contains("production") || reason.contains("baseline"),
            "Reason should indicate this is not for production use");
    }
}
