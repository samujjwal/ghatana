/**
 * Container Orchestration Canvas Content
 * 
 * Container/K8s management for Deploy × Component level.
 * Visualize pods, services, deployments.
 * 
 * @doc.type component
 * @doc.purpose Container orchestration visualization
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  Surface as Paper,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

interface Container {
    id: string;
    name: string;
    type: 'pod' | 'service' | 'deployment' | 'statefulset';
    namespace: string;
    status: 'running' | 'pending' | 'error' | 'terminated';
    replicas?: number;
    ready?: number;
    restarts: number;
    age: string;
    image?: string;
    cpu: number;
    memory: number;
}

const MOCK_CONTAINERS: Container[] = [
    {
        id: '1',
        name: 'api-gateway',
        type: 'deployment',
        namespace: 'production',
        status: 'running',
        replicas: 3,
        ready: 3,
        restarts: 0,
        age: '2d',
        image: 'api-gateway:v1.2.3',
        cpu: 45,
        memory: 62,
    },
    {
        id: '2',
        name: 'auth-service',
        type: 'deployment',
        namespace: 'production',
        status: 'running',
        replicas: 2,
        ready: 2,
        restarts: 1,
        age: '5d',
        image: 'auth-service:v2.1.0',
        cpu: 28,
        memory: 48,
    },
    {
        id: '3',
        name: 'user-service-pod-7f8a',
        type: 'pod',
        namespace: 'production',
        status: 'running',
        restarts: 0,
        age: '12h',
        image: 'user-service:v1.5.2',
        cpu: 35,
        memory: 55,
    },
    {
        id: '4',
        name: 'postgres',
        type: 'statefulset',
        namespace: 'production',
        status: 'running',
        replicas: 1,
        ready: 1,
        restarts: 0,
        age: '15d',
        image: 'postgres:14-alpine',
        cpu: 18,
        memory: 72,
    },
    {
        id: '5',
        name: 'redis-cache',
        type: 'statefulset',
        namespace: 'production',
        status: 'running',
        replicas: 1,
        ready: 1,
        restarts: 2,
        age: '8d',
        image: 'redis:7-alpine',
        cpu: 12,
        memory: 38,
    },
    {
        id: '6',
        name: 'worker-queue',
        type: 'deployment',
        namespace: 'production',
        status: 'running',
        replicas: 5,
        ready: 4,
        restarts: 3,
        age: '3d',
        image: 'worker:v3.0.1',
        cpu: 58,
        memory: 68,
    },
    {
        id: '7',
        name: 'notification-service',
        type: 'deployment',
        namespace: 'production',
        status: 'error',
        replicas: 2,
        ready: 0,
        restarts: 15,
        age: '1h',
        image: 'notification:v1.0.0',
        cpu: 0,
        memory: 0,
    },
    {
        id: '8',
        name: 'metrics-collector',
        type: 'service',
        namespace: 'monitoring',
        status: 'running',
        restarts: 0,
        age: '20d',
        cpu: 8,
        memory: 22,
    },
];

const getStatusColor = (status: Container['status']) => {
    switch (status) {
        case 'running':
            return '#10B981';
        case 'pending':
            return '#F59E0B';
        case 'error':
            return '#EF4444';
        case 'terminated':
            return '#6B7280';
    }
};

const getTypeIcon = (type: Container['type']) => {
    switch (type) {
        case 'pod':
            return '📦';
        case 'service':
            return '🔗';
        case 'deployment':
            return '🚀';
        case 'statefulset':
            return '💾';
    }
};

export const ContainerOrchestrationCanvas = () => {
    const [containers] = useState<Container[]>(MOCK_CONTAINERS);
    const [selectedContainer, setSelectedContainer] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterNamespace, setFilterNamespace] = useState<string>('all');

    const namespaces = useMemo(() => {
        return Array.from(new Set(containers.map(c => c.namespace)));
    }, [containers]);

    const filteredContainers = useMemo(() => {
        return containers.filter(
            c =>
                (filterNamespace === 'all' || c.namespace === filterNamespace) &&
                (searchQuery === '' ||
                    c.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                    c.type.toLowerCase().includes(searchQuery.toLowerCase()))
        );
    }, [containers, searchQuery, filterNamespace]);

    const stats = useMemo(() => {
        return {
            total: containers.length,
            running: containers.filter(c => c.status === 'running').length,
            error: containers.filter(c => c.status === 'error').length,
            totalReplicas: containers.reduce((sum, c) => sum + (c.replicas || 0), 0),
            readyReplicas: containers.reduce((sum, c) => sum + (c.ready || 0), 0),
        };
    }, [containers]);

    const hasContent = containers.length > 0;

    const selectedContainerData = containers.find(c => c.id === selectedContainer);

    return (
        <BaseCanvasContent hasContent={hasContent}>
            <Box className="h-full flex flex-col bg-[#fafafa]">
                <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)] bg-white">
                    <Box className="flex gap-4 mb-2">
                        <TextField
                            size="small"
                            placeholder="Search containers..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="flex-1"
                        />
                    </Box>
                    <Box className="flex gap-2 flex-wrap">
                        <Chip
                            label="All"
                            size="small"
                            onClick={() => setFilterNamespace('all')}
                            className="cursor-pointer" style={{ backgroundColor: filterNamespace === 'all' ? '#3B82F6' : '#E5E7EB', color: filterNamespace === 'all' ? 'white' : 'black', alignItems: 'start' }}
                        />
                        {namespaces.map(ns => (
                            <Chip
                                key={ns}
                                label={ns}
                                size="small"
                                onClick={() => setFilterNamespace(ns)}
                                className="cursor-pointer" style={{ backgroundColor: filterNamespace === ns ? '#3B82F6' : '#E5E7EB', color: filterNamespace === ns ? 'white' : 'black', backgroundColor: getStatusColor(container.status) }}
                            />
                        ))}
                    </Box>
                </Box>

                <Box className="flex-1 flex gap-4 overflow-hidden p-4">
                    <Box className="overflow-y-auto transition-all duration-300" style={{ width: selectedContainerData ? '50%' : '100%' }}>
                        {filteredContainers.map(container => (
                            <Paper
                                key={container.id}
                                elevation={selectedContainer === container.id ? 4 : 2}
                                onClick={() => setSelectedContainer(container.id === selectedContainer ? null : container.id)}
                                className="p-3 mb-2 cursor-pointer" style={{ border: selectedContainer === container.id ? `3px solid ${getStatusColor(container.status)}` : '2px solid transparent' }}
                            >
                                <Box className="flex gap-2 mb-1">
                                    <Typography fontSize="1.2rem">{getTypeIcon(container.type)}</Typography>
                                    <Box className="flex-1">
                                        <Box className="flex items-center gap-2 mb-[2.4px]">
                                            <Typography variant="body2" className="text-[0.85rem] font-semibold">
                                                {container.name}
                                            </Typography>
                                            <Box
                                                className="rounded-full w-[10px] h-[10px]" />
                                        </Box>
                                        <Typography variant="caption" color="text.secondary" className="text-[0.7rem]">
                                            {container.type} • {container.namespace}
                                        </Typography>
                                        {container.replicas !== undefined && (
                                            <Typography variant="caption" display="block" className="text-[0.7rem] mt-[2.4px]">
                                                Replicas: {container.ready}/{container.replicas} • Restarts: {container.restarts}
                                            </Typography>
                                        )}
                                    </Box>
                                    <Chip label={container.age} size="small" className="h-[20px] text-[0.65rem]" />
                                </Box>
                            </Paper>
                        ))}
                    </Box>

                    {selectedContainerData && (
                        <Box className="overflow-y-auto w-[50%]">
                            <Paper elevation={3} className="p-4">
                                <Box className="flex items-center gap-2 mb-2">
                                    <Typography fontSize="1.5rem">{getTypeIcon(selectedContainerData.type)}</Typography>
                                    <Box className="flex-1">
                                        <Typography variant="subtitle2" className="font-semibold">
                                            {selectedContainerData.name}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            {selectedContainerData.type}
                                        </Typography>
                                    </Box>
                                    <Chip
                                        label={selectedContainerData.status}
                                        size="small"
                                        className="text-white" style={{ backgroundColor: getStatusColor(selectedContainerData.status) }}
                                    />
                                </Box>

                                <Box className="mt-4">
                                    <Typography variant="caption" className="font-semibold block mb-1">
                                        Details
                                    </Typography>
                                    <Paper className="p-2 bg-[#F8FAFC] mb-2">
                                        <Typography variant="caption" display="block">
                                            Namespace: {selectedContainerData.namespace}
                                        </Typography>
                                        <Typography variant="caption" display="block">
                                            Age: {selectedContainerData.age}
                                        </Typography>
                                        {selectedContainerData.image && (
                                            <Typography variant="caption" display="block" className="font-mono">
                                                Image: {selectedContainerData.image}
                                            </Typography>
                                        )}
                                        <Typography variant="caption" display="block">
                                            Restarts: {selectedContainerData.restarts}
                                        </Typography>
                                        {selectedContainerData.replicas !== undefined && (
                                            <Typography variant="caption" display="block">
                                                Replicas: {selectedContainerData.ready}/{selectedContainerData.replicas}
                                            </Typography>
                                        )}
                                    </Paper>

                                    <Typography variant="caption" className="font-semibold block mb-1 mt-2">
                                        Resource Usage
                                    </Typography>
                                    <Box className="grid gap-2" style={{ gridTemplateColumns: 'repeat(2 }} >
                                        <Paper className="p-2 bg-[#F8FAFC]">
                                            <Typography variant="caption" color="text.secondary" display="block">
                                                CPU
                                            </Typography>
                                            <Typography variant="body2" className="font-semibold">
                                                {selectedContainerData.cpu}%
                                            </Typography>
                                        </Paper>
                                        <Paper className="p-2 bg-[#F8FAFC]">
                                            <Typography variant="caption" color="text.secondary" display="block">
                                                Memory
                                            </Typography>
                                            <Typography variant="body2" className="font-semibold">
                                                {selectedContainerData.memory}%
                                            </Typography>
                                        </Paper>
                                    </Box>
                                </Box>
                            </Paper>
                        </Box>
                    )}
                </Box>

                <Box
                    className="absolute rounded top-[80px] right-[16px] bg-white p-4 shadow"
                >
                    <Typography variant="subtitle2" className="font-semibold mb-2">
                        Cluster Stats
                    </Typography>
                    <Typography variant="caption" display="block">
                        Total: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('running') }}>
                        Running: {stats.running}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('error') }}>
                        Error: {stats.error}
                    </Typography>
                    <Typography variant="caption" display="block" className="mt-2">
                        Replicas: {stats.readyReplicas}/{stats.totalReplicas}
                    </Typography>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default ContainerOrchestrationCanvas;
