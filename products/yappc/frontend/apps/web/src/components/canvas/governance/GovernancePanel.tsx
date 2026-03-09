/**
 * Governance & Ops Panel
 * 
 * Manages project members, roles, and deployment operations.
 * 
 * @doc.type component
 * @doc.purpose Governance and operations management
 * @doc.layer product
 * @doc.pattern Composite Panel
 */

import React, { useState } from 'react';
import { Box, Typography, InteractiveList as List, ListItem, ListItemIcon as ListItemAvatar, ListItemText, Avatar, Chip, Button, IconButton, Stack, Divider, Surface as Paper, LinearProgress } from '@ghatana/ui';
import { Users as Group, Shield as Security, Rocket as RocketLaunch, MoreVertical as MoreVert, Plus as Add, Globe as Public, CloudCheck as CloudDone, AlertCircle as ErrorOutline, Download as FileDownload, FileText as Description } from 'lucide-react';

export interface ProjectMember {
    id: string;
    name: string;
    avatar?: string;
    role: 'Admin' | 'Architect' | 'Developer' | 'Reviewer';
    status: 'online' | 'offline';
}

export interface DeploymentStatus {
    environment: string;
    status: 'deployed' | 'failed' | 'in-progress' | 'pending';
    version: string;
    lastUpdated: Date;
}

/**
 * GovernancePanel Component
 */
export const GovernancePanel: React.FC = () => {
    const [isExporting, setIsExporting] = useState(false);
    const [members] = useState<ProjectMember[]>([
        { id: '1', name: 'Alice Chen', role: 'Architect', status: 'online' },
        { id: '2', name: 'Bob Smith', role: 'Developer', status: 'online' },
        { id: '3', name: 'Charlie Dave', role: 'Admin', status: 'offline' },
    ]);

    const [deployments] = useState<DeploymentStatus[]>([
        { environment: 'Staging', status: 'deployed', version: 'v1.4.2', lastUpdated: new Date() },
        { environment: 'Production', status: 'pending', version: 'v1.4.1', lastUpdated: new Date(Date.now() - 86400000) },
    ]);

    const handleExport = (format: 'markdown' | 'yaml' | 'pdf') => {
        setIsExporting(true);
        // Simulate export process
        setTimeout(() => {
            setIsExporting(false);
            console.log(`Exported project in ${format} format`);
            // In a real app, this would trigger a file download
            alert(`Project specification exported as ${format.toUpperCase()}. Check your downloads.`);
        }, 1500);
    };

    return (
        <Box className="py-2">
            {/* Team Section */}
            <Box className="mb-8">
                <Box className="flex justify-between items-center mb-4">
                    <Typography as="span" className="text-xs uppercase tracking-wider" color="text.secondary" fontWeight="800">
                        Project Team
                    </Typography>
                    <Button size="sm" startIcon={<Add />} className="rounded-lg">
                        Invite
                    </Button>
                </Box>

                <Paper variant="outlined" className="rounded-lg overflow-hidden">
                    <List disablePadding>
                        {members.map((member, idx) => (
                            <React.Fragment key={member.id}>
                                <ListItem
                                    secondaryAction={
                                        <IconButton size="sm"><MoreVert /></IconButton>
                                    }
                                    className="py-3"
                                >
                                    <ListItemAvatar>
                                        <Avatar className={`w-[32px] h-[32px] text-[0.8rem] ${member.status === 'online' ? 'bg-green-500' : 'bg-gray-400'}`}>
                                            {member.name.charAt(0)}
                                        </Avatar>
                                    </ListItemAvatar>
                                    <ListItemText
                                        primary={
                                            <Typography as="p" className="text-sm" fontWeight="700">
                                                {member.name}
                                            </Typography>
                                        }
                                        secondary={
                                            <Chip
                                                label={member.role}
                                                size="sm"
                                                variant="outlined"
                                                className="mt-1 h-[18px] text-[0.65rem]"
                                            />
                                        }
                                    />
                                </ListItem>
                                {idx < members.length - 1 && <Divider />}
                            </React.Fragment>
                        ))}
                    </List>
                </Paper>
            </Box>

            {/* Export Section */}
            <Box className="mb-8">
                <Typography as="span" className="text-xs uppercase tracking-wider" color="text.secondary" fontWeight="800" className="mb-4 block">
                    Project Handoff
                </Typography>
                <Paper variant="outlined" className="p-4 rounded-lg">
                    <Typography as="p" className="text-sm" color="text.secondary" className="mb-4 text-[0.8125rem]">
                        Export your project's technical specification for implementation or review.
                    </Typography>
                    <Stack direction="row" spacing={1}>
                        <Button
                            variant="outlined"
                            size="sm"
                            startIcon={<Description />}
                            onClick={() => handleExport('markdown')}
                            disabled={isExporting}
                            className="flex-1 text-xs rounded-md"
                        >
                            Markdown
                        </Button>
                        <Button
                            variant="outlined"
                            size="sm"
                            startIcon={<Public />}
                            onClick={() => handleExport('yaml')}
                            disabled={isExporting}
                            className="flex-1 text-xs rounded-md"
                        >
                            YAML
                        </Button>
                    </Stack>
                    {isExporting && (
                        <Box className="mt-4">
                            <LinearProgress className="rounded h-[2px]" />
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-2 block text-center">
                                Generating specification documents...
                            </Typography>
                        </Box>
                    )}
                </Paper>
            </Box>

            {/* Operations Section */}
            <Box>
                <Typography as="span" className="text-xs uppercase tracking-wider" color="text.secondary" fontWeight="800" className="mb-4 block">
                    Operations & Deployment
                </Typography>

                <Stack spacing={2}>
                    {deployments.map((deploy) => (
                        <Paper
                            key={deploy.environment}
                            variant="outlined"
                            className="p-4 rounded-lg" style={{ borderLeftColor: deploy.status === 'deployed' ? 'success.main' : 'warning.main', borderLeft: '4px solidpx solid' }} >
                            <Box className="flex justify-between mb-2">
                                <Typography as="p" className="text-sm font-medium" fontWeight="700">
                                    {deploy.environment}
                                </Typography>
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    {deploy.version}
                                </Typography>
                            </Box>

                            <Box className="flex items-center gap-2 mb-3">
                                {deploy.status === 'deployed' ? (
                                    <CloudDone tone="success" className="text-base" />
                                ) : (
                                    <ErrorOutline tone="warning" className="text-base" />
                                )}
                                <Typography as="span" className="text-xs text-gray-500" className="capitalize">
                                    {deploy.status.replace('-', ' ')}
                                </Typography>
                            </Box>

                            <Button
                                fullWidth
                                variant="solid"
                                size="sm"
                                startIcon={<RocketLaunch />}
                                disabled={deploy.status === 'in-progress'}
                                className="py-1 text-xs rounded-md"
                            >
                                Deploy to {deploy.environment}
                            </Button>
                        </Paper>
                    ))}
                </Stack>
            </Box>
        </Box>
    );
};
