/**
 * Decision audit trail tracking for HITL Console and AI Intelligence.
 *
 * <p><b>Purpose</b><br>
 * Records all user decisions (Approve/Defer/Reject) with context including
 * insight/action ID, reasoning, user, and timestamp. Enables audit trails,
 * compliance reporting, and decision analytics.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { recordDecision, getAuditTrail } = useDecisionAudit();
 *
 * const handleApprove = async (insightId: string, reason?: string) => {
 *   await recordDecision({
 *     entityId: insightId,
 *     entityType: 'insight',
 *     decision: 'approve',
 *     reason,
 *     confidence: 0.92,
 *   });
 * };
 *
 * const trail = await getAuditTrail('insight', insightId);
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose Decision audit trail recording
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback } from 'react';

/**
 * Decision record in audit trail.
 *
 * @doc.type type
 * @doc.purpose Audit trail entry structure
 * @doc.layer product
 * @doc.pattern Type Definition
 */
export interface DecisionRecord {
    id: string;
    entityType: 'insight' | 'action' | 'workflow';
    entityId: string;
    decision: 'approve' | 'defer' | 'reject';
    reason?: string;
    confidence?: number;
    userId: string;
    userName: string;
    timestamp: string;
    department?: string;
    tags?: string[];
    metadata?: Record<string, any>;
}

/**
 * Audit trail response from server.
 *
 * @doc.type type
 * @doc.purpose Audit trail query result
 * @doc.layer product
 * @doc.pattern Type Definition
 */
export interface AuditTrailResponse {
    total: number;
    records: DecisionRecord[];
    avgApprovalRate?: number;
    avgDeferRate?: number;
    avgRejectionRate?: number;
}

/**
 * Hook for recording and querying decision audit trails.
 *
 * @returns Object with recordDecision and getAuditTrail functions
 *
 * @doc.type hook
 * @doc.purpose Decision recording and audit trail
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useDecisionAudit() {
    /**
     * Records a user decision in the audit trail.
     *
     * GIVEN: User makes a decision on an insight/action
     * WHEN: recordDecision is called with decision details
     * THEN: Decision is recorded with timestamp and user context
     */
    const recordDecision = useCallback(
        async (record: Omit<DecisionRecord, 'id' | 'timestamp' | 'userId' | 'userName'>) => {
            try {
                // Mock: Generate record with current user and timestamp
                const mockUserId = localStorage.getItem('userId') || 'user-demo';
                const mockUserName = localStorage.getItem('userName') || 'Demo User';

                const auditRecord: DecisionRecord = {
                    id: `decision-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
                    ...record,
                    userId: mockUserId,
                    userName: mockUserName,
                    timestamp: new Date().toISOString(),
                };

                // Mock: Store in localStorage (replace with API call in production)
                const auditTrail = JSON.parse(
                    localStorage.getItem('auditTrail') || '[]'
                ) as DecisionRecord[];
                auditTrail.push(auditRecord);
                localStorage.setItem('auditTrail', JSON.stringify(auditTrail));

                console.log('[Audit] Decision recorded:', auditRecord);

                // API call (mock implementation)
                // await api.post('/audit/decisions', auditRecord);

                return auditRecord;
            } catch (err) {
                console.error('[Audit] Failed to record decision:', err);
                throw err;
            }
        },
        []
    );

    /**
     * Retrieves audit trail for an entity.
     *
     * GIVEN: Entity type and ID
     * WHEN: getAuditTrail is called
     * THEN: Returns all decisions for that entity
     */
    const getAuditTrail = useCallback(
        async (
            entityType: 'insight' | 'action' | 'workflow',
            entityId: string
        ): Promise<AuditTrailResponse> => {
            try {
                // Mock: Retrieve from localStorage (replace with API call in production)
                const auditTrail = JSON.parse(
                    localStorage.getItem('auditTrail') || '[]'
                ) as DecisionRecord[];

                const records = auditTrail.filter(
                    (r) => r.entityType === entityType && r.entityId === entityId
                );

                // Calculate rates
                const total = records.length;
                const approved = records.filter((r) => r.decision === 'approve').length;
                const deferred = records.filter((r) => r.decision === 'defer').length;
                const rejected = records.filter((r) => r.decision === 'reject').length;

                return {
                    total,
                    records: records.sort(
                        (a, b) =>
                            new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
                    ),
                    avgApprovalRate: total > 0 ? (approved / total) * 100 : 0,
                    avgDeferRate: total > 0 ? (deferred / total) * 100 : 0,
                    avgRejectionRate: total > 0 ? (rejected / total) * 100 : 0,
                };

                // API call (mock implementation)
                // return await api.get(`/audit/trails/${entityType}/${entityId}`);
            } catch (err) {
                console.error('[Audit] Failed to retrieve audit trail:', err);
                throw err;
            }
        },
        []
    );

    /**
     * Gets all decisions by a user.
     *
     * @param userId - User ID to filter by
     * @param limit - Max number of records to return
     */
    const getUserDecisions = useCallback(
        async (userId: string, limit: number = 100): Promise<DecisionRecord[]> => {
            try {
                const auditTrail = JSON.parse(
                    localStorage.getItem('auditTrail') || '[]'
                ) as DecisionRecord[];

                return auditTrail
                    .filter((r) => r.userId === userId)
                    .sort(
                        (a, b) =>
                            new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
                    )
                    .slice(0, limit);
            } catch (err) {
                console.error('[Audit] Failed to retrieve user decisions:', err);
                throw err;
            }
        },
        []
    );

    /**
     * Clears all audit trail data (for testing/reset only).
     */
    const clearAuditTrail = useCallback(() => {
        localStorage.setItem('auditTrail', '[]');
        console.log('[Audit] Audit trail cleared');
    }, []);

    return {
        recordDecision,
        getAuditTrail,
        getUserDecisions,
        clearAuditTrail,
    };
}
