/**
 * CategoryContextPanel - Shows category-specific guidance and context for workflow steps
 *
 * @doc.type component
 * @doc.purpose Provides DevSecOps category-aware context and guidance
 * @doc.layer product
 * @doc.pattern Context Panel
 */

import React from 'react';
import { Box, Typography, Surface as Paper, Chip, InteractiveList as List, ListItem, ListItemIcon, ListItemText, Collapse, IconButton, Tooltip, alpha } from '@ghatana/ui';
import { ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon, Shield as SecurityIcon, Gavel as GavelIcon, Bug as BugIcon, Hammer as BuildIcon, CloudUpload as DeployIcon, HeartPulse as OpsIcon, Brain as IdeaIcon, Building2 as ArchIcon, TrendingUp as OptimizeIcon, GraduationCap as LearnIcon, CheckCircle as CheckIcon, AlertTriangle as WarningIcon, Info as InfoIcon } from 'lucide-react';

import type { WorkflowCategory, WorkflowStep } from '@ghatana/yappc-types';

// ============================================================================
// TYPES
// ============================================================================

interface CategoryContextPanelProps {
    category: WorkflowCategory;
    currentStep: WorkflowStep;
    collapsed?: boolean;
    onToggle?: () => void;
}

interface CategoryConfig {
    icon: React.ReactNode;
    color: string;
    label: string;
    description: string;
    stepGuidance: Partial<Record<WorkflowStep, StepGuidance>>;
    keyMetrics: string[];
    typicalAgents: string[];
}

interface StepGuidance {
    focus: string;
    checkpoints: string[];
    antiPatterns: string[];
    tools?: string[];
}

// ============================================================================
// CATEGORY CONFIGURATIONS
// ============================================================================

