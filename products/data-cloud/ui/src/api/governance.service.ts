/**
 * Governance & Policy API Service
 *
 * Provides API client for policy management, compliance, and audit operations.
 *
 * @doc.type service
 * @doc.purpose Governance and policy API client
 * @doc.layer frontend
 */

import axios, { AxiosInstance } from 'axios';

export interface Policy {
  id: string;
  name: string;
  type: 'SECURITY' | 'PRIVACY' | 'RETENTION' | 'ACCESS' | 'QUALITY';
  scope: {
    datasets?: string[];
    users?: string[];
    roles?: string[];
  };
  rules: PolicyRule[];
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  metadata: Record<string, any>;
}

export interface PolicyRule {
  condition: string;
  action: 'ALLOW' | 'DENY' | 'MASK' | 'AUDIT' | 'REQUIRE_APPROVAL';
  severity: 'INFO' | 'WARNING' | 'ERROR';
  metadata?: Record<string, any>;
}

export interface PolicyViolation {
  id: string;
  policyId: string;
  policyName: string;
  timestamp: string;
  userId: string;
  datasetId: string;
  action: string;
  status: 'BLOCKED' | 'WARNED' | 'APPROVED' | 'PENDING_APPROVAL';
  details: string;
  metadata: Record<string, any>;
}

export interface ComplianceReport {
  id: string;
  period: string;
  generatedAt: string;
  summary: {
    totalPolicies: number;
    activePolicies: number;
    violations: number;
    remediations: number;
    complianceScore: number;
  };
  details: {
    piiScans: {
      totalDatasets: number;
      datasetsWithPII: number;
      violations: number;
      remediated: number;
    };
    accessAudits: {
      totalAccesses: number;
      unauthorizedAttempts: number;
      blockedAccesses: number;
    };
    retentionCompliance: {
      datasetsCompliant: number;
      datasetsViolating: number;
    };
  };
}

export interface AuditLog {
  id: string;
  timestamp: string;
  userId: string;
  userName: string;
  action: string;
  resourceType: string;
  resourceId: string;
  outcome: 'SUCCESS' | 'FAILURE' | 'BLOCKED';
  details: Record<string, any>;
}

export interface AccessRequest {
  id: string;
  requestedBy: string;
  datasetId: string;
  datasetName: string;
  reason: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  requestedAt: string;
  reviewedBy?: string;
  reviewedAt?: string;
}

/**
 * Governance Service Client
 */
export class GovernanceService {
  private client: AxiosInstance;

  constructor(baseURL: string = '/api') {
    this.client = axios.create({
      baseURL,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }

  // ==================== Policies ====================

  /**
   * Get all policies
   */
  async getPolicies(type?: string): Promise<Policy[]> {
    const response = await this.client.get<Policy[]>('/policies', {
      params: { type },
    });
    return response.data;
  }

  /**
   * Get policy by ID
   */
  async getPolicy(policyId: string): Promise<Policy> {
    const response = await this.client.get<Policy>(`/policies/${policyId}`);
    return response.data;
  }

  /**
   * Create policy
   */
  async createPolicy(policy: Partial<Policy>): Promise<Policy> {
    const response = await this.client.post<Policy>('/policies', policy);
    return response.data;
  }

  /**
   * Update policy
   */
  async updatePolicy(policyId: string, policy: Partial<Policy>): Promise<Policy> {
    const response = await this.client.put<Policy>(`/policies/${policyId}`, policy);
    return response.data;
  }

  /**
   * Delete policy
   */
  async deletePolicy(policyId: string): Promise<void> {
    await this.client.delete(`/policies/${policyId}`);
  }

  /**
   * Enable/disable policy
   */
  async togglePolicy(policyId: string, enabled: boolean): Promise<Policy> {
    const response = await this.client.patch<Policy>(`/policies/${policyId}`, {
      enabled,
    });
    return response.data;
  }

  // ==================== Violations ====================

  /**
   * Get policy violations
   */
  async getViolations(
    policyId?: string,
    limit: number = 50
  ): Promise<PolicyViolation[]> {
    const response = await this.client.get<PolicyViolation[]>('/governance/violations', {
      params: { policyId, limit },
    });
    return response.data;
  }

  /**
   * Resolve violation
   */
  async resolveViolation(violationId: string, resolution: string): Promise<void> {
    await this.client.post(`/governance/violations/${violationId}/resolve`, {
      resolution,
    });
  }

  // ==================== Compliance ====================

  /**
   * Get compliance report
   */
  async getComplianceReport(period: string = '30d'): Promise<ComplianceReport> {
    const response = await this.client.get<ComplianceReport>('/governance/reports', {
      params: { period },
    });
    return response.data;
  }

  /**
   * Generate compliance report
   */
  async generateComplianceReport(
    period: string
  ): Promise<{ reportId: string; status: string }> {
    const response = await this.client.post('/governance/reports', { period });
    return response.data;
  }

  /**
   * Download compliance report
   */
  async downloadComplianceReport(reportId: string): Promise<Blob> {
    const response = await this.client.get(`/governance/reports/${reportId}/download`, {
      responseType: 'blob',
    });
    return response.data;
  }

  // ==================== Audit ====================

  /**
   * Get audit logs
   */
  async getAuditLogs(
    resourceType?: string,
    userId?: string,
    limit: number = 100
  ): Promise<AuditLog[]> {
    const response = await this.client.get<AuditLog[]>('/audit/logs', {
      params: { resourceType, userId, limit },
    });
    return response.data;
  }

  /**
   * Search audit logs
   */
  async searchAuditLogs(query: string): Promise<AuditLog[]> {
    const response = await this.client.get<AuditLog[]>('/audit/logs/search', {
      params: { q: query },
    });
    return response.data;
  }

  // ==================== Access Requests ====================

  /**
   * Get access requests
   */
  async getAccessRequests(status?: string): Promise<AccessRequest[]> {
    const response = await this.client.get<AccessRequest[]>('/governance/access-requests', {
      params: { status },
    });
    return response.data;
  }

  /**
   * Request dataset access
   */
  async requestAccess(
    datasetId: string,
    reason: string
  ): Promise<AccessRequest> {
    const response = await this.client.post<AccessRequest>('/governance/access-requests', {
      datasetId,
      reason,
    });
    return response.data;
  }

  /**
   * Approve/reject access request
   */
  async reviewAccessRequest(
    requestId: string,
    decision: 'APPROVED' | 'REJECTED',
    comment?: string
  ): Promise<AccessRequest> {
    const response = await this.client.post<AccessRequest>(
      `/governance/access-requests/${requestId}/review`,
      { decision, comment }
    );
    return response.data;
  }
}

/**
 * Default governance service instance
 */
export const governanceService = new GovernanceService();

export default governanceService;

