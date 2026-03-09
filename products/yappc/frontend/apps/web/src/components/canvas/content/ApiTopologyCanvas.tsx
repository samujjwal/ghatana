/**
 * API Topology Canvas Content
 * 
 * API endpoint topology for Code × Component level.
 * Visualizes REST/GraphQL endpoints and their relationships.
 * 
 * @doc.type component
 * @doc.purpose API endpoint topology visualization
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

interface APIEndpoint {
    id: string;
    path: string;
    method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
    service: string;
    auth: boolean;
    rateLimit?: number;
    avgLatency: number;
    requests24h: number;
    errorRate: number;
    position: { x: number; y: number };
}

// Mock API data
const MOCK_ENDPOINTS: APIEndpoint[] = [
    {
        id: 'login',
        path: '/auth/login',
        method: 'POST',
        service: 'Auth',
        auth: false,
        rateLimit: 10,
        avgLatency: 120,
        requests24h: 15420,
        errorRate: 2.1,
        position: { x: 20, y: 20 },
    },
    {
        id: 'register',
        path: '/auth/register',
        method: 'POST',
        service: 'Auth',
        auth: false,
        rateLimit: 5,
        avgLatency: 180,
        requests24h: 3240,
        errorRate: 4.5,
        position: { x: 20, y: 35 },
    },
    {
        id: 'me',
        path: '/users/me',
        method: 'GET',
        service: 'User',
        auth: true,
        avgLatency: 45,
        requests24h: 89200,
        errorRate: 0.3,
        position: { x: 40, y: 20 },
    },
    {
        id: 'update-profile',
        path: '/users/me',
        method: 'PUT',
        service: 'User',
        auth: true,
        avgLatency: 95,
        requests24h: 12400,
        errorRate: 1.2,
        position: { x: 40, y: 35 },
    },
    {
        id: 'list-orders',
        path: '/orders',
        method: 'GET',
        service: 'Order',
        auth: true,
        avgLatency: 220,
        requests24h: 45600,
        errorRate: 1.8,
        position: { x: 60, y: 20 },
    },
    {
        id: 'create-order',
        path: '/orders',
        method: 'POST',
        service: 'Order',
        auth: true,
        rateLimit: 20,
        avgLatency: 340,
        requests24h: 8900,
        errorRate: 3.2,
        position: { x: 60, y: 35 },
    },
    {
        id: 'get-order',
        path: '/orders/:id',
        method: 'GET',
        service: 'Order',
        auth: true,
        avgLatency: 85,
        requests24h: 67800,
        errorRate: 0.9,
        position: { x: 60, y: 50 },
    },
    {
        id: 'list-products',
        path: '/products',
        method: 'GET',
        service: 'Product',
        auth: false,
        avgLatency: 180,
        requests24h: 156000,
        errorRate: 0.5,
        position: { x: 80, y: 20 },
    },
    {
        id: 'get-product',
        path: '/products/:id',
        method: 'GET',
        service: 'Product',
        auth: false,
        avgLatency: 65,
        requests24h: 234000,
        errorRate: 0.4,
        position: { x: 80, y: 35 },
    },
    {
        id: 'health',
        path: '/health',
        method: 'GET',
        service: 'System',
        auth: false,
        avgLatency: 12,
        requests24h: 864000,
        errorRate: 0.01,
        position: { x: 50, y: 65 },
    },
];

const getMethodColor = (method: APIEndpoint['method']) => {
    switch (method) {
        case 'GET':
            return '#4CAF50';
        case 'POST':
            return '#2196F3';
        case 'PUT':
            return '#FF9800';
        case 'PATCH':
            return '#9C27B0';
        case 'DELETE':
            return '#F44336';
    }
};

const getHealthColor = (errorRate: number) => {
    if (errorRate < 1) return '#4CAF50';
    if (errorRate < 5) return '#FF9800';
    return '#F44336';
};

const EndpointCard = ({
    endpoint,
    onClick,
    isSelected,
}: {
    endpoint: APIEndpoint;
    onClick: (id: string) => void;
    isSelected: boolean;
}) => {
    const methodColor = getMethodColor(endpoint.method);
    const healthColor = getHealthColor(endpoint.errorRate);

    return (
        <Paper
            elevation={isSelected ? 8 : 3}
            onClick={() => onClick(endpoint.id)}
            className="absolute" style={{ left: `${endpoint.position.x, backgroundColor: 'healthColor', backgroundColor: 'methodColor' }}
        >
            <Box className="flex items-center gap-2 mb-1">
                <Chip
                    label={endpoint.method}
                    size="small"
                    className="font-semibold text-white text-backgroundColor: methodColor */
                />
                <Box
                    className="rounded-full w-[10px] h-[10px]" />
            </Box>

            <Typography
                variant="body2"
                className="text-[0.8rem] font-medium font-mono mb-1"
            >
                {endpoint.path}
            </Typography>

            <Box className="flex gap-1 flex-wrap mb-1">
                <Chip label={endpoint.service} size="small" variant="outlined" className="h-[18px] text-[0.65rem]" />
                {endpoint.auth && (
                    <Chip label="🔒 Auth" size="small" color="warning" className="h-[18px] text-[0.65rem]" />
                )}
                {endpoint.rateLimit && (
                    <Chip
                        label={`${endpoint.rateLimit}/min`}
                        size="small"
                        variant="outlined"
                        className="h-[18px] text-[0.65rem]"
                    />
                )}
            </Box>

            <Box className="flex gap-3 mt-1">
                <Typography variant="caption" color="text.secondary" className="text-[0.65rem]">
                    {endpoint.avgLatency}ms
                </Typography>
                <Typography variant="caption" color="text.secondary" className="text-[0.65rem]">
                    {(endpoint.requests24h / 1000).toFixed(1)}K req
                </Typography>
                <Typography variant="caption" className="text-[0.65rem]" style={{ color: healthColor }}>
                    {endpoint.errorRate.toFixed(1)}% err
                </Typography>
            </Box>
        </Paper>
    );
};

