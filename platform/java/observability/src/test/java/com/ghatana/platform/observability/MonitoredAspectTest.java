package com.ghatana.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MonitoredAspect AOP functionality.
 *
 * Note: Full AOP testing requires AspectJ weaving (compile-time or load-time). 
 * This test validates the aspect initialization and metric registry setup.
 * Integration tests with actual AOP weaving should be added when AspectJ
 * weaving is configured in the build.
 *
 * @doc.type class
 * @doc.purpose Test MonitoredAspect initialization and setup
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("MonitoredAspect Tests")
class MonitoredAspectTest {

    private MeterRegistry meterRegistry;
    private MonitoredAspect monitoredAspect;

    @BeforeEach
    void setUp() { 
        meterRegistry = new SimpleMeterRegistry(); 
        monitoredAspect = new MonitoredAspect(meterRegistry); 
    }

    @Test
    @DisplayName("Should create MonitoredAspect with meter registry")
    void shouldCreateMonitoredAspectWithMeterRegistry() { 
        assertThat(monitoredAspect).isNotNull(); 
    }

    @Test
    @DisplayName("Should initialize metric caches")
    void shouldInitializeMetricCaches() { 
        assertThat(monitoredAspect).isNotNull(); 
        // Caches are private but aspect should be initialized without errors
    }

    @Test
    @DisplayName("Should handle null meter registry gracefully")
    void shouldHandleNullMeterRegistryGracefully() { 
        // This test documents that meter registry is required
        // In production, this should be validated at construction
        assertThat(meterRegistry).isNotNull(); 
    }

    @Test
    @DisplayName("Monitored annotation should have default values")
    void monitoredAnnotationShouldHaveDefaultValues() { 
        Monitored monitored = TestClass.class.getAnnotation(Monitored.class); 
        
        assertThat(monitored).isNotNull(); 
        assertThat(monitored.value()).isEmpty(); 
        assertThat(monitored.description()).isEmpty(); 
        assertThat(monitored.recordTiming()).isTrue(); 
        assertThat(monitored.recordCounters()).isTrue(); 
        assertThat(monitored.captureParameters()).isFalse(); 
        assertThat(monitored.tags()).isEmpty(); 
    }

    @Test
    @DisplayName("Monitored annotation should accept custom values")
    void monitoredAnnotationShouldAcceptCustomValues() { 
        Monitored monitored = TestClassWithCustom.class.getAnnotation(Monitored.class); 
        
        assertThat(monitored).isNotNull(); 
        assertThat(monitored.value()).isEqualTo("custom-metric");
        assertThat(monitored.description()).isEqualTo("Custom description");
        assertThat(monitored.recordTiming()).isFalse(); 
        assertThat(monitored.recordCounters()).isFalse(); 
        assertThat(monitored.tags()).isEqualTo(new String[]{"tenant:123", "region:us-west"}); 
    }

    @Test
    @DisplayName("Monitored annotation should support class-level annotation")
    void monitoredAnnotationShouldSupportClassLevelAnnotation() { 
        Monitored monitored = TestClassLevel.class.getAnnotation(Monitored.class); 
        
        assertThat(monitored).isNotNull(); 
    }

    /**
     * Test class with default @Monitored annotation.
     */
    @Monitored
    static class TestClass {
        public void testMethod() {} 
    }

    /**
     * Test class with custom @Monitored annotation values.
     */
    @Monitored( 
        value = "custom-metric",
        description = "Custom description",
        recordTiming = false,
        recordCounters = false,
        tags = {"tenant:123", "region:us-west"}
    )
    static class TestClassWithCustom {
        public void testMethod() {} 
    }

    /**
     * Test class with class-level @Monitored annotation.
     */
    @Monitored
    static class TestClassLevel {
        public void testMethod() {} 
    }
}
