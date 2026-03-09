/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.service.LanguageService;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/** Integration tests for language service discovery and basic functionality. */
@ExtendWith(MockitoExtension.class)
/**
 * @doc.type class
 * @doc.purpose Handles language service integration test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class LanguageServiceIntegrationTest extends AbstractLanguageTest {

    private List<LanguageService> languageServices;

    @BeforeEach
    void setUpServices() {
        // Discover language services using ServiceLoader
        languageServices =
                StreamSupport.stream(ServiceLoader.load(LanguageService.class).spliterator(), false)
                        .collect(Collectors.toList());

        // Add discovered services to the project context
        projectContext =
                new PolyfixProjectContext(
                        projectContext.root(), // root
                        projectContext.config(), // config
                        languageServices, // languages
                        projectContext.exec(), // exec
                        projectContext.log() // log
                        );
    }

    @Test
    void shouldDiscoverJavaLanguageService() {
        assertThat(languageServices)
                .as("Should discover Java language service")
                .anyMatch(service -> "java".equals(service.id()));
    }

    @Test
    void shouldDiscoverPythonLanguageService() {
        assertThat(languageServices)
                .as("Should discover Python language service")
                .anyMatch(service -> "python".equals(service.id()));
    }

    @Test
    void javaServiceShouldSupportJavaFiles() {
        LanguageService javaService =
                languageServices.stream()
                        .filter(service -> "java".equals(service.id()))
                        .findFirst()
                        .orElseThrow();

        assertThat(javaService.supports(Path.of("Test.java"))).isTrue();
        assertThat(javaService.supports(Path.of("src/main/java/com/example/Test.java"))).isTrue();
        assertThat(javaService.supports(Path.of("pom.xml"))).isFalse();
    }

    @Test
    void pythonServiceShouldSupportPythonFiles() {
        LanguageService pythonService =
                languageServices.stream()
                        .filter(service -> "python".equals(service.id()))
                        .findFirst()
                        .orElseThrow();

        assertThat(pythonService.supports(Path.of("script.py"))).isTrue();
        assertThat(pythonService.supports(Path.of("src/main/python/module/__init__.py"))).isTrue();
        assertThat(pythonService.supports(Path.of("requirements.txt"))).isFalse();
    }
}
