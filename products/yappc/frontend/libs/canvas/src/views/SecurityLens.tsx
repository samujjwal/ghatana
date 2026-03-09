/**
 * @doc.type component
 * @doc.purpose Security Lens overlay for security analysis (Journey 11.2)
 * @doc.layer product
 * @doc.pattern Security View Component
 */

import React, { useState, useCallback } from 'react';
import { Box, Typography, Surface as Paper, Button, Switch, FormControlLabel, Chip, Alert, InteractiveList as List, ListItem, ListItemText, Divider } from '@ghatana/ui';
import { Shield as Security, Bug as BugReport, Gauge as Speed, Lock, Shield, AlertTriangle as Warning } from 'lucide-react';

/**
 * Security control types
 */
export type SecurityControlType = 'ratelimiter' | 'waf' | 'encryption' | 'authentication' | 'authorization';

/**
 * Threat types (STRIDE)
 */
export type ThreatType = 'spoofing' | 'tampering' | 'repudiation' | 'information_disclosure' | 'denial_of_service' | 'elevation_of_privilege';

/**
 * Security finding interface
 */
export interface SecurityFinding {
    id: string;
    type: 'vulnerability' | 'missing_control' | 'compliance';
    severity: 'critical' | 'high' | 'medium' | 'low';
    title: string;
    description: string;
    nodeId?: string;
    recommendation: string;
}

/**
 * Props for SecurityLens
 */
export interface SecurityLensProps {
    enabled?: boolean;
    findings?: SecurityFinding[];
    onToggle?: (enabled: boolean) => void;
    onRunAnalysis?: () => void;
    onAddControl?: (type: SecurityControlType) => void;
    pciDssCompliant?: boolean;
}

/**
 * SecurityLens Component
 * 
 * Security overlay for security analysis with:
 * - Toggle overlay showing security metadata
 * - Run Threat Analysis action (STRIDE)
 * - Security control nodes (Rate Limiter, WAF, Encryption)
 * - SQL injection detection
 * - Missing rate limiting checks
 * - PII data logging detection
 * - PCI-DSS validation
 */
export const SecurityLens: React.FC<SecurityLensProps> = ({
    enabled = false,
    findings = [],
    onToggle,
    onRunAnalysis,
    onAddControl,
    pciDssCompliant = false,
}) => {
    const [analyzing, setAnalyzing] = useState(false);

    const handleRunAnalysis = useCallback(() => {
        setAnalyzing(true);
        onRunAnalysis?.();
        setTimeout(() => setAnalyzing(false), 2000);
    }, [onRunAnalysis]);

    const getSeverityColor = (severity: string) => {
        switch (severity) {
            case 'critical':
                return 'error';
            case 'high':
                return 'error';
            case 'medium':
                return 'warning';
            case 'low':
                return 'info';
            default:
                return 'default';
        }
    };

    const criticalCount = findings.filter((f) => f.severity === 'critical').length;
    const highCount = findings.filter((f) => f.severity === 'high').length;

    return (
        <Box className="h-full flex flex-col">
            <Paper className="p-4 mb-4">
                <Box className="flex items-center gap-4 flex-wrap">
                    <Security className="text-[32px]" style={{ color: enabled ? 'primary.main' : 'text.secondary' }} />
                    <Typography as="h6">Security Lens</Typography>

                    <FormControlLabel
                        control={<Switch checked={enabled} onChange={(e) => onToggle?.(e.target.checked)} />}
                        label="Enable Overlay"
                    />

                    <Button
                        variant="solid"
                        startIcon={<BugReport />}
                        onClick={handleRunAnalysis}
                        disabled={analyzing || !enabled}
                    >
                        {analyzing ? 'Analyzing...' : 'Run STRIDE Analysis'}
                    </Button>

                    <Box className="flex-1" />

                    {pciDssCompliant ? (
                        <Chip icon={<Shield />} label="PCI-DSS Compliant" tone="success" />
                    ) : (
                        <Chip icon={<Warning />} label="PCI-DSS Non-Compliant" tone="danger" />
                    )}
                </Box>

                <Box className="flex gap-2 mt-4">
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">Security Controls:</Typography>
                    <Button size="sm" startIcon={<Speed />} onClick={() => onAddControl?.('ratelimiter')}>
                        Rate Limiter
                    </Button>
                    <Button size="sm" startIcon={<Shield />} onClick={() => onAddControl?.('waf')}>
                        WAF
                    </Button>
                    <Button size="sm" startIcon={<Lock />} onClick={() => onAddControl?.('encryption')}>
                        Encryption
                    </Button>
                </Box>
            </Paper>

            {enabled && (
                <Box className="flex-1 overflow-y-auto p-4">
                    {findings.length === 0 ? (
                        <Alert severity="success">
                            <Typography as="p" className="text-sm font-medium">No security issues found</Typography>
                            <Typography as="p" className="text-sm">Your architecture appears secure</Typography>
                        </Alert>
                    ) : (
                        <>
                            <Box className="mb-4 flex gap-2">
                                <Chip label={`${criticalCount} Critical`} tone="danger" />
                                <Chip label={`${highCount} High`} tone="danger" variant="outlined" />
                                <Chip label={`${findings.length - criticalCount - highCount} Other`} />
                            </Box>

                            <List>
                                {findings.map((finding) => (
                                    <React.Fragment key={finding.id}>
                                        <ListItem alignItems="flex-start" className="flex-col items-stretch">
                                            <Box className="flex items-center gap-2 mb-2">
                                                <Chip label={finding.severity} color={getSeverityColor(finding.severity)} size="sm" />
                                                <Chip label={finding.type} size="sm" variant="outlined" />
                                            </Box>
                                            <ListItemText
                                                primary={finding.title}
                                                secondary={
                                                    <>
                                                        <Typography as="p" className="text-sm" color="text.secondary" className="mb-2">
                                                            {finding.description}
                                                        </Typography>
                                                        <Typography as="span" className="text-xs text-gray-500" tone="primary">
                                                            💡 {finding.recommendation}
                                                        </Typography>
                                                    </>
                                                }
                                            />
                                        </ListItem>
                                        <Divider />
                                    </React.Fragment>
                                ))}
                            </List>
                        </>
                    )}
                </Box>
            )}
        </Box>
    );
};
