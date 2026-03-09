import { apiClient } from './index';

/**
 * Security and Audit API (Day 8).
 *
 * <p><b>Purpose</b><br>
 * API methods for security monitoring, audit logging, compliance, and access control.
 *
 * <p><b>Endpoints</b><br>
 * - GET /security/vulnerabilities: Vulnerability scan results
 * - GET /security/audit: Audit log events
 * - GET /security/access: User access/RBAC matrix
 * - GET /compliance: Compliance checklist status
 *
 * @doc.type service
 * @doc.purpose Security and Audit API client
 * @doc.layer product
 * @doc.pattern Service Layer
 */

export type VulnerabilitySeverity = 'low' | 'medium' | 'high' | 'critical';
export type AuditEventType = 'login' | 'deploy' | 'config_change' | 'access_grant' | 'access_revoke' | 'permission_change';

export interface Vulnerability {
    id: string;
    title: string;
    severity: VulnerabilitySeverity;
    description: string;
    affectedComponent: string;
    cveId?: string;
    discoveredAt: string;
    status: 'open' | 'acknowledged' | 'fixed' | 'wontfix';
    assignedTo?: string;
}

export interface AuditEvent {
    id: string;
    timestamp: string;
    eventType: AuditEventType;
    userId: string;
    userName: string;
    resource: string;
    action: string;
    status: 'success' | 'failure';
    ipAddress: string;
    details?: Record<string, any>;
}

export interface UserAccess {
    userId: string;
    userName: string;
    email: string;
    role: string;
    department: string;
    lastLogin: string;
    permissions: string[];
    mfaEnabled: boolean;
}

export interface ComplianceItem {
    id: string;
    framework: string; // SOC2, GDPR, HIPAA, etc.
    requirement: string;
    status: 'pass' | 'fail' | 'pending';
    evidence?: string;
    lastChecked: string;
}

export const securityApi = {
    /**
     * Get vulnerability scan results
     */
    async getVulnerabilities(params?: {
        severity?: VulnerabilitySeverity | 'all';
        status?: 'open' | 'fixed' | 'all';
    }) {
        const response = await apiClient.get<Vulnerability[]>('/security/vulnerabilities', {
            params,
        });
        return response.data;
    },

    /**
     * Trigger a security scan
     */
    async triggerSecurityScan() {
        const response = await apiClient.post<{ scanId: string; status: string }>(
            '/security/scan'
        );
        return response.data;
    },

    /**
     * Get audit log events
     */
    async getAuditLog(params?: {
        eventType?: AuditEventType;
        userId?: string;
        limit?: number;
        offset?: number;
    }) {
        const response = await apiClient.get<AuditEvent[]>('/security/audit', { params });
        return response.data;
    },

    /**
     * Export audit log
     */
    async exportAuditLog(format: 'csv' | 'json', timeRange: string) {
        const response = await apiClient.get('/security/audit/export', {
            params: { format, timeRange },
            responseType: 'blob',
        });
        return response.data;
    },

    /**
     * Get user access control matrix
     */
    async getUserAccess() {
        const response = await apiClient.get<UserAccess[]>('/security/access');
        return response.data;
    },

    /**
     * Get single user access details
     */
    async getUserAccessDetails(userId: string) {
        const response = await apiClient.get<UserAccess>(`/security/access/${userId}`);
        return response.data;
    },

    /**
     * Grant user permission
     */
    async grantPermission(userId: string, permission: string) {
        const response = await apiClient.post(
            `/security/access/${userId}/grant`,
            { permission }
        );
        return response.data;
    },

    /**
     * Revoke user permission
     */
    async revokePermission(userId: string, permission: string) {
        const response = await apiClient.post(
            `/security/access/${userId}/revoke`,
            { permission }
        );
        return response.data;
    },

    /**
     * Get compliance status
     */
    async getComplianceStatus(params?: { framework?: string }) {
        const response = await apiClient.get<ComplianceItem[]>('/compliance', { params });
        return response.data;
    },

    /**
     * Get compliance summary
     */
    async getComplianceSummary() {
        const response = await apiClient.get<{
            frameworks: Record<string, { passed: number; failed: number; pending: number }>;
            overallScore: number;
        }>('/compliance/summary');
        return response.data;
    },
};
