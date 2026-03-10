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
@ExtendWith(MockitoExtension.class)
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
    void setUp() {
        lenient().when(mockScanner.scan(any())).thenReturn(Promise.of(SecurityReport.clean("test-scanner")));
        securityServiceAdapter = new SecurityServiceAdapter(mockScanner);
    }

    @Test
    @DisplayName("Should scan project for vulnerabilities")
    void shouldScanProject() {
        Path projectPath = Paths.get("/project");
        Map<String, Object> result = runPromise(() -> securityServiceAdapter.scanProject(projectPath));
        assertThat(result).containsKey("status");
    }

    @Test
    @DisplayName("Should generate SBOM")
    void shouldGenerateSbom() {
        Path projectPath = Paths.get("/project");
        String result = runPromise(() -> securityServiceAdapter.generateSbom(projectPath));
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should check dependencies")
    void shouldCheckDependencies() {
        Path projectPath = Paths.get("/project");
        Map<String, Object> result = runPromise(() -> securityServiceAdapter.checkDependencies(projectPath));
        assertThat(result).containsKey("status");
    }
}
