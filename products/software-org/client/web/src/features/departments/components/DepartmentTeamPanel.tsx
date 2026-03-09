import React from 'react';

/**
 * Team member interface.
 */
export interface TeamMember {
    id: string;
    name: string;
    role: string;
    email: string;
    avatar?: string;
    status: 'active' | 'idle' | 'offline';
    lastActive?: Date;
}

/**
 * Department Team Panel Props interface.
 */
export interface DepartmentTeamPanelProps {
    departmentId: string;
    departmentName: string;
    teamMembers: TeamMember[];
    onMemberClick?: (member: TeamMember) => void;
    onInviteMember?: () => void;
    isLoading?: boolean;
    maxVisibleMembers?: number;
}

/**
 * Department Team Panel - Displays team members for a department.
 *
 * <p><b>Purpose</b><br>
 * Shows team composition with member details, status indicators, and team management controls.
 *
 * <p><b>Features</b><br>
 * - Team member list with avatar, name, role, status
 * - Online/idle/offline status indicators
 * - Member detail drill-down
 * - Invite member button
 * - Pagination for large teams
 * - Dark mode support
 * - Keyboard accessible
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <DepartmentTeamPanel 
 *   departmentId="dept_123"
 *   departmentName="Engineering"
 *   teamMembers={members}
 *   onMemberClick={handleMemberClick}
 *   onInviteMember={handleInvite}
 *   maxVisibleMembers={10}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Department team display
 * @doc.layer product
 * @doc.pattern Organism
 */
export const DepartmentTeamPanel = React.memo(
    ({
        departmentName,
        teamMembers,
        onMemberClick,
        onInviteMember,
        isLoading,
        maxVisibleMembers = 10,
    }: DepartmentTeamPanelProps) => {
        const [showAll, setShowAll] = React.useState(false);
        const displayedMembers = showAll ? teamMembers : teamMembers.slice(0, maxVisibleMembers);

        if (isLoading) {
            return (
                <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                    <div className="animate-pulse space-y-4">
                        <div className="h-8 w-48 bg-slate-200 dark:bg-neutral-700 rounded" />
                        {Array.from({ length: 3 }).map((_, i) => (
                            <div key={i} className="h-12 bg-slate-200 dark:bg-neutral-700 rounded" />
                        ))}
                    </div>
                </div>
            );
        }

        const getStatusColor = (status: TeamMember['status']) => {
            const colors = {
                active: 'bg-green-400 dark:bg-green-500',
                idle: 'bg-yellow-400 dark:bg-yellow-500',
                offline: 'bg-slate-400 dark:bg-slate-600',
            };
            return colors[status];
        };

        const getInitials = (name: string) => {
            return name
                .split(' ')
                .map((n) => n[0])
                .join('')
                .toUpperCase()
                .slice(0, 2);
        };

        return (
            <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                <div className="flex items-center justify-between mb-6">
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                        {departmentName} Team
                    </h3>
                    <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-slate-600 dark:text-neutral-400">
                            {teamMembers.length} members
                        </span>
                        {onInviteMember && (
                            <button
                                onClick={onInviteMember}
                                className="px-3 py-1.5 rounded-md bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 dark:hover:bg-blue-500 transition-colors"
                                aria-label="Invite team member"
                            >
                                + Invite
                            </button>
                        )}
                    </div>
                </div>

                {teamMembers.length === 0 ? (
                    <div className="py-8 text-center">
                        <p className="text-slate-600 dark:text-neutral-400 mb-4">No team members yet</p>
                        {onInviteMember && (
                            <button
                                onClick={onInviteMember}
                                className="px-4 py-2 rounded-md bg-blue-50 text-blue-600 dark:bg-indigo-600/30 dark:text-indigo-400 font-medium hover:bg-blue-100 dark:hover:bg-blue-900/40 transition-colors"
                            >
                                Add Team Members
                            </button>
                        )}
                    </div>
                ) : (
                    <>
                        <div className="space-y-3 mb-4">
                            {displayedMembers.map((member) => (
                                <div
                                    key={member.id}
                                    onClick={() => onMemberClick?.(member)}
                                    role={onMemberClick ? 'button' : undefined}
                                    tabIndex={onMemberClick ? 0 : undefined}
                                    onKeyDown={(e) => {
                                        if ((e.key === 'Enter' || e.key === ' ') && onMemberClick) {
                                            onMemberClick(member);
                                        }
                                    }}
                                    className={`flex items-center gap-3 p-3 rounded-lg border border-slate-200 dark:border-neutral-600 transition-colors ${onMemberClick
                                            ? 'cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800'
                                            : 'bg-white dark:bg-slate-900'
                                        }`}
                                >
                                    {/* Avatar */}
                                    <div className="relative flex-shrink-0">
                                        <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-400 to-purple-500 flex items-center justify-center text-white text-sm font-bold">
                                            {member.avatar ? (
                                                <img
                                                    src={member.avatar}
                                                    alt={member.name}
                                                    className="w-full h-full rounded-full object-cover"
                                                />
                                            ) : (
                                                getInitials(member.name)
                                            )}
                                        </div>
                                        {/* Status indicator */}
                                        <div
                                            className={`absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full border-2 border-white dark:border-slate-900 ${getStatusColor(
                                                member.status
                                            )}`}
                                        />
                                    </div>

                                    {/* Member info */}
                                    <div className="flex-1 min-w-0">
                                        <p className="font-medium text-slate-900 dark:text-neutral-100 truncate">
                                            {member.name}
                                        </p>
                                        <p className="text-sm text-slate-600 dark:text-neutral-400 truncate">
                                            {member.role}
                                        </p>
                                        <p className="text-xs text-slate-500 dark:text-slate-500 truncate">
                                            {member.email}
                                        </p>
                                    </div>

                                    {/* Status badge */}
                                    <div className="flex-shrink-0">
                                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-slate-100 text-slate-700 dark:bg-neutral-800 dark:text-neutral-300">
                                            <span className={`w-1.5 h-1.5 rounded-full ${getStatusColor(member.status)}`} />
                                            {member.status}
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </div>

                        {teamMembers.length > maxVisibleMembers && (
                            <button
                                onClick={() => setShowAll(!showAll)}
                                className="w-full py-2 px-3 rounded-md text-sm font-medium text-blue-600 hover:text-blue-700 dark:text-indigo-400 dark:hover:text-blue-300 hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors"
                                aria-expanded={showAll}
                                aria-label={showAll ? 'Show less team members' : 'Show all team members'}
                            >
                                {showAll ? '← Show less' : `→ Show ${teamMembers.length - maxVisibleMembers} more`}
                            </button>
                        )}
                    </>
                )}
            </div>
        );
    }
);

DepartmentTeamPanel.displayName = 'DepartmentTeamPanel';

export default DepartmentTeamPanel;
