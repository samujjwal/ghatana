package com.ghatana.softwareorg.domain.devsecops;

/**
 * ProductLifecyclePhase represents the 10 product lifecycle phases used for
 * DevSecOps persona and phase classification.
 *
 * This enum mirrors ProductLifecyclePhaseProto in the DevSecOps contracts.
 */
public enum ProductLifecyclePhase {

    PROBLEM_DISCOVERY,
    IDEATION,
    PLAN_AND_SCOPE,
    DESIGN_AND_ARCHITECTURE,
    BUILD_AND_INTEGRATE,
    VALIDATE_AND_HARDEN,
    RELEASE,
    OPERATE,
    FEEDBACK_AND_LEARNING,
    RETIRE_OR_EVOLVE
}
