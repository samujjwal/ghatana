package com.ghatana.phr.launcher;

import com.ghatana.kernel.context.DefaultKernelContext;
import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.BoundaryPolicyStore;
import com.ghatana.kernel.policy.InMemoryBoundaryPolicyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Tests for PHR launcher configuration argument parsing
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("PHR Launcher Tests")
class PhrLauncherTest {

    /** Resolve the private inner record class. */
    private static Class<?> configClass() throws ClassNotFoundException {
        return Class.forName("com.ghatana.phr.launcher.PhrLauncher$PhrLauncherConfig");
    }

    /** Invoke the private static factory {@code PhrLauncherConfig.from(String[])}. */
    private static Object parseConfig(String[] args) throws Exception {
        Method from = configClass().getDeclaredMethod("from", String[].class);
        from.setAccessible(true);
        return from.invoke(null, (Object) args);
    }

    /** Invoke the private static factory {@code PhrLauncher.createContext(String)}. */
    private static DefaultKernelContext createContext(String environment) throws Exception {
        Method createContext = PhrLauncher.class.getDeclaredMethod("createContext", String.class);
        createContext.setAccessible(true);
        return (DefaultKernelContext) createContext.invoke(null, environment);
    }

    /** Read a named component from the record. */
    private static Object component(Object record, String name) throws Exception {
        Method accessor = record.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(record);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Port parsing
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should use default port (8080) when no --port argument provided")
    void shouldUseDefaultPortWhenNoPortArgumentProvided() throws Exception {
        Object config = parseConfig(new String[]{});
        int port = (int) component(config, "httpPort");
        assertEquals(8080, port, "Default HTTP port must be 8080");
    }

    @Test
    @DisplayName("Should use custom port from --port argument")
    void shouldUseCustomPortWhenPortArgumentProvided() throws Exception {
        Object config = parseConfig(new String[]{"--port", "9090"});
        int port = (int) component(config, "httpPort");
        assertEquals(9090, port, "Port must be parsed from --port argument");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Host parsing
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should use default host (0.0.0.0) when no --host argument provided")
    void shouldUseDefaultHostWhenNoHostArgumentProvided() throws Exception {
        Object config = parseConfig(new String[]{});
        String host = (String) component(config, "httpHost");
        assertEquals("0.0.0.0", host, "Default host must be 0.0.0.0");
    }

    @Test
    @DisplayName("Should use custom host from --host argument")
    void shouldUseCustomHostWhenHostArgumentProvided() throws Exception {
        Object config = parseConfig(new String[]{"--host", "127.0.0.1"});
        String host = (String) component(config, "httpHost");
        assertEquals("127.0.0.1", host, "Host must be parsed from --host argument");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Environment parsing
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should default to 'local' environment when no --environment argument provided")
    void shouldDefaultToLocalEnvironment() throws Exception {
        Object config = parseConfig(new String[]{});
        String env = (String) component(config, "environment");
        assertEquals("local", env, "Default environment must be 'local'");
    }

    @Test
    @DisplayName("Should parse environment from --environment argument")
    void shouldParseEnvironmentFromArgument() throws Exception {
        Object config = parseConfig(new String[]{"--environment", "production"});
        String env = (String) component(config, "environment");
        assertEquals("production", env, "Environment must be parsed from --environment argument");
    }

    @Test
    @DisplayName("Should parse environment from --env short form")
    void shouldParseEnvironmentFromShortArgument() throws Exception {
        Object config = parseConfig(new String[]{"--env", "staging"});
        String env = (String) component(config, "environment");
        assertEquals("staging", env, "Environment must be parsed from --env argument");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Combined arguments
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should parse all arguments together")
    void shouldParseAllArgumentsTogether() throws Exception {
        Object config = parseConfig(
            new String[]{"--port", "8443", "--host", "10.0.0.1", "--env", "production"});
        assertEquals(8443, component(config, "httpPort"));
        assertEquals("10.0.0.1", component(config, "httpHost"));
        assertEquals("production", component(config, "environment"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Record structure
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PhrLauncherConfig must be a record with exactly 3 components")
    void phrLauncherConfigMustBeARecordWith3Components() throws ClassNotFoundException {
        Class<?> clazz = configClass();
        assertTrue(clazz.isRecord(), "PhrLauncherConfig must be a record");
        RecordComponent[] components = clazz.getRecordComponents();
        assertEquals(3, components.length, "Record must have exactly 3 components");
    }

    @Test
    @DisplayName("Should allow InMemoryBoundaryPolicyStore in local environment")
    void shouldAllowInMemoryBoundaryPolicyStoreInLocalEnvironment() throws Exception {
        DefaultKernelContext context = createContext("local");
        context.registerDependency(BoundaryPolicyStore.class, testInMemoryStore());

        assertDoesNotThrow(() -> PhrLauncher.assertRuntimeDependencies(context));
    }

    @Test
    @DisplayName("Should reject InMemoryBoundaryPolicyStore in production environment")
    void shouldRejectInMemoryBoundaryPolicyStoreInProductionEnvironment() throws Exception {
        DefaultKernelContext context = createContext("production");
        context.registerDependency(BoundaryPolicyStore.class, testInMemoryStore());

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> PhrLauncher.assertRuntimeDependencies(context)
        );

        assertTrue(exception.getMessage().contains("InMemoryBoundaryPolicyStore"));
        assertTrue(exception.getMessage().contains("products/phr/launcher"));
    }

    @Test
    @DisplayName("Should allow product-owned BoundaryPolicyStore in production environment")
    void shouldAllowProductOwnedBoundaryPolicyStoreInProductionEnvironment() throws Exception {
        DefaultKernelContext context = createContext("production");
        context.registerDependency(BoundaryPolicyStore.class, new TestBoundaryPolicyStore());

        assertDoesNotThrow(() -> PhrLauncher.assertRuntimeDependencies(context));
    }

    private static final class TestBoundaryPolicyStore implements BoundaryPolicyStore {

        @Override
        public List<BoundaryPolicyRule> loadRules(BoundaryPolicyLoadContext context) {
            return List.of();
        }
    }

    private static InMemoryBoundaryPolicyStore testInMemoryStore() {
        return new InMemoryBoundaryPolicyStore(List.of(
            BoundaryPolicyRule.builder()
                .ruleId("TEST-001")
                .sourceScopePattern("test.*")
                .targetScopePattern("test.*")
                .resourcePattern("resource/**")
                .actions("read")
                .effect(BoundaryPolicyRule.Effect.ALLOW)
                .requiresAudit(false)
                .build()
        ));
    }
}
