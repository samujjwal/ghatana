/**
 * DevSecOps Navigation Hub - Central navigation component integrating personas, domains, and phases
 * 
 * Provides comprehensive navigation across the DevSecOps lifecycle with:
 * - Persona-based filtering and views
 * - Domain-specific workflows and tasks
 * - Phase-aligned process navigation
 * - Context-aware recommendations
 */

import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAtom } from 'jotai';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Chip,
  Button,
  Tabs,
  Tab,
  Grid,
  Avatar,
  Badge,
  IconButton,
  Tooltip,
  Divider,
  Stack,
  Select,
  FormControl,
  InputLabel,
} from '@ghatana/ui';
import { MenuItem } from '@ghatana/ui';
import { LayoutDashboard as DashboardIcon, GitBranch as WorkflowIcon, Shield as SecurityIcon, Code as CodeIcon, Gauge as PerformanceIcon, ClipboardList as TaskIcon, User as PersonaIcon, Activity as PhaseIcon, Settings as ConfigIcon, RefreshCw as RefreshIcon } from 'lucide-react';

// Config hooks and state
import {
    useTaskDomains,
    useWorkflows,
    useLifecycleConfig,
    useAgentCapabilities,
    useAllTasks,
    useConfigRefresh
} from '../../hooks/useConfig';
import {
    selectedDomainIdAtom,
    selectedWorkflowIdAtom,
    selectedPhaseIdAtom,
    configViewModeAtom
} from '../../state/configAtoms';

// DevSecOps persona integration - use canonical types and hook
import type { PersonaType } from '@ghatana/yappc-types/devsecops';
import { usePersonas } from '../../hooks/useConfigData';
import { getPersonaDashboard } from './persona-configs';

// Domain icons mapping
const DOMAIN_ICONS: Record<string, React.ReactNode> = {
    'development': <CodeIcon />,
    'security': <SecurityIcon />,
    'operations': <PerformanceIcon />,
    'quality': <TaskIcon />,
    'product': <DashboardIcon />,
};

// Phase color mapping
const PHASE_COLORS: Record<string, string> = {
    'intake': '#9C27B0',
    'plan': '#2196F3',
    'develop': '#4CAF50',
    'secure': '#F44336',
    'test': '#FF9800',
    'deploy': '#795548',
    'monitor': '#607D8B',
    'operate': '#009688',
    'govern': '#E91E63',
};

interface DevSecOpsNavigationHubProps {
    currentPersona: PersonaType;
    onPersonaChange: (persona: PersonaType) => void;
}

