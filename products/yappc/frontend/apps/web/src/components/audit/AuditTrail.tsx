/**
 * YAPPC Audit Trail Component
 *
 * Displays chronological audit events with filtering and export capabilities.
 *
 * @doc.type component
 * @doc.purpose Audit trail visualization
 * @doc.layer product
 * @doc.pattern Timeline Component
 */

import React from 'react';
import { Box, Typography, Card, CardContent, Chip, IconButton, Button, TextField, InputAdornment, FormControl, InputLabel, Select, MenuItem, Tooltip, Collapse, Divider, Spinner as CircularProgress, Alert, Menu, ListItemText } from '@ghatana/ui';
import { Search as SearchIcon, Download as DownloadIcon, RefreshCw as RefreshIcon, ChevronDown as ExpandIcon, ChevronUp as CollapseIcon, AlertCircle as ErrorIcon, AlertTriangle as WarningIcon, Info as InfoIcon, User as PersonIcon, Bot as AgentIcon, Settings as SystemIcon, Clock as AutomationIcon, ChevronDown as MenuIcon } from 'lucide-react';

// ============================================================================
// Types (Inline for module independence)
// ============================================================================

type AuditEventCategory =
    | 'workflow'
    | 'task'
    | 'lifecycle'
    | 'agent'
    | 'artifact'
    | 'compliance'
    | 'security'
    | 'system';

type AuditEventSeverity = 'info' | 'warning' | 'error' | 'critical';

type ActorType = 'user' | 'agent' | 'system' | 'automation';

interface AuditActor {
    type: ActorType;
    id: string;
    name: string;
    metadata?: Record<string, unknown>;
}

interface AuditTarget {
    type: string;
    id: string;
    name: string;
    metadata?: Record<string, unknown>;
}

interface AuditEvent {
    id: string;
    timestamp: string;
    category: AuditEventCategory;
    action: string;
    severity: AuditEventSeverity;
    actor: AuditActor;
    target: AuditTarget;
    message?: string;
    workflowId?: string;
    taskId?: string;
    metadata: Record<string, unknown>;
    snapshot?: Record<string, unknown>;
}

interface AuditTrailFilter {
    search?: string;
    categories?: AuditEventCategory[];
    severities?: AuditEventSeverity[];
    actorTypes?: ActorType[];
    startDate?: string;
    endDate?: string;
}

interface AuditTrailProps {
    workflowId?: string;
    events?: AuditEvent[];
    isLoading?: boolean;
    error?: string;
    onFilter?: (filter: AuditTrailFilter) => void;
    onExport?: (format: 'json' | 'csv' | 'pdf') => void;
    onRefresh?: () => void;
}

interface EventCardProps {
    event: AuditEvent;
    expanded?: boolean;
    onToggle?: () => void;
}

// ============================================================================
// Constants
// ============================================================================

const SEVERITY_CONFIG: Record<AuditEventSeverity, {
    color: 'default' | 'info' | 'warning' | 'error';
    icon: React.ReactNode;
}> = {
    info: { color: 'info', icon: <InfoIcon size={16} /> },
    warning: { color: 'warning', icon: <WarningIcon size={16} /> },
    error: { color: 'error', icon: <ErrorIcon size={16} /> },
    critical: { color: 'error', icon: <ErrorIcon size={16} /> },
};

const CATEGORY_COLORS: Record<AuditEventCategory, string> = {
    workflow: '#2196f3',
    task: '#4caf50',
    lifecycle: '#ff9800',
    agent: '#9c27b0',
    artifact: '#00bcd4',
    compliance: '#f44336',
    security: '#e91e63',
    system: '#607d8b',
};

const ACTOR_ICONS: Record<ActorType, React.ReactElement> = {
    user: <PersonIcon size={16} />,
    agent: <AgentIcon size={16} />,
    system: <SystemIcon size={16} />,
    automation: <AutomationIcon size={16} />,
};

// ============================================================================
// Component
// ============================================================================

