package com.ghatana.kernel.plugin;

import com.ghatana.kernel.annotation.PluginInject;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link PluginInjector}.
 *
 * @doc.type class
 * @doc.purpose Validates field injection from KernelContext into plugin instances
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("PluginInjector Tests")
@ExtendWith(MockitoExtension.class) 
class PluginInjectorTest {

    @Mock
    private KernelContext context;

    private PluginInjector injector;

    @BeforeEach
    void setUp() { 
        injector = new PluginInjector(); 
    }

    @Test
    @DisplayName("Should inject required dependency into annotated field")
    void shouldInjectRequiredDependency() { 
        // GIVEN
        FakeService service = new FakeService(); 
        when(context.getDependency(FakeService.class)).thenReturn(service); 
        PluginWithRequiredDep plugin = new PluginWithRequiredDep(); 

        // WHEN
        injector.inject(plugin, context); 

        // THEN
        assertThat(plugin.fakeService).isSameAs(service); 
    }

    @Test
    @DisplayName("Should inject optional dependency when present")
    void shouldInjectOptionalDependencyWhenPresent() { 
        // GIVEN
        FakeService service = new FakeService(); 
        when(context.getOptionalDependency(FakeService.class)).thenReturn(Optional.of(service)); 
        PluginWithOptionalDep plugin = new PluginWithOptionalDep(); 

        // WHEN
        injector.inject(plugin, context); 

        // THEN
        assertThat(plugin.fakeService).isSameAs(service); 
    }

    @Test
    @DisplayName("Should leave optional field null when dependency absent")
    void shouldLeaveOptionalFieldNullWhenAbsent() { 
        // GIVEN
        when(context.getOptionalDependency(FakeService.class)).thenReturn(Optional.empty()); 
        PluginWithOptionalDep plugin = new PluginWithOptionalDep(); 

        // WHEN
        injector.inject(plugin, context); 

        // THEN — no exception; field stays null
        assertThat(plugin.fakeService).isNull(); 
    }

    @Test
    @DisplayName("Should throw PluginInjectionException when required dependency missing")
    void shouldThrowWhenRequiredDependencyMissing() { 
        // GIVEN
        when(context.getDependency(FakeService.class)) 
            .thenThrow(new IllegalStateException("Dependency not found: FakeService"));
        PluginWithRequiredDep plugin = new PluginWithRequiredDep(); 

        // WHEN / THEN
        assertThatThrownBy(() -> injector.inject(plugin, context)) 
            .isInstanceOf(PluginInjector.PluginInjectionException.class) 
            .hasMessageContaining("fakeService");
    }

    @Test
    @DisplayName("Should inject fields from superclass hierarchy")
    void shouldInjectSuperclassFields() { 
        // GIVEN
        FakeService service = new FakeService(); 
        AnotherService other = new AnotherService(); 
        when(context.getDependency(FakeService.class)).thenReturn(service); 
        when(context.getDependency(AnotherService.class)).thenReturn(other); 
        PluginWithInheritedDeps plugin = new PluginWithInheritedDeps(); 

        // WHEN
        injector.inject(plugin, context); 

        // THEN
        assertThat(plugin.fakeService).isSameAs(service); 
        assertThat(plugin.anotherService).isSameAs(other); 
    }

    @Test
    @DisplayName("Should throw NullPointerException when target is null")
    void shouldRejectNullTarget() { 
        assertThatThrownBy(() -> injector.inject(null, context)) 
            .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("Should throw NullPointerException when context is null")
    void shouldRejectNullContext() { 
        assertThatThrownBy(() -> injector.inject(new PluginWithRequiredDep(), null)) 
            .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("Should do nothing on plugin with no annotated fields")
    void shouldHandlePluginWithNoInjectFields() { 
        // GIVEN
        PluginWithNoInject plugin = new PluginWithNoInject(); 

        // WHEN / THEN — no interaction with context, no exception
        injector.inject(plugin, context); 
        verifyNoInteractions(context); 
    }

    // ==================== Test fixtures ====================

    static class FakeService { }

    static class AnotherService { }

    /** Minimal KernelModule stub — lifecycle not exercised in these tests. */
    abstract static class StubModule implements com.ghatana.kernel.module.KernelModule {
        @Override public String getModuleId() { return "stub"; } 
        @Override public String getVersion() { return "1.0.0"; } 
        @Override public Set<KernelCapability> getCapabilities() { return Set.of(); } 
        @Override public Set<KernelDependency> getDependencies() { return Set.of(); } 
        @Override public void initialize(KernelContext ctx) { } 
        @Override public Promise<Void> start() { return Promise.complete(); } 
        @Override public Promise<Void> stop() { return Promise.complete(); } 
        @Override public HealthStatus getHealthStatus() { return HealthStatus.healthy(); } 
    }

    static class PluginWithRequiredDep extends StubModule {
        @PluginInject
        FakeService fakeService;
    }

    static class PluginWithOptionalDep extends StubModule {
        @PluginInject(optional = true) 
        FakeService fakeService;
    }

    static class BasePluginWithService extends StubModule {
        @PluginInject
        FakeService fakeService;
    }

    static class PluginWithInheritedDeps extends BasePluginWithService {
        @PluginInject
        AnotherService anotherService;
    }

    static class PluginWithNoInject extends StubModule {
        @SuppressWarnings("unused")
        String notInjected;
    }
}
