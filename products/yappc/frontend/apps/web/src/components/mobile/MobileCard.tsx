import { Capacitor } from '@capacitor/core';
import { MoreVertical as MoreVert, CheckCircle, AlertCircle as Error, Clock as Schedule, Hammer as Build, Heart as Favorite } from 'lucide-react';
// Core UI components from @ghatana/yappc-ui
import {
    Card,
    CardContent,
    Typography,
    Box,
    Chip,
    Avatar,
    IconButton
} from '@ghatana/ui';

// MUI components not available in @ghatana/yappc-ui
import { CardContent as CardActionArea, LinearProgress as MuiLinearProgress, AvatarGroup } from '@ghatana/ui';

// MUI hooks and utilities
import React, { useState } from 'react';

// Types
/**
 *
 */
interface MobileCardProps {
    title: string;
    subtitle: string;
    status: 'active' | 'paused' | 'completed' | 'archived';
    lastModified: Date;
    buildStatus: 'success' | 'failed' | 'building' | 'pending';
    health: number;
    favorite: boolean;
    team: Array<{ name: string; avatar: string }>;
    onClick: () => void;
    onLongPress?: () => void;
    onMenuClick?: (event: React.MouseEvent<HTMLElement>) => void;
}

// Mobile-optimized project card component
/**
 *
 */
