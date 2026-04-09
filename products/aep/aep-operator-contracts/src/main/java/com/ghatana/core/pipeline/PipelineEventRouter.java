package com.ghatana.core.pipeline;

import com.ghatana.platform.domain.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Routes successful, error, and fallback events between pipeline stages.
 */
final class PipelineEventRouter {

    private static final Logger logger = LoggerFactory.getLogger(PipelineEventRouter.class);

    void routeOutputs(
            String stageId,
            StageExecutionResult result,
            PipelineExecutionGraph graph,
            Map<String, List<Event>> stageInputs
    ) {
        List<Event> outputs = result.getOutputEvents();
        if (outputs.isEmpty()) {
            logger.debug("Stage '{}' produced no output, activating fallback edges", stageId);
            activateFallbackEdges(stageId, graph, stageInputs);
            return;
        }

        for (PipelineEdgeTarget target : graph.primaryAdj().getOrDefault(stageId, List.of())) {
            stageInputs.computeIfAbsent(target.stageId(), ignored -> new ArrayList<>()).addAll(outputs);
            logger.trace("Routed {} event(s) from '{}' → '{}' (primary)",
                outputs.size(), stageId, target.stageId());
        }

        for (PipelineEdgeTarget target : graph.broadcastAdj().getOrDefault(stageId, List.of())) {
            stageInputs.computeIfAbsent(target.stageId(), ignored -> new ArrayList<>()).addAll(outputs);
            logger.trace("Routed {} event(s) from '{}' → '{}' (broadcast)",
                outputs.size(), stageId, target.stageId());
        }
    }

    void routeError(
            String stageId,
            List<Event> originalInputs,
            PipelineExecutionGraph graph,
            Map<String, List<Event>> stageInputs
    ) {
        List<PipelineEdgeTarget> errorTargets = graph.errorAdj().getOrDefault(stageId, List.of());
        if (errorTargets.isEmpty()) {
            logger.debug("Stage '{}' failed but has no error edges", stageId);
            return;
        }

        for (PipelineEdgeTarget target : errorTargets) {
            stageInputs.computeIfAbsent(target.stageId(), ignored -> new ArrayList<>()).addAll(originalInputs);
            logger.debug("Routed {} event(s) from '{}' → '{}' (error)",
                originalInputs.size(), stageId, target.stageId());
        }
    }

    void activateFallbackEdges(
            String stageId,
            PipelineExecutionGraph graph,
            Map<String, List<Event>> stageInputs
    ) {
        List<PipelineEdgeTarget> fallbackTargets = graph.fallbackAdj().getOrDefault(stageId, List.of());
        if (fallbackTargets.isEmpty()) {
            return;
        }

        Event fallbackEvent = Event.builder()
            .type("pipeline.fallback")
            .payload(Map.of(
                "_fallback_source_stage", stageId,
                "_fallback_reason", "no_output"
            ))
            .headers(Map.of(
                "stage", stageId,
                "fallback", "true"
            ))
            .build();

        for (PipelineEdgeTarget target : fallbackTargets) {
            stageInputs.computeIfAbsent(target.stageId(), ignored -> new ArrayList<>()).add(fallbackEvent);
            logger.debug("Activated fallback from '{}' → '{}'", stageId, target.stageId());
        }
    }
}
