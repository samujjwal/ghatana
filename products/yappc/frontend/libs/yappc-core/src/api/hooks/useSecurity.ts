/**
 * Security Phase React Hooks
 *
 * @description Custom hooks for security phase including vulnerability management,
 * security scanning, compliance tracking, secrets management, and policy enforcement.
 *
 * @doc.type hooks
 * @doc.purpose Security phase data management
 * @doc.layer integration
 */

/* eslint-disable @typescript-eslint/no-unsafe-assignment,@typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call,@typescript-eslint/no-unsafe-return -- Apollo hook refactor pending */

import { useQuery, useLazyQuery, useMutation, useSubscription } from "@apollo/client/react";
import { useAtom, useSetAtom } from 'jotai';
import { useCallback, useMemo, useState } from 'react';

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
} from 'yappc-state';
import {
  GET_VULNERABILITY,
  LIST_VULNERABILITIES as GET_VULNERABILITIES,
  GET_VULNERABILITY_TRENDS as GET_VULNERABILITY_STATS,
  UPDATE_VULNERABILITY,
  ASSIGN_VULNERABILITY,
  MARK_VULNERABILITY_FALSE_POSITIVE as SUPPRESS_VULNERABILITY,
  BULK_UPDATE_VULNERABILITIES,
  START_SECURITY_SCAN as TRIGGER_SECURITY_SCAN,
  CANCEL_SECURITY_SCAN,
  UPDATE_COMPLIANCE_CONTROL,
  ADD_COMPLIANCE_EVIDENCE as UPLOAD_COMPLIANCE_EVIDENCE,
  CREATE_SECRET,
  UPDATE_SECRET,
  DELETE_SECRET,
  ROTATE_SECRET,
  CREATE_SECURITY_POLICY,
  UPDATE_SECURITY_POLICY,
  DELETE_SECURITY_POLICY,
  ACKNOWLEDGE_SECURITY_ALERT,
} from '../graphql/operations/security.operations';

// =============================================================================
// Vulnerability Hooks
// =============================================================================

/**
 * Hook for fetching a single vulnerability
 */
