package com.ghatana.yappc.knowledge.model;

import java.util.List;

/**

 * @doc.type record

 * @doc.purpose Immutable data carrier for yappc impact analysis

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public record YAPPCImpactAnalysis(
    String componentId,
    List<YAPPCGraphNode> affectedNodes,
    double impactScore,
    List<String> recommendations
) {}
