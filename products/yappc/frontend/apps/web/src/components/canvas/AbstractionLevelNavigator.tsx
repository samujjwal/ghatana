/**
 * @doc.type component
 * @doc.purpose Abstraction level navigator with breadcrumbs and controls
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useMemo } from 'react';
import { Box, Breadcrumb as Breadcrumbs, Button, ToggleButtonGroup as ButtonGroup, Chip, IconButton, Surface as Paper, Tooltip, Typography } from '@ghatana/ui';
import { ArrowUp as ArrowUpward, ArrowDown as ArrowDownward, ArrowLeft as ArrowBack, Home, ZoomIn, ZoomOut } from 'lucide-react';
import { AbstractionLevel, ABSTRACTION_LEVELS, AbstractionBreadcrumb } from '../../types/abstractionLevel';

interface AbstractionLevelNavigatorProps {
    currentLevel: AbstractionLevel;
    breadcrumbs: AbstractionBreadcrumb[];
    canDrillDown: boolean;
    canZoomOut: boolean;
    canGoBack: boolean;
    isTransitioning?: boolean;
    onLevelChange: (level: AbstractionLevel) => void;
    onDrillDown: () => void;
    onZoomOut: () => void;
    onGoBack: () => void;
    onBreadcrumbClick: (index: number) => void;
    onReset: () => void;
    variant?: 'full' | 'compact' | 'mini';
}

export function AbstractionLevelNavigator({
    currentLevel,
    breadcrumbs,
    canDrillDown,
    canZoomOut,
    canGoBack,
    isTransitioning = false,
    onLevelChange,
    onDrillDown,
    onZoomOut,
    onGoBack,
    onBreadcrumbClick,
    onReset,
    variant = 'full',
}: AbstractionLevelNavigatorProps) {
    const levelInfo = useMemo(
        () => ABSTRACTION_LEVELS.find(l => l.level === currentLevel),
        [currentLevel]
    );

    if (variant === 'mini') {
        return (
            <MiniNavigator
                currentLevel={currentLevel}
                canDrillDown={canDrillDown}
                canZoomOut={canZoomOut}
                onDrillDown={onDrillDown}
                onZoomOut={onZoomOut}
                isTransitioning={isTransitioning}
            />
        );
    }

    if (variant === 'compact') {
        return (
            <CompactNavigator
                currentLevel={currentLevel}
                breadcrumbs={breadcrumbs}
                canDrillDown={canDrillDown}
                canZoomOut={canZoomOut}
                canGoBack={canGoBack}
                onDrillDown={onDrillDown}
                onZoomOut={onZoomOut}
                onGoBack={onGoBack}
                onBreadcrumbClick={onBreadcrumbClick}
                isTransitioning={isTransitioning}
            />
        );
    }

    return (
        <Paper
            elevation={2}
            className="p-3 rounded-lg bg-white dark:bg-gray-900 transition-all duration-200" style={{ opacity: isTransitioning ? 0.7 : 1 }}
        >
            {/* Level Selector Tabs */}
            <Box className="flex items-center gap-2 mb-3">
                <ButtonGroup size="sm" variant="outlined">
                    {ABSTRACTION_LEVELS.map(level => (
                        <Tooltip
                            key={level.level}
                            title={`${level.description} (${level.shortcut})`}
                            arrow
                        >
                            <Button
                                onClick={() => onLevelChange(level.level)}
                                variant={currentLevel === level.level ? 'contained' : 'outlined'}
                                className="px-3 transition-all duration-200 min-w-0" style={{ transform: currentLevel === level.level ? 'scale(1.05)' : 'scale(1)' }} >
                                <span style={{ marginRight: 4 }}>{level.icon}</span>
                                {level.label}
                            </Button>
                        </Tooltip>
                    ))}
                </ButtonGroup>

                <Box className="grow" />

                {/* Navigation Controls */}
                <Tooltip title="Zoom Out (Alt+↑)">
                    <span>
                        <IconButton
                            size="sm"
                            onClick={onZoomOut}
                            disabled={!canZoomOut || isTransitioning}
                        >
                            <ZoomOut size={16} />
                        </IconButton>
                    </span>
                </Tooltip>

                <Tooltip title="Drill Down (Alt+↓)">
                    <span>
                        <IconButton
                            size="sm"
                            onClick={onDrillDown}
                            disabled={!canDrillDown || isTransitioning}
                        >
                            <ZoomIn size={16} />
                        </IconButton>
                    </span>
                </Tooltip>

                <Tooltip title="Go Back (Alt+←)">
                    <span>
                        <IconButton
                            size="sm"
                            onClick={onGoBack}
                            disabled={!canGoBack || isTransitioning}
                        >
                            <ArrowBack size={16} />
                        </IconButton>
                    </span>
                </Tooltip>

                <Tooltip title="Reset to Component Level">
                    <IconButton size="sm" onClick={onReset}>
                        <Home size={16} />
                    </IconButton>
                </Tooltip>
            </Box>

            {/* Breadcrumbs */}
            <Box className="flex items-center gap-2">
                <Breadcrumbs
                    separator="›"
                    className="[&_.MuiBreadcrumbs-separator]:mx-1 [&_.MuiBreadcrumbs-separator]:text-gray-500"
                >
                    {breadcrumbs.map((crumb, index) => {
                        const isActive = index === breadcrumbs.length - 1;
                        const crumbLevelInfo = ABSTRACTION_LEVELS.find(l => l.level === crumb.level);

                        return (
                            <Chip
                                key={`${crumb.level}-${index}`}
                                label={
                                    <Box className="flex items-center gap-1">
                                        <span>{crumbLevelInfo?.icon}</span>
                                        <span>{crumb.label}</span>
                                    </Box>
                                }
                                size="sm"
                                onClick={() => !isActive && onBreadcrumbClick(index)}
                                variant={isActive ? 'filled' : 'outlined'}
                                color={isActive ? 'primary' : 'default'}
                                className="transition-all duration-200" style={{ cursor: isActive ? 'default' : 'pointer', fontWeight: isActive ? 600 : 400, transform: 'translateY(-1px)' }} />
                        );
                    })}
                </Breadcrumbs>

                {/* Current Level Description */}
                <Typography
                    as="span"
                    color="text.secondary"
                    className="text-xs text-gray-500 ml-auto italic"
                >
                    {levelInfo?.description}
                </Typography>
            </Box>

            {/* Transition Indicator */}
            {isTransitioning && (
                <Box
                    className="absolute top-[0px] left-[0px] right-[0px] h-[2px]"
                    style={{ background: 'linear-gradient(90deg, transparent, #3b82f6, transparent)' }}
                />
            )}
        </Paper>
    );
}

