/**
 * Enhanced Domain Selector with Persona Integration
 * 
 * Extends the original DomainSelector with:
 * - Persona-based filtering
 * - DevSecOps phase integration
 * - Enhanced UI with capabilities and phase information
 * - Improved navigation and context awareness
 */

import React, { useMemo } from 'react';
import { useAtom } from 'jotai';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Stack,
  Chip,
  Avatar,
  Badge,
  Tooltip,
  IconButton,
  Divider,
} from '@ghatana/ui';
import { Code as CodeIcon, Shield as SecurityIcon, Gauge as PerformanceIcon, ClipboardList as TaskIcon, LayoutDashboard as DashboardIcon, Info as InfoIcon } from 'lucide-react';

import { useTaskDomains } from '../../hooks/useConfig';
import { selectedDomainIdAtom } from '../../state/configAtoms';

// Domain icons mapping
const DOMAIN_ICONS: Record<string, React.ReactNode> = {
    'development': <CodeIcon />,
    'security': <SecurityIcon />,
    'operations': <PerformanceIcon />,
    'quality': <TaskIcon />,
    'product': <DashboardIcon />,
};

interface EnhancedDomainSelectorProps {
    className?: string;
    currentPersona?: string;
    showPersonaFilter?: boolean;
    onDomainSelect?: (domainId: string) => void;
}