const CATEGORY_CONFIGS: Record<WorkflowCategory, CategoryConfig> = {
    IDEATION: {
        icon: <IdeaIcon />,
        color: '#9C27B0',
        label: 'Ideation',
        description: 'Requirements gathering, feature ideation, and stakeholder alignment',
        keyMetrics: ['Time to approval', 'Stakeholder buy-in', 'Requirement completeness'],
        typicalAgents: ['requirements-analyst', 'stakeholder-coordinator', 'feasibility-assessor'],
        stepGuidance: {
            INTENT: {
                focus: 'Capture the business need or opportunity clearly',
                checkpoints: ['Stakeholders identified', 'Business value articulated', 'Success metrics defined'],
                antiPatterns: ['Jumping to solutions', 'Vague goals', 'Missing stakeholder input'],
            },
            CONTEXT: {
                focus: 'Understand market, competitive landscape, and constraints',
                checkpoints: ['Market analysis complete', 'Competitive analysis done', 'Resource constraints identified'],
                antiPatterns: ['Ignoring competition', 'Overlooking constraints', 'Bias toward preferred solution'],
            },
            PLAN: {
                focus: 'Create roadmap with clear milestones and decision points',
                checkpoints: ['MVP defined', 'Phased approach documented', 'Go/no-go criteria set'],
                antiPatterns: ['Over-planning', 'No fallback options', 'Ignoring dependencies'],
            },
        },
    },
    ARCHITECTURE: {
        icon: <ArchIcon />,
        color: '#3F51B5',
        label: 'Architecture',
        description: 'System design, threat modeling, and technical decision-making',
        keyMetrics: ['Design review coverage', 'Threat coverage', 'ADR completeness'],
        typicalAgents: ['system-architect', 'threat-modeler', 'data-classifier', 'adr-writer'],
        stepGuidance: {
            INTENT: {
                focus: 'Define architectural scope and quality attributes',
                checkpoints: ['Quality attributes prioritized', 'Scope boundaries clear', 'Constraints documented'],
                antiPatterns: ['Premature technology choices', 'Ignoring NFRs', 'Scope creep'],
            },
            CONTEXT: {
                focus: 'Map existing systems, data flows, and trust boundaries',
                checkpoints: ['System context diagram', 'Data flow diagrams', 'Trust boundaries identified'],
                antiPatterns: ['Missing integrations', 'Unclear data ownership', 'Hidden dependencies'],
                tools: ['C4 diagrams', 'DFD', 'STRIDE'],
            },
            PLAN: {
                focus: 'Design solution with threat mitigations and tradeoff analysis',
                checkpoints: ['ADRs documented', 'Threats enumerated', 'Mitigations assigned'],
                antiPatterns: ['Security as afterthought', 'Over-engineering', 'Undocumented decisions'],
            },
            INSTITUTIONALIZE: {
                focus: 'Update design patterns, security controls, and reference architectures',
                checkpoints: ['Patterns documented', 'Reference arch updated', 'Security controls catalogued'],
                antiPatterns: ['One-off designs', 'Knowledge silos', 'Outdated references'],
            },
        },
    },
    DEVELOPMENT: {
        icon: <BugIcon />,
        color: '#4CAF50',
        label: 'Development',
        description: 'Feature development, bug fixes, refactoring, and code quality',
        keyMetrics: ['Velocity', 'Code coverage', 'Defect rate', 'Tech debt ratio'],
        typicalAgents: ['code-generator', 'test-writer', 'code-reviewer', 'doc-updater'],
        stepGuidance: {
            INTENT: {
                focus: 'Define the change clearly with acceptance criteria',
                checkpoints: ['User story complete', 'Acceptance criteria defined', 'Dependencies identified'],
                antiPatterns: ['Vague requirements', 'Missing edge cases', 'No definition of done'],
            },
            CONTEXT: {
                focus: 'Understand codebase, dependencies, and test coverage',
                checkpoints: ['Impact analysis done', 'Test coverage assessed', 'Dependencies mapped'],
                antiPatterns: ['Coding without context', 'Ignoring existing tests', 'Missing impact areas'],
                tools: ['IDE search', 'Dependency graph', 'Coverage report'],
            },
            EXECUTE: {
                focus: 'Implement with TDD, clean code, and proper documentation',
                checkpoints: ['Tests written first', 'Code reviewed', 'Docs updated'],
                antiPatterns: ['Skipping tests', 'Copy-paste code', 'Undocumented changes'],
            },
            VERIFY: {
                focus: 'Validate functionality, regression, and code quality',
                checkpoints: ['All tests pass', 'No regressions', 'Code quality gates pass'],
                antiPatterns: ['Partial testing', 'Ignoring warnings', 'Skipping review'],
            },
        },
    },
    BUILD: {
        icon: <BuildIcon />,
        color: '#FF9800',
        label: 'Build',
        description: 'CI/CD pipelines, security scanning, and artifact management',
        keyMetrics: ['Build time', 'Pipeline success rate', 'Scan coverage'],
        typicalAgents: ['pipeline-runner', 'sast-scanner', 'sca-analyzer', 'image-builder'],
        stepGuidance: {
            INTENT: {
                focus: 'Define build requirements and security gates',
                checkpoints: ['Build triggers defined', 'Security gates configured', 'Artifact requirements set'],
                antiPatterns: ['No security scanning', 'Slow feedback loops', 'Missing notifications'],
            },
            EXECUTE: {
                focus: 'Run pipeline with all quality and security checks',
                checkpoints: ['Compilation successful', 'All scans complete', 'Artifacts signed'],
                antiPatterns: ['Skipping scans', 'Unsigned artifacts', 'No provenance'],
                tools: ['SAST', 'SCA', 'Container scanning', 'SBOM'],
            },
            VERIFY: {
                focus: 'Validate scan results and address findings',
                checkpoints: ['Critical findings addressed', 'False positives triaged', 'Compliance verified'],
                antiPatterns: ['Ignoring findings', 'Suppressing without review', 'No baseline'],
            },
        },
    },
    RELEASE: {
        icon: <DeployIcon />,
        color: '#00BCD4',
        label: 'Release',
        description: 'Deployment, rollout strategies, and change management',
        keyMetrics: ['Deployment frequency', 'Change failure rate', 'MTTR'],
        typicalAgents: ['release-manager', 'deployment-automator', 'change-reviewer', 'rollback-handler'],
        stepGuidance: {
            INTENT: {
                focus: 'Define release scope, timing, and rollback criteria',
                checkpoints: ['Release scope defined', 'Deployment window set', 'Rollback criteria clear'],
                antiPatterns: ['Big bang releases', 'No rollback plan', 'Missing change approval'],
            },
            PLAN: {
                focus: 'Create deployment plan with canary/blue-green strategy',
                checkpoints: ['Rollout strategy defined', 'Rollback tested', 'Communication plan ready'],
                antiPatterns: ['No staged rollout', 'Untested rollback', 'Missing stakeholder notification'],
            },
            EXECUTE: {
                focus: 'Deploy with monitoring and health checks',
                checkpoints: ['Health checks passing', 'Metrics stable', 'No errors in logs'],
                antiPatterns: ['Deploy without monitoring', 'Ignoring warnings', 'Rushing rollout'],
            },
            OBSERVE: {
                focus: 'Monitor for regressions and performance impact',
                checkpoints: ['SLOs maintained', 'No error spike', 'Performance baseline met'],
                antiPatterns: ['Short observation window', 'Ignoring anomalies', 'No comparison baseline'],
            },
        },
    },
    OPERATIONS: {
        icon: <OpsIcon />,
        color: '#E91E63',
        label: 'Operations',
        description: 'Incident response, capacity planning, and SRE practices',
        keyMetrics: ['MTTR', 'MTTD', 'SLO attainment', 'Incident count'],
        typicalAgents: ['incident-commander', 'diagnostician', 'mitigator', 'communicator'],
        stepGuidance: {
            INTENT: {
                focus: 'Define incident severity and immediate goals',
                checkpoints: ['Severity assessed', 'Incident commander assigned', 'Communication started'],
                antiPatterns: ['Delayed response', 'No clear ownership', 'Silent incident'],
            },
            CONTEXT: {
                focus: 'Gather system state, recent changes, and correlations',
                checkpoints: ['Logs collected', 'Recent changes reviewed', 'Impact assessed'],
                antiPatterns: ['Guessing without data', 'Missing recent changes', 'Tunnel vision'],
                tools: ['Log aggregation', 'APM', 'Change log'],
            },
            EXECUTE: {
                focus: 'Apply mitigation and restore service',
                checkpoints: ['Mitigation applied', 'Service restored', 'Customers notified'],
                antiPatterns: ['Delayed mitigation', 'Heroics over process', 'No status updates'],
            },
            LEARN: {
                focus: 'Conduct blameless post-mortem and identify improvements',
                checkpoints: ['Timeline documented', 'Root causes identified', 'Action items assigned'],
                antiPatterns: ['Blame culture', 'No follow-through', 'Repeating incidents'],
            },
        },
    },
    SECOPS: {
        icon: <SecurityIcon />,
        color: '#F44336',
        label: 'SecOps',
        description: 'Security incident response, vulnerability management, and access control',
        keyMetrics: ['MTTD', 'MTTR', 'Vulnerability SLA compliance', 'Patching coverage'],
        typicalAgents: ['threat-hunter', 'incident-responder', 'vuln-remediator', 'access-reviewer'],
        stepGuidance: {
            INTENT: {
                focus: 'Assess threat severity and containment priority',
                checkpoints: ['Threat classified', 'Blast radius assessed', 'Containment priority set'],
                antiPatterns: ['Underestimating severity', 'Delayed containment', 'No escalation'],
            },
            CONTEXT: {
                focus: 'Gather threat intelligence, IOCs, and affected assets',
                checkpoints: ['IOCs collected', 'Affected assets identified', 'Threat intel correlated'],
                antiPatterns: ['Incomplete asset inventory', 'Missing IOCs', 'No external intel'],
                tools: ['SIEM', 'EDR', 'Threat intel feeds'],
            },
            PLAN: {
                focus: 'Develop containment and remediation strategy',
                checkpoints: ['Containment plan ready', 'Remediation sequenced', 'Evidence preserved'],
                antiPatterns: ['Destroying evidence', 'Ad-hoc remediation', 'No legal coordination'],
            },
            VERIFY: {
                focus: 'Confirm threat eradication and hardening',
                checkpoints: ['No active threat', 'Vulnerability patched', 'Access reviewed'],
                antiPatterns: ['Premature all-clear', 'Unpatched systems', 'Orphaned access'],
            },
        },
    },
    GRC: {
        icon: <GavelIcon />,
        color: '#795548',
        label: 'GRC',
        description: 'Governance, risk management, and compliance activities',
        keyMetrics: ['Control coverage', 'Compliance score', 'Risk score', 'Audit findings'],
        typicalAgents: ['evidence-collector', 'control-assessor', 'risk-analyst', 'policy-reviewer'],
        stepGuidance: {
            INTENT: {
                focus: 'Define audit scope, framework, and timeline',
                checkpoints: ['Framework identified', 'Scope defined', 'Timeline confirmed'],
                antiPatterns: ['Last-minute prep', 'Scope gaps', 'Missing stakeholders'],
            },
            CONTEXT: {
                focus: 'Inventory controls, collect evidence, and identify gaps',
                checkpoints: ['Controls mapped', 'Evidence collected', 'Gaps documented'],
                antiPatterns: ['Missing evidence', 'Undocumented controls', 'Stale documentation'],
                tools: ['GRC platform', 'Control matrix', 'Evidence repository'],
            },
            VERIFY: {
                focus: 'Validate control effectiveness and remediate gaps',
                checkpoints: ['Controls tested', 'Gaps remediated', 'Evidence complete'],
                antiPatterns: ['Paper compliance', 'Untested controls', 'Deferred remediation'],
            },
            INSTITUTIONALIZE: {
                focus: 'Update policies, procedures, and control documentation',
                checkpoints: ['Policies updated', 'Procedures documented', 'Training scheduled'],
                antiPatterns: ['Outdated policies', 'Tribal knowledge', 'No continuous improvement'],
            },
        },
    },
    OPTIMIZATION: {
        icon: <OptimizeIcon />,
        color: '#607D8B',
        label: 'Optimization',
        description: 'Post-mortem analysis, cost optimization, and continuous improvement',
        keyMetrics: ['Cost savings', 'Performance improvement', 'Process efficiency'],
        typicalAgents: ['cost-analyzer', 'performance-tuner', 'process-improver', 'retrospective-facilitator'],
        stepGuidance: {
            INTENT: {
                focus: 'Define optimization goals and measurement criteria',
                checkpoints: ['Baseline established', 'Target defined', 'Metrics selected'],
                antiPatterns: ['No baseline', 'Unclear goals', 'Premature optimization'],
            },
            CONTEXT: {
                focus: 'Analyze current state, identify bottlenecks and opportunities',
                checkpoints: ['Data collected', 'Bottlenecks identified', 'Opportunities ranked'],
                antiPatterns: ['Analysis paralysis', 'Ignoring data', 'Bias toward favorites'],
                tools: ['APM', 'Cost explorer', 'Profilers'],
            },
            LEARN: {
                focus: 'Synthesize learnings and identify repeatable improvements',
                checkpoints: ['Learnings documented', 'Patterns identified', 'Improvements prioritized'],
                antiPatterns: ['One-off fixes', 'No documentation', 'Ignored learnings'],
            },
        },
    },
    INSTITUTIONALIZATION: {
        icon: <LearnIcon />,
        color: '#9E9E9E',
        label: 'Institutionalization',
        description: 'Creating templates, guardrails, and organizational learning',
        keyMetrics: ['Template adoption', 'Guardrail coverage', 'Knowledge reuse'],
        typicalAgents: ['template-creator', 'guardrail-updater', 'knowledge-curator', 'training-developer'],
        stepGuidance: {
            INTENT: {
                focus: 'Define what knowledge or patterns to institutionalize',
                checkpoints: ['Knowledge identified', 'Value articulated', 'Audience defined'],
                antiPatterns: ['Forced standardization', 'Outdated patterns', 'No adoption plan'],
            },
            EXECUTE: {
                focus: 'Create templates, update guardrails, and document patterns',
                checkpoints: ['Templates created', 'Guardrails updated', 'Documentation published'],
                antiPatterns: ['Over-engineering', 'No examples', 'Complex adoption'],
            },
            INSTITUTIONALIZE: {
                focus: 'Roll out changes and drive adoption',
                checkpoints: ['Training delivered', 'Adoption measured', 'Feedback collected'],
                antiPatterns: ['No training', 'Forced adoption', 'Ignoring feedback'],
            },
        },
    },
};

