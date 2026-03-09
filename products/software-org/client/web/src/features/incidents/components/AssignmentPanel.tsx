import React from 'react';
import { StatusBadge } from '@/shared/components';

/**
 * Assignment Panel Props interface.
 */
export interface AssignmentPanelProps {
    incidentId: string;
    currentAssignee?: string;
    availableAssignees: Array<{ id: string; name: string; avatar?: string; isAvailable: boolean }>;
    onAssign?: (userId: string) => void;
    isLoading?: boolean;
}

/**
 * Assignment Panel - Assign incidents to team members.
 *
 * <p><b>Purpose</b><br>
 * Provides UI for assigning incidents to available team members with availability indicators.
 *
 * <p><b>Features</b><br>
 * - List of available assignees
 * - Current assignee highlight
 * - Availability indicators
 * - Avatar display
 * - Quick assignment buttons
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <AssignmentPanel
 *   incidentId="inc_123"
 *   currentAssignee="user_456"
 *   availableAssignees={teamMembers}
 *   onAssign={handleAssign}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Incident assignment UI
 * @doc.layer product
 * @doc.pattern Molecule
 */
export const AssignmentPanel = React.memo(
    ({
        incidentId,
        currentAssignee,
        availableAssignees,
        onAssign,
        isLoading,
    }: AssignmentPanelProps) => {
        return (
            <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                    Assign Incident
                </h3>

                {isLoading ? (
                    <div className="animate-pulse space-y-3">
                        {Array.from({ length: 3 }).map((_, i) => (
                            <div key={i} className="h-12 bg-slate-200 dark:bg-neutral-700 rounded" />
                        ))}
                    </div>
                ) : availableAssignees.length === 0 ? (
                    <p className="text-center text-slate-500 dark:text-neutral-400 py-6">
                        No team members available
                    </p>
                ) : (
                    <div className="space-y-2">
                        {availableAssignees.map((assignee) => (
                            <button
                                key={assignee.id}
                                onClick={() => onAssign?.(assignee.id)}
                                disabled={!assignee.isAvailable || isLoading}
                                className={`w-full p-3 rounded-lg border-2 transition-all text-left flex items-center justify-between ${currentAssignee === assignee.id
                                    ? 'border-blue-500 bg-blue-50 dark:bg-indigo-600/30 dark:border-blue-500'
                                    : 'border-slate-200 hover:border-slate-300 dark:border-neutral-600 dark:hover:border-slate-600'
                                    } ${!assignee.isAvailable ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                                aria-pressed={currentAssignee === assignee.id}
                            >
                                <div className="flex items-center gap-3 flex-1">
                                    {assignee.avatar ? (
                                        <img
                                            src={assignee.avatar}
                                            alt={assignee.name}
                                            className="w-8 h-8 rounded-full"
                                        />
                                    ) : (
                                        <div className="w-8 h-8 rounded-full bg-slate-300 dark:bg-slate-600 flex items-center justify-center text-xs font-semibold">
                                            {assignee.name.charAt(0)}
                                        </div>
                                    )}
                                    <span className="font-medium text-slate-900 dark:text-neutral-100">
                                        {assignee.name}
                                    </span>
                                </div>
                                <span
                                    className="px-2 py-1 rounded-full text-xs font-medium"
                                >
                                    {assignee.isAvailable ? <StatusBadge status="active" /> : <StatusBadge status="inactive" />}
                                </span>
                            </button>
                        ))}
                    </div>
                )}

                {currentAssignee && (
                    <p className="text-xs text-slate-600 dark:text-neutral-400 mt-4 text-center">
                        Incident #{incidentId.slice(-4)} assigned
                    </p>
                )}
            </div>
        );
    }
);

AssignmentPanel.displayName = 'AssignmentPanel';

export default AssignmentPanel;
