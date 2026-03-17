/**
 * Security Phase React Hooks
 *
 * @description Custom hooks for security phase including vulnerability management,
 * security scanning, compliance tracking, secrets management, and policy enforcement.
 *
 * @doc.type hooks
 * @doc.purpose Security phase data management
 * @doc.layer integration
 * @doc.phase security
 */

import { useCallback, useMemo, useState } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { useQuery, useMutation, useSubscription, useLazyQuery } from '@apollo/client';

import {
  vulnerabilitiesAtom,
  selectedVulnerabilityAtom,
  securityScansAtom,
  complianceStatusAtom,
  secretsAtom,
  securityPoliciesAtom,
  securityScoreAtom,
  securityAlertsAtom,
  auditLogsAtom,
} from '@ghatana/yappc-canvas';

import {
  GET_VULNERABILITY,
  GET_VULNERABILITIES,
  GET_VULNERABILITY_STATS,
  GET_SECURITY_SCAN,
  GET_SECURITY_SCANS,
  GET_COMPLIANCE_FRAMEWORKS,
  GET_COMPLIANCE_CONTROLS,
  GET_COMPLIANCE_EVIDENCE,
  GET_SECRETS,
  GET_SECRET_AUDIT_LOG,
  GET_SECURITY_POLICIES,
  GET_SECURITY_SCORE,
  GET_SECURITY_ALERTS,
  GET_AUDIT_LOGS,
  UPDATE_VULNERABILITY,
  ASSIGN_VULNERABILITY,
  SUPPRESS_VULNERABILITY,
  BULK_UPDATE_VULNERABILITIES,
  TRIGGER_SECURITY_SCAN,
  CANCEL_SECURITY_SCAN,
  UPDATE_COMPLIANCE_CONTROL,
  UPLOAD_COMPLIANCE_EVIDENCE,
  CREATE_SECRET,
  UPDATE_SECRET,
  DELETE_SECRET,
  ROTATE_SECRET,
  CREATE_SECURITY_POLICY,
  UPDATE_SECURITY_POLICY,
  DELETE_SECURITY_POLICY,
  CREATE_POLICY_EXCEPTION,
  ACKNOWLEDGE_SECURITY_ALERT,
  VULNERABILITY_UPDATES_SUBSCRIPTION,
  SCAN_PROGRESS_SUBSCRIPTION,
  SECURITY_ALERT_SUBSCRIPTION,
  type Vulnerability,
  type VulnerabilityInput,
  type VulnerabilityFilters,
  type SecurityScan,
  type ScanType,
  type ComplianceFramework,
  type ComplianceControl,
  type ComplianceEvidence,
  type Secret,
  type SecretInput,
  type SecurityPolicy,
  type PolicyInput,
  type SecurityAlert,
  type AuditLogEntry,
} from '@ghatana/yappc-api';

// =============================================================================
// Vulnerability Hooks
// =============================================================================

/**
 * Hook for fetching a single vulnerability
 */
export function useVulnerability(vulnerabilityId?: string) {
  const [selected, setSelected] = useAtom(selectedVulnerabilityAtom);

  const { data, loading, error, refetch } = useQuery(GET_VULNERABILITY, {
    variables: { vulnerabilityId },
    skip: !vulnerabilityId,
    onCompleted: (data) => {
      if (data?.vulnerability) {
        setSelected(data.vulnerability);
      }
    },
  });

  // Subscribe to vulnerability updates
  useSubscription(VULNERABILITY_UPDATES_SUBSCRIPTION, {
    variables: { vulnerabilityId },
    skip: !vulnerabilityId,
    onData: ({ data }) => {
      if (data?.data?.vulnerabilityUpdates) {
        setSelected((prev) =>
          prev?.id === vulnerabilityId
            ? { ...prev, ...data.data.vulnerabilityUpdates }
            : prev
        );
      }
    },
  });

  return {
    vulnerability: selected || data?.vulnerability,
    isLoading: loading,
    error,
    refetch,
  };
}

/**
 * Hook for fetching vulnerabilities list
 */