export const EnhancedDomainSelector: React.FC<EnhancedDomainSelectorProps> = ({
    className = '',
    currentPersona,
    showPersonaFilter = true,
    onDomainSelect,
}) => {
    const domains = useTaskDomains();
    const [selectedId, setSelectedId] = useAtom(selectedDomainIdAtom);

    // Filter domains based on current persona
    const filteredDomains = useMemo(() => {
        if (!currentPersona || !showPersonaFilter) return domains;

        return domains.filter(domain =>
            domain.personas?.includes(currentPersona) || !domain.personas
        );
    }, [domains, currentPersona, showPersonaFilter]);

    const handleDomainSelect = (domainId: string) => {
        setSelectedId(domainId);
        onDomainSelect?.(domainId);
    };

    const getPersonaRelevanceScore = (domain: unknown) => {
        if (!currentPersona || !domain.personas) return 0;
        return domain.personas.includes(currentPersona) ? 2 : 1;
    };

    const sortedDomains = useMemo(() => {
        return [...filteredDomains].sort((a, b) => {
            const scoreA = getPersonaRelevanceScore(a);
            const scoreB = getPersonaRelevanceScore(b);
            return scoreB - scoreA;
        });
    }, [filteredDomains, currentPersona]);

    return (
        <Box className={className}>
            {/* Header */}
            <Box className="mb-6">
                <Typography variant="h6" fontWeight="600" gutterBottom>
                    Task Domains
                </Typography>
                <Typography variant="body2" color="text.secondary">
                    {currentPersona ?
                        `Domains relevant to ${currentPersona.replace('-', ' ')} persona` :
                        'All available task domains'
                    }
                </Typography>
            </Box>

            {/* Domain Cards */}
            <Grid container spacing={2}>
                {sortedDomains.map((domain) => {
                    const isSelected = selectedId === domain.id;
                    const isHighRelevance = getPersonaRelevanceScore(domain) === 2;

                    return (
                        <Grid item xs={12} sm={6} md={4} key={domain.id}>
                            <Card
                                className={`cursor-pointer transition-all duration-200 relative hover:-translate-y-0.5 hover:shadow-lg hover:border-blue-600 ${
                                    isSelected
                                        ? 'border-2 border-blue-600 bg-blue-50 dark:bg-blue-900/20'
                                        : 'border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900'
                                }`}
                                onClick={() => handleDomainSelect(domain.id)}
                            >
                                {/* High Relevance Badge */}
                                {isHighRelevance && (
                                    <Badge
                                        badgeContent="✓"
                                        color="success"
                                        className="[&_.MuiBadge-badge]:top-2 [&_.MuiBadge-badge]:right-2"
                                    >
                                        <Box className="w-full" />
                                    </Badge>
                                )}

                                <CardContent>
                                    {/* Domain Header */}
                                    <Stack direction="row" spacing={2} alignItems="center" mb={2}>
                                        <Avatar
                                            className="text-white"
                                            style={{ backgroundColor: domain.color || '#2196F3' }}
                                        >
                                            {DOMAIN_ICONS[domain.id] || <DashboardIcon />}
                                        </Avatar>
                                        <Box className="flex-1">
                                            <Typography
                                                variant="subtitle1"
                                                fontWeight="600"
                                                color={isSelected ? 'primary.main' : 'text.primary'}
                                            >
                                                {domain.name}
                                            </Typography>
                                            <Typography variant="body2" color="text.secondary">
                                                {domain.description}
                                            </Typography>
                                        </Box>
                                        <Tooltip title="Domain Information">
                                            <IconButton size="small" color="primary">
                                                <InfoIcon size={16} />
                                            </IconButton>
                                        </Tooltip>
                                    </Stack>

                                    <Divider className="mb-4" />

                                    {/* Personas */}
                                    {domain.personas && domain.personas.length > 0 && (
                                        <Box className="mb-4">
                                            <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                                                Personas:
                                            </Typography>
                                            <Stack direction="row" spacing={1} flexWrap="wrap" gap={0.5}>
                                                {domain.personas.slice(0, 3).map((persona: string) => (
                                                    <Chip
                                                        key={persona}
                                                        label={persona.replace('-', ' ')}
                                                        size="small"
                                                        variant={persona === currentPersona ? "filled" : "outlined"}
                                                        color={persona === currentPersona ? "primary" : "default"}
                                                        className="text-[0.7rem] h-[20px]"
                                                    />
                                                ))}
                                                {domain.personas.length > 3 && (
                                                    <Chip
                                                        label={`+${domain.personas.length - 3}`}
                                                        size="small"
                                                        variant="outlined"
                                                        className="text-[0.7rem] h-[20px]"
                                                    />
                                                )}
                                            </Stack>
                                        </Box>
                                    )}

                                    {/* Phases */}
                                    {domain.phases && domain.phases.length > 0 && (
                                        <Box className="mb-4">
                                            <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                                                Phases:
                                            </Typography>
                                            <Stack direction="row" spacing={1} flexWrap="wrap" gap={0.5}>
                                                {domain.phases.slice(0, 4).map((phase: string) => (
                                                    <Chip
                                                        key={phase}
                                                        label={phase}
                                                        size="small"
                                                        variant="outlined"
                                                        color="secondary"
                                                        className="text-[0.7rem] h-[20px]"
                                                    />
                                                ))}
                                                {domain.phases.length > 4 && (
                                                    <Chip
                                                        label={`+${domain.phases.length - 4}`}
                                                        size="small"
                                                        variant="outlined"
                                                        color="secondary"
                                                        className="text-[0.7rem] h-[20px]"
                                                    />
                                                )}
                                            </Stack>
                                        </Box>
                                    )}

                                    {/* Footer Stats */}
                                    <Box className="flex justify-between items-center pt-2 border-t border-solid border-gray-200 dark:border-gray-700">
                                        <Typography variant="caption" color="text.secondary">
                                            {domain.capabilities?.length || 0} capabilities
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            {domain.phases?.length || 0} phases
                                        </Typography>
                                    </Box>
                                </CardContent>
                            </Card>
                        </Grid>
                    );
                })}
            </Grid>

            {/* No domains message */}
            {sortedDomains.length === 0 && (
                <Box className="text-center py-8">
                    <Typography variant="h6" color="text.secondary" gutterBottom>
                        No domains available
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        {currentPersona ?
                            `No domains are configured for the ${currentPersona} persona.` :
                            'No domains are currently configured in the system.'
                        }
                    </Typography>
                </Box>
            )}

            {/* Legend */}
            {currentPersona && (
                <Box className="mt-6 p-4 rounded bg-gray-50 dark:bg-gray-800">
                    <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                        Legend:
                    </Typography>
                    <Stack direction="row" spacing={2} alignItems="center">
                        <Stack direction="row" spacing={1} alignItems="center">
                            <Badge badgeContent="✓" color="success" className="scale-[0.8]">
                                <Box className="w-[16px] h-[16px]" />
                            </Badge>
                            <Typography variant="caption">Primary relevance</Typography>
                        </Stack>
                        <Stack direction="row" spacing={1} alignItems="center">
                            <Chip
                                label={currentPersona}
                                size="small"
                                color="primary"
                                variant="filled"
                                className="text-[0.7rem] h-[20px]"
                            />
                            <Typography variant="caption">Your persona</Typography>
                        </Stack>
                    </Stack>
                </Box>
            )}
        </Box>
    );
};