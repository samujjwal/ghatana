/**
 * Compliance Control Entity
 *
 * Represents a single compliance control from a framework (SOC2, ISO 27001, HIPAA).
 * Tracks assessment status, evidence, and remediation progress.
 *
 * @see ControlAssessmentService for assessment operations
 * @see ComplianceControlRepository for data access
 */

export enum ComplianceFramework {
  SOC2 = 'SOC2',
  ISO_27001 = 'ISO_27001',
  HIPAA = 'HIPAA',
  GDPR = 'GDPR',
  PCI_DSS = 'PCI_DSS',
}

export enum ControlStatus {
  NOT_STARTED = 'NOT_STARTED',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLIANT = 'COMPLIANT',
  NON_COMPLIANT = 'NON_COMPLIANT',
  PARTIALLY_COMPLIANT = 'PARTIALLY_COMPLIANT',
  NEEDS_REMEDIATION = 'NEEDS_REMEDIATION',
}

export interface IComplianceControl {
  id: string;
  framework: ComplianceFramework;
  controlId: string;
  title: string;
  description: string;
  status: ControlStatus;
  score?: number;
  lastAssessmentDate?: Date;
  nextAssessmentDate: Date;
  evidenceIds: string[];
  remediationPlan?: string;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * ComplianceControl entity class
 *
 * GIVEN: Control from compliance framework
 * WHEN: Assessed by ControlAssessmentService
 * THEN: Status updated, evidence linked, compliance tracked
 *
 * Database: PostgreSQL table 'compliance_controls'
 * Indexes: framework, control_id, status, assessment_date
 */
export class ComplianceControl implements IComplianceControl {
  id: string;
  framework: ComplianceFramework;
  controlId: string;
  title: string;
  description: string;
  status: ControlStatus;
  score?: number;
  lastAssessmentDate?: Date;
  nextAssessmentDate: Date;
  evidenceIds: string[] = [];
  remediationPlan?: string;
  createdAt: Date = new Date();
  updatedAt: Date = new Date();

  constructor(
    id: string,
    framework: ComplianceFramework,
    controlId: string,
    title: string,
    description: string
  ) {
    this.id = id;
    this.framework = framework;
    this.controlId = controlId;
    this.title = title;
    this.description = description;
    this.status = ControlStatus.NOT_STARTED;
    this.nextAssessmentDate = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000); // 30 days from now
  }

  /**
   * Update control status
   */
  updateStatus(status: ControlStatus, score?: number): void {
    this.status = status;
    if (score !== undefined) {
      this.score = Math.max(0, Math.min(100, score));
    }
    this.lastAssessmentDate = new Date();
    this.touch();
  }

  /**
   * Add evidence reference
   */
  addEvidence(evidenceId: string): void {
    if (!this.evidenceIds.includes(evidenceId)) {
      this.evidenceIds.push(evidenceId);
      this.touch();
    }
  }

  /**
   * Set remediation plan
   */
  setRemediationPlan(plan: string): void {
    this.remediationPlan = plan;
    this.status = ControlStatus.NEEDS_REMEDIATION;
    this.touch();
  }

  /**
   * Schedule next assessment
   */
  scheduleNextAssessment(daysFromNow: number = 90): void {
    this.nextAssessmentDate = new Date(Date.now() + daysFromNow * 24 * 60 * 60 * 1000);
    this.touch();
  }

  /**
   * Mark as updated
   */
  private touch(): void {
    this.updatedAt = new Date();
  }

  /**
   * Is assessment overdue?
   */
  isAssessmentOverdue(): boolean {
    return this.nextAssessmentDate < new Date();
  }

  /**
   * Validate control
   */
  validate(): boolean {
    if (!this.id || !this.controlId || !this.title) {
      throw new Error('Invalid control: required fields missing');
    }
    if (!Object.values(ComplianceFramework).includes(this.framework)) {
      throw new Error('Invalid framework');
    }
    return true;
  }

  /**
   * Convert to JSON
   */
  toJSON(): Record<string, unknown> {
    return {
      id: this.id,
      framework: this.framework,
      controlId: this.controlId,
      title: this.title,
      description: this.description,
      status: this.status,
      score: this.score,
      lastAssessmentDate: this.lastAssessmentDate?.toISOString(),
      nextAssessmentDate: this.nextAssessmentDate.toISOString(),
      evidenceIds: this.evidenceIds,
      remediationPlan: this.remediationPlan,
      createdAt: this.createdAt.toISOString(),
      updatedAt: this.updatedAt.toISOString(),
    };
  }

  /**
   * Create from JSON
   */
  static fromJSON(data: Partial<ComplianceControl>): ComplianceControl {
    const control = new ComplianceControl(
      data.id || '',
      data.framework || ComplianceFramework.SOC2,
      data.controlId || '',
      data.title || '',
      data.description || ''
    );
    if (data.status) control.status = data.status;
    if (data.score) control.score = data.score;
    if (data.lastAssessmentDate) control.lastAssessmentDate = new Date(data.lastAssessmentDate);
    if (data.nextAssessmentDate) control.nextAssessmentDate = new Date(data.nextAssessmentDate);
    if (data.evidenceIds) control.evidenceIds = data.evidenceIds;
    if (data.remediationPlan) control.remediationPlan = data.remediationPlan;
    return control;
  }
}