export function useVulnerabilities(filters?: VulnerabilityFilters) {
  const [vulnerabilities, setVulnerabilities] = useAtom(vulnerabilitiesAtom);

  const { data, loading, error, refetch, fetchMore } = useQuery(GET_VULNERABILITIES, {
    variables: { filters, first: 50 },
    onCompleted: (data) => {
      if (data?.vulnerabilities?.nodes) {
        setVulnerabilities(data.vulnerabilities.nodes);
      }
    },
  });

  const loadMore = useCallback(async () => {
    if (!data?.vulnerabilities?.pageInfo?.hasNextPage) return;
    const result = await fetchMore({
      variables: {
        after: data.vulnerabilities.pageInfo.endCursor,
      },
    });
    if (result.data?.vulnerabilities?.nodes) {
      setVulnerabilities((prev) => [...prev, ...result.data.vulnerabilities.nodes]);
    }
  }, [data, fetchMore, setVulnerabilities]);

  // Subscribe to new vulnerabilities
  useSubscription(VULNERABILITY_UPDATES_SUBSCRIPTION, {
    variables: { vulnerabilityId: null },
    onData: ({ data }) => {
      if (data?.data?.vulnerabilityUpdates) {
        const update = data.data.vulnerabilityUpdates;
        setVulnerabilities((prev) => {
          const exists = prev.find((v) => v.id === update.id);
          if (exists) {
            return prev.map((v) => (v.id === update.id ? { ...v, ...update } : v));
          }
          return [update, ...prev];
        });
      }
    },
  });

  return {
    vulnerabilities: vulnerabilities || data?.vulnerabilities?.nodes || [],
    pageInfo: data?.vulnerabilities?.pageInfo,
    totalCount: data?.vulnerabilities?.totalCount,
    isLoading: loading,
    error,
    refetch,
    loadMore,
  };
}

/**
 * Hook for vulnerability statistics
 */
export function useVulnerabilityStats(projectId?: string) {
  const { data, loading, error, refetch } = useQuery(GET_VULNERABILITY_STATS, {
    variables: { projectId },
    skip: !projectId,
  });

  const stats = useMemo(() => {
    if (!data?.vulnerabilityStats) return null;
    return {
      total: data.vulnerabilityStats.total,
      bySeverity: data.vulnerabilityStats.bySeverity,
      byStatus: data.vulnerabilityStats.byStatus,
      bySource: data.vulnerabilityStats.bySource,
      slaBreaches: data.vulnerabilityStats.slaBreaches,
      meanTimeToRemediate: data.vulnerabilityStats.meanTimeToRemediate,
      trend: data.vulnerabilityStats.trend,
    };
  }, [data]);

  return {
    stats,
    isLoading: loading,
    error,
    refetch,
  };
}

/**
 * Hook for vulnerability mutations
 */
export function useVulnerabilityMutations() {
  const setVulnerabilities = useSetAtom(vulnerabilitiesAtom);
  const setSelected = useSetAtom(selectedVulnerabilityAtom);

  const [update] = useMutation(UPDATE_VULNERABILITY);
  const [assign] = useMutation(ASSIGN_VULNERABILITY);
  const [suppress] = useMutation(SUPPRESS_VULNERABILITY);
  const [bulkUpdate] = useMutation(BULK_UPDATE_VULNERABILITIES);

  const updateVulnerability = useCallback(
    async (vulnId: string, input: Partial<VulnerabilityInput>) => {
      const result = await update({ variables: { vulnerabilityId: vulnId, input } });
      if (result.data?.updateVulnerability) {
        const updated = result.data.updateVulnerability;
        setVulnerabilities((prev) =>
          prev.map((v) => (v.id === vulnId ? updated : v))
        );
        setSelected((prev) => (prev?.id === vulnId ? updated : prev));
        return updated;
      }
      return null;
    },
    [update, setVulnerabilities, setSelected]
  );

  const assignVulnerability = useCallback(
    async (vulnId: string, assigneeId: string, dueDate?: string) => {
      const result = await assign({
        variables: { vulnerabilityId: vulnId, assigneeId, dueDate },
      });
      if (result.data?.assignVulnerability) {
        const updated = result.data.assignVulnerability;
        setVulnerabilities((prev) =>
          prev.map((v) => (v.id === vulnId ? updated : v))
        );
        return updated;
      }
      return null;
    },
    [assign, setVulnerabilities]
  );

  const suppressVulnerability = useCallback(
    async (vulnId: string, reason: string, expiresAt?: string) => {
      const result = await suppress({
        variables: { vulnerabilityId: vulnId, reason, expiresAt },
      });
      if (result.data?.suppressVulnerability) {
        const updated = result.data.suppressVulnerability;
        setVulnerabilities((prev) =>
          prev.map((v) => (v.id === vulnId ? updated : v))
        );
        return updated;
      }
      return null;
    },
    [suppress, setVulnerabilities]
  );

  const bulkUpdateVulnerabilities = useCallback(
    async (ids: string[], action: string, params?: Record<string, unknown>) => {
      const result = await bulkUpdate({ variables: { ids, action, params } });
      if (result.data?.bulkUpdateVulnerabilities) {
        const updated = result.data.bulkUpdateVulnerabilities;
        setVulnerabilities((prev) =>
          prev.map((v) => {
            const found = updated.find((u: Vulnerability) => u.id === v.id);
            return found || v;
          })
        );
        return updated;
      }
      return null;
    },
    [bulkUpdate, setVulnerabilities]
  );

  return {
    updateVulnerability,
    assignVulnerability,
    suppressVulnerability,
    bulkUpdateVulnerabilities,
  };
}

