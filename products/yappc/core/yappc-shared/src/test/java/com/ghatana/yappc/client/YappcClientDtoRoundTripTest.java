package com.ghatana.yappc.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("YAPPC Client DTO Round-Trip Tests")
class YappcClientDtoRoundTripTest {

    @Test
    @DisplayName("CanvasResult preserves constructor values")
    void canvasResult_roundTrip() {
        CanvasResult result = new CanvasResult("canvas-1", "Primary Canvas", true);

        assertThat(result.getCanvasId()).isEqualTo("canvas-1");
        assertThat(result.getName()).isEqualTo("Primary Canvas");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("ValidationContext factory produces expected defaults")
    void validationContext_factoryRoundTrip() {
        ValidationContext context = ValidationContext.forPhase("analysis");

        assertThat(context.getPhase()).isEqualTo("analysis");
        assertThat(context.isStrict()).isFalse();
    }

    @Test
    @DisplayName("GenerationResult defensively copies artifact list")
    void generationResult_defensiveListCopy() {
        List<GenerationResult.GeneratedArtifact> source = new ArrayList<>();
        source.add(new GenerationResult.GeneratedArtifact("src/Main.java", "class Main {}", "JAVA"));

        GenerationResult result = new GenerationResult(true, source);
        source.clear();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getArtifacts()).hasSize(1);
        assertThat(result.getArtifacts().getFirst().getPath()).isEqualTo("src/Main.java");
    }

    @Test
    @DisplayName("KnowledgeDocument defensively copies metadata map")
    void knowledgeDocument_defensiveMapCopy() {
        Map<String, Object> source = new HashMap<>();
        source.put("tenant", "t-1");

        KnowledgeDocument document = new KnowledgeDocument("doc-1", "Title", "Body", source);
        source.put("tenant", "mutated");

        assertThat(document.getId()).isEqualTo("doc-1");
        assertThat(document.getTitle()).isEqualTo("Title");
        assertThat(document.getContent()).isEqualTo("Body");
        assertThat(document.getMetadata()).containsEntry("tenant", "t-1");
    }
}
