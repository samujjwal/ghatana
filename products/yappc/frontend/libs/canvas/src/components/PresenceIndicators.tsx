import { Circle, Eye as Visibility, Pencil as Edit, MousePointer as Mouse, Pointer as TouchApp, SignalWifiOff, Users as Group, ChevronDown as ExpandMore, ChevronUp as ExpandLess } from 'lucide-react';
import {
  Box,
  Avatar,
  AvatarGroup,
  Chip,
  Stack,
  Typography,
  IconButton,
  Tooltip,
  Badge,
  ListItem,
  ListItemText,
  Surface as Paper,
  InteractiveList as List,
} from '@ghatana/ui';
import { ListItemAvatar, Fade } from '@ghatana/ui';
import { Popover, useTheme, resolveMuiColor } from '@ghatana/yappc-ui';
import React, { useState, useEffect } from 'react';

import { useCollaboration } from '../hooks/useCollaboration';

import type { CollaborationUser } from '../hooks/useCollaboration';

/**
 *
 */
export interface PresenceIndicatorsProps {
  /** Room ID for collaboration */
  roomId: string;
  /** Current user ID */
  userId: string;
  /** Current user name */
  userName: string;
  /** Position on screen */
  position?: 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left';
  /** Show detailed user list */
  showUserList?: boolean;
  /** Maximum users to show in avatar group */
  maxAvatars?: number;
}

/**
 * Real-time presence indicators showing collaborating users
 * Implements Sprint 3 presence awareness requirements
 */
