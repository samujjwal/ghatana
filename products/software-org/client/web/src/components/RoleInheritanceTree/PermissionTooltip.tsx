/**
 * PermissionTooltip Component
 * 
 * Shows detailed permission list when hovering over a role node
 */

import { memo } from 'react';

export interface PermissionTooltipProps {
    /** List of permissions to display */
    permissions: string[];

    /** Role name */
    roleName: string;

    /** Position of the tooltip */
    position?: { x: number; y: number };

    /** Whether tooltip is visible */
    visible?: boolean;
}

/**
 * PermissionTooltip - Displays permission details on hover
 * 
 * Features:
 * - Lists all permissions for a role
 * - Positioned near cursor
 * - Auto-scrolling for large permission lists
 * - Clean, readable design
 */
export const PermissionTooltip = memo<PermissionTooltipProps>(
    ({ permissions, roleName, position, visible = false }) => {
        if (!visible || !position) {
            return null;
        }

        return (
            <div
                className="fixed z-50 bg-white dark:bg-neutral-800 border-2 border-blue-500 rounded-lg shadow-2xl p-4 max-w-md"
                style={{
                    left: position.x + 15,
                    top: position.y + 15,
                    pointerEvents: 'none',
                }}
            >
                {/* Header */}
                <div className="font-bold text-lg mb-2 text-blue-600 dark:text-indigo-400 border-b border-slate-200 dark:border-neutral-600 pb-2">
                    {roleName}
                </div>

                {/* Permission count */}
                <div className="text-sm text-slate-600 dark:text-neutral-400 mb-2">
                    {permissions.length} permission{permissions.length !== 1 ? 's' : ''}
                </div>

                {/* Permission list */}
                <div className="max-h-60 overflow-y-auto">
                    {permissions.length > 0 ? (
                        <ul className="space-y-1">
                            {permissions.map((permission, index) => (
                                <li
                                    key={index}
                                    className="text-sm text-slate-700 dark:text-neutral-300 flex items-start"
                                >
                                    <span className="text-blue-500 dark:text-indigo-400 mr-2">•</span>
                                    <span className="font-mono text-xs bg-slate-100 dark:bg-neutral-700 px-2 py-1 rounded">
                                        {permission}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <div className="text-sm text-slate-500 dark:text-neutral-400 italic">
                            No permissions defined
                        </div>
                    )}
                </div>
            </div>
        );
    }
);

PermissionTooltip.displayName = 'PermissionTooltip';
