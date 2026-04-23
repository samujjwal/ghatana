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
@ExtendWith(MockitoExtension.class) // GH-90000
/**
 * @doc.type class
 * @doc.purpose Handles language service integration test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class LanguageServiceIntegrationTest extends AbstractLanguageTest {

    private List<LanguageService> languageServices;

    @BeforeEach
    void setUpServices() { // GH-90000
        // Discover language services using ServiceLoader
        languageServices =
                StreamSupport.stream(ServiceLoader.load(LanguageService.class).spliterator(), false) // GH-90000
                        .collect(Collectors.toList()); // GH-90000

        // Add discovered services to the project context
        projectContext =
                new PolyfixProjectContext( // GH-90000
                        projectContext.root(), // root // GH-90000
                        projectContext.config(), // config // GH-90000
                        languageServices, // languages
                        projectContext.exec(), // exec // GH-90000
                        projectContext.log() // log // GH-90000
                        );
    }

    @Test
    void shouldDiscoverJavaLanguageService() { // GH-90000
        assertThat(languageServices) // GH-90000
                .as("Should discover Java language service")
                .anyMatch(service -> "java".equals(service.id())); // GH-90000
    }

    @Test
    void shouldDiscoverPythonLanguageService() { // GH-90000
        assertThat(languageServices) // GH-90000
                .as("Should discover Python language service")
                .anyMatch(service -> "python".equals(service.id())); // GH-90000
    }

    @Test
    void javaServiceShouldSupportJavaFiles() { // GH-90000
        LanguageService javaService =
                languageServices.stream() // GH-90000
                        .filter(service -> "java".equals(service.id())) // GH-90000
                        .findFirst() // GH-90000
                        .orElseThrow(); // GH-90000

        assertThat(javaService.supports(Path.of("Test.java"))).isTrue();
        assertThat(javaService.supports(Path.of("src/main/java/com/example/Test.java"))).isTrue();
        assertThat(javaService.supports(Path.of("pom.xml"))).isFalse();
    }

    @Test
    void pythonServiceShouldSupportPythonFiles() { // GH-90000
        LanguageService pythonService =
                languageServices.stream() // GH-90000
                        .filter(service -> "python".equals(service.id())) // GH-90000
                        .findFirst() // GH-90000
                        .orElseThrow(); // GH-90000

        assertThat(pythonService.supports(Path.of("script.py"))).isTrue();
        assertThat(pythonService.supports(Path.of("src/main/python/module/__init__.py"))).isTrue();
        assertThat(pythonService.supports(Path.of("requirements.txt"))).isFalse();
    }
}
