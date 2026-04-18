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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Operator catalog for YAPPC lifecycle phase operators.
 *
 * @doc.type class
 * @doc.purpose Registers and discovers PhaseOperator instances for lifecycle execution
 * @doc.layer operator
 * @doc.pattern Registry
 */
public final class PhaseOperatorCatalog {

    private final ConcurrentMap<String, PhaseOperator> operatorsById = new ConcurrentHashMap<>();

    public void register(PhaseOperator operator) {
        operatorsById.put(operator.getOperatorId(), operator);
    }

    public Optional<PhaseOperator> get(String operatorId) {
        return Optional.ofNullable(operatorsById.get(operatorId));
    }

    public Optional<PhaseOperator> getByPhase(PhaseType phase) {
        return get(toOperatorId(phase));
    }

    public boolean supports(String operatorId) {
        return operatorsById.containsKey(operatorId);
    }

    public List<Map<String, String>> metadata() {
        return operatorsById.values().stream()
                .map(PhaseOperator::getMetadata)
                .toList();
    }

    public Collection<PhaseOperator> list() {
        return List.copyOf(operatorsById.values());
    }

    public static PhaseOperatorCatalog fromServices(
            IntentService intentService,
            ShapeService shapeService,
            ValidationService validationService,
            GenerationService generationService,
            RunService runService,
            ObserveService observeService,
            LearningService learningService,
            EvolutionService evolutionService
    ) {
        PhaseOperatorCatalog catalog = new PhaseOperatorCatalog();
        for (PhaseType phase : PhaseType.values()) {
            catalog.register(new PhaseOperator(
                    phase,
                    intentService,
                    shapeService,
                    validationService,
                    generationService,
                    runService,
                    observeService,
                    learningService,
                    evolutionService));
        }
        return catalog;
    }

    private static String toOperatorId(PhaseType phase) {
        return "yappc.phase." + phase.name().toLowerCase();
    }
}