export default function MobileCard({
    title,
    subtitle,
    status,
    lastModified,
    buildStatus,
    health,
    favorite,
    team,
    onClick,
    onLongPress,
    onMenuClick
}: MobileCardProps) {
    const theme = useTheme();
    const [longPressTimer, setLongPressTimer] = useState<NodeJS.Timeout | null>(null);
    const [isPressing, setIsPressing] = useState(false);
    const [showActions, setShowActions] = useState(false);
    const [startX, setStartX] = useState<number | null>(null);

    // Handle long press for mobile context menu
    const handleTouchStart = (e: React.TouchEvent) => {
        setIsPressing(true);
        setStartX(e.touches[0].clientX);
        const timer = setTimeout(() => {
            if (onLongPress) {
                onLongPress();
                // Haptic feedback for long press (guarded for web/E2E)
                try {
                    if (Capacitor && typeof Capacitor.isNativePlatform === 'function' && Capacitor.isNativePlatform()) {
                        const h = (globalThis as unknown).Haptics;
                        if (h && typeof h.impact === 'function') {
                            const Impact = (globalThis as unknown).ImpactStyle;
                            const style = Impact && Impact.Medium ? Impact.Medium : 'medium';
                            h.impact({ style });
                        }
                    }
                } catch (_err) {
                    // noop in web/E2E
                }
            }
        }, 500); // 500ms for long press
        setLongPressTimer(timer);
    };

    const handleTouchEnd = (e: React.TouchEvent) => {
        setIsPressing(false);
        if (longPressTimer) {
            clearTimeout(longPressTimer);
            setLongPressTimer(null);
        }

        // Handle swipe gesture
        if (startX !== null && e.changedTouches[0]) {
            const endX = e.changedTouches[0].clientX;
            const diffX = endX - startX;
            
            // Swipe right to show actions (at least 100px swipe)
            if (diffX > 100) {
                setShowActions(true);
            }
        }
        setStartX(null);
    };

    // Handle mouse events for testing/desktop
    const handleMouseDown = (e: React.MouseEvent) => {
        setStartX(e.clientX);
    };

    const handleMouseUp = (e: React.MouseEvent) => {
        if (startX !== null) {
            const diffX = e.clientX - startX;
            if (diffX > 100) {
                setShowActions(true);
            }
        }
        setStartX(null);
    };

    // Get status color and icon
    const getStatusColor = (status: string) => {
        switch (status) {
            case 'active': return 'success';
            case 'paused': return 'warning';
            case 'completed': return 'info';
            case 'archived': return 'default';
            default: return 'default';
        }
    };

    const getBuildStatusIcon = (buildStatus: string) => {
        switch (buildStatus) {
            case 'success':
                return <CheckCircle tone="success" />;
            case 'failed':
                return <Error tone="danger" />;
            case 'building':
                return <Build tone="info" />;
            case 'pending':
                return <Schedule color="action" />;
            default:
                return <Schedule />;
        }
    };

    const getHealthColor = (health: number): 'success' | 'warning' | 'error' => {
        if (health >= 90) return 'success';
        if (health >= 70) return 'warning';
        return 'error';
    };

    const formatRelativeTime = (date: Date) => {
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        const diffMins = Math.round(diffMs / 60000);
        const diffHours = Math.round(diffMs / 3600000);
        const diffDays = Math.round(diffMs / 86400000);

        if (diffMins < 60) return `${diffMins}m ago`;
        if (diffHours < 24) return `${diffHours}h ago`;
        return `${diffDays}d ago`;
    };

    return (
        <Card
            className="mb-4 rounded-xl relative overflow-hidden transition-all duration-200" style={{ transform: isPressing ? 'scale(0.98)' : 'scale(1)', boxShadow: isPressing ? theme.shadows[8] : theme.shadows[2] }}
        >
            <CardActionArea
                onClick={onClick}
                onTouchStart={handleTouchStart}
                onTouchEnd={handleTouchEnd}
                onMouseDown={handleMouseDown}
                onMouseUp={handleMouseUp}
                className="p-0 block min-h-[120px]"
            >
                <CardContent className="p-4 pb-32" >
                    {/* Header row with title and menu */}
                    <Box className="flex items-start mb-2">
                        <Box className="grow min-w-0">
                            <Typography
                                as="h6"
                                className="font-semibold mb-1 overflow-hidden text-ellipsis whitespace-nowrap text-[1.1rem]"
                            >
                                {title}
                            </Typography>
                            <Typography
                                as="p" className="text-sm"
                                color="text.secondary"
                                className="overflow-hidden text-ellipsis leading-[1.3] line-clamp-2 line-clamp-2 max-h-[2.6em]" >
                                {subtitle}
                            </Typography>
                        </Box>

                        <Box className="flex items-center gap-1 ml-2">
                            {favorite && (
                                <Favorite
                                    className="text-red-600 text-[1.2rem]"
                                />
                            )}
                            {onMenuClick && (
                                // Render as a non-button element to avoid nested <button> inside CardActionArea (which is a button)
                                <IconButton
                                    component="span"
                                    size="sm"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        onMenuClick(e as unknown);
                                    }}
                                    className="p-1 text-gray-500 dark:text-gray-400"
                                >
                                    <MoreVert size={16} />
                                </IconButton>
                            )}
                        </Box>
                    </Box>

                    {/* Status and build status row */}
                    <Box className="flex items-center gap-2 mb-4">
                        <Chip
                            label={status.charAt(0).toUpperCase() + status.slice(1)}
                            color={getStatusColor(status)}
                            size="sm"
                            className="text-xs font-medium h-[24px]"
                        />

                        <Box className="flex items-center gap-1">
                            {getBuildStatusIcon(buildStatus)}
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                {buildStatus === 'building' ? 'Building...' : buildStatus}
                            </Typography>
                        </Box>

                        <Box className="flex items-center gap-1 ml-auto">
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                {formatRelativeTime(lastModified)}
                            </Typography>
                        </Box>
                    </Box>

                    {/* Health progress bar */}
                    <Box className="mb-3">
                        <Box className="flex justify-between items-center mb-1">
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                Health Score
                            </Typography>
                            <Typography
                                as="span" className="text-xs text-gray-500 font-semibold" style={{ color: theme.palette[getHealthColor(health)].main }}
                            >
                                {health}%
                            </Typography>
                        </Box>
                        <MuiLinearProgress
                            variant="determinate"
                            value={health}
                            color={getHealthColor(health)}
                            className="h-[4px] rounded-lg" style={{ backgroundColor: alpha(theme.palette.grey[300], background: 'linear-gradient(90deg, borderTopRightRadius: 12, borderBottomRightRadius: 12' }}
                        />
                    </Box>

                    {/* Team avatars */}
                    {team.length > 0 && (
                        <Box className="flex items-center gap-2">
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                Team:
                            </Typography>
                            <AvatarGroup
                                max={4}
                                className="[&_.MuiAvatar-root]:w-6 [&_.MuiAvatar-root]:h-6 [&_.MuiAvatar-root]:text-[0.7rem] [&_.MuiAvatar-root]:border [&_.MuiAvatar-root]:border-solid [&_.MuiAvatar-root]:border-white dark:[&_.MuiAvatar-root]:border-gray-900"
                            >
                                {team.map((member, index) => (
                                        <Avatar
                                        key={index}
                                        src={member.avatar}
                                        alt={member.name}
                                        style={{ backgroundColor: theme.palette.primary.main }}
                                    >
                                        {member.name.charAt(0).toUpperCase()}
                                    </Avatar>
                                ))}
                            </AvatarGroup>
                        </Box>
                    )}
                </CardContent>
            </CardActionArea>
            
            {/* Project Actions Overlay (shown after swipe) */}
            {showActions && (
                <Box
                    data-testid="project-actions"
                    className="absolute flex items-center justify-center gap-2 top-[0px] right-[0px] bottom-[0px] w-[120px] z-10" onClick={(e) => {
                        e.stopPropagation();
                        setShowActions(false);
                    }}
                >
                    <IconButton
                        size="sm"
                        className="text-white"
                        onClick={(e) => {
                            e.stopPropagation();
                            setShowActions(false);
                        }}
                    >
                        <Favorite size={16} />
                    </IconButton>
                    <IconButton
                        size="sm"
                        className="text-white"
                        onClick={(e) => {
                            e.stopPropagation();
                            if (onMenuClick) {
                                onMenuClick(e as unknown);
                            }
                            setShowActions(false);
                        }}
                    >
                        <MoreVert size={16} />
                    </IconButton>
                </Box>
            )}
        </Card>
    );
}