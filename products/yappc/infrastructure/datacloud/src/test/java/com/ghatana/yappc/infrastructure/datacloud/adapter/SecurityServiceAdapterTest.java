package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for SecurityServiceAdapter.
 */
@DisplayName("SecurityServiceAdapter Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
/**
 * @doc.type class
 * @doc.purpose Handles security service adapter test operations
 * @doc.layer platform
 * @doc.pattern Test
 */
class SecurityServiceAdapterTest extends EventloopTestBase {

    @Mock
    private SecurityScanner mockScanner;

    private SecurityServiceAdapter securityServiceAdapter;

    @BeforeEach
    void setUp() { // GH-90000
        lenient().when(mockScanner.scan(any())).thenReturn(Promise.of(SecurityReport.clean("test-scanner")));
        securityServiceAdapter = new SecurityServiceAdapter(mockScanner); // GH-90000
    }

    @Test
    @DisplayName("Should scan project for vulnerabilities")
    void shouldScanProject() { // GH-90000
        Path projectPath = Paths.get("/project");
        Map<String, Object> result = runPromise(() -> securityServiceAdapter.scanProject(projectPath)); // GH-90000
        assertThat(result).containsKey("status");
    }

    @Test
    @DisplayName("generateSbom returns valid CycloneDX stub — bomFormat and specVersion present")
    void shouldGenerateSbomWithCycloneDxFields() { // GH-90000
        Path projectPath = Paths.get("/project");
        String result = runPromise(() -> securityServiceAdapter.generateSbom(projectPath)); // GH-90000
        assertThat(result).isNotNull(); // GH-90000
        assertThat(result).contains("\"bomFormat\":\"CycloneDX\"");
        assertThat(result).contains("\"specVersion\":\"1.4\"");
        assertThat(result).contains("\"components\"");
    }

    @Test
    @DisplayName("generateSbom includes project name in metadata component")
    void shouldGenerateSbomWithProjectName() { // GH-90000
        Path projectPath = Paths.get("/my-project");
        String result = runPromise(() -> securityServiceAdapter.generateSbom(projectPath)); // GH-90000
        assertThat(result).contains("my-project");
    }

    @Test
    @DisplayName("Should check dependencies")
    void shouldCheckDependencies() { // GH-90000
        Path projectPath = Paths.get("/project");
        Map<String, Object> result = runPromise(() -> securityServiceAdapter.checkDependencies(projectPath)); // GH-90000
        assertThat(result).containsKey("status");
    }
}