export default function DevSecOpsNavigationHub({
    currentPersona,
    onPersonaChange,
}: DevSecOpsNavigationHubProps) {
    const navigate = useNavigate();
    const location = useLocation();
    const [activeTab, setActiveTab] = useState(0);

    // Config state
    const [selectedDomainId, setSelectedDomainId] = useAtom(selectedDomainIdAtom);
    const [selectedWorkflowId, setSelectedWorkflowId] = useAtom(selectedWorkflowIdAtom);
    const [selectedPhaseId, setSelectedPhaseId] = useAtom(selectedPhaseIdAtom);
    const [viewMode, setViewMode] = useAtom(configViewModeAtom);

    // Config data
    const domains = useTaskDomains();
    const workflows = useWorkflows();
    const lifecycleConfig = useLifecycleConfig();
    const agents = useAgentCapabilities();
    const tasks = useAllTasks();
    const { refreshAll } = useConfigRefresh();

    // AI-First: Load personas from backend (single source of truth)
    const { data: PERSONAS = [] } = usePersonas();

    // Current persona metadata
    const currentPersonaData = useMemo(() =>
        PERSONAS.find((p: unknown) => p.id === currentPersona) || PERSONAS[0],
        [currentPersona, PERSONAS]
    );

    // Filtered data based on current persona
    const personaFilteredData = useMemo(() => {
        const personaDomains = domains.filter(domain =>
            domain.personas?.includes(currentPersona) || !domain.personas
        );

        const personaWorkflows = workflows.filter(workflow =>
            workflow.persona === currentPersona || !workflow.persona
        );

        const personaPhases = lifecycleConfig.phases?.filter(phase =>
            phase.primary_personas?.includes(currentPersona) ||
            phase.secondary_personas?.includes(currentPersona) ||
            !phase.primary_personas
        ) || [];

        const personaTasks = tasks.filter(task =>
            task.persona === currentPersona || !task.persona
        );

        return {
            domains: personaDomains,
            workflows: personaWorkflows,
            phases: personaPhases,
            tasks: personaTasks,
        };
    }, [domains, workflows, lifecycleConfig, tasks, currentPersona]);

    // Auto-select first relevant domain for persona
    useEffect(() => {
        if (personaFilteredData.domains.length > 0 && !selectedDomainId) {
            setSelectedDomainId(personaFilteredData.domains[0].id);
        }
    }, [personaFilteredData.domains, selectedDomainId, setSelectedDomainId]);

    const handleDomainSelect = (domainId: string) => {
        setSelectedDomainId(domainId);
        // Auto-select relevant workflow
        const relevantWorkflow = personaFilteredData.workflows.find(w => w.domain === domainId);
        if (relevantWorkflow) {
            setSelectedWorkflowId(relevantWorkflow.id);
        }
    };

    const handleWorkflowSelect = (workflowId: string) => {
        setSelectedWorkflowId(workflowId);
        // Navigate to workflow detail
        navigate(`/devsecops/workflows/${workflowId}?persona=${currentPersona}`);
    };

    const handlePhaseSelect = (phaseId: string) => {
        setSelectedPhaseId(phaseId);
        navigate(`/devsecops/phases/${phaseId}?persona=${currentPersona}`);
    };

    const handleTaskSelect = (taskId: string) => {
        navigate(`/devsecops/tasks/${taskId}?persona=${currentPersona}`);
    };

    const renderDomainCard = (domain: unknown) => (
        <Card
            key={domain.id}
            className={`cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg ${
                selectedDomainId === domain.id
                    ? 'border-2 border-blue-600'
                    : 'border border-gray-200 dark:border-gray-700'
            }`}
            onClick={() => handleDomainSelect(domain.id)}
        >
            <CardContent>
                <Stack direction="row" spacing={2} alignItems="center" mb={2}>
                    <Avatar style={{ backgroundColor: domain.color || '#2196F3' }}>
                        {DOMAIN_ICONS[domain.id] || <DashboardIcon />}
                    </Avatar>
                    <Box>
                        <Typography variant="h6" fontWeight="600">
                            {domain.name}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            {domain.description}
                        </Typography>
                    </Box>
                </Stack>

                <Stack direction="row" spacing={1} flexWrap="wrap" gap={1} mb={2}>
                    {domain.personas?.map((persona: string) => (
                        <Chip
                            key={persona}
                            label={persona.replace('-', ' ')}
                            size="small"
                            variant={persona === currentPersona ? "filled" : "outlined"}
                            color={persona === currentPersona ? "primary" : "default"}
                        />
                    ))}
                </Stack>

                <Typography variant="caption" color="text.secondary">
                    {domain.capabilities?.length || 0} capabilities • {domain.phases?.length || 0} phases
                </Typography>
            </CardContent>
        </Card>
    );

    const renderWorkflowCard = (workflow: unknown) => (
        <Card
            key={workflow.id}
            className={`cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg ${
                selectedWorkflowId === workflow.id
                    ? 'border-2 border-blue-600'
                    : 'border border-gray-200 dark:border-gray-700'
            }`}
            onClick={() => handleWorkflowSelect(workflow.id)}
        >
            <CardContent>
                <Stack direction="row" spacing={2} alignItems="center" mb={2}>
                    <WorkflowIcon color="primary" />
                    <Box>
                        <Typography variant="h6" fontWeight="600">
                            {workflow.name}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            {workflow.description}
                        </Typography>
                    </Box>
                </Stack>

                <Stack direction="row" spacing={1} mb={2}>
                    <Chip
                        label={workflow.persona || 'All Personas'}
                        size="small"
                        color="primary"
                        variant="outlined"
                    />
                    <Chip
                        label={workflow.domain || 'All Domains'}
                        size="small"
                        variant="outlined"
                    />
                </Stack>

                <Typography variant="caption" color="text.secondary">
                    {workflow.steps?.length || 0} steps • {workflow.phases?.length || 0} phases
                </Typography>
            </CardContent>
        </Card>
    );

    const renderPhaseCard = (phase: unknown) => (
        <Card
            key={phase.id}
            className={`cursor-pointer transition-all duration-200 border-l-4 hover:-translate-y-0.5 hover:shadow-lg ${
                selectedPhaseId === phase.id
                    ? 'border-2 border-blue-600'
                    : 'border border-gray-200 dark:border-gray-700'
            }`}
            style={{ borderLeftColor: PHASE_COLORS[phase.id] || '#2196F3' }}
            onClick={() => handlePhaseSelect(phase.id)}
        >
            <CardContent>
                <Stack direction="row" spacing={2} alignItems="center" mb={2}>
                    <PhaseIcon style={{ color: PHASE_COLORS[phase.id] || '#2196F3' }} />
                    <Box>
                        <Typography variant="h6" fontWeight="600">
                            {phase.name}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            {phase.description}
                        </Typography>
                    </Box>
                </Stack>

                <Stack spacing={1} mb={2}>
                    <Typography variant="caption" color="text.secondary">
                        Duration: {phase.duration}
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                        {phase.primary_personas?.slice(0, 3).map((persona: string) => (
                            <Chip
                                key={persona}
                                label={persona}
                                size="small"
                                color={persona === currentPersona ? "primary" : "default"}
                                variant={persona === currentPersona ? "filled" : "outlined"}
                            />
                        ))}
                        {(phase.primary_personas?.length || 0) > 3 && (
                            <Chip label={`+${(phase.primary_personas?.length || 0) - 3}`} size="small" variant="outlined" />
                        )}
                    </Stack>
                </Stack>

                <Typography variant="caption" color="text.secondary">
                    {phase.activities?.length || 0} activities • {phase.gates?.length || 0} gates
                </Typography>
            </CardContent>
        </Card>
    );

    const renderTaskCard = (task: unknown) => (
        <Card
            key={task.id}
            className="cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg"
            onClick={() => handleTaskSelect(task.id)}
        >
            <CardContent>
                <Stack direction="row" spacing={2} alignItems="center" mb={2}>
                    <Badge
                        badgeContent={task.priority === 'critical' ? '!' : task.priority === 'high' ? 'H' : 'M'}
                        color={task.priority === 'critical' ? 'error' : task.priority === 'high' ? 'warning' : 'primary'}
                    >
                        <TaskIcon color="primary" />
                    </Badge>
                    <Box>
                        <Typography variant="h6" fontWeight="600">
                            {task.name}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            {task.description}
                        </Typography>
                    </Box>
                </Stack>

                <Stack direction="row" spacing={1} mb={2}>
                    <Chip label={task.type} size="small" variant="outlined" />
                    <Chip label={task.complexity || 'medium'} size="small" />
                </Stack>

                <Typography variant="caption" color="text.secondary">
                    Est: {task.estimated_duration} • Agent: {task.agent}
                </Typography>
            </CardContent>
        </Card>
    );

    return (
        <Box>
            {/* Header */}
            <Stack direction="row" justifyContent="space-between" alignItems="center" mb={4}>
                <Box>
                    <Typography variant="h4" fontWeight="700" gutterBottom>
                        DevSecOps Navigation Hub
                    </Typography>
                    <Stack direction="row" spacing={2} alignItems="center">
                        <Avatar style={{ backgroundColor: currentPersonaData.color }}>
                            {currentPersonaData.icon}
                        </Avatar>
                        <Box>
                            <Typography variant="h6" fontWeight="600">
                                {currentPersonaData.name} View
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                                {currentPersonaData.description}
                            </Typography>
                        </Box>
                    </Stack>
                </Box>

                <Stack direction="row" spacing={2}>
                    <FormControl size="small" className="min-w-[120px]">
                        <InputLabel>View Mode</InputLabel>
                        <Select
                            value={viewMode}
                            label="View Mode"
                            onChange={(e) => setViewMode(e.target.value as unknown)}
                        >
                            <MenuItem value="list">List</MenuItem>
                            <MenuItem value="grid">Grid</MenuItem>
                            <MenuItem value="detail">Detail</MenuItem>
                        </Select>
                    </FormControl>
                    <IconButton onClick={refreshAll} color="primary">
                        <RefreshIcon />
                    </IconButton>
                    <Button
                        variant="outlined"
                        startIcon={<ConfigIcon />}
                        onClick={() => navigate('/config-demo')}
                    >
                        Config Demo
                    </Button>
                </Stack>
            </Stack>

            {/* Navigation Tabs */}
            <Tabs
                value={activeTab}
                onChange={(_, newValue) => setActiveTab(newValue)}
                className="mb-6"
            >
                <Tab label="Domains" icon={<DashboardIcon />} />
                <Tab label="Workflows" icon={<WorkflowIcon />} />
                <Tab label="Phases" icon={<PhaseIcon />} />
                <Tab label="Tasks" icon={<TaskIcon />} />
            </Tabs>

            {/* Content based on active tab */}
            {activeTab === 0 && (
                <Box>
                    <Typography variant="h6" fontWeight="600" mb={3}>
                        Available Domains for {currentPersonaData.name}
                    </Typography>
                    <Grid container spacing={3}>
                        {personaFilteredData.domains.map(domain => (
                            <Grid item xs={12} md={6} lg={4} key={domain.id}>
                                {renderDomainCard(domain)}
                            </Grid>
                        ))}
                    </Grid>
                </Box>
            )}

            {activeTab === 1 && (
                <Box>
                    <Typography variant="h6" fontWeight="600" mb={3}>
                        Relevant Workflows for {currentPersonaData.name}
                    </Typography>
                    <Grid container spacing={3}>
                        {personaFilteredData.workflows.map(workflow => (
                            <Grid item xs={12} md={6} lg={4} key={workflow.id}>
                                {renderWorkflowCard(workflow)}
                            </Grid>
                        ))}
                    </Grid>
                </Box>
            )}

            {activeTab === 2 && (
                <Box>
                    <Typography variant="h6" fontWeight="600" mb={3}>
                        DevSecOps Phases for {currentPersonaData.name}
                    </Typography>
                    <Grid container spacing={3}>
                        {personaFilteredData.phases.map(phase => (
                            <Grid item xs={12} md={6} lg={4} key={phase.id}>
                                {renderPhaseCard(phase)}
                            </Grid>
                        ))}
                    </Grid>
                </Box>
            )}

            {activeTab === 3 && (
                <Box>
                    <Typography variant="h6" fontWeight="600" mb={3}>
                        Available Tasks for {currentPersonaData.name}
                    </Typography>
                    <Grid container spacing={3}>
                        {personaFilteredData.tasks.map(task => (
                            <Grid item xs={12} md={6} lg={4} key={task.id}>
                                {renderTaskCard(task)}
                            </Grid>
                        ))}
                    </Grid>
                </Box>
            )}

            {/* Quick Actions Bar */}
            <Card className="mt-8 bg-gray-50 dark:bg-gray-800">
                <CardContent>
                    <Typography variant="h6" fontWeight="600" mb={2}>
                        Quick Actions
                    </Typography>
                    <Stack direction="row" spacing={2} flexWrap="wrap">
                        <Button
                            variant="contained"
                            startIcon={<DashboardIcon />}
                            onClick={() => navigate(`/devsecops?persona=${currentPersona}`)}
                        >
                            My Dashboard
                        </Button>
                        {selectedWorkflowId && (
                            <Button
                                variant="outlined"
                                startIcon={<WorkflowIcon />}
                                onClick={() => navigate(`/devsecops/workflows/${selectedWorkflowId}`)}
                            >
                                Open Workflow
                            </Button>
                        )}
                        {selectedPhaseId && (
                            <Button
                                variant="outlined"
                                startIcon={<PhaseIcon />}
                                onClick={() => navigate(`/devsecops/phases/${selectedPhaseId}`)}
                            >
                                View Phase
                            </Button>
                        )}
                        <Button
                            variant="outlined"
                            startIcon={<PersonaIcon />}
                            onClick={() => navigate('/devsecops/personas')}
                        >
                            Switch Persona
                        </Button>
                    </Stack>
                </CardContent>
            </Card>
        </Box>
    );
}