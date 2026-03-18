/**
 * Compliance Control Repository
 *
 * Data access layer for compliance controls.
 * Provides querying, filtering, and persistence operations.
 *
 * @see ControlAssessmentService for business logic
 */

import { ComplianceControl, ComplianceFramework, ControlStatus } from '../models/compliance/ComplianceControl.entity';

export interface IComplianceControlRepository {
  save(control: ComplianceControl): Promise<void>;
  findById(id: string): Promise<ComplianceControl | null>;
  findByFramework(framework: ComplianceFramework): Promise<ComplianceControl[]>;
  findByStatus(status: ControlStatus): Promise<ComplianceControl[]>;
  findOverdueAssessments(): Promise<ComplianceControl[]>;
  findAll(): Promise<ComplianceControl[]>;
  update(control: ComplianceControl): Promise<void>;
  delete(id: string): Promise<boolean>;
}

/**
 * In-memory repository implementation for development/testing
 *
 * GIVEN: ComplianceControl objects
 * WHEN: Saved to repository
 * THEN: Can be queried by various criteria
 *
 * Production: Replace with TypeORM repository
 */
export class InMemoryComplianceControlRepository implements IComplianceControlRepository {
  private controls: Map<string, unknown> = new Map();

  async save(control: ComplianceControl): Promise<void> {
    control.validate();
    this.controls.set(control.id, control);
  }

  async findById(id: string): Promise<ComplianceControl | null> {
    const data = this.controls.get(id);
    return data ? ComplianceControl.fromJSON(data) : null;
  }

  async findByFramework(framework: ComplianceFramework): Promise<ComplianceControl[]> {
    return Array.from(this.controls.values())
      .filter((c: unknown) => c.framework === framework)
      .map((c: unknown) => ComplianceControl.fromJSON(c));
  }

  async findByStatus(status: ControlStatus): Promise<ComplianceControl[]> {
    return Array.from(this.controls.values())
      .filter((c: unknown) => c.status === status)
      .map((c: unknown) => ComplianceControl.fromJSON(c));
  }

  async findOverdueAssessments(): Promise<ComplianceControl[]> {
    return Array.from(this.controls.values())
      .map((c: unknown) => ComplianceControl.fromJSON(c))
      .filter((c) => c.isAssessmentOverdue());
  }

  async findAll(): Promise<ComplianceControl[]> {
    return Array.from(this.controls.values()).map((c: unknown) => ComplianceControl.fromJSON(c));
  }

  async update(control: ComplianceControl): Promise<void> {
    if (!this.controls.has(control.id)) {
      throw new Error(`Control not found: ${control.id}`);
    }
    control.validate();
    this.controls.set(control.id, control);
  }

  async delete(id: string): Promise<boolean> {
    return this.controls.delete(id);
  }
}

/**
 * Mock repository for testing
 */
export class MockComplianceControlRepository implements IComplianceControlRepository {
  savedControls: ComplianceControl[] = [];
  foundControls: ComplianceControl[] = [];

  async save(control: ComplianceControl): Promise<void> {
    this.savedControls.push(control);
  }

  async findById(id: string): Promise<ComplianceControl | null> {
    return this.foundControls.find((c) => c.id === id) || null;
  }

  async findByFramework(framework: ComplianceFramework): Promise<ComplianceControl[]> {
    return this.foundControls.filter((c) => c.framework === framework);
  }

  async findByStatus(status: ControlStatus): Promise<ComplianceControl[]> {
    return this.foundControls.filter((c) => c.status === status);
  }

  async findOverdueAssessments(): Promise<ComplianceControl[]> {
    return this.foundControls.filter((c) => c.isAssessmentOverdue());
  }

  async findAll(): Promise<ComplianceControl[]> {
    return this.foundControls;
  }

  async update(control: ComplianceControl): Promise<void> {
    const index = this.foundControls.findIndex((c) => c.id === control.id);
    if (index >= 0) {
      this.foundControls[index] = control;
    }
  }

  async delete(id: string): Promise<boolean> {
    const before = this.foundControls.length;
    this.foundControls = this.foundControls.filter((c) => c.id !== id);
    return this.foundControls.length < before;
  }
}
