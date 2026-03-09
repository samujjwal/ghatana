/**
 * Context Drawer Component
 * 
 * Right drawer with AI, artifacts, and audit context.
 * Collapsible and tabbed interface.
 * 
 * @doc.type component
 * @doc.purpose Context panel drawer
 * @doc.layer product
 * @doc.pattern Drawer Component
 */

import { useState } from 'react';
import { useParams } from 'react-router';
import { Drawer, Box, IconButton, Tooltip, Typography, Chip, Spinner as CircularProgress, Alert } from '@ghatana/ui';
import { ChevronRight as ChevronRightIcon, ChevronLeft as ChevronLeftIcon, Sparkles as AIIcon, Folder as FolderIcon, Shield as SecurityIcon } from 'lucide-react';
import { TabNavigation } from '@ghatana/yappc-ui';
import type { TabNavigationItem } from '@ghatana/yappc-ui';
import { useEvidence, useAIInsights, useAuditEvents } from '@/hooks/useLifecycleData';
import { FOWStage } from '@/types/fow-stages';
import { LifecyclePhase } from '@/types/lifecycle';

// ============================================================================
// Types
// ============================================================================

interface ContextDrawerProps {
    open?: boolean;
    onToggle?: () => void;
    fowStage: FOWStage;
    phase: LifecyclePhase;
}

// ============================================================================
// AI Assistant Tab
// ============================================================================

function AIAssistantTab({ projectId }: { projectId: string }) {
    const { data: insights, isLoading, error } = useAIInsights(projectId);

    if (isLoading) {
        return (
            <Box className="flex justify-center p-8">
                <CircularProgress />
            </Box>
        );
    }

    if (error) {
        return (
            <Alert severity="error" className="m-4">
                Failed to load AI insights
            </Alert>
        );
    }

    if (!insights || insights.length === 0) {
        return (
            <Box className="p-6 text-center">
                <AIIcon className="mb-4 text-5xl text-gray-400 dark:text-gray-600" />
                <Typography as="p" className="text-sm" color="text.secondary">
                    No AI insights available yet
                </Typography>
            </Box>
        );
    }

    return (
        <Box className="p-4">
            {insights.map((insight) => (
                <Box
                    key={insight.id}
                    className="mb-4 p-4 rounded border-blue-600 bg-blue-50 border-l-[3px]" >
                    <Typography as="p" className="text-sm font-medium" fontWeight={600} gutterBottom>
                        {insight.title}
                    </Typography>
                    {insight.description && (
                        <Typography as="p" className="text-sm" color="text.secondary" className="mb-2">
                            {insight.description}
                        </Typography>
                    )}
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        {new Date(insight.timestamp).toLocaleString()}
                    </Typography>
                </Box>
            ))}
        </Box>
    );
}

// ============================================================================
// Artifacts Tab
// ============================================================================

function ArtifactsTab({ projectId }: { projectId: string }) {
    const { data: evidence, isLoading, error } = useEvidence(projectId);

    if (isLoading) {
        return (
            <Box className="flex justify-center p-8">
                <CircularProgress />
            </Box>
        );
    }

    if (error) {
        return (
            <Alert severity="error" className="m-4">
                Failed to load artifacts
            </Alert>
        );
    }

    const artifacts = evidence?.filter(e => e.type === 'artifact') || [];

    if (artifacts.length === 0) {
        return (
            <Box className="p-6 text-center">
                <FolderIcon className="mb-4 text-5xl text-gray-400 dark:text-gray-600" />
                <Typography as="p" className="text-sm" color="text.secondary">
                    No artifacts yet
                </Typography>
            </Box>
        );
    }

    return (
        <Box className="p-4">
            {artifacts.map((artifact) => (
                <Box
                    key={artifact.id}
                    className="mb-4 p-4 rounded border border-gray-200 dark:border-gray-700 hover:bg-gray-100 hover:dark:bg-gray-800 hover:cursor-pointer"
                >
                    <Box className="flex justify-between mb-2" style={{ alignItems: 'start' }} >
                        <Typography as="p" className="text-sm font-medium" fontWeight={600}>
                            {artifact.title}
                        </Typography>
                        {artifact.status && (
                            <Chip
                                label={artifact.status}
                                size="sm"
                                color={
                                    artifact.status === 'approved' ? 'success' :
                                        artifact.status === 'review' ? 'warning' :
                                            'default'
                                }
                            />
                        )}
                    </Box>
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        {new Date(artifact.timestamp).toLocaleString()}
                    </Typography>
                </Box>
            ))}
        </Box>
    );
}

