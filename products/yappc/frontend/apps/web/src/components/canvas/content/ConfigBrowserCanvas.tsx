/**
 * Config Browser Canvas Content
 * 
 * Configuration file browser for Deploy × File level.
 * Displays environment configs, build settings, and deployment configurations.
 * 
 * @doc.type component
 * @doc.purpose Configuration file browser for deployment settings
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import { Box, Typography, Chip } from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { Accordion, AccordionSummary, AccordionDetails } from '@ghatana/yappc-ui';
import { Settings, Search, ChevronDown as ExpandMore, Cloud, Hammer as Build, Shield as Security, Braces as DataObject } from 'lucide-react';

interface ConfigFile {
    name: string;
    path: string;
    category: 'environment' | 'build' | 'deployment' | 'security';
    environment?: string;
    size: string;
    lastModified: string;
    variables: ConfigVariable[];
}

interface ConfigVariable {
    key: string;
    value: string;
    description?: string;
    sensitive?: boolean;
}

// Mock configuration data
const MOCK_CONFIG_FILES: ConfigFile[] = [
    {
        name: '.env.development',
        path: '/.env.development',
        category: 'environment',
        environment: 'development',
        size: '2.1 KB',
        lastModified: '2 hours ago',
        variables: [
            { key: 'NODE_ENV', value: 'development', description: 'Node environment' },
            { key: 'API_URL', value: 'http://localhost:3000', description: 'API endpoint' },
            { key: 'DATABASE_URL', value: 'postgresql://localhost:5432/dev', description: 'Database connection', sensitive: true },
            { key: 'LOG_LEVEL', value: 'debug', description: 'Logging verbosity' },
        ],
    },
    {
        name: '.env.production',
        path: '/.env.production',
        category: 'environment',
        environment: 'production',
        size: '1.8 KB',
        lastModified: '1 day ago',
        variables: [
            { key: 'NODE_ENV', value: 'production', description: 'Node environment' },
            { key: 'API_URL', value: 'https://api.example.com', description: 'API endpoint' },
            { key: 'DATABASE_URL', value: '***REDACTED***', description: 'Database connection', sensitive: true },
            { key: 'LOG_LEVEL', value: 'info', description: 'Logging verbosity' },
        ],
    },
    {
        name: 'docker-compose.yml',
        path: '/docker-compose.yml',
        category: 'deployment',
        size: '3.5 KB',
        lastModified: '3 days ago',
        variables: [
            { key: 'services', value: 'web, db, redis', description: 'Docker services' },
            { key: 'networks', value: 'backend', description: 'Network configuration' },
            { key: 'volumes', value: 'postgres-data', description: 'Persistent volumes' },
        ],
    },
    {
        name: 'tsconfig.json',
        path: '/tsconfig.json',
        category: 'build',
        size: '892 B',
        lastModified: '5 days ago',
        variables: [
            { key: 'target', value: 'ES2020', description: 'ECMAScript target' },
            { key: 'module', value: 'ESNext', description: 'Module system' },
            { key: 'strict', value: 'true', description: 'Strict type checking' },
            { key: 'esModuleInterop', value: 'true', description: 'Module interoperability' },
        ],
    },
    {
        name: 'vite.config.ts',
        path: '/vite.config.ts',
        category: 'build',
        size: '1.2 KB',
        lastModified: '1 week ago',
        variables: [
            { key: 'plugins', value: 'react(), tsconfigPaths()', description: 'Vite plugins' },
            { key: 'server.port', value: '3000', description: 'Dev server port' },
            { key: 'build.outDir', value: 'dist', description: 'Build output directory' },
        ],
    },
    {
        name: 'security-policy.json',
        path: '/security-policy.json',
        category: 'security',
        size: '2.8 KB',
        lastModified: '2 weeks ago',
        variables: [
            { key: 'cors.allowedOrigins', value: '*.example.com', description: 'CORS allowed origins' },
            { key: 'rateLimit.maxRequests', value: '100', description: 'Rate limit per minute' },
            { key: 'jwt.expiresIn', value: '24h', description: 'JWT expiration time' },
        ],
    },
];

const getCategoryIcon = (category: ConfigFile['category']) => {
    switch (category) {
        case 'environment':
            return <DataObject size={16} />;
        case 'build':
            return <Build size={16} />;
        case 'deployment':
            return <Cloud size={16} />;
        case 'security':
            return <Security size={16} />;
    }
};

const getCategoryColor = (category: ConfigFile['category']) => {
    switch (category) {
        case 'environment':
            return '#4CAF50';
        case 'build':
            return '#2196F3';
        case 'deployment':
            return '#FF9800';
        case 'security':
            return '#F44336';
    }
};

const ConfigFileItem = ({
    file,
    onClick
}: {
    file: ConfigFile;
    onClick: (path: string) => void;
}) => {
    const [expanded, setExpanded] = useState(false);

    return (
        <Accordion
            expanded={expanded}
            onChange={() => {
                setExpanded(!expanded);
                if (!expanded) onClick(file.path);
            }}
            className="mb-2"
        >
            <AccordionSummary expandIcon={<ExpandMore />}>
                <Box className="flex items-center w-full">
                    <Box className="flex mr-2" style={{ color: getCategoryColor(file.category) }} >
                        {getCategoryIcon(file.category)}
                    </Box>
                    <Typography
                        variant="body2"
                        className="flex-1 font-medium font-mono"
                    >
                        {file.name}
                    </Typography>
                    <Chip
                        label={file.category}
                        size="small"
                        className="ml-2 text-white" style={{ backgroundColor: getCategoryColor(file.category), borderBottom: '1px solid rgba(0 }}
                    />
                    {file.environment && (
                        <Chip
                            label={file.environment}
                            size="small"
                            variant="outlined"
                            className="ml-2"
                        />
                    )}
                </Box>
            </AccordionSummary>
            <AccordionDetails>
                <Box>
                    <Box className="flex gap-4 mb-4 pb-2" >
                        <Typography variant="caption" color="text.secondary">
                            Size: {file.size}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            Modified: {file.lastModified}
                        </Typography>
                    </Box>

                    <Typography variant="subtitle2" gutterBottom>
                        Configuration Variables:
                    </Typography>

                    {file.variables.map((variable) => (
                        <Box
                            key={variable.key}
                            className="p-2 mb-2 bg-[rgba(0,_0,_0,_0.02)] rounded" style={{ borderLeft: variable.sensitive ? '3px solid #F44336' : '3px solid transparent' }}
                        >
                            <Box className="flex items-center mb-1">
                                <Typography
                                    variant="body2"
                                    className="font-semibold flex-1 font-mono"
                                >
                                    {variable.key}
                                </Typography>
                                {variable.sensitive && (
                                    <Chip label="SENSITIVE" size="small" color="error" className="h-[20px]" />
                                )}
                            </Box>
                            <Typography
                                variant="body2"
                                className="text-blue-600 font-mono mb-1"
                            >
                                {variable.value}
                            </Typography>
                            {variable.description && (
                                <Typography variant="caption" color="text.secondary">
                                    {variable.description}
                                </Typography>
                            )}
                        </Box>
                    ))}
                </Box>
            </AccordionDetails>
        </Accordion>
    );
};

export const ConfigBrowserCanvas = () => {
    const [configFiles] = useState<ConfigFile[]>(MOCK_CONFIG_FILES);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterCategory, setFilterCategory] = useState<ConfigFile['category'] | 'all'>('all');

    const filteredFiles = useMemo(() => {
        return configFiles.filter(file => {
            const matchesSearch = searchQuery === '' ||
                file.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                file.variables.some(v => v.key.toLowerCase().includes(searchQuery.toLowerCase()));
            const matchesCategory = filterCategory === 'all' || file.category === filterCategory;
            return matchesSearch && matchesCategory;
        });
    }, [configFiles, searchQuery, filterCategory]);

    const hasContent = configFiles.length > 0;

    const handleFileClick = (path: string) => {
        console.log('View config file:', path);
    };

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Import Config',
                    icon: <Settings />,
                    onClick: () => {
                        console.log('Import config');
                    },
                },
                secondaryAction: {
                    label: 'Create Config File',
                    onClick: () => {
                        console.log('Create config');
                    },
                },
            }}
        >
            <Box
                className="h-full w-full flex flex-col bg-white dark:bg-gray-900"
            >
                {/* Header */}
                <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)] bg-[rgba(0,_0,_0,_0.02)]">
                    <Typography variant="h6" gutterBottom>
                        Configuration Files
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                        {filteredFiles.length} configuration file{filteredFiles.length !== 1 ? 's' : ''}
                    </Typography>
                </Box>

                {/* Search and filters */}
                <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)]">
                    <TextField
                        fullWidth
                        size="small"
                        placeholder="Search config files and variables..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        InputProps={{
                            startAdornment: <Search className="text-gray-500 dark:text-gray-400 mr-2" />,
                        }}
                        className="mb-2"
                    />
                    <Box className="flex gap-2">
                        {(['all', 'environment', 'build', 'deployment', 'security'] as const).map((category) => (
                            <Chip
                                key={category}
                                label={category.charAt(0).toUpperCase() + category.slice(1)}
                                size="small"
                                onClick={() => setFilterCategory(category)}
                                color={filterCategory === category ? 'primary' : 'default'}
                                variant={filterCategory === category ? 'filled' : 'outlined'}
                            />
                        ))}
                    </Box>
                </Box>

                {/* Config files list */}
                <Box className="flex-1 overflow-auto p-4">
                    {filteredFiles.map((file) => (
                        <ConfigFileItem key={file.path} file={file} onClick={handleFileClick} />
                    ))}
                    {filteredFiles.length === 0 && (
                        <Box className="text-center p-8">
                            <Typography color="text.secondary">
                                No configuration files match your search
                            </Typography>
                        </Box>
                    )}
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default ConfigBrowserCanvas;
