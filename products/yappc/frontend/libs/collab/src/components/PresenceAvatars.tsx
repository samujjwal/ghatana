/**
 * Presence Avatars Component
 *
 * @description Shows avatars of users currently present in a
 * collaborative session with their status indicators.
 */

import React, { useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { PresenceUser, PresenceState } from '../PresenceManager';
import { cn } from '@ghatana/ui';

// =============================================================================
// Types
// =============================================================================

export interface PresenceAvatarsProps {
  users: PresenceUser[];
  maxVisible?: number;
  size?: 'sm' | 'md' | 'lg';
  showStatus?: boolean;
  showTooltip?: boolean;
  className?: string;
  onUserClick?: (user: PresenceUser) => void;
}

// =============================================================================
// Status Indicator Colors
// =============================================================================

const statusColors: Record<PresenceState['status'], string> = {
  online: 'bg-green-500',
  away: 'bg-amber-500',
  busy: 'bg-red-500',
  offline: 'bg-zinc-500',
};

// =============================================================================
// Size Config
// =============================================================================

const sizeConfig = {
  sm: {
    avatar: 'w-6 h-6 text-xs',
    status: 'w-2 h-2',
    overlap: '-ml-1.5',
    ring: 'ring-1',
  },
  md: {
    avatar: 'w-8 h-8 text-sm',
    status: 'w-2.5 h-2.5',
    overlap: '-ml-2',
    ring: 'ring-2',
  },
  lg: {
    avatar: 'w-10 h-10 text-base',
    status: 'w-3 h-3',
    overlap: '-ml-3',
    ring: 'ring-2',
  },
};

// =============================================================================
// Avatar Component
// =============================================================================

const Avatar: React.FC<{
  user: PresenceUser;
  size: 'sm' | 'md' | 'lg';
  showStatus: boolean;
  showTooltip: boolean;
  isFirst: boolean;
  onClick?: () => void;
}> = ({ user, size, showStatus, showTooltip, isFirst, onClick }) => {
  const config = sizeConfig[size];
  const initials = user.name
    .split(' ')
    .map((n) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.8, x: -10 }}
      animate={{ opacity: 1, scale: 1, x: 0 }}
      exit={{ opacity: 0, scale: 0.8, x: -10 }}
      className={cn(
        'relative',
        !isFirst && config.overlap,
        onClick && 'cursor-pointer'
      )}
      onClick={onClick}
      whileHover={onClick ? { scale: 1.1, zIndex: 10 } : undefined}
    >
      <div
        className={cn(
          'relative rounded-full ring-zinc-900 flex items-center justify-center font-medium',
          config.avatar,
          config.ring
        )}
        style={{ backgroundColor: user.color, color: 'white' }}
        title={showTooltip ? user.name : undefined}
      >
        {initials}
      </div>
      {showStatus && (
        <span
          className={cn(
            'absolute bottom-0 right-0 rounded-full ring-1 ring-zinc-900',
            config.status,
            statusColors[user.presence.status]
          )}
        />
      )}
    </motion.div>
  );
};

// =============================================================================
// Overflow Indicator
// =============================================================================

const OverflowIndicator: React.FC<{
  count: number;
  size: 'sm' | 'md' | 'lg';
  users: PresenceUser[];
}> = ({ count, size, users }) => {
  const config = sizeConfig[size];
  const names = users.map((u) => u.name).join(', ');

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      className={cn('relative', config.overlap)}
    >
      <div
        className={cn(
          'rounded-full bg-zinc-700 text-zinc-300 flex items-center justify-center font-medium ring-zinc-900',
          config.avatar,
          config.ring
        )}
        title={names}
      >
        +{count}
      </div>
    </motion.div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const PresenceAvatars: React.FC<PresenceAvatarsProps> = ({
  users,
  maxVisible = 5,
  size = 'md',
  showStatus = true,
  showTooltip = true,
  className,
  onUserClick,
}) => {
  const { visible, overflow } = useMemo(() => {
    if (users.length <= maxVisible) {
      return { visible: users, overflow: [] };
    }
    return {
      visible: users.slice(0, maxVisible),
      overflow: users.slice(maxVisible),
    };
  }, [users, maxVisible]);

  if (users.length === 0) {
    return null;
  }

  return (
    <div className={cn('flex items-center', className)}>
      <AnimatePresence mode="popLayout">
        {visible.map((user, index) => (
          <Avatar
            key={user.id}
            user={user}
            size={size}
            showStatus={showStatus}
            showTooltip={showTooltip}
            isFirst={index === 0}
            onClick={onUserClick ? () => onUserClick(user) : undefined}
          />
        ))}
        {overflow.length > 0 && (
          <OverflowIndicator
            key="overflow"
            count={overflow.length}
            size={size}
            users={overflow}
          />
        )}
      </AnimatePresence>
    </div>
  );
};

export default PresenceAvatars;
