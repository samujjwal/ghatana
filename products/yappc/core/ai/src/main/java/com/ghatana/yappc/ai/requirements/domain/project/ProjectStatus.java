package com.ghatana.yappc.ai.requirements.domain.project;

/**
 * Project lifecycle status enumeration.
 *
 * <p><b>Purpose</b><br>
 * Defines all possible states a project can transition through during its lifecycle.
 * Status transitions may trigger workflow approvals and org-unit updates.
 *
 * <p><b>Status Transitions</b><br>
 * - DRAFT: Initial state when project is created
 * - PLANNING: Requirements gathering and project setup phase
 * - ACTIVE: Development/delivery in progress
 * - ON_HOLD: Temporarily paused, can resume to ACTIVE
 * - COMPLETED: Successfully finished
 * - ARCHIVED: Closed and no longer active
 *
 * <p><b>Integration</b><br>
 * - Status changes may require workflow approval (COMPLETED, ARCHIVED)
 * - Maps to workflow task states
 * - Can trigger org-unit status updates
 *
 * @doc.type enum
 * @doc.purpose Project lifecycle status
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum ProjectStatus {
  /**
 * Being created, not yet in planning phase */
  DRAFT,
  /**
 * Requirements gathering, project setup */
  PLANNING,
  /**
 * Development or delivery in progress */
  ACTIVE,
  /**
 * Temporarily paused, can resume */
  ON_HOLD,
  /**
 * Successfully completed */
  COMPLETED,
  /**
 * Closed and archived */
  ARCHIVED
}