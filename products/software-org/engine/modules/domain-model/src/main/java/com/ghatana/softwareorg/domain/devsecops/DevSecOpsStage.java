package com.ghatana.softwareorg.domain.devsecops;

/**
 * DevSecOpsStage represents the 16 stages of the DevSecOps pipeline.
 *
 * This enum is the domain-level mirror of the DevSecOpsStageProto contract
 * in the ghatana.sw_org.devsecops.agent.v1 (Java package
 * com.ghatana.contracts.sw_org.devsecops.agent.v1) namespace.
 */
public enum DevSecOpsStage {

    PLAN,
    SOLUTION,
    DESIGN,
    DEVELOP,
    BUILD,
    TEST,
    SECURE,
    COMPLIANCE_VALIDATION,
    RELEASE_VALIDATION,
    UAT,
    PACKAGE_RELEASE,
    DEPLOY,
    OPERATE,
    MONITOR,
    BACKUP,
    RETROSPECTIVE
}