// =============================================================================
// Security Scan Hooks
// =============================================================================

/**
 * Hook for security scans
 */
export function useSecurityScans(projectId?: string) {
  const [scans, setScans] = useAtom(securityScansAtom);

  const { data, loading, error, refetch } = useQuery(GET_SECURITY_SCANS, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.securityScans) {
        setScans(data.securityScans);
      }
    },
  });

  const [trigger] = useMutation(TRIGGER_SECURITY_SCAN);
  const [cancel] = useMutation(CANCEL_SECURITY_SCAN);

  // Subscribe to scan progress
  useSubscription(SCAN_PROGRESS_SUBSCRIPTION, {
    variables: { projectId },
    skip: !projectId,
    onData: ({ data }) => {
      if (data?.data?.scanProgress) {
        const progress = data.data.scanProgress;
        setScans((prev) =>
          prev.map((s) =>
            s.id === progress.scanId
              ? { ...s, status: progress.status, progress: progress.progress }
              : s
          )
        );
      }
    },
  });

  const triggerScan = useCallback(
    async (scanType: ScanType, target?: string) => {
      if (!projectId) return null;
      const result = await trigger({
        variables: { projectId, scanType, target },
      });
      if (result.data?.triggerSecurityScan) {
        setScans((prev) => [result.data.triggerSecurityScan, ...prev]);
        return result.data.triggerSecurityScan;
      }
      return null;
    },
    [projectId, trigger, setScans]
  );

  const cancelScan = useCallback(
    async (scanId: string) => {
      await cancel({ variables: { scanId } });
      setScans((prev) =>
        prev.map((s) => (s.id === scanId ? { ...s, status: 'cancelled' } : s))
      );
    },
    [cancel, setScans]
  );

  return {
    scans: scans || data?.securityScans || [],
    isLoading: loading,
    error,
    triggerScan,
    cancelScan,
    refetch,
  };
}

// =============================================================================
// Compliance Hooks
// =============================================================================

/**
 * Hook for compliance frameworks and status
 */
export function useCompliance(projectId?: string) {
  const [complianceStatus, setComplianceStatus] = useAtom(complianceStatusAtom);

  const { data, loading, error, refetch } = useQuery(GET_COMPLIANCE_FRAMEWORKS, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.complianceFrameworks) {
        setComplianceStatus(data.complianceFrameworks);
      }
    },
  });

  return {
    frameworks: complianceStatus || data?.complianceFrameworks || [],
    isLoading: loading,
    error,
    refetch,
  };
}

/**
 * Hook for compliance controls
 */
export function useComplianceControls(frameworkId?: string) {
  const [controls, setControls] = useState<ComplianceControl[]>([]);

  const { data, loading, error, refetch } = useQuery(GET_COMPLIANCE_CONTROLS, {
    variables: { frameworkId },
    skip: !frameworkId,
    onCompleted: (data) => {
      if (data?.complianceControls) {
        setControls(data.complianceControls);
      }
    },
  });

  const [updateControl] = useMutation(UPDATE_COMPLIANCE_CONTROL);
  const [uploadEvidence] = useMutation(UPLOAD_COMPLIANCE_EVIDENCE);

  const updateComplianceControl = useCallback(
    async (controlId: string, status: string, notes?: string) => {
      const result = await updateControl({
        variables: { controlId, status, notes },
      });
      if (result.data?.updateComplianceControl) {
        setControls((prev) =>
          prev.map((c) =>
            c.id === controlId ? result.data.updateComplianceControl : c
          )
        );
        return result.data.updateComplianceControl;
      }
      return null;
    },
    [updateControl]
  );

  const uploadComplianceEvidence = useCallback(
    async (controlId: string, file: File, description?: string) => {
      const result = await uploadEvidence({
        variables: { controlId, file, description },
      });
      return result.data?.uploadComplianceEvidence;
    },
    [uploadEvidence]
  );

  return {
    controls: controls || data?.complianceControls || [],
    isLoading: loading,
    error,
    updateComplianceControl,
    uploadComplianceEvidence,
    refetch,
  };
}

