package com.ghatana.products.finance.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Tests for Finance launcher argument parsing and configuration defaults
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("Finance Launcher Tests")
class FinanceLauncherTest {

    /** Resolve the private inner record class. */
    private static Class<?> configClass() throws ClassNotFoundException {
        return Class.forName(
            "com.ghatana.products.finance.launcher.FinanceLauncher$FinanceLauncherConfig");
    }

    /** Invoke the private static factory {@code FinanceLauncherConfig.from(String[])}. */
    private static Object parseConfig(String[] args) throws Exception {
        Method from = configClass().getDeclaredMethod("from", String[].class);
        from.setAccessible(true);
        return from.invoke(null, (Object) args);
    }

    /** Read a named record component accessor. */
    private static Object component(Object record, String name) throws Exception {
        Method accessor = record.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(record);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Port parsing
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should use default port (8081) when no --port argument provided")
    void shouldUseDefaultPortWhenNoPortArgumentProvided() throws Exception {
        Object config = parseConfig(new String[]{});
        int port = (int) component(config, "httpPort");
        assertEquals(8081, port, "Default HTTP port for Finance must be 8081");
    }

    @Test
    @DisplayName("Should use custom port from --port argument")
    void shouldUseCustomPortWhenPortArgumentProvided() throws Exception {
        Object config = parseConfig(new String[]{"--port", "9091"});
        int port = (int) component(config, "httpPort");
        assertEquals(9091, port, "Port must be parsed from --port argument");
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
            new String[]{"--port", "8443", "--host", "10.0.0.2", "--env", "production"});
        assertEquals(8443, component(config, "httpPort"));
        assertEquals("10.0.0.2", component(config, "httpHost"));
        assertEquals("production", component(config, "environment"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Record structure
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FinanceLauncherConfig must be a record with exactly 3 components")
    void financeLauncherConfigMustBeARecordWith3Components() throws ClassNotFoundException {
        Class<?> clazz = configClass();
        assertTrue(clazz.isRecord(), "FinanceLauncherConfig must be a record");
        RecordComponent[] components = clazz.getRecordComponents();
        assertEquals(3, components.length, "Record must have exactly 3 components");
    }
}
