/**
 * Control Assessment Service
 *
 * Service for evaluating compliance controls and generating scores.
 * Orchestrates evidence collection, review, and status tracking.
 *
 * @see ControlAssessmentService for assessment logic
 */

import { ComplianceControl, ControlStatus } from '../../models/compliance/ComplianceControl.entity';

export interface IControlAssessmentService {
  assessControl(controlId: string, evidence: string[]): Promise<{ score: number; status: ControlStatus }>;
  scoreControl(controlId: string): Promise<number>;
  trackEvidence(controlId: string, evidenceId: string): Promise<void>;
  updateStatus(controlId: string, status: ControlStatus): Promise<void>;
}

/**
 * Control Assessment Service Implementation
 *
 * GIVEN: Control to assess
 * WHEN: assessControl called with evidence
 * THEN: Score calculated, status updated
 *
 * Scoring logic:
 * - 0-40 evidence: Non-compliant
 * - 40-70 evidence: Partially compliant
 * - 70-100 evidence: Compliant
 */
export class ControlAssessmentService implements IControlAssessmentService {
  private controls: Map<string, ComplianceControl> = new Map();

  /**
   * Assess control with evidence
   *
   * GIVEN: Control ID and evidence list
   * WHEN: assessControl called
   * THEN: Score calculated and status determined
   */
  async assessControl(controlId: string, evidence: string[]): Promise<{ score: number; status: ControlStatus }> {
    const score = Math.min(100, Math.max(0, evidence.length * 25));

    let status: ControlStatus;
    if (score >= 70) {
      status = ControlStatus.COMPLIANT;
    } else if (score >= 40) {
      status = ControlStatus.PARTIALLY_COMPLIANT;
    } else {
      status = ControlStatus.NON_COMPLIANT;
    }

    const control = this.controls.get(controlId);
    if (control) {
      control.updateStatus(status, score);
      await this.updateStatus(controlId, status);
    }

    return { score, status };
  }

  /**
   * Get control score
   */
  async scoreControl(controlId: string): Promise<number> {
    const control = this.controls.get(controlId);
    return control?.score || 0;
  }

  /**
   * Add evidence to control
   */
  async trackEvidence(controlId: string, evidenceId: string): Promise<void> {
    const control = this.controls.get(controlId);
    if (control) {
      control.addEvidence(evidenceId);
    }
  }

  /**
   * Update control status
   */
  async updateStatus(controlId: string, status: ControlStatus): Promise<void> {
    const control = this.controls.get(controlId);
    if (control) {
      control.updateStatus(status, control.score);
    }
  }
}

/**
 * Mock service for testing
 */
export class MockControlAssessmentService implements IControlAssessmentService {
  assessedControls: Map<string, { score: number; status: ControlStatus }> = new Map();

  async assessControl(controlId: string, evidence: string[]): Promise<{ score: number; status: ControlStatus }> {
    const score = Math.min(100, evidence.length * 25);
    const status = score >= 70 ? ControlStatus.COMPLIANT : ControlStatus.NON_COMPLIANT;
    this.assessedControls.set(controlId, { score, status });
    return { score, status };
  }

  async scoreControl(controlId: string): Promise<number> {
    return this.assessedControls.get(controlId)?.score || 0;
  }

  async trackEvidence(controlId: string, evidenceId: string): Promise<void> {
    // Mock implementation
  }

  async updateStatus(controlId: string, status: ControlStatus): Promise<void> {
    const existing = this.assessedControls.get(controlId);
    if (existing) {
      this.assessedControls.set(controlId, { ...existing, status });
    }
  }
}
