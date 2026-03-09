/**
 * Compliance Policy Management
 *
 * Centralized policy storage, versioning, and acknowledgment tracking.
 * Ensures all users acknowledge compliance policies within defined timeframes.
 *
 * Features:
 * - Policy versioning and history
 * - User acknowledgment tracking
 * - Automatic review reminders
 * - Audit trail of policy changes
 */

export enum PolicyStatus {
  DRAFT = 'DRAFT',
  ACTIVE = 'ACTIVE',
  DEPRECATED = 'DEPRECATED',
  ARCHIVED = 'ARCHIVED',
}

export interface PolicyAcknowledgment {
  email: string;
  acknowledgedDate: Date;
  version: string;
  ipAddress?: string;
}

export interface CompliancePolicy {
  id: string;
  name: string;
  framework: string;
  content: string;
  status: PolicyStatus;
  version: string;
  owner: string;
  createdDate: Date;
  updatedDate: Date;
  reviewCycle: number; // days
  acknowledgments: PolicyAcknowledgment[];
}

/**
 * Compliance policy manager
 *
 * GIVEN: Policies with versions and statuses
 * WHEN: createPolicy/updatePolicy called
 * THEN: Policies stored with version tracking and audit trail
 */
export class CompliancePolicyManager {
  private policies: Map<string, CompliancePolicy> = new Map();
  private history: Map<string, CompliancePolicy[]> = new Map();