// =============================================================================
// Secrets Management Hooks
// =============================================================================

/**
 * Hook for secrets management
 */
export function useSecrets(projectId?: string) {
  const [secrets, setSecrets] = useAtom(secretsAtom);

  const { data, loading, error, refetch } = useQuery(GET_SECRETS, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.secrets) {
        setSecrets(data.secrets);
      }
    },
  });

  const [create] = useMutation(CREATE_SECRET);
  const [update] = useMutation(UPDATE_SECRET);
  const [remove] = useMutation(DELETE_SECRET);
  const [rotate] = useMutation(ROTATE_SECRET);

  const createSecret = useCallback(
    async (input: SecretInput) => {
      if (!projectId) return null;
      const result = await create({ variables: { projectId, input } });
      if (result.data?.createSecret) {
        setSecrets((prev) => [...prev, result.data.createSecret]);
        return result.data.createSecret;
      }
      return null;
    },
    [projectId, create, setSecrets]
  );

  const updateSecret = useCallback(
    async (secretId: string, input: Partial<SecretInput>) => {
      const result = await update({ variables: { secretId, input } });
      if (result.data?.updateSecret) {
        setSecrets((prev) =>
          prev.map((s) => (s.id === secretId ? result.data.updateSecret : s))
        );
        return result.data.updateSecret;
      }
      return null;
    },
    [update, setSecrets]
  );

  const deleteSecret = useCallback(
    async (secretId: string) => {
      await remove({ variables: { secretId } });
      setSecrets((prev) => prev.filter((s) => s.id !== secretId));
    },
    [remove, setSecrets]
  );

  const rotateSecret = useCallback(
    async (secretId: string) => {
      const result = await rotate({ variables: { secretId } });
      if (result.data?.rotateSecret) {
        setSecrets((prev) =>
          prev.map((s) => (s.id === secretId ? result.data.rotateSecret : s))
        );
        return result.data.rotateSecret;
      }
      return null;
    },
    [rotate, setSecrets]
  );

  return {
    secrets: secrets || data?.secrets || [],
    isLoading: loading,
    error,
    createSecret,
    updateSecret,
    deleteSecret,
    rotateSecret,
    refetch,
  };
}

// =============================================================================
// Security Policy Hooks
// =============================================================================

/**
 * Hook for security policies
 */
export function useSecurityPolicies(projectId?: string) {
  const [policies, setPolicies] = useAtom(securityPoliciesAtom);

  const { data, loading, error, refetch } = useQuery(GET_SECURITY_POLICIES, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.securityPolicies) {
        setPolicies(data.securityPolicies);
      }
    },
  });

  const [create] = useMutation(CREATE_SECURITY_POLICY);
  const [update] = useMutation(UPDATE_SECURITY_POLICY);
  const [remove] = useMutation(DELETE_SECURITY_POLICY);
  const [createException] = useMutation(CREATE_POLICY_EXCEPTION);

  const createPolicy = useCallback(
    async (input: PolicyInput) => {
      if (!projectId) return null;
      const result = await create({ variables: { projectId, input } });
      if (result.data?.createSecurityPolicy) {
        setPolicies((prev) => [...prev, result.data.createSecurityPolicy]);
        return result.data.createSecurityPolicy;
      }
      return null;
    },
    [projectId, create, setPolicies]
  );

  const updatePolicy = useCallback(
    async (policyId: string, input: Partial<PolicyInput>) => {
      const result = await update({ variables: { policyId, input } });
      if (result.data?.updateSecurityPolicy) {
        setPolicies((prev) =>
          prev.map((p) => (p.id === policyId ? result.data.updateSecurityPolicy : p))
        );
        return result.data.updateSecurityPolicy;
      }
      return null;
    },
    [update, setPolicies]
  );

  const deletePolicy = useCallback(
    async (policyId: string) => {
      await remove({ variables: { policyId } });
      setPolicies((prev) => prev.filter((p) => p.id !== policyId));
    },
    [remove, setPolicies]
  );

  const createPolicyException = useCallback(
    async (policyId: string, reason: string, expiresAt?: string, scope?: string) => {
      const result = await createException({
        variables: { policyId, reason, expiresAt, scope },
      });
      return result.data?.createPolicyException;
    },
    [createException]
  );

  return {
    policies: policies || data?.securityPolicies || [],
    isLoading: loading,
    error,
    createPolicy,
    updatePolicy,
    deletePolicy,
    createPolicyException,
    refetch,
  };
}

