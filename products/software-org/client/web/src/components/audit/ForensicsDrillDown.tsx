/**
 * Forensics Drill-Down Component
 *
 * Provides deep forensic analysis of audit events with:
 * - Full JSON diff visualization
 * - User context (role, IP, session)
 * - Anomaly score and risk assessment
 * - Remediation action buttons
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { Box, Card, Button, Stack, Chip } from '@ghatana/ui';
import type { AuditEntry } from '@/types/org.types';

interface ForensicsDrillDownProps {
    entry: AuditEntry;
    onClose?: () => void;
    onRevert?: (entryId: string) => void;
    onLockUser?: (userId: string) => void;
    onEscalate?: (entryId: string) => void;
    onMarkReviewed?: (entryId: string) => void;
}

interface UserContext {
    userId: string;
    role: string;
    ipAddress: string;
    location: string;
    device: string;
    sessionId: string;
    lastLogin: Date;
}

interface AnomalyScore {
    overall: number; // 0-100
    factors: {
        timing: number; // Off-hours access
        frequency: number; // Unusual activity volume
        privilege: number; // Elevated privilege use
        pattern: number; // Deviation from normal behavior
    };
    riskLevel: 'low' | 'medium' | 'high' | 'critical';
    flags: string[];
}

/**
 * Calculate anomaly score for an audit entry
 */
function calculateAnomalyScore(entry: AuditEntry, userContext?: UserContext): AnomalyScore {
    const factors = {
        timing: 0,
        frequency: 0,
        privilege: 0,
        pattern: 0,
    };

    const flags: string[] = [];

    // Timing analysis (off-hours detection)
    const hour = new Date(entry.timestamp).getHours();
    if (hour < 6 || hour > 22) {
        factors.timing = 70;
        flags.push('Off-hours activity detected');
    }

    // Frequency analysis (multiple changes in short time)
    if (entry.metadata?.rapidChanges) {
        factors.frequency = 85;
        flags.push('Rapid successive changes');
    }

    // Privilege analysis (sensitive actions)
    if (entry.action.includes('role:') || entry.action.includes('permission:')) {
        factors.privilege = 60;
        flags.push('Sensitive permission change');
    }

    // Pattern analysis (unusual resource access)
    if (entry.metadata?.firstTimeAccess) {
        factors.pattern = 50;
        flags.push('First-time resource access');
    }

    // Geographic anomaly
    if (userContext?.location && userContext.location !== 'US') {
        factors.pattern += 30;
        flags.push('Access from unusual location');
    }

    // Calculate overall score
    const overall = Math.round(
        (factors.timing + factors.frequency + factors.privilege + factors.pattern) / 4
    );

    // Determine risk level
    let riskLevel: AnomalyScore['riskLevel'] = 'low';
    if (overall >= 75) riskLevel = 'critical';
    else if (overall >= 50) riskLevel = 'high';
    else if (overall >= 25) riskLevel = 'medium';

    return { overall, factors, riskLevel, flags };
}

/**
 * Mock user context data
 */
function getUserContext(userId: string): UserContext {
    return {
        userId,
        role: 'Admin',
        ipAddress: '192.168.1.100',
        location: 'San Francisco, CA, US',
        device: 'Chrome 120 on macOS',
        sessionId: 'sess-abc123',
        lastLogin: new Date(Date.now() - 3600000), // 1 hour ago
    };
}