  /**
   * Create new policy
   *
   * GIVEN: Policy details
   * WHEN: createPolicy called
   * THEN: Policy created with ID and timestamp
   */
  createPolicy(
    policy: Omit<CompliancePolicy, 'id' | 'createdDate' | 'updatedDate'>
  ): CompliancePolicy {
    const id = `policy-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    const now = new Date();

    const newPolicy: CompliancePolicy = {
      ...policy,
      id,
      createdDate: now,
      updatedDate: now,
    };

    this.policies.set(id, newPolicy);
    this.history.set(id, [newPolicy]);

    return newPolicy;
  }

  /**
   * Get policy by ID
   */
  getPolicy(id: string): CompliancePolicy | null {
    return this.policies.get(id) || null;
  }

  /**
   * Get all policies
   */
  getAllPolicies(): CompliancePolicy[] {
    return Array.from(this.policies.values());
  }

  /**
   * Get policies by status
   */
  getPoliciesByStatus(status: PolicyStatus): CompliancePolicy[] {
    return Array.from(this.policies.values()).filter((p) => p.status === status);
  }

  /**
   * Get policies by framework
   */
  getPoliciesByFramework(framework: string): CompliancePolicy[] {
    return Array.from(this.policies.values()).filter((p) => p.framework === framework);
  }

  /**
   * Update policy
   *
   * GIVEN: Policy ID and updates
   * WHEN: updatePolicy called
   * THEN: Policy updated with incremented version
   */
  updatePolicy(
    id: string,
    updates: Partial<Omit<CompliancePolicy, 'id' | 'createdDate' | 'version'>>
  ): CompliancePolicy | null {
    const existing = this.policies.get(id);
    if (!existing) return null;

    // Increment version
    const versionParts = existing.version.split('.');
    versionParts[1] = String((parseInt(versionParts[1]) || 0) + 1);
    const newVersion = versionParts.join('.');

    const updated: CompliancePolicy = {
      ...existing,
      ...updates,
      version: newVersion,
      updatedDate: new Date(),
    };

    this.policies.set(id, updated);

    // Keep history
    const policyHistory = this.history.get(id) || [];
    policyHistory.push(updated);
    this.history.set(id, policyHistory);

    return updated;
  }

  /**
   * Archive policy
   */
  archivePolicy(id: string): CompliancePolicy | null {
    return this.updatePolicy(id, { status: PolicyStatus.ARCHIVED });
  }

  /**
   * Deprecate policy
   */
  deprecatePolicy(id: string): CompliancePolicy | null {
    return this.updatePolicy(id, { status: PolicyStatus.DEPRECATED });
  }

  /**
   * Get policy history
   */
  getPolicyHistory(id: string): CompliancePolicy[] {
    return this.history.get(id) || [];
  }

  /**
   * Record user acknowledgment
   *
   * GIVEN: Policy ID and user email
   * WHEN: recordAcknowledgment called
   * THEN: Acknowledgment logged with timestamp and version
   */
  recordAcknowledgment(id: string, email: string, ipAddress?: string): boolean {
    const policy = this.policies.get(id);
    if (!policy) return false;

    // Check if already acknowledged this version
    if (policy.acknowledgments.some((a) => a.email === email && a.version === policy.version)) {
      return false;
    }

    policy.acknowledgments.push({
      email,
      acknowledgedDate: new Date(),
      version: policy.version,
      ipAddress,
    });

    return true;
  }

  /**
   * Check if user has acknowledged policy
   */
  hasAcknowledged(id: string, email: string, version?: string): boolean {
    const policy = this.policies.get(id);
    if (!policy) return false;

    const targetVersion = version || policy.version;
    return policy.acknowledgments.some((a) => a.email === email && a.version === targetVersion);
  }

  /**
   * Get policies needing acknowledgment
   *
   * GIVEN: User email
   * WHEN: getPoliciesNeedingAcknowledgment called
   * THEN: Active policies not acknowledged by user returned
   */
  getPoliciesNeedingAcknowledgment(email: string): CompliancePolicy[] {
    return Array.from(this.policies.values()).filter(
      (p) =>
        p.status === PolicyStatus.ACTIVE &&
        !p.acknowledgments.some((a) => a.email === email && a.version === p.version)
    );
  }

  /**
   * Get policies due for review
   *
   * GIVEN: Days threshold
   * WHEN: getPoliciesDueForReview called
   * THEN: Policies not reviewed within threshold returned
   */
  getPoliciesDueForReview(daysThreshold: number = 365): CompliancePolicy[] {
    const cutoffDate = new Date(Date.now() - daysThreshold * 24 * 60 * 60 * 1000);

    return Array.from(this.policies.values()).filter(
      (p) => p.status === PolicyStatus.ACTIVE && p.updatedDate < cutoffDate
    );
  }

  /**
   * Get acknowledgment statistics
   *
   * GIVEN: Policy ID
   * WHEN: getAcknowledgmentStats called
   * THEN: Acknowledgment coverage and status returned
   */
  getAcknowledgmentStats(id: string, allUsers: string[]) {
    const policy = this.policies.get(id);
    if (!policy) return null;

    const acknowledgedUsers = new Set(policy.acknowledgments.map((a) => a.email));
    const acknowledged = acknowledgedUsers.size;
    const total = allUsers.length;
    const coverage = Math.round((acknowledged / total) * 100);

    return {
      policyId: id,
      acknowledged,
      pending: total - acknowledged,
      total,
      coverage,
      pendingUsers: allUsers.filter((u) => !acknowledgedUsers.has(u)),
    };
  }

  /**
   * Search policies
   *
   * GIVEN: Search term and filters
   * WHEN: searchPolicies called
   * THEN: Matching policies returned
   */
  searchPolicies(
    query: string,
    filters?: { framework?: string; status?: PolicyStatus }
  ): CompliancePolicy[] {
    const lowerQuery = query.toLowerCase();

    return Array.from(this.policies.values()).filter((p) => {
      // Text search
      const matches =
        p.name.toLowerCase().includes(lowerQuery) ||
        p.content.toLowerCase().includes(lowerQuery) ||
        p.framework.toLowerCase().includes(lowerQuery);

      if (!matches) return false;

      // Apply filters
      if (filters?.framework && p.framework !== filters.framework) return false;
      if (filters?.status && p.status !== filters.status) return false;

      return true;
    });
  }

  /**
   * Export policies for compliance report
   */
  exportPolicies(format: 'json' | 'csv' = 'json'): string {
    const policies = Array.from(this.policies.values());

    if (format === 'csv') {
      const headers = ['ID', 'Name', 'Framework', 'Status', 'Version', 'Owner', 'Created Date'];
      const rows = policies.map((p) => [
        p.id,
        p.name,
        p.framework,
        p.status,
        p.version,
        p.owner,
        p.createdDate.toISOString(),
      ]);

      return [headers, ...rows].map((row) => row.map((cell) => `"${cell}"`).join(',')).join('\n');
    }

    return JSON.stringify(policies, null, 2);
  }

  /**
   * Cleanup old archived policies
   *
   * GIVEN: Retention period in days
   * WHEN: cleanup called
   * THEN: Archived policies older than retention removed
   */
  cleanup(retentionDays: number = 2555): number {
    // 2555 days = 7 years
    const cutoffDate = new Date(Date.now() - retentionDays * 24 * 60 * 60 * 1000);
    let deletedCount = 0;

    for (const [id, policy] of this.policies) {
      if (policy.status === PolicyStatus.ARCHIVED && policy.updatedDate < cutoffDate) {
        this.policies.delete(id);
        this.history.delete(id);
        deletedCount++;
      }
    }

    return deletedCount;
  }
}

/**
 * Global policy manager instance
 */
export const policyManager = new CompliancePolicyManager();
