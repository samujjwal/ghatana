package com.ghatana.core.pipeline;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.core.operator.OperatorState;
import com.ghatana.core.operator.UnifiedOperator;
import com.ghatana.core.operator.catalog.OperatorCatalog;
import com.ghatana.platform.domain.event.Event;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Executes a single pipeline stage by resolving its operator and processing inputs.
 */
final class PipelineStageExecutor {

    private static final Logger logger = LoggerFactory.getLogger(PipelineStageExecutor.class);

    Promise<StageExecutionResult> executeSingleStage(
            String stageId,
            PipelineStage stage,
            List<Event> inputs,
            PipelineExecutionContext context
    ) {
        OperatorId operatorId = stage.operatorId();
        OperatorCatalog catalog = context.getOperatorCatalog();
        Instant stageStart = Instant.now();

        logger.debug("Executing stage '{}' with operator {} ({} input event(s))",
            stageId, operatorId, inputs.size());

        return catalog.get(operatorId)
            .then(optionalOperator -> {
                if (optionalOperator.isEmpty()) {
                    Duration duration = Duration.between(stageStart, Instant.now());
                    String message = String.format("Operator not found in catalog: %s (stage: %s)", operatorId, stageId);
                    logger.error(message);
                    return Promise.of(StageExecutionResult.failure(stageId, operatorId, inputs, duration, message));
                }

                UnifiedOperator operator = optionalOperator.get();
                OperatorState state = operator.getState();
                if (state != OperatorState.RUNNING && state != OperatorState.INITIALIZED) {
                    Duration duration = Duration.between(stageStart, Instant.now());
                    String message = String.format(
                        "Operator %s is in non-processable state: %s (stage: %s)",
                        operatorId,
                        state,
                        stageId);
                    logger.error(message);
                    return Promise.of(StageExecutionResult.failure(stageId, operatorId, inputs, duration, message));
                }

                return processInputEvents(operator, inputs)
                    .map(mergedResult -> {
                        Duration duration = Duration.between(stageStart, Instant.now());
                        if (mergedResult.isSuccess()) {
                            logger.debug("Stage '{}' succeeded: {} output event(s) in {}ms",
                                stageId, mergedResult.getOutputEvents().size(), duration.toMillis());
                            return StageExecutionResult.success(stageId, operatorId, inputs, mergedResult, duration);
                        }

                        logger.warn("Stage '{}' operator returned failure: {}",
                            stageId, mergedResult.getErrorMessage());
                        return StageExecutionResult.failure(
                            stageId,
                            operatorId,
                            inputs,
                            duration,
                            mergedResult.getErrorMessage());
                    })
                    .mapException(ex -> {
                        logger.error("Stage '{}' threw exception: {}", stageId, ex.getMessage(), ex);
                        return new RuntimeException(
                            String.format("Stage '%s' operator exception: %s", stageId, ex.getMessage()),
                            ex);
                    });
            })
            .mapException(ex -> {
                logger.error("Failed to resolve operator {} for stage '{}': {}",
                    operatorId, stageId, ex.getMessage(), ex);
                return new RuntimeException(
                    String.format("Operator catalog error for stage '%s': %s", stageId, ex.getMessage()),
                    ex);
            })
            .then(
                result -> Promise.of(result),
                ex -> {
                    Duration duration = Duration.between(stageStart, Instant.now());
                    return Promise.of(StageExecutionResult.failure(
                        stageId,
                        operatorId,
                        inputs,
                        duration,
                        ex.getMessage()));
                }
            );
    }

    private Promise<OperatorResult> processInputEvents(UnifiedOperator operator, List<Event> inputs) {
        try {
            if (inputs.size() == 1) {
                return operator.process(inputs.get(0));
            }
            return processSequential(operator, inputs, 0, OperatorResult.builder().success());
        } catch (Exception e) {
            logger.warn("Operator '{}' threw synchronously during process(): {}",
                operator.getId(), e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    private Promise<OperatorResult> processSequential(
            UnifiedOperator operator,
            List<Event> inputs,
            int index,
            OperatorResult.Builder accumulator
    ) {
        if (index >= inputs.size()) {
            return Promise.of(accumulator.build());
        }

        return operator.process(inputs.get(index))
            .then(result -> {
                accumulator.mergeWith(result);
                return processSequential(operator, inputs, index + 1, accumulator);
            });
    }
}