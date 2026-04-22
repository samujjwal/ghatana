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
@DisplayName("PluginInjector Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class PluginInjectorTest {

    @Mock
    private KernelContext context;

    private PluginInjector injector;

    @BeforeEach
    void setUp() { // GH-90000
        injector = new PluginInjector(); // GH-90000
    }

    @Test
    @DisplayName("Should inject required dependency into annotated field [GH-90000]")
    void shouldInjectRequiredDependency() { // GH-90000
        // GIVEN
        FakeService service = new FakeService(); // GH-90000
        when(context.getDependency(FakeService.class)).thenReturn(service); // GH-90000
        PluginWithRequiredDep plugin = new PluginWithRequiredDep(); // GH-90000

        // WHEN
        injector.inject(plugin, context); // GH-90000

        // THEN
        assertThat(plugin.fakeService).isSameAs(service); // GH-90000
    }

    @Test
    @DisplayName("Should inject optional dependency when present [GH-90000]")
    void shouldInjectOptionalDependencyWhenPresent() { // GH-90000
        // GIVEN
        FakeService service = new FakeService(); // GH-90000
        when(context.getOptionalDependency(FakeService.class)).thenReturn(Optional.of(service)); // GH-90000
        PluginWithOptionalDep plugin = new PluginWithOptionalDep(); // GH-90000

        // WHEN
        injector.inject(plugin, context); // GH-90000

        // THEN
        assertThat(plugin.fakeService).isSameAs(service); // GH-90000
    }

    @Test
    @DisplayName("Should leave optional field null when dependency absent [GH-90000]")
    void shouldLeaveOptionalFieldNullWhenAbsent() { // GH-90000
        // GIVEN
        when(context.getOptionalDependency(FakeService.class)).thenReturn(Optional.empty()); // GH-90000
        PluginWithOptionalDep plugin = new PluginWithOptionalDep(); // GH-90000

        // WHEN
        injector.inject(plugin, context); // GH-90000

        // THEN — no exception; field stays null
        assertThat(plugin.fakeService).isNull(); // GH-90000
    }

    @Test
    @DisplayName("Should throw PluginInjectionException when required dependency missing [GH-90000]")
    void shouldThrowWhenRequiredDependencyMissing() { // GH-90000
        // GIVEN
        when(context.getDependency(FakeService.class)) // GH-90000
            .thenThrow(new IllegalStateException("Dependency not found: FakeService [GH-90000]"));
        PluginWithRequiredDep plugin = new PluginWithRequiredDep(); // GH-90000

        // WHEN / THEN
        assertThatThrownBy(() -> injector.inject(plugin, context)) // GH-90000
            .isInstanceOf(PluginInjector.PluginInjectionException.class) // GH-90000
            .hasMessageContaining("fakeService [GH-90000]");
    }

    @Test
    @DisplayName("Should inject fields from superclass hierarchy [GH-90000]")
    void shouldInjectSuperclassFields() { // GH-90000
        // GIVEN
        FakeService service = new FakeService(); // GH-90000
        AnotherService other = new AnotherService(); // GH-90000
        when(context.getDependency(FakeService.class)).thenReturn(service); // GH-90000
        when(context.getDependency(AnotherService.class)).thenReturn(other); // GH-90000
        PluginWithInheritedDeps plugin = new PluginWithInheritedDeps(); // GH-90000

        // WHEN
        injector.inject(plugin, context); // GH-90000

        // THEN
        assertThat(plugin.fakeService).isSameAs(service); // GH-90000
        assertThat(plugin.anotherService).isSameAs(other); // GH-90000
    }

    @Test
    @DisplayName("Should throw NullPointerException when target is null [GH-90000]")
    void shouldRejectNullTarget() { // GH-90000
        assertThatThrownBy(() -> injector.inject(null, context)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("Should throw NullPointerException when context is null [GH-90000]")
    void shouldRejectNullContext() { // GH-90000
        assertThatThrownBy(() -> injector.inject(new PluginWithRequiredDep(), null)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("Should do nothing on plugin with no annotated fields [GH-90000]")
    void shouldHandlePluginWithNoInjectFields() { // GH-90000
        // GIVEN
        PluginWithNoInject plugin = new PluginWithNoInject(); // GH-90000

        // WHEN / THEN — no interaction with context, no exception
        injector.inject(plugin, context); // GH-90000
        verifyNoInteractions(context); // GH-90000
    }

    // ==================== Test fixtures ====================

    static class FakeService { }

    static class AnotherService { }

    /** Minimal KernelModule stub — lifecycle not exercised in these tests. */
    abstract static class StubModule implements com.ghatana.kernel.module.KernelModule {
        @Override public String getModuleId() { return "stub"; } // GH-90000
        @Override public String getVersion() { return "1.0.0"; } // GH-90000
        @Override public Set<KernelCapability> getCapabilities() { return Set.of(); } // GH-90000
        @Override public Set<KernelDependency> getDependencies() { return Set.of(); } // GH-90000
        @Override public void initialize(KernelContext ctx) { } // GH-90000
        @Override public Promise<Void> start() { return Promise.complete(); } // GH-90000
        @Override public Promise<Void> stop() { return Promise.complete(); } // GH-90000
        @Override public HealthStatus getHealthStatus() { return HealthStatus.healthy(); } // GH-90000
    }

    static class PluginWithRequiredDep extends StubModule {
        @PluginInject
        FakeService fakeService;
    }

    static class PluginWithOptionalDep extends StubModule {
        @PluginInject(optional = true) // GH-90000
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
        @SuppressWarnings("unused [GH-90000]")
        String notInjected;
    }
}
