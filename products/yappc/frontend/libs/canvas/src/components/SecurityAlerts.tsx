/**
 * SecurityAlerts Component
 * 
 * Visual overlay for displaying security vulnerabilities on canvas nodes.
 * Shows pulsing red indicators, shield icons, and AI-powered fix options.
 * 
 * Features:
 * - Pulsing red nodes for CVEs
 * - Shield icons with severity badges
 * - Fix with AI button
 * - PR generation workflow
 * 
 * @doc.type component
 * @doc.purpose Security vulnerability alerts
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback } from 'react';
import { Box, Surface as Paper, Typography, IconButton, Chip, Badge, Tooltip, Button, Dialog, DialogTitle, DialogContent, DialogActions, InteractiveList as List, ListItem, ListItemIcon, ListItemText, Divider, Spinner as CircularProgress, Alert, Link, Collapse } from '@ghatana/ui';
import { Shield as ShieldIcon, AlertTriangle as WarningIcon, AlertCircle as ErrorIcon, Info as InfoIcon, Bug as BugIcon, X as CloseIcon, Wand2 as FixIcon, Github as GitHubIcon, ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon, RefreshCw as RefreshIcon } from 'lucide-react';
import type { Node } from '@xyflow/react';
import {
    useSecurityMonitoring,
    type Vulnerability,
    type NodeVulnerabilityStatus,
    type VulnerabilitySeverity,
} from '../hooks/useSecurityMonitoring';

/**
 * SecurityAlerts props
 */
export interface SecurityAlertsProps {
    /** Selected nodes to scan */
    selectedNodes?: Node[];
    /** Show alerts panel */
    showPanel?: boolean;
    /** On close callback */
    onClose?: () => void;
}

/**
 * Severity colors
 */
const SEVERITY_COLORS: Record<VulnerabilitySeverity, string> = {
    critical: '#d32f2f',
    high: '#f57c00',
    medium: '#fbc02d',
    low: '#388e3c',
    info: '#1976d2',
};

/**
 * Severity icons
 */
const SEVERITY_ICONS: Record<VulnerabilitySeverity, React.ReactElement> = {
    critical: <ErrorIcon />,
    high: <WarningIcon />,
    medium: <InfoIcon />,
    low: <InfoIcon />,
    info: <InfoIcon />,
};

/**
 * SecurityAlerts component
 */