// =============================================================================
// Security Score Hooks
// =============================================================================

/**
 * Hook for security posture score
 */
export function useSecurityScore(projectId?: string) {
  const [score, setScore] = useAtom(securityScoreAtom);

  const { data, loading, error, refetch } = useQuery(GET_SECURITY_SCORE, {
    variables: { projectId },
    skip: !projectId,
    pollInterval: 60000, // Poll every minute
    onCompleted: (data) => {
      if (data?.securityScore) {
        setScore(data.securityScore);
      }
    },
  });

  return {
    score: score || data?.securityScore,
    isLoading: loading,
    error,
    refetch,
  };
}

// =============================================================================
// Security Alert Hooks
// =============================================================================

/**
 * Hook for security alerts
 */
export function useSecurityAlerts(projectId?: string) {
  const [alerts, setAlerts] = useAtom(securityAlertsAtom);

  const { data, loading, error, refetch } = useQuery(GET_SECURITY_ALERTS, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.securityAlerts) {
        setAlerts(data.securityAlerts);
      }
    },
  });

  const [acknowledge] = useMutation(ACKNOWLEDGE_SECURITY_ALERT);

  // Subscribe to new alerts
  useSubscription(SECURITY_ALERT_SUBSCRIPTION, {
    variables: { projectId },
    skip: !projectId,
    onData: ({ data }) => {
      if (data?.data?.securityAlert) {
        const alert = data.data.securityAlert;
        setAlerts((prev) => [alert, ...prev].slice(0, 100));
      }
    },
  });

  const acknowledgeAlert = useCallback(
    async (alertId: string, notes?: string) => {
      const result = await acknowledge({ variables: { alertId, notes } });
      if (result.data?.acknowledgeSecurityAlert) {
        setAlerts((prev) =>
          prev.map((a) =>
            a.id === alertId ? result.data.acknowledgeSecurityAlert : a
          )
        );
        return result.data.acknowledgeSecurityAlert;
      }
      return null;
    },
    [acknowledge, setAlerts]
  );

  return {
    alerts: alerts || data?.securityAlerts || [],
    isLoading: loading,
    error,
    acknowledgeAlert,
    refetch,
  };
}

// =============================================================================
// Audit Log Hooks
// =============================================================================

/**
 * Hook for audit logs
 */
export function useAuditLogs(filters?: {
  startDate?: string;
  endDate?: string;
  action?: string;
  actor?: string;
  resource?: string;
}) {
  const [logs, setLogs] = useAtom(auditLogsAtom);

  const { data, loading, error, refetch, fetchMore } = useQuery(GET_AUDIT_LOGS, {
    variables: { filters, first: 50 },
    onCompleted: (data) => {
      if (data?.auditLogs?.nodes) {
        setLogs(data.auditLogs.nodes);
      }
    },
  });

  const loadMore = useCallback(async () => {
    if (!data?.auditLogs?.pageInfo?.hasNextPage) return;
    const result = await fetchMore({
      variables: {
        after: data.auditLogs.pageInfo.endCursor,
      },
    });
    if (result.data?.auditLogs?.nodes) {
      setLogs((prev) => [...prev, ...result.data.auditLogs.nodes]);
    }
  }, [data, fetchMore, setLogs]);

  return {
    logs: logs || data?.auditLogs?.nodes || [],
    pageInfo: data?.auditLogs?.pageInfo,
    isLoading: loading,
    error,
    refetch,
    loadMore,
  };
}

export default {
  useVulnerability,
  useVulnerabilities,
  useVulnerabilityStats,
  useVulnerabilityMutations,
  useSecurityScans,
  useCompliance,
  useComplianceControls,
  useSecrets,
  useSecurityPolicies,
  useSecurityScore,
  useSecurityAlerts,
  useAuditLogs,
};
