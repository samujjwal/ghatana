/**
 * YAPPC Compliance Report Component
 *
 * Displays compliance check results with status, findings, and recommendations.
 *
 * @doc.type component
 * @doc.purpose Compliance report visualization
 * @doc.layer product
 * @doc.pattern Report Component
 */

import React from 'react';
import { Box, Typography, Card, CardContent, Chip, Button, Accordion, AccordionSummary, AccordionDetails, InteractiveList as List, ListItem, ListItemIcon, ListItemText, Alert, AlertTitle, Avatar, Spinner as CircularProgress, Stack } from '@ghatana/ui';
import { ChevronDown as ExpandIcon, CheckCircle as PassedIcon, XCircle as FailedIcon, AlertTriangle as WarningIcon, MinusCircle as NAIcon, HelpCircle as PendingIcon, Shield as SecurityIcon, FileText as DocumentIcon, Code as CodeIcon, Bug as TestIcon, Settings as ConfigIcon, BarChart3 as ReportIcon, Lightbulb as RecommendationIcon, Download as DownloadIcon, RefreshCw as RefreshIcon } from 'lucide-react';

// ============================================================================
// Types (Inline for module independence)
// ============================================================================

type ComplianceCheckStatus = 'passed' | 'failed' | 'warning' | 'not-applicable' | 'pending';

type ComplianceFramework =
    | 'soc2'
    | 'iso27001'
    | 'gdpr'
    | 'hipaa'
    | 'pci-dss'
    | 'nist'
    | 'fedramp'
    | 'custom';

interface ComplianceCheck {
    id: string;
    controlId: string;
    name: string;
    description: string;
    status: ComplianceCheckStatus;
    requiredArtifacts: string[];
    foundArtifacts: string[];
    missingArtifacts: string[];
    recommendations?: string[];
}

interface ComplianceSummary {
    total: number;
    passed: number;
    failed: number;
    warnings: number;
    notApplicable: number;
}

interface ComplianceReportData {
    id: string;
    framework: ComplianceFramework;
    workflowId: string;
    status: 'compliant' | 'non-compliant' | 'partial';
    score: number;
    checks: ComplianceCheck[];
    summary: ComplianceSummary;
    generatedAt: string;
}

// ============================================================================
// Props
// ============================================================================

interface ComplianceReportProps {
    report: ComplianceReportData | null;
    isLoading?: boolean;
    error?: string;
    onRunCheck?: () => void;
    onExport?: () => void;
}

interface CheckCardProps {
    check: ComplianceCheck;
    defaultExpanded?: boolean;
}

// ============================================================================
// Constants
// ============================================================================

const STATUS_CONFIG: Record<
    ComplianceCheckStatus,
    {
        color: 'success' | 'error' | 'warning' | 'default' | 'info';
        icon: React.ReactNode;
        label: string;
    }
> = {
    passed: { color: 'success', icon: <PassedIcon />, label: 'Passed' },
    failed: { color: 'error', icon: <FailedIcon />, label: 'Failed' },
    warning: { color: 'warning', icon: <WarningIcon />, label: 'Warning' },
    'not-applicable': { color: 'default', icon: <NAIcon />, label: 'N/A' },
    pending: { color: 'info', icon: <PendingIcon />, label: 'Pending' },
};

const FRAMEWORK_INFO: Record<ComplianceFramework, { name: string; color: string }> = {
    soc2: { name: 'SOC 2', color: '#2196f3' },
    iso27001: { name: 'ISO 27001', color: '#4caf50' },
    gdpr: { name: 'GDPR', color: '#ff9800' },
    hipaa: { name: 'HIPAA', color: '#f44336' },
    'pci-dss': { name: 'PCI DSS', color: '#9c27b0' },
    nist: { name: 'NIST CSF', color: '#00bcd4' },
    fedramp: { name: 'FedRAMP', color: '#607d8b' },
    custom: { name: 'Custom', color: '#795548' },
};

const ARTIFACT_ICONS: Record<string, React.ReactNode> = {
    code: <CodeIcon size={16} />,
    document: <DocumentIcon size={16} />,
    test: <TestIcon size={16} />,
    config: <ConfigIcon size={16} />,
    report: <ReportIcon size={16} />,
};

// ============================================================================
// Component
// ============================================================================

