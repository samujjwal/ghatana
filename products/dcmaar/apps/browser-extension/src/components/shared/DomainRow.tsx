/**
 * @fileoverview Domain Row Component
 *
 * Reusable row component for displaying domain information with actions.
 * Used in Dashboard (DomainUsageTable, BlockedPagesLog) and Settings (domain list).
 */

import React from 'react';
import { StatusBadge, ContentRiskBadge } from './StatusBadge';
import { ActionButton } from './ActionButton';
import type { AccessStatus, ContentRiskLevel, DomainPolicyStatus } from '../../types';
import { formatDuration, formatRelativeTime } from '../../types';

// ============================================================================
// Domain Usage Row (for Dashboard DomainUsageTable)
// ============================================================================

export interface DomainUsageRowProps {
    domain: string;
    timeMinutes: number;
    visits: number;
    blockedAttempts: number;
    contentRisk: ContentRiskLevel;
    status?: AccessStatus;
    selected?: boolean;
    onSelect?: () => void;
    onBlock?: () => void;
    onUnblock?: () => void;
}

export function DomainUsageRow({
    domain,
    timeMinutes,
    visits,
    blockedAttempts,
    contentRisk,
    status = 'allowed',
    selected = false,
    onSelect,
    onBlock,
    onUnblock,
}: DomainUsageRowProps) {
    const isBlocked = status === 'blocked';

    return (
        <div
            className={`flex items-center justify-between p-3 rounded-lg border transition-colors cursor-pointer ${selected
                ? 'border-blue-300 bg-blue-50'
                : 'border-gray-100 hover:border-gray-200 hover:bg-gray-50'
                }`}
            onClick={onSelect}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => e.key === 'Enter' && onSelect?.()}
        >
            <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-gray-900 truncate">{domain}</span>
                    {status !== 'allowed' && <StatusBadge status={status} size="sm" />}
                </div>
                <div className="flex items-center gap-3 mt-1 text-xs text-gray-500">
                    <span>{formatDuration(timeMinutes)}</span>
                    <span>•</span>
                    <span>{visits} visits</span>
                    {blockedAttempts > 0 && (
                        <>
                            <span>•</span>
                            <span className="text-red-600">{blockedAttempts} blocked</span>
                        </>
                    )}
                </div>
            </div>

            <div className="flex items-center gap-2 ml-3">
                <ContentRiskBadge risk={contentRisk} size="sm" />
                {isBlocked ? (
                    <ActionButton variant="secondary" size="sm" onClick={(e: React.MouseEvent) => { e.stopPropagation(); onUnblock?.(); }}>
                        Unblock
                    </ActionButton>
                ) : (
                    <ActionButton variant="danger" size="sm" onClick={(e: React.MouseEvent) => { e.stopPropagation(); onBlock?.(); }}>
                        Block
                    </ActionButton>
                )}
            </div>
        </div>
    );
}

// ============================================================================
// Blocked Event Row (for Dashboard BlockedPagesLog)
// ============================================================================

export interface BlockedEventRowProps {
    timestamp: number;
    domain: string;
    url: string;
    title?: string;
    reason: string;
    onAllow?: () => void;
    onTempAllow?: () => void;
}

export function BlockedEventRow({
    timestamp,
    domain,
    url,
    title,
    reason,
    onAllow,
    onTempAllow,
}: BlockedEventRowProps) {
    return (
        <div className="flex items-center justify-between p-3 rounded-lg border border-gray-100 hover:border-gray-200 hover:bg-gray-50 transition-colors">
            <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                    <span className="text-xs text-gray-400">{formatRelativeTime(timestamp)}</span>
                    <span className="text-sm font-medium text-gray-900 truncate">{domain}</span>
                </div>
                <p className="text-xs text-gray-500 truncate mt-0.5" title={url}>
                    {title || url}
                </p>
                <p className="text-xs text-red-600 mt-0.5">{reason}</p>
            </div>

            <div className="flex items-center gap-2 ml-3">
                <ActionButton variant="secondary" size="sm" onClick={onAllow}>
                    Allow
                </ActionButton>
                <ActionButton variant="primary" size="sm" onClick={onTempAllow}>
                    Allow 30m
                </ActionButton>
            </div>
        </div>
    );
}

// ============================================================================
// Domain Policy Row (for Settings block/allow list)
// ============================================================================

export interface DomainPolicyRowProps {
    domain: string;
    status: DomainPolicyStatus;
    onStatusChange: (status: DomainPolicyStatus) => void;
    onRemove?: () => void;
}

export function DomainPolicyRow({
    domain,
    status,
    onStatusChange,
    onRemove,
}: DomainPolicyRowProps) {
    return (
        <div className="flex items-center justify-between p-3 rounded-lg border border-gray-100 hover:border-gray-200 transition-colors">
            <span className="text-sm font-medium text-gray-900 truncate flex-1 min-w-0">{domain}</span>

            <div className="flex items-center gap-2 ml-3">
                <select
                    value={status}
                    onChange={(e) => onStatusChange(e.target.value as DomainPolicyStatus)}
                    className="text-sm border border-gray-300 rounded-md px-2 py-1 focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                    <option value="default">Default</option>
                    <option value="blocked">Blocked</option>
                    <option value="allowed">Allowed</option>
                </select>
                {onRemove && (
                    <ActionButton variant="ghost" size="sm" onClick={onRemove}>
                        ✕
                    </ActionButton>
                )}
            </div>
        </div>
    );
}

// ============================================================================
// Simple Domain Info Row (for Popup current site)
// ============================================================================

export interface CurrentDomainRowProps {
    domain: string;
    status: AccessStatus;
    reason?: string;
    timeToday?: number;
    visitsToday?: number;
}

export function CurrentDomainRow({
    domain,
    status,
    reason,
    timeToday,
    visitsToday,
}: CurrentDomainRowProps) {
    return (
        <div className="p-4 bg-white rounded-lg border border-gray-200">
            <div className="flex items-center justify-between">
                <span className="text-base font-semibold text-gray-900 truncate">{domain}</span>
                <StatusBadge status={status} size="md" />
            </div>
            {reason && (
                <p className="text-xs text-gray-500 mt-1">{reason}</p>
            )}
            {(timeToday !== undefined || visitsToday !== undefined) && (
                <div className="flex items-center gap-3 mt-2 text-xs text-gray-600">
                    {timeToday !== undefined && <span>{formatDuration(timeToday)} today</span>}
                    {visitsToday !== undefined && (
                        <>
                            <span>•</span>
                            <span>{visitsToday} visits today</span>
                        </>
                    )}
                </div>
            )}
        </div>
    );
}