// Compact variant
function CompactNavigator({
    currentLevel,
    breadcrumbs,
    canDrillDown,
    canZoomOut,
    canGoBack,
    onDrillDown,
    onZoomOut,
    onGoBack,
    onBreadcrumbClick,
    isTransitioning,
}: {
    currentLevel: AbstractionLevel;
    breadcrumbs: AbstractionBreadcrumb[];
    canDrillDown: boolean;
    canZoomOut: boolean;
    canGoBack: boolean;
    onDrillDown: () => void;
    onZoomOut: () => void;
    onGoBack: () => void;
    onBreadcrumbClick: (index: number) => void;
    isTransitioning: boolean;
}) {
    return (
        <Box
            className="flex items-center gap-2 p-1 rounded bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700" style={{ opacity: isTransitioning ? 0.7 : 1 }}
        >
            <Tooltip title="Go Back">
                <span>
                    <IconButton size="sm" onClick={onGoBack} disabled={!canGoBack}>
                        <ArrowBack className="text-base" />
                    </IconButton>
                </span>
            </Tooltip>

            <Box className="flex items-center gap-1">
                {breadcrumbs.slice(-3).map((crumb, i, arr) => (
                    <React.Fragment key={`${crumb.level}-${i}`}>
                        <Typography
                            as="span" className="text-xs text-gray-500"
                            onClick={() => i < arr.length - 1 && onBreadcrumbClick(breadcrumbs.length - arr.length + i)}
                            style={{ cursor: i < arr.length - 1 ? 'pointer' : 'default', fontWeight: i === arr.length - 1 ? 600 : 400, color: i === arr.length - 1 ? 'primary.main' : 'text.secondary' }}
                        >
                            {ABSTRACTION_LEVELS.find(l => l.level === crumb.level)?.icon} {crumb.label}
                        </Typography>
                        {i < arr.length - 1 && (
                            <Typography as="span" className="text-xs text-gray-500" color="text.disabled">›</Typography>
                        )}
                    </React.Fragment>
                ))}
            </Box>

            <Box className="flex ml-auto">
                <Tooltip title="Zoom Out (Alt+↑)">
                    <span>
                        <IconButton size="sm" onClick={onZoomOut} disabled={!canZoomOut}>
                            <ArrowUpward className="text-base" />
                        </IconButton>
                    </span>
                </Tooltip>
                <Tooltip title="Drill Down (Alt+↓)">
                    <span>
                        <IconButton size="sm" onClick={onDrillDown} disabled={!canDrillDown}>
                            <ArrowDownward className="text-base" />
                        </IconButton>
                    </span>
                </Tooltip>
            </Box>
        </Box>
    );
}

// Mini variant - just icons
function MiniNavigator({
    currentLevel,
    canDrillDown,
    canZoomOut,
    onDrillDown,
    onZoomOut,
    isTransitioning,
}: {
    currentLevel: AbstractionLevel;
    canDrillDown: boolean;
    canZoomOut: boolean;
    onDrillDown: () => void;
    onZoomOut: () => void;
    isTransitioning: boolean;
}) {
    const currentLevelInfo = ABSTRACTION_LEVELS.find(l => l.level === currentLevel);

    return (
        <Box
            className="flex flex-col items-center gap-1 p-1 rounded bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700" style={{ opacity: isTransitioning ? 0.7 : 1 }}
        >
            <Tooltip title="Zoom Out" placement="left">
                <span>
                    <IconButton size="sm" onClick={onZoomOut} disabled={!canZoomOut}>
                        <ArrowUpward className="text-sm" />
                    </IconButton>
                </span>
            </Tooltip>

            <Tooltip title={`${currentLevelInfo?.label}: ${currentLevelInfo?.description}`} placement="left">
                <Box
                    className="py-1 text-xl leading-none"
                >
                    {currentLevelInfo?.icon}
                </Box>
            </Tooltip>

            <Tooltip title="Drill Down" placement="left">
                <span>
                    <IconButton size="sm" onClick={onDrillDown} disabled={!canDrillDown}>
                        <ArrowDownward className="text-sm" />
                    </IconButton>
                </span>
            </Tooltip>
        </Box>
    );
}

export default AbstractionLevelNavigator;