export function ComplianceReport({
    report,
    isLoading = false,
    error,
    onRunCheck,
    onExport,
}: ComplianceReportProps) {
    if (error) {
        return (
            <Alert severity="error" className="m-4">
                <AlertTitle>Error</AlertTitle>
                {error}
            </Alert>
        );
    }

    if (isLoading && !report) {
        return (
            <Box className="flex justify-center items-center h-[300px]">
                <CircularProgress />
            </Box>
        );
    }

    if (!report) {
        return (
            <Box className="text-center py-16">
                <SecurityIcon className="mb-4 text-[64px] text-gray-500 dark:text-gray-400" />
                <Typography as="h6" gutterBottom>
                    No Compliance Report
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-6">
                    Run a compliance check to generate a report
                </Typography>
                <Button
                    variant="solid"
                    startIcon={<RefreshIcon />}
                    onClick={onRunCheck}
                    disabled={isLoading}
                >
                    Run Compliance Check
                </Button>
            </Box>
        );
    }

    const frameworkInfo = FRAMEWORK_INFO[report.framework];

    return (
        <Box className="p-4">
            {/* Header Card */}
            <Card className="mb-6">
                <CardContent>
                    <Box className="flex items-center gap-4 mb-4">
                        <Avatar
                            className="w-[56px] h-[56px]" style={{ backgroundColor: 'frameworkInfo.color' }} >
                            <SecurityIcon />
                        </Avatar>
                        <Box className="flex-1">
                            <Typography as="h5">{frameworkInfo.name} Compliance Report</Typography>
                            <Typography as="p" className="text-sm" color="text.secondary">
                                Generated {new Date(report.generatedAt).toLocaleString()}
                            </Typography>
                        </Box>
                        <Box className="flex gap-2">
                            <Button
                                variant="outlined"
                                startIcon={<RefreshIcon />}
                                onClick={onRunCheck}
                                disabled={isLoading}
                                size="sm"
                            >
                                Re-run
                            </Button>
                            <Button
                                variant="outlined"
                                startIcon={<DownloadIcon />}
                                onClick={onExport}
                                size="sm"
                            >
                                Export
                            </Button>
                        </Box>
                    </Box>

                    {/* Score and Status */}
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={3} alignItems="center">
                        <Box className="text-center min-w-[150px]">
                            <Box className="relative inline-flex">
                                <CircularProgress
                                    variant="determinate"
                                    value={report.score}
                                    size={120}
                                    thickness={4}
                                    style={{ color: report.score >= 80
                                                ? 'success.main'
                                                : report.score >= 50
                                                    ? 'warning.main'
                                                    : 'error.main' }}
                                />
                                <Box
                                    className="absolute flex items-center justify-center flex-col top-[0px] left-[0px] right-[0px] bottom-[0px]"
                                >
                                    <Typography as="h4" fontWeight="bold">
                                        {report.score}%
                                    </Typography>
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        Score
                                    </Typography>
                                </Box>
                            </Box>
                        </Box>
                        <Box className="text-center min-w-[150px]">
                            <Chip
                                label={report.status.toUpperCase()}
                                color={
                                    report.status === 'compliant'
                                        ? 'success'
                                        : report.status === 'non-compliant'
                                            ? 'error'
                                            : 'warning'
                                }
                                className="text-base py-5 px-4"
                            />
                            <Typography as="p" className="text-sm" color="text.secondary" className="mt-2">
                                Overall Status
                            </Typography>
                        </Box>
                        <Box className="flex justify-center gap-6">
                            <StatusCounter count={report.summary.passed} label="Passed" tone="success" />
                            <StatusCounter count={report.summary.failed} label="Failed" tone="danger" />
                            <StatusCounter count={report.summary.warnings} label="Warnings" tone="warning" />
                        </Box>
                    </Stack>
                </CardContent>
            </Card>

            {/* Alerts */}
            {report.summary.failed > 0 && (
                <Alert severity="error" className="mb-4">
                    <AlertTitle>Compliance Issues Found</AlertTitle>
                    {report.summary.failed} control(s) failed compliance checks. Review the findings below and take
                    corrective action.
                </Alert>
            )}

            {report.summary.warnings > 0 && report.summary.failed === 0 && (
                <Alert severity="warning" className="mb-4">
                    <AlertTitle>Partial Compliance</AlertTitle>
                    {report.summary.warnings} control(s) have warnings. Consider addressing these to improve compliance
                    posture.
                </Alert>
            )}

            {/* Checks */}
            <Typography as="h6" gutterBottom className="mt-6">
                Control Checks ({report.checks.length})
            </Typography>

            {/* Failed checks first */}
            {report.checks
                .filter((c: ComplianceCheck) => c.status === 'failed')
                .map((check: ComplianceCheck) => (
                    <CheckCard key={check.id} check={check} defaultExpanded />
                ))}

            {/* Warning checks */}
            {report.checks
                .filter((c: ComplianceCheck) => c.status === 'warning')
                .map((check: ComplianceCheck) => (
                    <CheckCard key={check.id} check={check} defaultExpanded />
                ))}

            {/* Passed checks */}
            {report.checks
                .filter((c: ComplianceCheck) => c.status === 'passed')
                .map((check: ComplianceCheck) => (
                    <CheckCard key={check.id} check={check} />
                ))}

            {/* N/A checks */}
            {report.checks
                .filter((c: ComplianceCheck) => c.status === 'not-applicable')
                .map((check: ComplianceCheck) => (
                    <CheckCard key={check.id} check={check} />
                ))}
        </Box>
    );
}

