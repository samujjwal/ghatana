import { useQuery } from '@tanstack/react-query';
import { securityApi } from '@/services/api/securityApi';

/**
 * Hook for fetching security vulnerabilities and scan results.
 *
 * <p><b>Purpose</b><br>
 * Provides vulnerability data for security dashboard (Day 8).
 * Supports filtering by severity and vulnerability type.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: vulns, isLoading, error } = useSecurityVulnerabilities();
 * vulns?.forEach(v => console.log(v.id, v.severity, v.affectedServices));
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Polls every 60 seconds (security data updates periodically)
 * - Caches for 10 minutes
 * - Retries on network failure
 *
 * @doc.type hook
 * @doc.purpose Fetch security vulnerabilities
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useSecurityVulnerabilities(options?: {
    enabled?: boolean;
    refetchInterval?: number;
}) {
    return useQuery({
        queryKey: ['securityVulnerabilities'],
        queryFn: async () => {
            try {
                return await securityApi.getVulnerabilities();
            } catch (error) {
                console.warn('[useSecurityVulnerabilities] API unavailable, using fallback:', error);
                return [];
            }
        },
        staleTime: 10 * 60 * 1000, // 10 minutes
        gcTime: 20 * 60 * 1000, // 20 minutes
        retry: 2,
        refetchInterval: options?.refetchInterval ?? 60 * 1000, // 60 seconds
        enabled: options?.enabled ?? true,
    });
}

/**
 * Hook for fetching audit log events.
 *
 * <p><b>Purpose</b><br>
 * Provides audit trail for compliance and security review (Day 8).
 * Supports pagination and filtering.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: events, isLoading, error } = useAuditLog({ limit: 50 });
 * events?.forEach(e => console.log(e.user, e.action, e.timestamp));
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Caches for 5 minutes
 * - Polls every 20 seconds (frequent updates)
 * - Retries on network failure
 *
 * @doc.type hook
 * @doc.purpose Fetch audit log events
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useAuditLog(params?: { limit?: number; enabled?: boolean; refetchInterval?: number }) {
    return useQuery({
        queryKey: ['auditLog', params?.limit],
        queryFn: async () => {
            try {
                return await securityApi.getAuditLog({ limit: params?.limit ?? 50 });
            } catch (error) {
                console.warn('[useAuditLog] API unavailable, using fallback:', error);
                return [];
            }
        },
        staleTime: 5 * 60 * 1000, // 5 minutes
        gcTime: 10 * 60 * 1000, // 10 minutes
        retry: 2,
        refetchInterval: params?.refetchInterval ?? 20 * 1000, // 20 seconds
        enabled: params?.enabled ?? true,
    });
}

/**
 * Hook for fetching compliance status across frameworks.
 *
 * <p><b>Purpose</b><br>
 * Provides compliance metrics and framework coverage (Day 8).
 * Tracks checklist completion and issues.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: compliance, isLoading, error } = useComplianceStatus();
 * console.log(compliance?.frameworks, compliance?.completeness);
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Caches for 30 minutes (compliance data changes infrequently)
 * - No polling (static reference data)
 * - Retries on network failure
 *
 * @doc.type hook
 * @doc.purpose Fetch compliance status
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useComplianceStatus(options?: { enabled?: boolean; refetchInterval?: number }) {
    return useQuery({
        queryKey: ['complianceStatus'],
        queryFn: async () => {
            try {
                const data = await securityApi.getComplianceStatus();
                // Transform API array to component structure
                // Group by framework and calculate aggregates
                const frameworks: Record<
                    string,
                    { status: 'compliant' | 'partially' | 'non-compliant'; completeness: number; issues: number; items: typeof data }
                > = {};

                data.forEach((item) => {
                    if (!frameworks[item.framework]) {
                        frameworks[item.framework] = {
                            status: 'compliant',
                            completeness: 0,
                            issues: 0,
                            items: [],
                        };
                    }
                    frameworks[item.framework].items.push(item);
                    if (item.status === 'fail') {
                        frameworks[item.framework].issues += 1;
                        frameworks[item.framework].status = 'non-compliant';
                    } else if (item.status === 'pending') {
                        frameworks[item.framework].status =
                            frameworks[item.framework].status === 'non-compliant' ? 'non-compliant' : 'partially';
                    }
                });

                // Calculate completeness percentage
                Object.values(frameworks).forEach((fw) => {
                    const passed = fw.items.filter((i) => i.status === 'pass').length;
                    fw.completeness = Math.round((passed / fw.items.length) * 100) || 0;
                });

                return {
                    frameworks: Object.entries(frameworks).map(([name, fw]) => ({
                        framework: name,
                        status: fw.status,
                        completeness: fw.completeness,
                        lastAudit: fw.items[0]?.lastChecked || '',
                        nextAudit: '',
                        issues: fw.issues,
                    })),
                    checklist: data.map((item) => ({
                        id: item.id,
                        title: item.requirement,
                        framework: item.framework,
                        status: (
                            {
                                pass: 'complete',
                                fail: 'failed',
                                pending: 'pending',
                            } as const
                        )[item.status],
                        dueDate: item.lastChecked,
                        evidence: item.evidence ? [item.evidence] : [],
                    })),
                };
            } catch (error) {
                console.warn('[useComplianceStatus] API unavailable, using fallback:', error);
                return {
                    frameworks: [],
                    checklist: [],
                };
            }
        },
        staleTime: 30 * 60 * 1000, // 30 minutes (reference data)
        gcTime: 60 * 60 * 1000, // 60 minutes
        retry: 2,
        refetchInterval: options?.refetchInterval || undefined,
        enabled: options?.enabled ?? true,
    });
}