export function SecurityAlerts({ selectedNodes, showPanel = true, onClose }: SecurityAlertsProps) {
    const [selectedVulnerability, setSelectedVulnerability] = useState<{
        vuln: Vulnerability;
        nodeId: string;
    } | null>(null);
    const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set());
    const [showFixDialog, setShowFixDialog] = useState(false);

    const {
        nodeStatuses,
        totalVulnerabilities,
        scanning,
        lastScan,
        currentFix,
        generatingFix,
        creatingPR,
        scanVulnerabilities,
        generateFix,
        createPR,
        dismissVulnerability,
    } = useSecurityMonitoring({
        autoScan: true,
        minSeverity: 'low',
    });

    /**
     * Toggle node expansion
     */
    const toggleNodeExpansion = useCallback((nodeId: string) => {
        setExpandedNodes((prev) => {
            const next = new Set(prev);
            if (next.has(nodeId)) {
                next.delete(nodeId);
            } else {
                next.add(nodeId);
            }
            return next;
        });
    }, []);

    /**
     * Handle fix with AI
     */
    const handleFixWithAI = useCallback(
        async (vulnerability: Vulnerability, nodeId: string) => {
            setSelectedVulnerability({ vuln: vulnerability, nodeId });
            setShowFixDialog(true);
            await generateFix(vulnerability.id, nodeId);
        },
        [generateFix]
    );

    /**
     * Handle create PR
     */
    const handleCreatePR = useCallback(async () => {
        if (!currentFix) return;

        const result = await createPR(currentFix);

        if (result.success && result.prUrl) {
            window.open(result.prUrl, '_blank');
        }
    }, [currentFix, createPR]);

    /**
     * Render vulnerability item
     */
    const renderVulnerability = (vuln: Vulnerability, nodeId: string) => (
        <ListItem
            key={vuln.id}
            className="bg-white dark:bg-gray-900"
            style={{
                border: `1px solid ${SEVERITY_COLORS[vuln.severity]}`,
                backgroundColor: SEVERITY_COLORS[vuln.severity],
            }}
        >
            <ListItemIcon>
                <Badge
                    badgeContent={vuln.cvssScore?.toFixed(1)}
                    color={vuln.severity === 'critical' || vuln.severity === 'high' ? 'error' : 'warning'}
                >
                    {SEVERITY_ICONS[vuln.severity]}
                </Badge>
            </ListItemIcon>
            <ListItemText
                primary={
                    <Box display="flex" alignItems="center" gap={1}>
                        <Typography as="p" className="text-sm font-medium" fontWeight="bold">
                            {vuln.title}
                        </Typography>
                        <Chip
                            label={vuln.severity}
                            size="sm"
                            className="font-bold text-[#fff]" />
                    </Box>
                }
                secondary={
                    <Box>
                        <Typography as="p" className="text-sm mt-1" color="text.secondary">
                            {vuln.description}
                        </Typography>
                        <Box display="flex" gap={1} mt={1} flexWrap="wrap">
                            <Chip label={vuln.id} size="sm" variant="outlined" />
                            <Chip
                                label={`${vuln.package} ${vuln.currentVersion}`}
                                size="sm"
                                variant="outlined"
                            />
                            {vuln.fixedVersion && (
                                <Chip
                                    label={`Fix: ${vuln.fixedVersion}`}
                                    size="sm"
                                    tone="success"
                                    variant="outlined"
                                />
                            )}
                        </Box>
                    </Box>
                }
            />
            <Box display="flex" flexDirection="column" gap={0.5}>
                <Button
                    size="sm"
                    variant="solid"
                    tone="primary"
                    startIcon={<FixIcon />}
                    onClick={() => handleFixWithAI(vuln, nodeId)}
                >
                    Fix with AI
                </Button>
                <Button
                    size="sm"
                    variant="outlined"
                    onClick={() => dismissVulnerability(nodeId, vuln.id)}
                >
                    Dismiss
                </Button>
            </Box>
        </ListItem>
    );

    /**
     * Render node status
     */
    const renderNodeStatus = (status: NodeVulnerabilityStatus) => {
        const isExpanded = expandedNodes.has(status.nodeId);

        return (
            <Box key={status.nodeId} className="mb-4">
                <Paper
                    elevation={2}
                    className="p-4 cursor-pointer"
                    style={{
                        border:
                            status.criticalCount > 0
                                ? `2px solid ${SEVERITY_COLORS.critical}`
                                : status.highCount > 0
                                    ? `2px solid ${SEVERITY_COLORS.high}`
                                    : `1px solid ${SEVERITY_COLORS.medium}`,
                    }}
                    onClick={() => toggleNodeExpansion(status.nodeId)}
                >
                    <Box display="flex" alignItems="center" justifyContent="space-between">
                        <Box display="flex" alignItems="center" gap={2}>
                            <Badge
                                badgeContent={status.totalCount}
                                tone="danger"
                                max={99}
                            >
                                <ShieldIcon
                                    className="text-[32px]"
                                    style={{
                                        color:
                                            status.criticalCount > 0
                                                ? SEVERITY_COLORS.critical
                                                : status.highCount > 0
                                                    ? SEVERITY_COLORS.high
                                                    : SEVERITY_COLORS.medium,
                                    }}
                                />
                            </Badge>
                            <Box>
                                <Typography as="h6">{status.nodeLabel}</Typography>
                                <Box display="flex" gap={1} mt={0.5}>
                                    {status.criticalCount > 0 && (
                                        <Chip
                                            label={`${status.criticalCount} Critical`}
                                            size="sm"
                                            tone="danger"
                                        />
                                    )}
                                    {status.highCount > 0 && (
                                        <Chip
                                            label={`${status.highCount} High`}
                                            size="sm"
                                            tone="warning"
                                        />
                                    )}
                                    <Chip
                                        label={`${status.totalCount} Total`}
                                        size="sm"
                                        variant="outlined"
                                    />
                                </Box>
                            </Box>
                        </Box>
                        <IconButton size="sm">
                            {isExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                        </IconButton>
                    </Box>
                </Paper>

                <Collapse in={isExpanded}>
                    <Box className="mt-2 pl-4">
                        <List>
                            {status.vulnerabilities.map((vuln) =>
                                renderVulnerability(vuln, status.nodeId)
                            )}
                        </List>
                    </Box>
                </Collapse>
            </Box>
        );
    };

    if (!showPanel) return null;

    return (
        <>
            {/* Security Alerts Panel */}
            <Paper
                elevation={4}
                className="fixed overflow-auto top-[80px] right-[16px] w-[400px] max-h-[calc(100vh - 100px)] z-[1000]"
            >
                {/* Header */}
                <Box
                    className="p-4 flex items-center justify-between bg-red-600 text-white"
                >
                    <Box display="flex" alignItems="center" gap={1}>
                        <ShieldIcon />
                        <Typography as="h6">Security Alerts</Typography>
                        <Badge badgeContent={totalVulnerabilities} tone="warning" max={99} />
                    </Box>
                    <Box display="flex" gap={0.5}>
                        <Tooltip title="Refresh scan">
                            <IconButton
                                size="sm"
                                onClick={scanVulnerabilities}
                                disabled={scanning}
                                className="text-inherit"
                            >
                                {scanning ? <CircularProgress size={20} tone="neutral" /> : <RefreshIcon />}
                            </IconButton>
                        </Tooltip>
                        {onClose && (
                            <IconButton size="sm" onClick={onClose} className="text-inherit">
                                <CloseIcon />
                            </IconButton>
                        )}
                    </Box>
                </Box>

                {/* Content */}
                <Box className="p-4">
                    {scanning && (
                        <Box display="flex" alignItems="center" gap={1} mb={2}>
                            <CircularProgress size={16} />
                            <Typography as="p" className="text-sm">Scanning for vulnerabilities...</Typography>
                        </Box>
                    )}

                    {lastScan && !scanning && (
                        <Typography as="span" className="text-xs text-gray-500 mb-4 block" color="text.secondary">
                            Last scanned: {lastScan.toLocaleTimeString()}
                        </Typography>
                    )}

                    {totalVulnerabilities === 0 && !scanning && (
                        <Alert severity="success">
                            No vulnerabilities detected. Your canvas is secure! 🎉
                        </Alert>
                    )}

                    {nodeStatuses.map(renderNodeStatus)}
                </Box>
            </Paper>

            {/* Fix Dialog */}
            <Dialog
                open={showFixDialog}
                onClose={() => setShowFixDialog(false)}
                size="md"
                fullWidth
            >
                <DialogTitle>
                    <Box display="flex" alignItems="center" gap={1}>
                        <FixIcon />
                        <Typography as="h6">AI-Powered Security Fix</Typography>
                    </Box>
                </DialogTitle>

                <DialogContent dividers>
                    {selectedVulnerability && (
                        <Box>
                            <Alert severity="warning" className="mb-4">
                                <Typography as="p" className="text-sm font-medium" gutterBottom>
                                    {selectedVulnerability.vuln.title}
                                </Typography>
                                <Typography as="p" className="text-sm">
                                    {selectedVulnerability.vuln.description}
                                </Typography>
                            </Alert>

                            {generatingFix && (
                                <Box display="flex" alignItems="center" gap={1} my={2}>
                                    <CircularProgress size={20} />
                                    <Typography>Generating AI-powered fix...</Typography>
                                </Box>
                            )}

                            {currentFix && !generatingFix && (
                                <Box>
                                    <Typography as="p" className="text-sm font-medium" gutterBottom>
                                        Suggested Fix:
                                    </Typography>
                                    <Paper variant="outlined" className="p-4 mb-4 bg-gray-50 dark:bg-gray-800">
                                        <Typography as="p" className="text-sm whitespace-pre-wrap">
                                            {currentFix.description}
                                        </Typography>
                                    </Paper>

                                    <Typography as="p" className="text-sm font-medium" gutterBottom>
                                        Changes ({currentFix.changes.length} file(s)):
                                    </Typography>
                                    <List>
                                        {currentFix.changes.map((change, index) => (
                                            <ListItem key={index}>
                                                <ListItemIcon>
                                                    <BugIcon tone="primary" />
                                                </ListItemIcon>
                                                <ListItemText
                                                    primary={change.file}
                                                    secondary={
                                                        <Box>
                                                            <Typography
                                                                as="span" className="text-xs text-gray-500"
                                                                component="div"
                                                                tone="danger"
                                                            >
                                                                - {change.oldContent}
                                                            </Typography>
                                                            <Typography
                                                                as="span" className="text-xs text-gray-500"
                                                                component="div"
                                                                color="success.main"
                                                            >
                                                                + {change.newContent}
                                                            </Typography>
                                                        </Box>
                                                    }
                                                />
                                            </ListItem>
                                        ))}
                                    </List>

                                    <Box display="flex" gap={1} mt={2}>
                                        <Chip
                                            label={`Confidence: ${(currentFix.confidence * 100).toFixed(0)}%`}
                                            size="sm"
                                            tone="info"
                                        />
                                        {currentFix.estimatedTime && (
                                            <Chip
                                                label={`Est. time: ${currentFix.estimatedTime}`}
                                                size="sm"
                                                variant="outlined"
                                            />
                                        )}
                                    </Box>
                                </Box>
                            )}
                        </Box>
                    )}
                </DialogContent>

                <DialogActions>
                    <Button onClick={() => setShowFixDialog(false)}>Cancel</Button>
                    {currentFix && !generatingFix && (
                        <Button
                            variant="solid"
                            startIcon={creatingPR ? <CircularProgress size={16} /> : <GitHubIcon />}
                            onClick={handleCreatePR}
                            disabled={creatingPR}
                        >
                            {creatingPR ? 'Creating PR...' : 'Create PR'}
                        </Button>
                    )}
                </DialogActions>
            </Dialog>
        </>
    );
}
