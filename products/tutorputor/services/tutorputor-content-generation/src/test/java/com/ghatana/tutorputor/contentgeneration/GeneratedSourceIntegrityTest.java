package com.ghatana.tutorputor.contentgeneration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Generated Source Integrity")
class GeneratedSourceIntegrityTest {

    @Test
    @DisplayName("Platform generator compiles against canonical getter-based source models without repair scripts")
    void platformGeneratorUsesCanonicalGetterModelsWithoutRepairScripts() throws IOException {
        Path moduleRoot = Path.of("").toAbsolutePath();
        Path repairScript = moduleRoot.resolve("src/fix_getters.sh");
        Path generatorSource = moduleRoot.resolve("src/main/java/com/ghatana/tutorputor/contentgeneration/PlatformContentGenerator.java");

        String source = Files.readString(generatorSource);

        assertThat(repairScript).doesNotExist();
        assertThat(source)
                .contains("request.getTopic()")
                .contains("request.getGradeLevel()")
                .contains("request.getDomain().name()")
                .contains("claim.getId()")
                .contains("claim.getText()");
        assertThat(source)
                .doesNotContain("request.topic()")
                .doesNotContain("claim.id()")
                .doesNotContain("claim.text()");
    }
}
