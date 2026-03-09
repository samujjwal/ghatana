/**
 * FullStackModeToggle Component
 * 
 * Toggle button and controls for full-stack split-screen development mode.
 * Provides layout switching, validation display, and canvas navigation.
 * 
 * @doc.type component
 * @doc.purpose Full-stack mode UI controls
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { Box, ToggleButtonGroup as ButtonGroup, Button, IconButton, Surface as Paper, Typography, Chip, Tooltip, Badge, Menu, MenuItem, Divider, Alert, Collapse } from '@ghatana/ui';
import { PanelsTopLeft as SplitVerticalIcon, ListTodo as SplitHorizontalIcon, LayoutDashboard as SingleIcon, ChevronLeft as ChevronLeftIcon, ChevronRight as ChevronRightIcon, AlertCircle as ErrorIcon, AlertTriangle as WarningIcon, CheckCircle as ValidIcon, ArrowLeftRight as DataFlowIcon, Code as FrontendIcon, HardDrive as BackendIcon } from 'lucide-react';
import {
    useFullStackMode,
    type LayoutMode,
    type CanvasSide,
} from '../hooks/useFullStackMode';

/**
 * FullStackModeToggle props
 */
export interface FullStackModeToggleProps {
    /** Position on screen */
    position?: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';
    /** Show validation panel */
    showValidation?: boolean;
    /** Compact mode */
    compact?: boolean;
}

/**
 * Position styles
 */
const POSITION_STYLES = {
    'top-left': { top: 16, left: 16 },
    'top-right': { top: 16, right: 16 },
    'bottom-left': { bottom: 16, left: 16 },
    'bottom-right': { bottom: 16, right: 16 },
};

/**
 * FullStackModeToggle component
 */
