/**
 * Presence Indicators Component
 * 
 * Shows real-time presence of users viewing/editing artifacts.
 * 
 * @doc.type component
 * @doc.purpose Real-time presence visualization
 * @doc.layer product
 * @doc.pattern Presence
 */

import * as React from 'react';
import { Box, Avatar, AvatarGroup, Tooltip, Typography, Badge } from '@ghatana/ui';
import { Eye as ViewingIcon, Pencil as EditingIcon } from 'lucide-react';

export interface PresenceUser {
    id: string;
    name: string;
    avatar?: string;
    color: string;
    status: 'viewing' | 'editing';
    lastSeen: Date;
}

export interface PresenceIndicatorProps {
    users: PresenceUser[];
    maxDisplay?: number;
    size?: 'small' | 'medium';
}

const getInitials = (name: string): string => {
    return name
        .split(' ')
        .map((n) => n[0])
        .join('')
        .toUpperCase()
        .substring(0, 2);
};

export const PresenceIndicator: React.FC<PresenceIndicatorProps> = ({ users, maxDisplay = 3, size = 'small' }) => {
    if (!users.length) return null;

    const avatarSize = size === 'small' ? 28 : 36;

    return (
        <AvatarGroup max={maxDisplay} className={`${size === 'small' ? '[&_.MuiAvatar-root]:w-7 [&_.MuiAvatar-root]:h-7 [&_.MuiAvatar-root]:text-xs' : '[&_.MuiAvatar-root]:w-9 [&_.MuiAvatar-root]:h-9 [&_.MuiAvatar-root]:text-sm'}`}>
            {users.map((user) => (
                <Tooltip
                    key={user.id}
                    title={
                        <Box>
                            <Typography as="span" className="text-xs text-gray-500" fontWeight="bold">
                                {user.name}
                            </Typography>
                            <Box className="flex items-center gap-1 mt-1">
                                {user.status === 'editing' ? (
                                    <>
                                        <EditingIcon className="text-xs" />
                                        <Typography as="span" className="text-xs text-gray-500">Editing</Typography>
                                    </>
                                ) : (
                                    <>
                                        <ViewingIcon className="text-xs" />
                                        <Typography as="span" className="text-xs text-gray-500">Viewing</Typography>
                                    </>
                                )}
                            </Box>
                        </Box>
                    }
                    arrow
                >
                    <Badge
                        overlap="circular"
                        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                        badgeContent={
                            <Box
                                className={`w-[10px] h-[10px] rounded-full border-[2px] border-white ${user.status === 'editing' ? 'bg-green-500' : 'bg-blue-500'}`} />
                        }
                    >
                        <Avatar
                            src={user.avatar}
                            className="border-[2px] cursor-pointer" style={{ backgroundColor: user.color, borderColor: user.status === 'editing' ? '#22c55e' : 'transparent' }}
                        >
                            {getInitials(user.name)}
                        </Avatar>
                    </Badge>
                </Tooltip>
            ))}
        </AvatarGroup>
    );
};

/**
 * Canvas-wide presence component showing all active users
 */
export interface CanvasPresenceProps {
    users: PresenceUser[];
}

export const CanvasPresence: React.FC<CanvasPresenceProps> = ({ users }) => {
    const viewingUsers = users.filter((u) => u.status === 'viewing');
    const editingUsers = users.filter((u) => u.status === 'editing');

    return (
        <Box
            className="flex items-center gap-4 px-4 py-2 rounded bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700"
        >
            {editingUsers.length > 0 && (
                <Box className="flex items-center gap-2">
                    <EditingIcon size={16} tone="success" />
                    <PresenceIndicator users={editingUsers} />
                </Box>
            )}
            {viewingUsers.length > 0 && (
                <Box className="flex items-center gap-2">
                    <ViewingIcon size={16} color="action" />
                    <PresenceIndicator users={viewingUsers} />
                </Box>
            )}
            {users.length === 0 && (
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                    No one else here
                </Typography>
            )}
        </Box>
    );
};
