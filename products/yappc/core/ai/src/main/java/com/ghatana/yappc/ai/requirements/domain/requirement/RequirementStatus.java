package com.ghatana.yappc.ai.requirements.domain.requirement;

/**
 * Requirement lifecycle status.
 *
 * <p><b>Purpose</b><br>
 * Tracks requirement through its lifecycle from creation to deprecation.
 * Maps to workflow task states for approval tracking.
 *
 * <p><b>Lifecycle</b><br>
 * DRAFT → PENDING_REVIEW → IN_REVIEW → {APPROVED|REJECTED} →
 * IMPLEMENTED → VERIFIED or DEPRECATED
 *
 * <p><b>Integration</b><br>
 * Status transitions trigger workflow events and approvals.
 * Maps to DecisionWorkflowEngine task states.
 *
 * @doc.type enum
 * @doc.purpose Requirement lifecycle status
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum RequirementStatus {
  /**
 * Being written, not yet submitted */
  DRAFT,
  /**
 * Submitted, waiting for review */
  PENDING_REVIEW,
  /**
 * Currently being reviewed */
  IN_REVIEW,
  /**
 * Approved by stakeholders */
  APPROVED,
  /**
 * Rejected, needs revision */
  REJECTED,
  /**
 * Implemented in product */
  IMPLEMENTED,
  /**
 * Tested and verified working */
  VERIFIED,
  /**
 * No longer valid, deprecated */
  DEPRECATED
}