export function FullStackModeToggle({
    position = 'top-right',
    showValidation = true,
    compact = false,
}: FullStackModeToggleProps) {
    const {
        mode,
        setMode,
        isSplit,
        frontendPartition,
        backendPartition,
        dataFlowEdges,
        validation,
        activeSide,
        setActiveSide,
    } = useFullStackMode({ autoValidate: true });

    const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
    const [showErrors, setShowErrors] = React.useState(false);

    /**
     * Handle mode change
     */
    const handleModeChange = (newMode: LayoutMode) => {
        setMode(newMode);
        setAnchorEl(null);
    };

    /**
     * Get mode icon
     */
    const getModeIcon = (layoutMode: LayoutMode) => {
        switch (layoutMode) {
            case 'split-vertical':
                return <SplitVerticalIcon />;
            case 'split-horizontal':
                return <SplitHorizontalIcon />;
            case 'single':
            default:
                return <SingleIcon />;
        }
    };

    /**
     * Get mode label
     */
    const getModeLabel = (layoutMode: LayoutMode) => {
        switch (layoutMode) {
            case 'split-vertical':
                return 'Vertical Split';
            case 'split-horizontal':
                return 'Horizontal Split';
            case 'single':
            default:
                return 'Single Canvas';
        }
    };

    return (
        <Paper
            elevation={4}
            className="fixed"
        >
            {/* Header */}
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={compact ? 0 : 1}>
                {!compact && (
                    <Typography as="p" className="text-sm font-medium" fontWeight="bold">
                        Full-Stack Mode
                    </Typography>
                )}
                <Tooltip title="Layout Options">
                    <IconButton
                        size="sm"
                        onClick={(e) => setAnchorEl(e.currentTarget)}
                    >
                        {getModeIcon(mode)}
                    </IconButton>
                </Tooltip>
            </Box>

            {/* Layout Menu */}
            <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={() => setAnchorEl(null)}
            >
                <MenuItem onClick={() => handleModeChange('single')}>
                    <SingleIcon className="mr-2" />
                    Single Canvas
                </MenuItem>
                <MenuItem onClick={() => handleModeChange('split-vertical')}>
                    <SplitVerticalIcon className="mr-2" />
                    Vertical Split
                </MenuItem>
                <MenuItem onClick={() => handleModeChange('split-horizontal')}>
                    <SplitHorizontalIcon className="mr-2" />
                    Horizontal Split
                </MenuItem>
            </Menu>

            {!compact && (
                <>
                    {/* Current Mode */}
                    <Box display="flex" alignItems="center" gap={1} mb={2}>
                        <Chip
                            label={getModeLabel(mode)}
                            size="sm"
                            tone="primary"
                            variant="outlined"
                        />
                    </Box>

                    {/* Canvas Navigation */}
                    {isSplit && (
                        <Box mb={2}>
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block" mb={0.5}>
                                Active Canvas
                            </Typography>
                            <ButtonGroup size="sm" fullWidth>
                                <Button
                                    variant={activeSide === 'frontend' ? 'contained' : 'outlined'}
                                    onClick={() => setActiveSide('frontend')}
                                    startIcon={<FrontendIcon />}
                                >
                                    Frontend
                                </Button>
                                <Button
                                    variant={activeSide === 'backend' ? 'contained' : 'outlined'}
                                    onClick={() => setActiveSide('backend')}
                                    startIcon={<BackendIcon />}
                                >
                                    Backend
                                </Button>
                            </ButtonGroup>
                        </Box>
                    )}

                    {/* Statistics */}
                    {isSplit && (
                        <Box mb={2}>
                            <Box display="flex" justifyContent="space-between" mb={1}>
                                <Box>
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        Frontend
                                    </Typography>
                                    <Typography as="h6" tone="primary">
                                        {frontendPartition.nodes.length}
                                    </Typography>
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        nodes
                                    </Typography>
                                </Box>
                                <Box textAlign="center">
                                    <DataFlowIcon color="action" />
                                    <Typography as="h6" tone="secondary">
                                        {dataFlowEdges.length}
                                    </Typography>
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        flows
                                    </Typography>
                                </Box>
                                <Box textAlign="right">
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        Backend
                                    </Typography>
                                    <Typography as="h6" tone="primary">
                                        {backendPartition.nodes.length}
                                    </Typography>
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        nodes
                                    </Typography>
                                </Box>
                            </Box>
                        </Box>
                    )}

                    {/* Validation */}
                    {isSplit && showValidation && (
                        <Box>
                            <Divider className="my-2" />
                            <Box
                                display="flex"
                                alignItems="center"
                                justifyContent="space-between"
                                mb={1}
                            >
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    Validation
                                </Typography>
                                {validation.valid ? (
                                    <Chip
                                        icon={<ValidIcon />}
                                        label="Valid"
                                        size="sm"
                                        tone="success"
                                    />
                                ) : (
                                    <Badge badgeContent={validation.errors.length} tone="danger">
                                        <Chip
                                            icon={<ErrorIcon />}
                                            label="Errors"
                                            size="sm"
                                            tone="danger"
                                            onClick={() => setShowErrors(!showErrors)}
                                        />
                                    </Badge>
                                )}
                            </Box>

                            {/* Errors/Warnings */}
                            <Collapse in={showErrors && !validation.valid}>
                                <Box mt={1}>
                                    {validation.errors.map((error, index) => (
                                        <Alert
                                            key={index}
                                            severity="error"
                                            icon={<ErrorIcon size={16} />}
                                            className="mb-1 py-0"
                                        >
                                            <Typography as="span" className="text-xs text-gray-500">
                                                {error.message}
                                            </Typography>
                                        </Alert>
                                    ))}
                                    {validation.warnings.map((warning, index) => (
                                        <Alert
                                            key={index}
                                            severity="warning"
                                            icon={<WarningIcon size={16} />}
                                            className="mb-1 py-0"
                                        >
                                            <Typography as="span" className="text-xs text-gray-500">
                                                {warning.message}
                                            </Typography>
                                        </Alert>
                                    ))}
                                </Box>
                            </Collapse>

                            {validation.warnings.length > 0 && validation.errors.length === 0 && (
                                <Box mt={1}>
                                    <Alert
                                        severity="warning"
                                        icon={<WarningIcon size={16} />}
                                        className="py-0"
                                    >
                                        <Typography as="span" className="text-xs text-gray-500">
                                            {validation.warnings.length} warning(s)
                                        </Typography>
                                    </Alert>
                                </Box>
                            )}
                        </Box>
                    )}
                </>
            )}

            {/* Compact Mode */}
            {compact && isSplit && (
                <Box display="flex" gap={0.5} alignItems="center">
                    <IconButton
                        size="sm"
                        onClick={() => setActiveSide('frontend')}
                        color={activeSide === 'frontend' ? 'primary' : 'default'}
                    >
                        <FrontendIcon size={16} />
                    </IconButton>
                    <Typography as="span" className="text-xs text-gray-500">{dataFlowEdges.length}</Typography>
                    <IconButton
                        size="sm"
                        onClick={() => setActiveSide('backend')}
                        color={activeSide === 'backend' ? 'primary' : 'default'}
                    >
                        <BackendIcon size={16} />
                    </IconButton>
                    {!validation.valid && (
                        <Badge badgeContent={validation.errors.length} tone="danger">
                            <ErrorIcon size={16} tone="danger" />
                        </Badge>
                    )}
                </Box>
            )}
        </Paper>
    );
}
