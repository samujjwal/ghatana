/**
 * Governance & Policy API Service
 *
 * Provides API client for policy management, compliance, and audit operations.
 *
 * @doc.type service
 * @doc.purpose Governance and policy API client
 * @doc.layer frontend
 */

import { apiClient } from '../lib/api/client';

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
  // ==================== Policies ====================

  /**
   * Get all policies
   */
  async getPolicies(type?: string): Promise<Policy[]> {
    return apiClient.get<Policy[]>('/policies', { params: { type } });
  }

  /**
   * Get policy by ID
   */
  async getPolicy(policyId: string): Promise<Policy> {
    return apiClient.get<Policy>(`/policies/${policyId}`);
  }

  /**
   * Create policy
   */
  async createPolicy(policy: Partial<Policy>): Promise<Policy> {
    return apiClient.post<Policy>('/policies', policy);
  }

  /**
   * Update policy
   */
  async updatePolicy(policyId: string, policy: Partial<Policy>): Promise<Policy> {
    return apiClient.put<Policy>(`/policies/${policyId}`, policy);
  }

  /**
   * Delete policy
   */
  async deletePolicy(policyId: string): Promise<void> {
    await apiClient.delete<void>(`/policies/${policyId}`);
  }

  /**
   * Enable/disable policy
   */
  async togglePolicy(policyId: string, enabled: boolean): Promise<Policy> {
    return apiClient.patch<Policy>(`/policies/${policyId}`, { enabled });
  }

  // ==================== Violations ====================

  /**
   * Get policy violations
   */
  async getViolations(
    policyId?: string,
    limit: number = 50
  ): Promise<PolicyViolation[]> {
    return apiClient.get<PolicyViolation[]>('/governance/violations', {
      params: { policyId, limit },
    });
  }

  /**
   * Resolve violation
   */
  async resolveViolation(violationId: string, resolution: string): Promise<void> {
    await apiClient.post<void>(`/governance/violations/${violationId}/resolve`, { resolution });
  }

  // ==================== Compliance ====================

  /**
   * Get compliance report
   */
  async getComplianceReport(period: string = '30d'): Promise<ComplianceReport> {
    return apiClient.get<ComplianceReport>('/governance/reports', { params: { period } });
  }

  /**
   * Generate compliance report
   */
  async generateComplianceReport(
    period: string
  ): Promise<{ reportId: string; status: string }> {
    return apiClient.post('/governance/reports', { period });
  }

  /**
   * Download compliance report
   */
  async downloadComplianceReport(reportId: string): Promise<Blob> {
    return apiClient.get<Blob>(`/governance/reports/${reportId}/download`, {
      responseType: 'blob',
    });
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
    return apiClient.get<AuditLog[]>('/audit/logs', {
      params: { resourceType, userId, limit },
    });
  }

  /**
   * Search audit logs
   */
  async searchAuditLogs(query: string): Promise<AuditLog[]> {
    return apiClient.get<AuditLog[]>('/audit/logs/search', { params: { q: query } });
  }

  // ==================== Access Requests ====================

  /**
   * Get access requests
   */
  async getAccessRequests(status?: string): Promise<AccessRequest[]> {
    return apiClient.get<AccessRequest[]>('/governance/access-requests', { params: { status } });
  }

  /**
   * Request dataset access
   */
  async requestAccess(
    datasetId: string,
    reason: string
  ): Promise<AccessRequest> {
    return apiClient.post<AccessRequest>('/governance/access-requests', { datasetId, reason });
  }

  /**
   * Approve/reject access request
   */
  async reviewAccessRequest(
    requestId: string,
    decision: 'APPROVED' | 'REJECTED',
    comment?: string
  ): Promise<AccessRequest> {
    return apiClient.post<AccessRequest>(
      `/governance/access-requests/${requestId}/review`,
      { decision, comment }
    );
  }
}

/**
 * Default governance service instance
 */
export const governanceService = new GovernanceService();

export default governanceService;