// ============================================================================
// Sub-components
// ============================================================================

function StatusCounter({
    count,
    label,
    color,
}: {
    count: number;
    label: string;
    color: 'success' | 'error' | 'warning';
}) {
    return (
        <Box className="text-center">
            <Typography as="h4" fontWeight="bold" color={`${color}.main`}>
                {count}
            </Typography>
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                {label}
            </Typography>
        </Box>
    );
}

function CheckCard({ check, defaultExpanded = false }: CheckCardProps) {
    const statusConfig = STATUS_CONFIG[check.status];

    return (
        <Accordion defaultExpanded={defaultExpanded} className="mb-2">
            <AccordionSummary expandIcon={<ExpandIcon />}>
                <Box className="flex items-center gap-4 w-full pr-4">
                    <Chip
                        size="sm"
                        icon={statusConfig.icon as React.ReactElement}
                        label={statusConfig.label}
                        color={statusConfig.color}
                    />
                    <Typography as="p" className="text-sm font-medium font-mono">
                        {check.controlId}
                    </Typography>
                    <Typography as="p" className="text-sm" className="flex-1">
                        {check.name}
                    </Typography>
                </Box>
            </AccordionSummary>
            <AccordionDetails>
                <Typography as="p" className="text-sm" color="text.secondary" paragraph>
                    {check.description}
                </Typography>

                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                    {/* Required Artifacts */}
                    <Box className="flex-1">
                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            Required Artifacts
                        </Typography>
                        <List dense disablePadding>
                            {check.requiredArtifacts.map((artifact: string, i: number) => {
                                const [type] = artifact.split(':');
                                const isFound = check.foundArtifacts.some((f: string) => f.startsWith(type));
                                return (
                                    <ListItem key={i} disablePadding className="py-0.5">
                                        <ListItemIcon className="min-w-[32px]">
                                            {isFound ? (
                                                <PassedIcon tone="success" size={16} />
                                            ) : (
                                                <FailedIcon tone="danger" size={16} />
                                            )}
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={artifact}
                                            primaryTypographyProps={{
                                                variant: 'body2',
                                                color: isFound ? 'text.primary' : 'error',
                                            }}
                                        />
                                    </ListItem>
                                );
                            })}
                        </List>
                    </Box>

                    {/* Found Artifacts */}
                    <Box className="flex-1">
                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            Found Artifacts ({check.foundArtifacts.length})
                        </Typography>
                        {check.foundArtifacts.length > 0 ? (
                            <List dense disablePadding>
                                {check.foundArtifacts.map((artifact: string, i: number) => {
                                    const [type] = artifact.split(':');
                                    return (
                                        <ListItem key={i} disablePadding className="py-0.5">
                                            <ListItemIcon className="min-w-[32px]">
                                                {ARTIFACT_ICONS[type] || <DocumentIcon size={16} />}
                                            </ListItemIcon>
                                            <ListItemText
                                                primary={artifact}
                                                primaryTypographyProps={{ variant: 'body2' }}
                                            />
                                        </ListItem>
                                    );
                                })}
                            </List>
                        ) : (
                            <Typography as="p" className="text-sm" color="text.secondary">
                                No artifacts found
                            </Typography>
                        )}
                    </Box>

                    {/* Recommendations */}
                    <Box className="flex-1">
                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            Recommendations
                        </Typography>
                        {check.recommendations && check.recommendations.length > 0 ? (
                            <List dense disablePadding>
                                {check.recommendations.map((rec: string, i: number) => (
                                    <ListItem key={i} disablePadding className="py-0.5">
                                        <ListItemIcon className="min-w-[32px]">
                                            <RecommendationIcon tone="info" size={16} />
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={rec}
                                            primaryTypographyProps={{ variant: 'body2' }}
                                        />
                                    </ListItem>
                                ))}
                            </List>
                        ) : (
                            <Typography as="p" className="text-sm" color="text.secondary">
                                No recommendations
                            </Typography>
                        )}
                    </Box>
                </Stack>

                {/* Missing Artifacts Alert */}
                {check.missingArtifacts.length > 0 && (
                    <Alert severity="error" className="mt-4">
                        <AlertTitle>Missing Artifacts</AlertTitle>
                        <List dense disablePadding>
                            {check.missingArtifacts.map((artifact: string, i: number) => (
                                <ListItem key={i} disablePadding>
                                    <ListItemText
                                        primary={artifact}
                                        primaryTypographyProps={{ variant: 'body2' }}
                                    />
                                </ListItem>
                            ))}
                        </List>
                    </Alert>
                )}
            </AccordionDetails>
        </Accordion>
    );
}

export default ComplianceReport;
