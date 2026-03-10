package com.ghatana.yappc.agent.specialists;

import java.util.List;

/**
 * Input for HITL Review Specialist.
 *
 * @doc.type record
 * @doc.purpose Input for human-in-the-loop review
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record HITLReviewInput(
    String architectureId,
    String designDocument,
    List<String> contracts,
    List<String> dataModels,
    List<String> reviewers,
    String priority) {}
