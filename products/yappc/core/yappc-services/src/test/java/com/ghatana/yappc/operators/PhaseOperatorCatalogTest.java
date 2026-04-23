package com.ghatana.yappc.operators;

import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.validate.ValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("PhaseOperatorCatalog")
class PhaseOperatorCatalogTest {

    @Test
    @DisplayName("builds catalog with all lifecycle phase operators")
    void buildsCatalogForAllPhases() { // GH-90000
        PhaseOperatorCatalog catalog = PhaseOperatorCatalog.fromServices( // GH-90000
                mock(IntentService.class), // GH-90000
                mock(ShapeService.class), // GH-90000
                mock(ValidationService.class), // GH-90000
                mock(GenerationService.class), // GH-90000
                mock(RunService.class), // GH-90000
                mock(ObserveService.class), // GH-90000
                mock(LearningService.class), // GH-90000
                mock(EvolutionService.class)); // GH-90000

        assertThat(catalog.list()).hasSize(PhaseType.values().length); // GH-90000
        assertThat(catalog.supports("yappc.phase.intent")).isTrue();
        assertThat(catalog.supports("yappc.phase.evolve")).isTrue();
    }

    @Test
    @DisplayName("discovers operator by phase")
    void discoversByPhase() { // GH-90000
        PhaseOperatorCatalog catalog = PhaseOperatorCatalog.fromServices( // GH-90000
                mock(IntentService.class), // GH-90000
                mock(ShapeService.class), // GH-90000
                mock(ValidationService.class), // GH-90000
                mock(GenerationService.class), // GH-90000
                mock(RunService.class), // GH-90000
                mock(ObserveService.class), // GH-90000
                mock(LearningService.class), // GH-90000
                mock(EvolutionService.class)); // GH-90000

        assertThat(catalog.getByPhase(PhaseType.VALIDATE)).isPresent(); // GH-90000
        assertThat(catalog.getByPhase(PhaseType.VALIDATE).orElseThrow().getOperatorId()) // GH-90000
                .isEqualTo("yappc.phase.validate");
    }
}