export const ApiTopologyCanvas = () => {
    const [endpoints] = useState<APIEndpoint[]>(MOCK_ENDPOINTS);
    const [selectedEndpoint, setSelectedEndpoint] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterMethod, setFilterMethod] = useState<APIEndpoint['method'] | 'all'>('all');
    const [filterService, setFilterService] = useState<string | 'all'>('all');

    const filteredEndpoints = useMemo(() => {
        return endpoints.filter(ep => {
            const matchesSearch =
                searchQuery === '' || ep.path.toLowerCase().includes(searchQuery.toLowerCase());
            const matchesMethod = filterMethod === 'all' || ep.method === filterMethod;
            const matchesService = filterService === 'all' || ep.service === filterService;
            return matchesSearch && matchesMethod && matchesService;
        });
    }, [endpoints, searchQuery, filterMethod, filterService]);

    const services = useMemo(() => {
        return Array.from(new Set(endpoints.map(ep => ep.service)));
    }, [endpoints]);

    const stats = useMemo(() => {
        return {
            total: endpoints.length,
            avgLatency: Math.round(endpoints.reduce((acc, ep) => acc + ep.avgLatency, 0) / endpoints.length),
            totalRequests: endpoints.reduce((acc, ep) => acc + ep.requests24h, 0),
            avgErrorRate: (endpoints.reduce((acc, ep) => acc + ep.errorRate, 0) / endpoints.length).toFixed(2),
            withAuth: endpoints.filter(ep => ep.auth).length,
            withRateLimit: endpoints.filter(ep => ep.rateLimit).length,
        };
    }, [endpoints]);

    const hasContent = endpoints.length > 0;

    const handleEndpointClick = (id: string) => {
        setSelectedEndpoint(id === selectedEndpoint ? null : id);
    };

    const selectedEp = endpoints.find(ep => ep.id === selectedEndpoint);

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Scan APIs',
                    onClick: () => {
                        console.log('Scan APIs');
                    },
                },
                secondaryAction: {
                    label: 'Import OpenAPI',
                    onClick: () => {
                        console.log('Import OpenAPI');
                    },
                },
            }}
        >
            <Box
                className="relative h-full w-full flex flex-col bg-[#fafafa]"
            >
                {/* Top toolbar */}
                <Box
                    className="z-[10] p-4 bg-white" style={{ borderBottom: '1px solid rgba(0 }} >
                    <Box className="flex gap-4 items-center mb-2">
                        <TextField
                            size="small"
                            placeholder="Search endpoints..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="flex-1"
                        />
                    </Box>

                    <Box className="flex gap-2 flex-wrap">
                        <Box className="flex gap-1">
                            <Chip
                                label="All"
                                size="small"
                                onClick={() => setFilterMethod('all')}
                                color={filterMethod === 'all' ? 'primary' : 'default'}
                            />
                            {(['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] as const).map(method => (
                                <Chip
                                    key={method}
                                    label={method}
                                    size="small"
                                    onClick={() => setFilterMethod(method)}
                                    style={{ backgroundColor: filterMethod === method ? getMethodColor(method) : undefined, color: filterMethod === method ? 'white' : undefined, backgroundImage: '`
              linear-gradient(rgba(0, backgroundSize: '20px 20px' }}
                                />
                            ))}
                        </Box>

                        <Box className="flex gap-1 ml-4">
                            <Chip
                                label="All Services"
                                size="small"
                                onClick={() => setFilterService('all')}
                                color={filterService === 'all' ? 'primary' : 'default'}
                            />
                            {services.map(service => (
                                <Chip
                                    key={service}
                                    label={service}
                                    size="small"
                                    onClick={() => setFilterService(service)}
                                    color={filterService === service ? 'primary' : 'default'}
                                    variant={filterService === service ? 'filled' : 'outlined'}
                                />
                            ))}
                        </Box>
                    </Box>
                </Box>

                {/* Canvas area */}
                <Box
                    className="flex-1 relative overflow-hidden" >
                    {filteredEndpoints.map(endpoint => (
                        <EndpointCard
                            key={endpoint.id}
                            endpoint={endpoint}
                            onClick={handleEndpointClick}
                            isSelected={endpoint.id === selectedEndpoint}
                        />
                    ))}

                    {filteredEndpoints.length === 0 && (
                        <Box className="flex justify-center items-center h-full">
                            <Typography color="text.secondary">
                                No endpoints match your filters
                            </Typography>
                        </Box>
                    )}
                </Box>

                {/* Stats panel */}
                <Box
                    className="absolute rounded bottom-[16px] left-[16px] bg-white p-4 shadow min-w-[200px]"
                >
                    <Typography variant="subtitle2" gutterBottom className="font-semibold">
                        API Statistics
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Total Endpoints: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Avg Latency: {stats.avgLatency}ms
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        24h Requests: {(stats.totalRequests / 1000000).toFixed(2)}M
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Avg Error Rate: {stats.avgErrorRate}%
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Protected: {stats.withAuth}/{stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Rate Limited: {stats.withRateLimit}
                    </Typography>
                </Box>

                {/* Endpoint details */}
                {selectedEp && (
                    <Box
                        className="absolute rounded top-[80px] right-[16px] bg-white p-4 shadow-lg min-w-[280px] max-w-[350px]"
                    >
                        <Box className="flex items-center gap-2 mb-2">
                            <Chip
                                label={selectedEp.method}
                                size="small"
                                className="font-semibold text-white" style={{ backgroundColor: getMethodColor(selectedEp.method) }} />
                            <Typography variant="subtitle2" className="font-semibold">
                                {selectedEp.path}
                            </Typography>
                        </Box>

                        <Typography variant="body2" className="mb-2">
                            Service: <strong>{selectedEp.service}</strong>
                        </Typography>

                        <Box className="flex flex-wrap gap-1 mb-2">
                            {selectedEp.auth && <Chip label="Requires Auth" size="small" color="warning" />}
                            {selectedEp.rateLimit && (
                                <Chip label={`Rate: ${selectedEp.rateLimit}/min`} size="small" variant="outlined" />
                            )}
                        </Box>

                        <Typography variant="caption" display="block" color="text.secondary">
                            Avg Latency: {selectedEp.avgLatency}ms
                        </Typography>
                        <Typography variant="caption" display="block" color="text.secondary">
                            Requests (24h): {selectedEp.requests24h.toLocaleString()}
                        </Typography>
                        <Typography
                            variant="caption"
                            display="block"
                            style={{ color: getHealthColor(selectedEp.errorRate) }}
                        >
                            Error Rate: {selectedEp.errorRate.toFixed(2)}%
                        </Typography>
                    </Box>
                )}
            </Box>
        </BaseCanvasContent>
    );
};

export default ApiTopologyCanvas;
