/**
 * User Activity Indicator Component
 *
 * @description Shows what a user is currently working on
 * in a collaborative session.
 */

import React from 'react';
import { motion } from 'framer-motion';
import {
  FileCode,
  Layout,
  FileText,
  MessageCircle,
  Settings,
  Eye,
} from 'lucide-react';
import { PresenceUser, PresenceLocation } from '../PresenceManager';
import { cn } from '@ghatana/ui';

// =============================================================================
// Types
// =============================================================================

export interface UserActivityIndicatorProps {
  user: PresenceUser;
  showLocation?: boolean;
  showActivity?: boolean;
  variant?: 'inline' | 'card';
  className?: string;
}

// =============================================================================
// Location Icon
// =============================================================================

const LocationIcon: React.FC<{ type: PresenceLocation['type'] }> = ({ type }) => {
  const iconProps = { size: 14, className: 'text-zinc-400' };

  switch (type) {
    case 'file':
      return <FileCode {...iconProps} />;
    case 'canvas':
      return <Layout {...iconProps} />;
    case 'document':
      return <FileText {...iconProps} />;
    case 'chat':
      return <MessageCircle {...iconProps} />;
    case 'page':
    default:
      return <Eye {...iconProps} />;
  }
};

// =============================================================================
// Inline Variant
// =============================================================================

const InlineVariant: React.FC<{
  user: PresenceUser;
  showLocation: boolean;
  showActivity: boolean;
}> = ({ user, showLocation, showActivity }) => {
  const initials = user.name
    .split(' ')
    .map((n) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);

  const location = user.presence.location;
  const activity = user.presence.activity;

  return (
    <div className="flex items-center gap-2">
      {/* Avatar */}
      <div
        className="w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium text-white"
        style={{ backgroundColor: user.color }}
      >
        {initials}
      </div>

      {/* Info */}
      <div className="flex items-center gap-1.5 text-sm">
        <span className="text-zinc-200 font-medium">{user.name}</span>
        {showActivity && activity && (
          <span className="text-zinc-500">• {activity}</span>
        )}
        {showLocation && location && (
          <>
            <span className="text-zinc-600">in</span>
            <span className="flex items-center gap-1 text-zinc-400">
              <LocationIcon type={location.type} />
              {location.name || location.path}
            </span>
          </>
        )}
      </div>
    </div>
  );
};

// =============================================================================
// Card Variant
// =============================================================================

const CardVariant: React.FC<{
  user: PresenceUser;
  showLocation: boolean;
  showActivity: boolean;
}> = ({ user, showLocation, showActivity }) => {
  const initials = user.name
    .split(' ')
    .map((n) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);

  const location = user.presence.location;
  const activity = user.presence.activity;

  const statusColors = {
    online: 'bg-green-500',
    away: 'bg-amber-500',
    busy: 'bg-red-500',
    offline: 'bg-zinc-500',
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="p-3 rounded-lg bg-zinc-800/50 border border-zinc-700/50"
    >
      <div className="flex items-start gap-3">
        {/* Avatar with status */}
        <div className="relative">
          <div
            className="w-10 h-10 rounded-full flex items-center justify-center text-sm font-medium text-white"
            style={{ backgroundColor: user.color }}
          >
            {initials}
          </div>
          <span
            className={cn(
              'absolute bottom-0 right-0 w-3 h-3 rounded-full ring-2 ring-zinc-800',
              statusColors[user.presence.status]
            )}
          />
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-medium text-zinc-200 truncate">
              {user.name}
            </span>
            <span className="text-xs text-zinc-500 capitalize">
              {user.presence.status}
            </span>
          </div>

          {showActivity && activity && (
            <p className="text-sm text-zinc-400 mt-0.5 truncate">{activity}</p>
          )}

          {showLocation && location && (
            <div className="flex items-center gap-1.5 mt-1.5 text-xs text-zinc-500">
              <LocationIcon type={location.type} />
              <span className="truncate">{location.name || location.path}</span>
            </div>
          )}
        </div>
      </div>
    </motion.div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const UserActivityIndicator: React.FC<UserActivityIndicatorProps> = ({
  user,
  showLocation = true,
  showActivity = true,
  variant = 'inline',
  className,
}) => {
  const Component = variant === 'card' ? CardVariant : InlineVariant;

  return (
    <div className={className}>
      <Component
        user={user}
        showLocation={showLocation}
        showActivity={showActivity}
      />
    </div>
  );
};

export default UserActivityIndicator;