// ============================================================================
// Audit Tab
// ============================================================================

function AuditTab({ projectId, fowStage, phase }: { projectId: string; fowStage: FOWStage; phase: LifecyclePhase }) {
    const { data: auditEvents, isLoading, error } = useAuditEvents(projectId, { fowStage, phase });

    if (isLoading) {
        return (
            <Box className="flex justify-center p-8">
                <CircularProgress />
            </Box>
        );
    }

    if (error) {
        return (
            <Alert severity="error" className="m-4">
                Failed to load audit events
            </Alert>
        );
    }

    if (!auditEvents || auditEvents.length === 0) {
        return (
            <Box className="p-6 text-center">
                <SecurityIcon className="mb-4 text-5xl text-gray-400 dark:text-gray-600" />
                <Typography as="p" className="text-sm" color="text.secondary">
                    No audit events yet
                </Typography>
            </Box>
        );
    }

    return (
        <Box className="p-4">
            {auditEvents.map((event) => (
                <Box
                    key={event.id}
                    className="mb-3 pb-3 border-gray-200 dark:border-gray-700 border-b" >
                    <Box className="flex items-center mb-1">
                        <Chip label={event.type} size="sm" className="mr-2" />
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            {new Date(event.timestamp).toLocaleString()}
                        </Typography>
                    </Box>
                    <Typography as="p" className="text-sm">{event.description}</Typography>
                </Box>
            ))}
        </Box>
    );
}

// ============================================================================
// Main Context Drawer Component
// ============================================================================

export function ContextDrawer({ open = true, onToggle, fowStage, phase }: ContextDrawerProps) {
    const { projectId } = useParams();
    const [activeTab, setActiveTab] = useState('ai');

    if (!projectId) return null;

    const tabs: TabNavigationItem[] = [
        { id: 'ai', label: 'AI Assistant', path: '#', icon: <AIIcon /> },
        { id: 'artifacts', label: 'Artifacts', path: '#', icon: <FolderIcon /> },
        { id: 'audit', label: 'Audit', path: '#', icon: <SecurityIcon /> },
    ];

    const renderTabContent = () => {
        switch (activeTab) {
            case 'ai':
                return <AIAssistantTab projectId={projectId} />;
            case 'artifacts':
                return <ArtifactsTab projectId={projectId} />;
            case 'audit':
                return <AuditTab projectId={projectId} fowStage={fowStage} phase={phase} />;
            default:
                return null;
        }
    };

    return (
        <Drawer
            anchor="right"
            variant="persistent"
            open={open}
            className="shrink-0" style={{ width: open ? 320 : 0 }}
        >
            <Box className="flex flex-col h-full">
                {/* Header with close button */}
                <Box
                    className="flex items-center justify-between p-4 border-gray-200 dark:border-gray-700 border-b" >
                    <Typography as="h6" fontWeight={600}>
                        Context
                    </Typography>
                    <Tooltip title={open ? 'Close panel' : 'Open panel'}>
                        <IconButton onClick={onToggle} size="sm">
                            {open ? <ChevronRightIcon /> : <ChevronLeftIcon />}
                        </IconButton>
                    </Tooltip>
                </Box>

                {/* Tab Navigation */}
                <Box className="border-gray-200 dark:border-gray-700 border-b" >
                    <TabNavigation
                        items={tabs}
                        activeTab={activeTab}
                        onTabChange={setActiveTab}
                    />
                </Box>

                {/* Tab Content */}
                <Box className="grow overflow-auto">
                    {renderTabContent()}
                </Box>
            </Box>
        </Drawer>
    );
}