export function PresenceIndicators({
  roomId,
  userId,
  userName,
  position = 'top-right',
  showUserList = true,
  maxAvatars = 5
}: PresenceIndicatorsProps) {
  const {
    remoteUsers,
    currentUser,
    isConnected,
    syncStatus,
    getRemoteCursors,
    getRemoteSelections
  } = useCollaboration(roomId, userId, userName);

  const [showDetails, setShowDetails] = useState(false);
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [cursors, setCursors] = useState<unknown>({});
  const [selections, setSelections] = useState<unknown>({});

  // Update cursors and selections periodically
  useEffect(() => {
    const interval = setInterval(() => {
      setCursors(getRemoteCursors());
      setSelections(getRemoteSelections());
    }, 1000);

    return () => clearInterval(interval);
  }, [getRemoteCursors, getRemoteSelections]);

  const handleUserListClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
    setShowDetails(true);
  };

  const handleClose = () => {
    setAnchorEl(null);
    setShowDetails(false);
  };

  const getPositionStyles = () => {
    const baseStyles = {
      position: 'fixed' as const,
      zIndex: 1300,
      padding: 2
    };

    switch (position) {
      case 'top-left':
        return { ...baseStyles, top: 16, left: 16 };
      case 'top-right':
        return { ...baseStyles, top: 16, right: 16 };
      case 'bottom-left':
        return { ...baseStyles, bottom: 16, left: 16 };
      case 'bottom-right':
        return { ...baseStyles, bottom: 16, right: 16 };
      default:
        return { ...baseStyles, top: 16, right: 16 };
    }
  };

  const getSyncStatusColor = () => {
    switch (syncStatus) {
      case 'synced': return 'success';
      case 'syncing': return 'warning';
      case 'error': return 'error';
      case 'offline': return 'error';
      default: return 'default';
    }
  };

  const theme = useTheme();

  const getSyncStatusIcon = () => {
    switch (syncStatus) {
      case 'synced': return <Circle size={16} />;
      case 'syncing': return <Circle size={16} className="animate-pulse" />;
      case 'error': return <SignalWifiOff size={16} />;
      case 'offline': return <SignalWifiOff size={16} />;
      default: return <Circle size={16} />;
    }
  };

  const onlineUsers = remoteUsers.filter(user => user.isOnline);
  const totalUsers = onlineUsers.length + 1; // +1 for current user

  return (
    <>
      <Paper 
        elevation={3}
        style={{
          ...getPositionStyles(),
          borderRadius: 12,
          overflow: 'hidden',
          border: '1px solid rgba(0,0,0,0.12)',
        }}
      >
        <Stack spacing={1}>
          {/* Connection Status */}
          <Box display="flex" alignItems="center" gap={1} px={1} py={0.5}>
            <Chip
              icon={getSyncStatusIcon()}
              label={isConnected ? syncStatus : 'offline'}
              size="small"
              color={resolveMuiColor(theme, String(getSyncStatusColor()), 'default')}
              variant="outlined"
              className="text-[0.7rem] h-[20px]"
            />
            <Typography variant="caption" color="text.secondary">
              {totalUsers} online
            </Typography>
          </Box>

          {/* User Avatars */}
          <Box px={1} pb={1}>
            <Stack direction="row" alignItems="center" spacing={1}>
              <AvatarGroup 
                max={maxAvatars}
                className="[&_.MuiAvatar-root]:w-8 [&_.MuiAvatar-root]:h-8 [&_.MuiAvatar-root]:text-sm [&_.MuiAvatar-root]:border-2 [&_.MuiAvatar-root]:border-white"
              >
                {/* Current User */}
                <Tooltip title={`${currentUser.name} (you)`}>
                  <Badge
                    badgeContent={<Edit size={undefined} />}
                    color="primary"
                    overlap="circular"
                    anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                  >
                    <Avatar
                      className="border-blue-600 border-[2px_solid]" style={{ backgroundColor: 'currentUser.color' }} >
                      {currentUser.name.charAt(0).toUpperCase()}
                    </Avatar>
                  </Badge>
                </Tooltip>

                {/* Remote Users */}
                {onlineUsers.map(user => (
                  <Tooltip key={user.id} title={user.name}>
                    <Badge
                      badgeContent={
                        cursors[user.id] ? (
                          <Mouse size={undefined} />
                        ) : selections[user.id] ? (
                          <TouchApp size={undefined} />
                        ) : (
                          <Visibility size={undefined} />
                        )
                      }
                      color="secondary"
                      overlap="circular"
                      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                    >
                      <Avatar
                        style={{ 
                          backgroundColor: user.color,
                          opacity: user.isOnline ? 1 : 0.5
                        }}
                      >
                        {user.name.charAt(0).toUpperCase()}
                      </Avatar>
                    </Badge>
                  </Tooltip>
                ))}
              </AvatarGroup>

              {/* User List Toggle */}
              {showUserList && onlineUsers.length > 0 && (
                <IconButton
                  size="small"
                  onClick={handleUserListClick}
                  className="ml-2"
                >
                  {showDetails ? <ExpandLess /> : <ExpandMore />}
                </IconButton>
              )}
            </Stack>
          </Box>
        </Stack>
      </Paper>

      {/* Detailed User List Popover */}
      <Popover
        open={showDetails}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'left',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'left',
        }}
      >
        <Paper className="overflow-auto w-[280px] max-h-[400px]">
          <Box p={2} borderBottom="1px solid" borderColor="divider">
            <Stack direction="row" alignItems="center" spacing={1}>
              <Group color="primary" />
              <Typography variant="h6">
                Collaborators ({totalUsers})
              </Typography>
            </Stack>
          </Box>

          <List dense>
            {/* Current User */}
            <ListItem>
              <ListItemAvatar>
                <Badge
                  badgeContent={<Circle size={16} />}
                  color="success"
                  overlap="circular"
                >
                  <Avatar style={{ backgroundColor: currentUser.color }}>
                    {currentUser.name.charAt(0).toUpperCase()}
                  </Avatar>
                </Badge>
              </ListItemAvatar>
              <ListItemText
                primary={`${currentUser.name} (you)`}
                secondary="Currently editing"
              />
            </ListItem>

            {/* Remote Users */}
            {onlineUsers.map(user => {
              const userCursor = cursors[user.id];
              const userSelection = selections[user.id];
              
              let activity = 'Viewing';
              if (userCursor) {
                activity = `Cursor at (${Math.round(userCursor.x)}, ${Math.round(userCursor.y)})`;
              } else if (userSelection?.nodeIds.length > 0) {
                activity = `Selected ${userSelection.nodeIds.length} item(s)`;
              }

              return (
                <ListItem key={user.id}>
                  <ListItemAvatar>
                    <Badge
                      badgeContent={<Circle size={16} />}
                      color={resolveMuiColor(theme, user.isOnline ? 'success' : 'error', 'default')}
                      overlap="circular"
                    >
                      <Avatar style={{ backgroundColor: user.color }}>
                        {user.name.charAt(0).toUpperCase()}
                      </Avatar>
                    </Badge>
                  </ListItemAvatar>
                  <ListItemText
                    primary={user.name}
                    secondary={activity}
                  />
                </ListItem>
              );
            })}
          </List>
        </Paper>
      </Popover>

      {/* Remote Cursors */}
      {Object.entries(cursors).map(([uid, cursor]: [string, any]) => (
        <RemoteCursor
          key={uid}
          x={cursor.x}
          y={cursor.y}
          user={cursor.user}
        />
      ))}
    </>
  );
}

/**
 * Remote cursor component showing other users' mouse positions
 */
function RemoteCursor({ x, y, user }: { x: number; y: number; user: CollaborationUser }) {
  return (
    <Fade in timeout={200}>
      <Box
        className="fixed pointer-events-none z-[1400]" style={{ left: 'x', top: 'y', transform: 'translate(-2px' }} >
        {/* Cursor SVG */}
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
          <path
            d="M8.5 2L2 8.5L8.5 15L15 8.5L8.5 2Z"
            fill={user.color}
            stroke="white"
            strokeWidth="1"
          />
          <path
            d="M12 12L18 18"
            stroke={user.color}
            strokeWidth="2"
            strokeLinecap="round"
          />
        </svg>

        {/* User name label */}
        <Paper
          elevation={2}
          className="absolute px-2 py-1 text-xs whitespace-nowrap rounded top-[20px] left-[10px] text-white" style={{ backgroundColor: 'user.color' }} >
          {user.name}
        </Paper>
      </Box>
    </Fade>
  );
}

export default PresenceIndicators;