// ============================================================================
// COMPONENT
// ============================================================================

export function CategoryContextPanel({
    category,
    currentStep,
    collapsed = false,
    onToggle,
}: CategoryContextPanelProps) {
    const config = CATEGORY_CONFIGS[category];
    const stepGuidance = config.stepGuidance[currentStep];

    return (
        <Paper
            variant="flat"
            className="border border-solid border-gray-200 dark:border-gray-700" style={{ borderLeft: `4px solid ${config.color}`, backgroundColor: alpha(config.color, 0.05), color: config.color }}
        >
            {/* Header */}
            <Box
                className="flex items-center p-4" style={{ backgroundColor: alpha(config.color, 0.1) }}
                onClick={onToggle}
            >
                <Box
                    className="flex items-center justify-center rounded mr-4 w-[40px] h-[40px]" >
                    {config.icon}
                </Box>
                <Box className="flex-1">
                    <Typography as="p" className="text-lg font-medium" className="font-semibold">
                        {config.label} Workflow
                    </Typography>
                    <Typography as="p" className="text-sm" color="text.secondary">
                        {config.description}
                    </Typography>
                </Box>
                {onToggle && (
                    <IconButton size="sm">
                        {collapsed ? <ExpandMoreIcon /> : <ExpandLessIcon />}
                    </IconButton>
                )}
            </Box>

            <Collapse in={!collapsed}>
                <Box className="p-4 pt-0">
                    {/* Step-specific guidance */}
                    {stepGuidance && (
                        <Box className="mb-4">
                            <Typography
                                as="p" className="text-sm font-medium"
                                className="mb-2 flex items-center gap-2"
                            >
                                <InfoIcon size={16} tone="primary" />
                                Focus for {currentStep} Step
                            </Typography>
                            <Typography as="p" className="text-sm" className="mb-4 text-gray-500 dark:text-gray-400">
                                {stepGuidance.focus}
                            </Typography>

                            {/* Checkpoints */}
                            <Typography as="span" className="text-xs text-gray-500" className="font-semibold block mb-2">
                                Checkpoints
                            </Typography>
                            <List dense className="py-0 mb-4">
                                {stepGuidance.checkpoints.map((checkpoint, idx) => (
                                    <ListItem key={idx} className="py-0.5 px-0">
                                        <ListItemIcon className="min-w-[28px]">
                                            <CheckIcon size={16} className="text-green-600" />
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={checkpoint}
                                            primaryTypographyProps={{ variant: 'body2' }}
                                        />
                                    </ListItem>
                                ))}
                            </List>

                            {/* Anti-patterns */}
                            <Typography as="span" className="text-xs text-gray-500" className="font-semibold block mb-2">
                                Anti-patterns to Avoid
                            </Typography>
                            <List dense className="py-0">
                                {stepGuidance.antiPatterns.map((antiPattern, idx) => (
                                    <ListItem key={idx} className="py-0.5 px-0">
                                        <ListItemIcon className="min-w-[28px]">
                                            <WarningIcon size={16} className="text-amber-600" />
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={antiPattern}
                                            primaryTypographyProps={{ variant: 'body2' }}
                                        />
                                    </ListItem>
                                ))}
                            </List>

                            {/* Tools */}
                            {stepGuidance.tools && stepGuidance.tools.length > 0 && (
                                <Box className="mt-4">
                                    <Typography as="span" className="text-xs text-gray-500" className="font-semibold block mb-2">
                                        Recommended Tools
                                    </Typography>
                                    <Box className="flex gap-1 flex-wrap">
                                        {stepGuidance.tools.map((tool, idx) => (
                                            <Chip
                                                key={idx}
                                                label={tool}
                                                size="sm"
                                                variant="outlined"
                                            />
                                        ))}
                                    </Box>
                                </Box>
                            )}
                        </Box>
                    )}

                    {/* Key Metrics */}
                    <Box className="mb-4">
                        <Typography as="span" className="text-xs text-gray-500" className="font-semibold block mb-2">
                            Key Metrics
                        </Typography>
                        <Box className="flex gap-1 flex-wrap">
                            {config.keyMetrics.map((metric, idx) => (
                                <Chip
                                    key={idx}
                                    label={metric}
                                    size="sm"
                                    style={{ backgroundColor: alpha(config.color, 0.1) }}
                                />
                            ))}
                        </Box>
                    </Box>

                    {/* Typical Agents */}
                    <Box>
                        <Typography as="span" className="text-xs text-gray-500" className="font-semibold block mb-2">
                            Active Agents
                        </Typography>
                        <Box className="flex gap-1 flex-wrap">
                            {config.typicalAgents.map((agent, idx) => (
                                <Tooltip key={idx} title={`Agent: ${agent}`}>
                                    <Chip
                                        label={agent}
                                        size="sm"
                                        variant="outlined"
                                        className="text-[0.7rem]"
                                    />
                                </Tooltip>
                            ))}
                        </Box>
                    </Box>
                </Box>
            </Collapse>
        </Paper>
    );
}

export default CategoryContextPanel;