export function ForensicsDrillDown({
    entry,
    onClose,
    onRevert,
    onLockUser,
    onEscalate,
    onMarkReviewed,
}: ForensicsDrillDownProps) {
    const [viewMode, setViewMode] = useState<'visual' | 'json'>('visual');
    const userContext = getUserContext(entry.actor);
    const anomalyScore = calculateAnomalyScore(entry, userContext);

    const getRiskColor = (level: string) => {
        switch (level) {
            case 'critical':
                return 'bg-red-100 text-red-800 border-red-300';
            case 'high':
                return 'bg-orange-100 text-orange-800 border-orange-300';
            case 'medium':
                return 'bg-yellow-100 text-yellow-800 border-yellow-300';
            default:
                return 'bg-green-100 text-green-800 border-green-300';
        }
    };

    const getScoreColor = (score: number) => {
        if (score >= 75) return 'text-red-600';
        if (score >= 50) return 'text-orange-600';
        if (score >= 25) return 'text-yellow-600';
        return 'text-green-600';
    };

    return (
        <Box className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                        Forensic Analysis
                    </h2>
                    <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                        Deep dive into event details and context
                    </p>
                </div>
                {onClose && (
                    <Button variant="ghost" size="sm" onClick={onClose}>
                        ✕ Close
                    </Button>
                )}
            </div>

            {/* Anomaly Score Card */}
            <Card>
                <Box className="p-6">
                    <div className="flex items-center justify-between mb-4">
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                            Anomaly Detection
                        </h3>
                        <div
                            className={`px-4 py-2 rounded-lg border-2 font-semibold ${getRiskColor(
                                anomalyScore.riskLevel
                            )}`}
                        >
                            {anomalyScore.riskLevel.toUpperCase()} RISK
                        </div>
                    </div>

                    {/* Overall Score */}
                    <div className="mb-6">
                        <div className="flex items-center justify-between mb-2">
                            <span className="text-sm text-slate-600 dark:text-neutral-400">
                                Anomaly Score
                            </span>
                            <span className={`text-3xl font-bold ${getScoreColor(anomalyScore.overall)}`}>
                                {anomalyScore.overall}/100
                            </span>
                        </div>
                        <div className="w-full bg-slate-200 dark:bg-neutral-700 rounded-full h-3">
                            <div
                                className={`h-3 rounded-full transition-all ${anomalyScore.overall >= 75
                                        ? 'bg-red-600'
                                        : anomalyScore.overall >= 50
                                            ? 'bg-orange-600'
                                            : anomalyScore.overall >= 25
                                                ? 'bg-yellow-600'
                                                : 'bg-green-600'
                                    }`}
                                style={{ width: `${anomalyScore.overall}%` }}
                            />
                        </div>
                    </div>

                    {/* Factor Breakdown */}
                    <div className="space-y-3 mb-6">
                        {Object.entries(anomalyScore.factors).map(([factor, score]) => (
                            <div key={factor}>
                                <div className="flex items-center justify-between mb-1">
                                    <span className="text-xs text-slate-600 dark:text-neutral-400 capitalize">
                                        {factor}
                                    </span>
                                    <span className={`text-sm font-semibold ${getScoreColor(score)}`}>
                                        {score}
                                    </span>
                                </div>
                                <div className="w-full bg-slate-100 dark:bg-neutral-700 rounded-full h-2">
                                    <div
                                        className={`h-2 rounded-full ${score >= 75
                                                ? 'bg-red-500'
                                                : score >= 50
                                                    ? 'bg-orange-500'
                                                    : score >= 25
                                                        ? 'bg-yellow-500'
                                                        : 'bg-green-500'
                                            }`}
                                        style={{ width: `${score}%` }}
                                    />
                                </div>
                            </div>
                        ))}
                    </div>

                    {/* Anomaly Flags */}
                    {anomalyScore.flags.length > 0 && (
                        <div>
                            <h4 className="text-sm font-semibold text-slate-700 dark:text-neutral-300 mb-2">
                                Detected Anomalies ({anomalyScore.flags.length})
                            </h4>
                            <Stack spacing={2}>
                                {anomalyScore.flags.map((flag, index) => (
                                    <div
                                        key={index}
                                        className="flex items-start gap-2 p-2 bg-yellow-50 dark:bg-yellow-900/20 rounded border border-yellow-200 dark:border-yellow-700"
                                    >
                                        <span className="text-yellow-600">⚠️</span>
                                        <span className="text-sm text-slate-700 dark:text-neutral-300">{flag}</span>
                                    </div>
                                ))}
                            </Stack>
                        </div>
                    )}
                </Box>
            </Card>

            {/* User Context Card */}
            <Card>
                <Box className="p-6">
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                        User Context
                    </h3>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <p className="text-xs text-slate-600 dark:text-neutral-400 mb-1">User ID</p>
                            <p className="text-sm font-mono text-slate-900 dark:text-neutral-100">
                                {userContext.userId}
                            </p>
                        </div>

                        <div>
                            <p className="text-xs text-slate-600 dark:text-neutral-400 mb-1">Role</p>
                            <Chip label={userContext.role} size="small" color="primary" />
                        </div>

                        <div>
                            <p className="text-xs text-slate-600 dark:text-neutral-400 mb-1">IP Address</p>
                            <p className="text-sm font-mono text-slate-900 dark:text-neutral-100">
                                {userContext.ipAddress}
                            </p>
                        </div>

                        <div>
                            <p className="text-xs text-slate-600 dark:text-neutral-400 mb-1">Location</p>
                            <p className="text-sm text-slate-900 dark:text-neutral-100">{userContext.location}</p>
                        </div>

                        <div>
                            <p className="text-xs text-slate-600 dark:text-neutral-400 mb-1">Device</p>
                            <p className="text-sm text-slate-900 dark:text-neutral-100">{userContext.device}</p>
                        </div>

                        <div>
                            <p className="text-xs text-slate-600 dark:text-neutral-400 mb-1">Session ID</p>
                            <p className="text-sm font-mono text-slate-900 dark:text-neutral-100">
                                {userContext.sessionId}
                            </p>
                        </div>

                        <div className="col-span-2">
                            <p className="text-xs text-slate-600 dark:text-neutral-400 mb-1">Last Login</p>
                            <p className="text-sm text-slate-900 dark:text-neutral-100">
                                {userContext.lastLogin.toLocaleString()}
                            </p>
                        </div>
                    </div>
                </Box>
            </Card>

            {/* Event Details Card */}
            <Card>
                <Box className="p-6">
                    <div className="flex items-center justify-between mb-4">
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                            Event Details
                        </h3>
                        <div className="flex gap-2">
                            <Button
                                variant={viewMode === 'visual' ? 'primary' : 'ghost'}
                                size="sm"
                                onClick={() => setViewMode('visual')}
                            >
                                Visual
                            </Button>
                            <Button
                                variant={viewMode === 'json' ? 'primary' : 'ghost'}
                                size="sm"
                                onClick={() => setViewMode('json')}
                            >
                                JSON
                            </Button>
                        </div>
                    </div>

                    {viewMode === 'visual' ? (
                        <div className="space-y-4">
                            <div>
                                <p className="text-xs text-slate-600 dark:text-neutral-400 mb-1">Action</p>
                                <p className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                    {entry.action}
                                </p>
                            </div>

                            <div>
                                <p className="text-xs text-slate-600 dark:text-neutral-400 mb-1">Target</p>
                                <div className="flex items-center gap-2">
                                    <Chip label={entry.target.type} size="small" variant="outlined" />
                                    <span className="text-sm text-slate-900 dark:text-neutral-100">
                                        {entry.target.name}
                                    </span>
                                </div>
                            </div>

                            <div>
                                <p className="text-xs text-slate-600 dark:text-neutral-400 mb-2">
                                    Changes ({Object.keys(entry.changes).length})
                                </p>
                                <Stack spacing={3}>
                                    {Object.entries(entry.changes).map(([field, change]) => (
                                        <div
                                            key={field}
                                            className="p-4 bg-slate-50 dark:bg-neutral-800 rounded-lg border border-slate-200 dark:border-neutral-700"
                                        >
                                            <p className="text-sm font-semibold text-slate-700 dark:text-neutral-300 mb-3">
                                                {field}
                                            </p>
                                            <div className="space-y-2">
                                                <div className="flex items-start gap-3 p-2 bg-red-50 dark:bg-red-900/20 rounded">
                                                    <span className="text-red-600 font-bold">−</span>
                                                    <pre className="text-xs text-slate-700 dark:text-neutral-300 overflow-x-auto">
                                                        {JSON.stringify(change.before, null, 2)}
                                                    </pre>
                                                </div>
                                                <div className="flex items-start gap-3 p-2 bg-green-50 dark:bg-green-900/20 rounded">
                                                    <span className="text-green-600 font-bold">+</span>
                                                    <pre className="text-xs text-slate-900 dark:text-neutral-100 overflow-x-auto font-semibold">
                                                        {JSON.stringify(change.after, null, 2)}
                                                    </pre>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </Stack>
                            </div>
                        </div>
                    ) : (
                        <div className="bg-slate-900 dark:bg-neutral-950 rounded-lg p-4 overflow-x-auto">
                            <pre className="text-xs text-green-400 font-mono">
                                {JSON.stringify({ entry, userContext, anomalyScore }, null, 2)}
                            </pre>
                        </div>
                    )}
                </Box>
            </Card>

            {/* Forensics Tools */}
            <Card>
                <Box className="p-6">
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                        Forensics Tools
                    </h3>

                    <div className="grid grid-cols-2 gap-3">
                        <Button variant="outline" size="md" fullWidth>
                            🔍 Find Similar Events
                        </Button>
                        <Button variant="outline" size="md" fullWidth>
                            📊 User Activity Timeline
                        </Button>
                        <Button variant="outline" size="md" fullWidth>
                            📥 Export for Compliance
                        </Button>
                        <Button variant="outline" size="md" fullWidth>
                            🔗 View Related Events
                        </Button>
                    </div>
                </Box>
            </Card>

            {/* Remediation Actions */}
            <Card>
                <Box className="p-6">
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                        Remediation Actions
                    </h3>

                    <Stack spacing={3}>
                        {entry.action.includes('restructure') && onRevert && (
                            <Button
                                variant="outline"
                                size="md"
                                fullWidth
                                className="text-orange-600 border-orange-600 hover:bg-orange-50"
                                onClick={() => onRevert(entry.id)}
                            >
                                ↩️ Revert Change
                            </Button>
                        )}

                        {anomalyScore.riskLevel === 'high' || anomalyScore.riskLevel === 'critical' ? (
                            <>
                                {onLockUser && (
                                    <Button
                                        variant="outline"
                                        size="md"
                                        fullWidth
                                        className="text-red-600 border-red-600 hover:bg-red-50"
                                        onClick={() => onLockUser(entry.actor)}
                                    >
                                        🔒 Lock User Account
                                    </Button>
                                )}

                                {onEscalate && (
                                    <Button
                                        variant="primary"
                                        size="md"
                                        fullWidth
                                        onClick={() => onEscalate(entry.id)}
                                    >
                                        🚨 Escalate to Admin
                                    </Button>
                                )}
                            </>
                        ) : null}

                        {onMarkReviewed && (
                            <Button
                                variant="outline"
                                size="md"
                                fullWidth
                                onClick={() => onMarkReviewed(entry.id)}
                            >
                                ✓ Mark as Reviewed
                            </Button>
                        )}
                    </Stack>
                </Box>
            </Card>
        </Box>
    );
}
