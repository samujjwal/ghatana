/**
 * Validation Panel Component
 * 
 * Displays validation results, issues, and recommendations
 * 
 * @doc.type component
 * @doc.purpose UI for validation results
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useMemo } from 'react';
import {
  Alert,
  AlertTitle,
  Box,
  Chip,
  Divider,
  IconButton,
  LinearProgress,
  ListItem,
  ListItemIcon,
  ListItemText,
  Stack,
  Tab,
  Tabs,
  Tooltip,
  Typography,
  InteractiveList as List,
  Surface as Paper,
} from '@ghatana/ui';
import { Collapse } from '@ghatana/ui';
import { AlertCircle as ErrorIcon, AlertTriangle as WarningIcon, Info as InfoIcon, CheckCircle as CheckCircleIcon, ChevronDown as ExpandMoreIcon, Bug as BugIcon, Shield as SecurityIcon, Gauge as SpeedIcon, Hammer as BuildIcon, TrendingUp as TrendingUpIcon } from 'lucide-react';
import type { ValidationReport, ValidationIssue, RiskAssessment } from '../services/canvas/agents/ValidationAgent';
import { LifecyclePhase } from '../types/lifecycle';

// ============================================================================
// Props
// ============================================================================

export interface ValidationPanelProps {
    validationReport: ValidationReport | null;
    isValidating: boolean;
    onFixIssue?: (issueId: string) => void;
}

// ============================================================================
// Component
// ============================================================================

/**
 * Validation Panel Component
 */
