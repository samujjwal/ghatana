package com.ghatana.yappc.operators;

import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.validate.ValidationService;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.evolve.EvolutionService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose YAPPC phase execution as UnifiedOperator
 * @doc.layer operator
 * @doc.pattern Operator
 */
public class PhaseOperator {
    
    private static final Logger log = LoggerFactory.getLogger(PhaseOperator.class);
    
    private final PhaseType phase;
    private final IntentService intentService;
    private final ShapeService shapeService;
    private final ValidationService validationService;
    private final GenerationService generationService;
    private final RunService runService;
    private final ObserveService observeService;
    private final LearningService learningService;
    private final EvolutionService evolutionService;
    
    public PhaseOperator(
            PhaseType phase,
            IntentService intentService,
            ShapeService shapeService,
            ValidationService validationService,
            GenerationService generationService,
            RunService runService,
            ObserveService observeService,
            LearningService learningService,
            EvolutionService evolutionService) {
        this.phase = phase;
        this.intentService = intentService;
        this.shapeService = shapeService;
        this.validationService = validationService;
        this.generationService = generationService;
        this.runService = runService;
        this.observeService = observeService;
        this.learningService = learningService;
        this.evolutionService = evolutionService;
    }
    
    /**
     * Executes the phase operation.
     * 
     * @param input Input data for the phase
     * @return Promise of execution result
     */
    public Promise<Object> execute(Object input) {
        log.info("Executing phase: {}", phase);
        
        return switch (phase) {
            case INTENT -> executeIntent(input);
            case SHAPE -> executeShape(input);
            case VALIDATE -> executeValidate(input);
            case GENERATE -> executeGenerate(input);
            case RUN -> executeRun(input);
            case OBSERVE -> executeObserve(input);
            case LEARN -> executeLearn(input);
            case EVOLVE -> executeEvolve(input);
        };
    }
    
    private Promise<Object> executeIntent(Object input) {
        if (input instanceof com.ghatana.yappc.domain.intent.IntentInput intentInput) {
            return intentService.capture(intentInput)
                    .map(result -> (Object) result);
        }
        return Promise.ofException(new IllegalArgumentException("Invalid input type for Intent phase"));
    }
    
    private Promise<Object> executeShape(Object input) {
        if (input instanceof com.ghatana.yappc.domain.intent.IntentSpec intentSpec) {
            return shapeService.derive(intentSpec)
                    .map(result -> (Object) result);
        }
        return Promise.ofException(new IllegalArgumentException("Invalid input type for Shape phase"));
    }
    
    private Promise<Object> executeValidate(Object input) {
        if (input instanceof com.ghatana.yappc.domain.shape.ShapeSpec shapeSpec) {
            return validationService.validate(shapeSpec)
                    .map(result -> (Object) result);
        }
        return Promise.ofException(new IllegalArgumentException("Invalid input type for Validate phase"));
    }
    
    private Promise<Object> executeGenerate(Object input) {
        if (input instanceof com.ghatana.yappc.domain.generate.ValidatedSpec validatedSpec) {
            return generationService.generate(validatedSpec)
                    .map(result -> (Object) result);
        }
        return Promise.ofException(new IllegalArgumentException("Invalid input type for Generate phase"));
    }
    
    private Promise<Object> executeRun(Object input) {
        if (input instanceof com.ghatana.yappc.domain.run.RunSpec runSpec) {
            return runService.execute(runSpec)
                    .map(result -> (Object) result);
        }
        return Promise.ofException(new IllegalArgumentException("Invalid input type for Run phase"));
    }
    
    private Promise<Object> executeObserve(Object input) {
        if (input instanceof com.ghatana.yappc.domain.run.RunResult runResult) {
            return observeService.collect(runResult)
                    .map(result -> (Object) result);
        }
        return Promise.ofException(new IllegalArgumentException("Invalid input type for Observe phase"));
    }
    
    private Promise<Object> executeLearn(Object input) {
        if (input instanceof com.ghatana.yappc.domain.observe.Observation observation) {
            return learningService.analyze(observation)
                    .map(result -> (Object) result);
        }
        return Promise.ofException(new IllegalArgumentException("Invalid input type for Learn phase"));
    }
    
    private Promise<Object> executeEvolve(Object input) {
        if (input instanceof com.ghatana.yappc.domain.learn.Insights insights) {
            return evolutionService.propose(insights)
                    .map(result -> (Object) result);
        }
        return Promise.ofException(new IllegalArgumentException("Invalid input type for Evolve phase"));
    }
    
    /**
     * Gets the operator ID for this phase.
     * 
     * @return Operator ID
     */
    public String getOperatorId() {
        return "yappc.phase." + phase.name().toLowerCase();
    }
    
    /**
     * Gets metadata about this operator.
     * 
     * @return Operator metadata
     */
    public Map<String, String> getMetadata() {
        return Map.of(
            "phase", phase.name(),
            "operator_id", getOperatorId(),
            "version", "1.0.0"
        );
    }
}