export function AuditTrail({
    workflowId: _workflowId,
    events = [],
    isLoading = false,
    error,
    onFilter: _onFilter,
    onExport,
    onRefresh,
}: AuditTrailProps) {
    const [searchQuery, setSearchQuery] = React.useState('');
    const [categoryFilter, setCategoryFilter] = React.useState<AuditEventCategory | 'all'>('all');
    const [severityFilter, setSeverityFilter] = React.useState<AuditEventSeverity | 'all'>('all');
    const [expandedEventId, setExpandedEventId] = React.useState<string | null>(null);
    const [exportAnchorEl, setExportAnchorEl] = React.useState<null | HTMLElement>(null);

    // Apply local filters
    const filteredEvents = React.useMemo(() => {
        let filtered = events;

        if (searchQuery) {
            const query = searchQuery.toLowerCase();
            filtered = filtered.filter(
                (e) =>
                    (e.message?.toLowerCase().includes(query) ?? false) ||
                    (e.target.name?.toLowerCase().includes(query) ?? false) ||
                    (e.actor.name?.toLowerCase().includes(query) ?? false)
            );
        }

        if (categoryFilter !== 'all') {
            filtered = filtered.filter((e) => e.category === categoryFilter);
        }

        if (severityFilter !== 'all') {
            filtered = filtered.filter((e) => e.severity === severityFilter);
        }

        return filtered;
    }, [events, searchQuery, categoryFilter, severityFilter]);

    // Group events by date
    const groupedEvents = React.useMemo(() => {
        const groups: Record<string, AuditEvent[]> = {};
        for (const event of filteredEvents) {
            const date = new Date(event.timestamp).toLocaleDateString();
            if (!groups[date]) groups[date] = [];
            groups[date].push(event);
        }
        return groups;
    }, [filteredEvents]);

    // Handle export
    const handleExport = (format: 'json' | 'csv' | 'pdf') => {
        setExportAnchorEl(null);
        onExport?.(format);
    };

    if (error) {
        return (
            <Alert severity="error" className="m-4">
                {error}
            </Alert>
        );
    }

    return (
        <Box className="flex flex-col h-full">
            {/* Toolbar */}
            <Box
                className="p-4 flex items-center gap-4 flex-wrap border-gray-200 dark:border-gray-700 border-b" >
                {/* Search */}
                <TextField
                    size="sm"
                    placeholder="Search events..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    InputProps={{
                        startAdornment: (
                            <InputAdornment position="start">
                                <SearchIcon size={16} />
                            </InputAdornment>
                        ),
                    }}
                    className="min-w-[200px]"
                />

                {/* Category Filter */}
                <FormControl size="sm" className="min-w-[140px]">
                    <InputLabel>Category</InputLabel>
                    <Select
                        value={categoryFilter}
                        label="Category"
                        onChange={(e) => setCategoryFilter(e.target.value as AuditEventCategory | 'all')}
                    >
                        <MenuItem value="all">All Categories</MenuItem>
                        <MenuItem value="workflow">Workflow</MenuItem>
                        <MenuItem value="task">Task</MenuItem>
                        <MenuItem value="lifecycle">Lifecycle</MenuItem>
                        <MenuItem value="agent">Agent</MenuItem>
                        <MenuItem value="artifact">Artifact</MenuItem>
                        <MenuItem value="compliance">Compliance</MenuItem>
                        <MenuItem value="security">Security</MenuItem>
                        <MenuItem value="system">System</MenuItem>
                    </Select>
                </FormControl>

                {/* Severity Filter */}
                <FormControl size="sm" className="min-w-[120px]">
                    <InputLabel>Severity</InputLabel>
                    <Select
                        value={severityFilter}
                        label="Severity"
                        onChange={(e) => setSeverityFilter(e.target.value as AuditEventSeverity | 'all')}
                    >
                        <MenuItem value="all">All</MenuItem>
                        <MenuItem value="info">Info</MenuItem>
                        <MenuItem value="warning">Warning</MenuItem>
                        <MenuItem value="error">Error</MenuItem>
                        <MenuItem value="critical">Critical</MenuItem>
                    </Select>
                </FormControl>

                <Box className="flex-1" />

                {/* Actions */}
                <Tooltip title="Refresh">
                    <IconButton onClick={onRefresh} disabled={isLoading}>
                        {isLoading ? <CircularProgress size={20} /> : <RefreshIcon />}
                    </IconButton>
                </Tooltip>

                <Button
                    variant="outlined"
                    startIcon={<DownloadIcon />}
                    endIcon={<MenuIcon />}
                    onClick={(e) => setExportAnchorEl(e.currentTarget)}
                    size="sm"
                >
                    Export
                </Button>
                <Menu
                    anchorEl={exportAnchorEl}
                    open={Boolean(exportAnchorEl)}
                    onClose={() => setExportAnchorEl(null)}
                >
                    <MenuItem onClick={() => handleExport('json')}>
                        <ListItemText>JSON</ListItemText>
                    </MenuItem>
                    <MenuItem onClick={() => handleExport('csv')}>
                        <ListItemText>CSV</ListItemText>
                    </MenuItem>
                    <MenuItem onClick={() => handleExport('pdf')}>
                        <ListItemText>PDF Report</ListItemText>
                    </MenuItem>
                </Menu>
            </Box>

            {/* Stats Bar */}
            <Box
                className="px-4 py-2 flex gap-4 bg-gray-100 dark:bg-gray-800 border-gray-200 dark:border-gray-700 border-b" >
                <Typography as="p" className="text-sm" color="text.secondary">
                    {filteredEvents.length} events
                </Typography>
                <Divider orientation="vertical" flexItem />
                {Object.entries(
                    filteredEvents.reduce((acc, e) => {
                        acc[e.severity] = (acc[e.severity] || 0) + 1;
                        return acc;
                    }, {} as Record<string, number>)
                ).map(([severity, count]) => (
                    <Chip
                        key={severity}
                        size="sm"
                        label={`${severity}: ${count}`}
                        color={SEVERITY_CONFIG[severity as AuditEventSeverity]?.color || 'default'}
                        variant="outlined"
                    />
                ))}
            </Box>

            {/* Timeline */}
            <Box className="flex-1 overflow-auto p-4">
                {isLoading && filteredEvents.length === 0 ? (
                    <Box className="flex justify-center py-8">
                        <CircularProgress />
                    </Box>
                ) : filteredEvents.length === 0 ? (
                    <Box className="text-center py-8">
                        <Typography color="text.secondary">No events found</Typography>
                    </Box>
                ) : (
                    Object.entries(groupedEvents).map(([date, dateEvents]) => (
                        <Box key={date} className="mb-6">
                            <Typography
                                as="span" className="text-xs uppercase tracking-wider"
                                color="text.secondary"
                                className="block mb-2"
                            >
                                {date}
                            </Typography>
                            <div className="relative pl-8">
                                {dateEvents.map((event, index) => (
                                    <div key={event.id} className="relative flex gap-4 pb-4">
                                        {/* Opposite content (time) */}
                                        <div className="absolute -left-8 w-20 flex-shrink-0 py-1.5 text-right">
                                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                {new Date(event.timestamp).toLocaleTimeString()}
                                            </Typography>
                                        </div>
                                        {/* Separator (dot + connector) */}
                                        <div className="flex flex-col items-center flex-shrink-0">
                                            <div
                                                className="w-3 h-3 rounded-full flex-shrink-0"
                                                style={{ backgroundColor: CATEGORY_COLORS[event.category] }}
                                            />
                                            {index < dateEvents.length - 1 && (
                                                <div className="w-0.5 flex-1 bg-gray-300 dark:bg-gray-600 mt-1" />
                                            )}
                                        </div>
                                        {/* Content */}
                                        <div className="flex-1 pb-1 -mt-1">
                                            <EventCard
                                                event={event}
                                                expanded={expandedEventId === event.id}
                                                onToggle={() =>
                                                    setExpandedEventId(
                                                        expandedEventId === event.id ? null : event.id
                                                    )
                                                }
                                            />
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </Box>
                    ))
                )}
            </Box>
        </Box>
    );
}

// ============================================================================
// Sub-components
// ============================================================================

function EventCard({ event, expanded = false, onToggle }: EventCardProps) {
    const severityConfig = SEVERITY_CONFIG[event.severity];

    return (
        <Card variant="outlined" className="bg-white dark:bg-gray-900">
            <CardContent className="p-3 last:pb-3">
                {/* Header */}
                <Box className="flex items-start gap-2">
                    <Chip
                        size="sm"
                        label={event.category}
                        className="text-[0.7rem] text-white" style={{ backgroundColor: CATEGORY_COLORS[event.category] }} />
                    <Chip
                        size="sm"
                        label={event.action}
                        variant="outlined"
                        className="text-[0.7rem]"
                    />
                    {event.severity !== 'info' && (
                        <Chip
                            size="sm"
                            icon={severityConfig.icon as React.ReactElement}
                            label={event.severity}
                            color={severityConfig.color}
                            variant="outlined"
                            className="text-[0.7rem]"
                        />
                    )}
                    <Box className="flex-1" />
                    <IconButton size="sm" onClick={onToggle}>
                        {expanded ? <CollapseIcon size={16} /> : <ExpandIcon size={16} />}
                    </IconButton>
                </Box>

                {/* Message */}
                <Typography as="p" className="text-sm" className="mt-2">
                    {event.message}
                </Typography>

                {/* Actor */}
                <Box className="flex items-center gap-1 mt-2">
                    {ACTOR_ICONS[event.actor.type]}
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        {event.actor.name || event.actor.id}
                    </Typography>
                </Box>

                {/* Expanded Details */}
                <Collapse in={expanded}>
                    <Divider className="my-2" />
                    <Box className="grid gap-2 grid-cols-2">
                        <Box>
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                Event ID
                            </Typography>
                            <Typography as="p" className="text-sm" className="text-xs font-mono">
                                {event.id}
                            </Typography>
                        </Box>
                        <Box>
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                Target
                            </Typography>
                            <Typography as="p" className="text-sm">
                                {event.target.name || event.target.id}
                            </Typography>
                        </Box>
                        {event.workflowId && (
                            <Box>
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    Workflow ID
                                </Typography>
                                <Typography as="p" className="text-sm" className="text-xs font-mono">
                                    {event.workflowId}
                                </Typography>
                            </Box>
                        )}
                        {event.taskId && (
                            <Box>
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    Task ID
                                </Typography>
                                <Typography as="p" className="text-sm" className="text-xs font-mono">
                                    {event.taskId}
                                </Typography>
                            </Box>
                        )}
                    </Box>
                    {event.metadata && Object.keys(event.metadata).length > 0 && (
                        <Box className="mt-2">
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                Metadata
                            </Typography>
                            <Box
                                component="pre"
                                className="p-2 rounded overflow-auto text-[0.7rem] bg-gray-100 dark:bg-gray-800 max-h-[100px]"
                            >
                                {JSON.stringify(event.metadata, null, 2)}
                            </Box>
                        </Box>
                    )}
                </Collapse>
            </CardContent>
        </Card>
    );
}

export default AuditTrail;