export const ValidationPanel: React.FC<ValidationPanelProps> = ({
    validationReport,
    isValidating,
    onFixIssue,
}) => {
    const [selectedTab, setSelectedTab] = React.useState(0);
    const [expandedIssues, setExpandedIssues] = React.useState<Set<string>>(new Set());

    /**
     * Toggle issue expansion
     */
    const toggleIssue = (issueId: string) => {
        setExpandedIssues((prev) => {
            const next = new Set(prev);
            if (next.has(issueId)) {
                next.delete(issueId);
            } else {
                next.add(issueId);
            }
            return next;
        });
    };

    /**
     * Group issues by severity
     */
    const issuesBySeverity = useMemo(() => {
        if (!validationReport) return { errors: [], warnings: [], info: [] };

        return {
            errors: validationReport.issues.filter((i) => i.severity === 'error'),
            warnings: validationReport.issues.filter((i) => i.severity === 'warning'),
            info: validationReport.issues.filter((i) => i.severity === 'info'),
        };
    }, [validationReport]);

    /**
     * Render validation score
     */
    const renderScore = () => {
        if (!validationReport) return null;

        const { score } = validationReport;
        const color = score >= 80 ? 'success' : score >= 60 ? 'warning' : 'error';

        return (
            <Box className="mb-6">
                <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}>
                    <Typography variant="h6">Validation Score</Typography>
                    <Chip
                        label={`${score}/100`}
                        color={color}
                        icon={score >= 80 ? <CheckCircleIcon /> : <ErrorIcon />}
                    />
                </Stack>
                <LinearProgress variant="determinate" value={score} color={color} />
            </Box>
        );
    };

    /**
     * Render issue item
     */
    const renderIssue = (issue: ValidationIssue) => {
        const isExpanded = expandedIssues.has(issue.id);
        const SeverityIcon = {
            error: ErrorIcon,
            warning: WarningIcon,
            info: InfoIcon,
        }[issue.severity];

        const severityColor = {
            error: 'error',
            warning: 'warning',
            info: 'info',
        }[issue.severity] as 'error' | 'warning' | 'info';

        return (
            <React.Fragment key={issue.id}>
                <ListItem
                    button
                    onClick={() => toggleIssue(issue.id)}
                    style={{ borderColor: `${severityColor, trng: borderLeft: 3 */
                >
                    <ListItemIcon>
                        <SeverityIcon color={severityColor} />
                    </ListItemIcon>
                    <ListItemText
                        primary={issue.title}
                        secondary={issue.category}
                        primaryTypographyProps={{ fontWeight: 'medium' }}
                    />
                    <Stack direction="row" spacing={1} alignItems="center">
                        {issue.autoFixable && (
                            <Tooltip title="Auto-fixable">
                                <BuildIcon color="primary" size={16} />
                            </Tooltip>
                        )}
                        <IconButton
                            size="small"
                            style={{ transform: isExpanded ? 'rotate(180deg)' : 'rotate(0deg)' }} >
                            <ExpandMoreIcon />
                        </IconButton>
                    </Stack>
                </ListItem>
                <Collapse in={isExpanded}>
                    <Box className="pl-14 pr-4 pb-4">
                        <Typography variant="body2" color="text.secondary" paragraph>
                            {issue.description}
                        </Typography>
                        {issue.suggestion && (
                            <Alert severity="info" className="mb-2">
                                <AlertTitle>Suggestion</AlertTitle>
                                {issue.suggestion}
                            </Alert>
                        )}
                        {issue.elementIds.length > 0 && (
                            <Typography variant="caption" color="text.secondary">
                                Affected elements: {issue.elementIds.length}
                            </Typography>
                        )}
                    </Box>
                </Collapse>
            </React.Fragment>
        );
    };

    /**
     * Render risk assessment
     */
    const renderRisk = (risk: RiskAssessment) => {
        const RiskIcon = {
            security: SecurityIcon,
            performance: SpeedIcon,
            maintainability: BuildIcon,
            scalability: TrendingUpIcon,
        }[risk.type];

        const severityColor = {
            low: 'success',
            medium: 'warning',
            high: 'error',
            critical: 'error',
        }[risk.severity] as 'success' | 'warning' | 'error';

        return (
            <Paper key={risk.id} variant="outlined" className="p-4 mb-4">
                <Stack direction="row" spacing={2} alignItems="flex-start">
                    <RiskIcon color={severityColor} />
                    <Box flex={1}>
                        <Stack direction="row" justifyContent="space-between" mb={1}>
                            <Typography variant="subtitle2">{risk.title}</Typography>
                            <Chip label={risk.severity} color={severityColor} size="small" />
                        </Stack>
                        <Typography variant="body2" color="text.secondary" paragraph>
                            {risk.description}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" display="block" mb={0.5}>
                            <strong>Impact:</strong> {risk.impact}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" display="block">
                            <strong>Mitigation:</strong> {risk.mitigation}
                        </Typography>
                    </Box>
                </Stack>
            </Paper>
        );
    };

    /**
     * Render tabs
     */
    const renderContent = () => {
        if (!validationReport) return null;

        switch (selectedTab) {
            case 0: // Issues
                return (
                    <List>
                        {issuesBySeverity.errors.map(renderIssue)}
                        {issuesBySeverity.warnings.map(renderIssue)}
                        {issuesBySeverity.info.map(renderIssue)}
                        {validationReport.issues.length === 0 && (
                            <Box textAlign="center" py={4}>
                                <CheckCircleIcon color="success" className="mb-4 text-5xl" />
                                <Typography color="text.secondary">
                                    No issues found. Canvas is valid for {validationReport.phase} phase.
                                </Typography>
                            </Box>
                        )}
                    </List>
                );

            case 1: // Risks
                return (
                    <Box>
                        {validationReport.risks.map(renderRisk)}
                        {validationReport.risks.length === 0 && (
                            <Box textAlign="center" py={4}>
                                <CheckCircleIcon color="success" className="mb-4 text-5xl" />
                                <Typography color="text.secondary">
                                    No risks identified.
                                </Typography>
                            </Box>
                        )}
                    </Box>
                );

            case 2: // Gaps
                return (
                    <Box>
                        {validationReport.gaps.length > 0 ? (
                            <Alert severity="warning">
                                <AlertTitle>Missing Components</AlertTitle>
                                <List dense>
                                    {validationReport.gaps.map((gap, index) => (
                                        <ListItem key={index}>
                                            <ListItemIcon>
                                                <WarningIcon color="warning" />
                                            </ListItemIcon>
                                            <ListItemText primary={gap} />
                                        </ListItem>
                                    ))}
                                </List>
                            </Alert>
                        ) : (
                            <Box textAlign="center" py={4}>
                                <CheckCircleIcon color="success" className="mb-4 text-5xl" />
                                <Typography color="text.secondary">
                                    No gaps identified. Architecture is complete.
                                </Typography>
                            </Box>
                        )}
                    </Box>
                );

            default:
                return null;
        }
    };

    /**
     * Loading state
     */
    if (isValidating) {
        return (
            <Paper className="p-6">
                <Typography variant="h6" gutterBottom>
                    Validating Canvas...
                </Typography>
                <LinearProgress />
            </Paper>
        );
    }

    /**
     * No validation report
     */
    if (!validationReport) {
        return (
            <Paper className="p-6">
                <Typography color="text.secondary" textAlign="center">
                    No validation results yet. Validation will run automatically.
                </Typography>
            </Paper>
        );
    }

    /**
     * Main render
     */
    return (
        <Paper className="h-full flex flex-col">
            {/* Header */}
            <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                <Typography variant="h6">Validation Report</Typography>
                <Typography variant="caption" color="text.secondary">
                    Phase: {validationReport.phase} | {new Date(validationReport.timestamp).toLocaleString()}
                </Typography>
            </Box>

            {/* Score */}
            <Box className="p-4">
                {renderScore()}

                {/* Summary */}
                <Stack direction="row" spacing={2}>
                    <Chip
                        icon={<ErrorIcon />}
                        label={`${validationReport.summary.errors} Errors`}
                        color={validationReport.summary.errors > 0 ? 'error' : 'default'}
                        size="small"
                    />
                    <Chip
                        icon={<WarningIcon />}
                        label={`${validationReport.summary.warnings} Warnings`}
                        color={validationReport.summary.warnings > 0 ? 'warning' : 'default'}
                        size="small"
                    />
                    <Chip
                        icon={<InfoIcon />}
                        label={`${validationReport.summary.info} Info`}
                        color={validationReport.summary.info > 0 ? 'info' : 'default'}
                        size="small"
                    />
                </Stack>
            </Box>

            <Divider />

            {/* Tabs */}
            <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)} className="px-4">
                <Tab label="Issues" />
                <Tab label="Risks" />
                <Tab label="Gaps" />
            </Tabs>

            <Divider />

            {/* Content */}
            <Box className="flex-1 overflow-auto p-4">
                {renderContent()}
            </Box>
        </Paper>
    );
};
