package com.ghatana.core.pipeline;

import java.util.List;
import java.util.Map;

record PipelineExecutionGraph(
        Map<String, PipelineStage> stageMap,
        Map<String, List<PipelineEdgeTarget>> primaryAdj,
        Map<String, List<PipelineEdgeTarget>> errorAdj,
        Map<String, List<PipelineEdgeTarget>> fallbackAdj,
        Map<String, List<PipelineEdgeTarget>> broadcastAdj,
        List<String> sourceStages,
        List<String> terminalStages,
        List<String> topoOrder
) {
}
