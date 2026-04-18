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
    void buildsCatalogForAllPhases() {
        PhaseOperatorCatalog catalog = PhaseOperatorCatalog.fromServices(
                mock(IntentService.class),
                mock(ShapeService.class),
                mock(ValidationService.class),
                mock(GenerationService.class),
                mock(RunService.class),
                mock(ObserveService.class),
                mock(LearningService.class),
                mock(EvolutionService.class));

        assertThat(catalog.list()).hasSize(PhaseType.values().length);
        assertThat(catalog.supports("yappc.phase.intent")).isTrue();
        assertThat(catalog.supports("yappc.phase.evolve")).isTrue();
    }

    @Test
    @DisplayName("discovers operator by phase")
    void discoversByPhase() {
        PhaseOperatorCatalog catalog = PhaseOperatorCatalog.fromServices(
                mock(IntentService.class),
                mock(ShapeService.class),
                mock(ValidationService.class),
                mock(GenerationService.class),
                mock(RunService.class),
                mock(ObserveService.class),
                mock(LearningService.class),
                mock(EvolutionService.class));

        assertThat(catalog.getByPhase(PhaseType.VALIDATE)).isPresent();
        assertThat(catalog.getByPhase(PhaseType.VALIDATE).orElseThrow().getOperatorId())
                .isEqualTo("yappc.phase.validate");
    }
}