export function useVulnerability(vulnerabilityId?: string) {
  type VulnerabilityQueryData = { vulnerability?: Vulnerability };
  type VulnerabilityQueryState = {
    data?: VulnerabilityQueryData;
    loading: boolean;
    error?: unknown;
    refetch: () => Promise<unknown>;
  };
  type VulnerabilitySubscriptionState = {
    data?: { vulnerabilityUpdates?: Vulnerability };
  };
  const runVulnerabilityQuery = useQuery as unknown as (
    query: unknown,
    options: unknown
  ) => VulnerabilityQueryState;
  const runVulnerabilitySubscription = useSubscription as unknown as (
    subscription: unknown,
    options: unknown
  ) => VulnerabilitySubscriptionState;

  const [selected, setSelected] = useAtom(selectedVulnerabilityAtom);

  const { data, loading, error, refetch } = runVulnerabilityQuery(
    GET_VULNERABILITY,
    {
      variables: { vulnerabilityId },
      skip: !vulnerabilityId,
      onCompleted: (queryData: VulnerabilityQueryData) => {
        if (queryData?.vulnerability) {
          setSelected(queryData.vulnerability);
        }
      },
    }
  );

  // Subscribe to vulnerability updates
  runVulnerabilitySubscription(VULNERABILITY_UPDATES_SUBSCRIPTION, {
    variables: { vulnerabilityId },
    skip: !vulnerabilityId,
    onData: ({ data: subData }: { data: VulnerabilitySubscriptionState }) => {
      if (subData?.data?.vulnerabilityUpdates) {
        setSelected((prev) =>
          prev?.id === vulnerabilityId
            ? { ...prev, ...subData.data.vulnerabilityUpdates }
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
  type VulnerabilitiesQueryData = {
    vulnerabilities?: {
      nodes?: Vulnerability[];
      pageInfo?: { hasNextPage?: boolean; endCursor?: string };
    };
  };
  type VulnerabilitiesQueryState = {
    data?: VulnerabilitiesQueryData;
    loading: boolean;
    error?: unknown;
    fetchMore: (opts: unknown) => Promise<{ data?: VulnerabilitiesQueryData }>;
  };
  const runVulnerabilitiesQuery = useQuery as unknown as (
    query: unknown,
    options: unknown
  ) => VulnerabilitiesQueryState;

  const [vulnerabilities, setVulnerabilities] = useAtom(vulnerabilitiesAtom);

  const { data, loading, error, refetch, fetchMore } = runVulnerabilitiesQuery(
    GET_VULNERABILITIES,
    {
      variables: { filters, first: 50 },
      onCompleted: (queryData: VulnerabilitiesQueryData) => {
        if (queryData?.vulnerabilities?.nodes) {
          setVulnerabilities(queryData.vulnerabilities.nodes);
        }
      },
    }
  );

  const loadMore = useCallback(async () => {
    if (!data?.vulnerabilities?.pageInfo?.hasNextPage) return;
    const result = await fetchMore({
      variables: {
        after: data.vulnerabilities.pageInfo.endCursor,
      },
    });
    if (result.data?.vulnerabilities?.nodes) {
      setVulnerabilities((prev) => [
        ...prev,
        ...result.data.vulnerabilities.nodes,
      ]);
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
            return prev.map((v) =>
              v.id === update.id ? { ...v, ...update } : v
            );
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
  type VulnStatsData = { vulnerabilityStats?: Record<string, unknown> };
  type VulnStatsState = {
    data?: VulnStatsData;
    loading: boolean;
    error?: unknown;
    refetch: (opts?: unknown) => Promise<{ data?: VulnStatsData }>;
  };
  const runVulnStatsQuery = useQuery as unknown as (query: unknown, options: unknown) => VulnStatsState;

  const { data, loading, error, refetch } = runVulnStatsQuery(GET_VULNERABILITY_STATS, {
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
  type _VulnMutationResult = { [key: string]: unknown };
  type MutationState = { data?: { [key: string]: unknown } };
  const runMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<MutationState>, { loading: boolean }];

  const setVulnerabilities = useSetAtom(vulnerabilitiesAtom);
  const setSelected = useSetAtom(selectedVulnerabilityAtom);

  const [update] = runMutation(UPDATE_VULNERABILITY);
  const [assign] = runMutation(ASSIGN_VULNERABILITY);
  const [suppress] = runMutation(SUPPRESS_VULNERABILITY);
  const [bulkUpdate] = runMutation(BULK_UPDATE_VULNERABILITIES);

  const updateVulnerability = useCallback(
    async (vulnId: string, input: Partial<VulnerabilityInput>) => {
      const result = await update({
        variables: { vulnerabilityId: vulnId, input },
      });
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
  type SecurityScansData = { securityScans?: Record<string, unknown>[] };
  type SecurityScansState = {
    data?: SecurityScansData;
    loading: boolean;
    error?: unknown;
    refetch: (opts?: unknown) => Promise<{ data?: SecurityScansData }>;
  };
  const runSecurityScansQuery = useQuery as unknown as (query: unknown, options: unknown) => SecurityScansState;

  type ScanMutationResult = { [key: string]: unknown };
  const runScanMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<{ data?: ScanMutationResult }>, { loading: boolean }];

  const [scans, setScans] = useAtom(securityScansAtom);

  const { data, loading, error, refetch } = runSecurityScansQuery(GET_SECURITY_SCANS, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.securityScans) {
        setScans(data.securityScans);
      }
    },
  });

  const [trigger] = runScanMutation(TRIGGER_SECURITY_SCAN);
  const [cancel] = runScanMutation(CANCEL_SECURITY_SCAN);

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
  type ComplianceData = { complianceFrameworks?: Record<string, unknown>[] };
  type ComplianceState = {
    data?: ComplianceData;
    loading: boolean;
    error?: unknown;
    refetch: (opts?: unknown) => Promise<{ data?: ComplianceData }>;
  };
  const runComplianceQuery = useQuery as unknown as (query: unknown, options: unknown) => ComplianceState;

  const [complianceStatus, setComplianceStatus] = useAtom(complianceStatusAtom);

  const { data, loading, error, refetch } = runComplianceQuery(
    GET_COMPLIANCE_FRAMEWORKS,
    {
      variables: { projectId },
      skip: !projectId,
      onCompleted: (data) => {
        if (data?.complianceFrameworks) {
          setComplianceStatus(data.complianceFrameworks);
        }
      },
    }
  );

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
  type ComplianceControlsData = { complianceControls?: Record<string, unknown>[] };
  type ComplianceControlsState = {
    data?: ComplianceControlsData;
    loading: boolean;
    error?: unknown;
    refetch: (opts?: unknown) => Promise<{ data?: ComplianceControlsData }>;
  };
  const runComplianceControlsQuery = useQuery as unknown as (query: unknown, options: unknown) => ComplianceControlsState;

  type ControlMutationResult = { [key: string]: unknown };
  const runControlMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<{ data?: ControlMutationResult }>, { loading: boolean }];

  const [controls, setControls] = useState<ComplianceControl[]>([]);

  const { data, loading, error, refetch } = runComplianceControlsQuery(GET_COMPLIANCE_CONTROLS, {
    variables: { frameworkId },
    skip: !frameworkId,
    onCompleted: (data) => {
      if (data?.complianceControls) {
        setControls((data.complianceControls as unknown as ComplianceControl[]) || []);
      }
    },
  });

  const [updateControl] = runControlMutation(UPDATE_COMPLIANCE_CONTROL);
  const [uploadEvidence] = runControlMutation(UPLOAD_COMPLIANCE_EVIDENCE);

  const updateComplianceControl = useCallback(
    async (controlId: string, status: string, notes?: string) => {
      const result = (await updateControl({
        variables: { controlId, status, notes },
      })) as unknown as { data?: { updateComplianceControl?: ComplianceControl } };
      if (result.data?.updateComplianceControl) {
        const updated = result.data.updateComplianceControl;
        setControls((prev) =>
          prev.map((c) =>
            c.id === controlId ? updated : c
          )
        );
        return updated;
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
  type SecretsData = { secrets?: Record<string, unknown>[] };
  type SecretsState = {
    data?: SecretsData;
    loading: boolean;
    error?: unknown;
    refetch: (opts?: unknown) => Promise<{ data?: SecretsData }>;
  };
  const runSecretsQuery = useQuery as unknown as (query: unknown, options: unknown) => SecretsState;

  type SecretMutationResult = { [key: string]: unknown };
  const runSecretMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<{ data?: SecretMutationResult }>, { loading: boolean }];

  const [secrets, setSecrets] = useAtom(secretsAtom);

  const { data, loading, error, refetch } = runSecretsQuery(GET_SECRETS, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.secrets) {
        setSecrets(data.secrets);
      }
    },
  });

  const [create] = runSecretMutation(CREATE_SECRET);
  const [update] = runSecretMutation(UPDATE_SECRET);
  const [remove] = runSecretMutation(DELETE_SECRET);
  const [rotate] = runSecretMutation(ROTATE_SECRET);

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
  type PoliciesData = { securityPolicies?: Record<string, unknown>[] };
  type PoliciesState = {
    data?: PoliciesData;
    loading: boolean;
    error?: unknown;
    refetch: (opts?: unknown) => Promise<{ data?: PoliciesData }>;
  };
  const runPoliciesQuery = useQuery as unknown as (query: unknown, options: unknown) => PoliciesState;

  type PolicyMutationResult = { [key: string]: unknown };
  const runPolicyMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<{ data?: PolicyMutationResult }>, { loading: boolean }];

  const [policies, setPolicies] = useAtom(securityPoliciesAtom);

  const { data, loading, error, refetch } = runPoliciesQuery(GET_SECURITY_POLICIES, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.securityPolicies) {
        setPolicies(data.securityPolicies);
      }
    },
  });

  const [create] = runPolicyMutation(CREATE_SECURITY_POLICY);
  const [update] = runPolicyMutation(UPDATE_SECURITY_POLICY);
  const [remove] = runPolicyMutation(DELETE_SECURITY_POLICY);
  const [createException] = runPolicyMutation(CREATE_POLICY_EXCEPTION);

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
          prev.map((p) =>
            p.id === policyId ? result.data.updateSecurityPolicy : p
          )
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
    async (
      policyId: string,
      reason: string,
      expiresAt?: string,
      scope?: string
    ) => {
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
  type SecurityScoreData = { securityScore?: Record<string, unknown> };
  type SecurityScoreState = {
    data?: SecurityScoreData;
    loading: boolean;
    error?: unknown;
    refetch: (opts?: unknown) => Promise<{ data?: SecurityScoreData }>;
  };
  const runSecurityScoreQuery = useQuery as unknown as (query: unknown, options: unknown) => SecurityScoreState;

  const [score, setScore] = useAtom(securityScoreAtom);

  const { data, loading, error, refetch } = runSecurityScoreQuery(GET_SECURITY_SCORE, {
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
  type SecurityAlertsData = { securityAlerts?: Record<string, unknown>[] };
  type SecurityAlertsState = {
    data?: SecurityAlertsData;
    loading: boolean;
    error?: unknown;
    refetch: (opts?: unknown) => Promise<{ data?: SecurityAlertsData }>;
  };
  const runSecurityAlertsQuery = useQuery as unknown as (query: unknown, options: unknown) => SecurityAlertsState;

  type SecurityAlertSubscriptionState = { data?: { securityAlert?: Record<string, unknown> } };
  const runSecurityAlertSubscription = useSubscription as unknown as (subscription: unknown, options: unknown) => SecurityAlertSubscriptionState;

  type AlertMutationResult = { [key: string]: unknown };
  const runAlertMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<{ data?: AlertMutationResult }>, { loading: boolean }];

  const [alerts, setAlerts] = useAtom(securityAlertsAtom);

  const { data, loading, error, refetch } = runSecurityAlertsQuery(GET_SECURITY_ALERTS, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.securityAlerts) {
        setAlerts(data.securityAlerts);
      }
    },
  });

  const [acknowledge] = runAlertMutation(ACKNOWLEDGE_SECURITY_ALERT);

  // Subscribe to new alerts
  runSecurityAlertSubscription(SECURITY_ALERT_SUBSCRIPTION, {
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
  type AuditLogsData = { auditLogs?: { nodes?: Record<string, unknown>[]; pageInfo?: Record<string, unknown> } };
  type AuditLogsState = {
    data?: AuditLogsData;
    loading: boolean;
    error?: unknown;
    refetch: (opts?: unknown) => Promise<{ data?: AuditLogsData }>;
    fetchMore: (opts: unknown) => Promise<{ data?: AuditLogsData }>;
  };
  const runAuditLogsQuery = useQuery as unknown as (query: unknown, options: unknown) => AuditLogsState;

  const [logs, setLogs] = useAtom(auditLogsAtom);

  const { data, loading, error, refetch, fetchMore } = runAuditLogsQuery(
    GET_AUDIT_LOGS,
    {
      variables: { filters, first: 50 },
      onCompleted: (data) => {
        if (data?.auditLogs?.nodes) {
          setLogs(data.auditLogs.nodes);
        }
      },
    }
  );

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
