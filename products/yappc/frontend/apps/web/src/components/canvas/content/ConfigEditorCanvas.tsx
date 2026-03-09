/**
 * Config Editor Canvas Content
 * 
 * Configuration file editor for Deploy × Code level.
 * Edit deployment configs with validation.
 * 
 * @doc.type component
 * @doc.purpose Configuration file editor with validation
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  Button,
  Surface as Paper,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

interface ConfigFile {
    id: string;
    name: string;
    path: string;
    type: 'yaml' | 'json' | 'env' | 'toml';
    environment: 'development' | 'staging' | 'production';
    content: string;
    errors: ConfigError[];
    lastModified: string;
}

interface ConfigError {
    line: number;
    message: string;
    severity: 'error' | 'warning';
}

const MOCK_CONFIGS: ConfigFile[] = [
    {
        id: '1',
        name: 'database.yaml',
        path: 'config/database.yaml',
        type: 'yaml',
        environment: 'production',
        lastModified: '2024-01-15 10:30',
        errors: [],
        content: `host: postgres.prod.example.com
port: 5432
database: app_db
user: app_user
password: ${process.env.DB_PASSWORD}
pool:
  min: 2
  max: 10
ssl:
  enabled: true`,
    },
    {
        id: '2',
        name: 'api.json',
        path: 'config/api.json',
        type: 'json',
        environment: 'production',
        lastModified: '2024-01-14 15:20',
        errors: [
            { line: 5, message: 'Missing required field: "timeout"', severity: 'error' },
        ],
        content: `{
  "baseUrl": "https://api.example.com",
  "version": "v1",
  "retries": 3,
  "headers": {
    "Content-Type": "application/json"
  }
}`,
    },
    {
        id: '3',
        name: '.env',
        path: '.env.production',
        type: 'env',
        environment: 'production',
        lastModified: '2024-01-16 09:00',
        errors: [
            { line: 3, message: 'Insecure: secret exposed in plaintext', severity: 'warning' },
        ],
        content: `NODE_ENV=production
PORT=3000
API_KEY=sk_live_abc123
DATABASE_URL=postgresql://user:pass@localhost:5432/db
REDIS_URL=redis://localhost:6379`,
    },
    {
        id: '4',
        name: 'nginx.conf',
        path: 'config/nginx.conf',
        type: 'toml',
        environment: 'production',
        lastModified: '2024-01-13 14:45',
        errors: [],
        content: `[server]
port = 80
worker_connections = 1024

[upstream]
backend = "http://localhost:3000"

[ssl]
certificate = "/etc/ssl/cert.pem"
certificate_key = "/etc/ssl/key.pem"`,
    },
    {
        id: '5',
        name: 'cache.yaml',
        path: 'config/cache.yaml',
        type: 'yaml',
        environment: 'staging',
        lastModified: '2024-01-12 11:30',
        errors: [],
        content: `redis:
  host: redis.staging.example.com
  port: 6379
  ttl: 3600
  prefix: "app:"`,
    },
];

const getTypeColor = (type: ConfigFile['type']) => {
    switch (type) {
        case 'yaml':
            return '#3B82F6';
        case 'json':
            return '#10B981';
        case 'env':
            return '#F59E0B';
        case 'toml':
            return '#8B5CF6';
    }
};

const getEnvColor = (env: ConfigFile['environment']) => {
    switch (env) {
        case 'development':
            return '#10B981';
        case 'staging':
            return '#F59E0B';
        case 'production':
            return '#EF4444';
    }
};

export const ConfigEditorCanvas = () => {
    const [configs] = useState<ConfigFile[]>(MOCK_CONFIGS);
    const [selectedConfig, setSelectedConfig] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');

    const filteredConfigs = useMemo(() => {
        return configs.filter(
            c =>
                searchQuery === '' ||
                c.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                c.path.toLowerCase().includes(searchQuery.toLowerCase())
        );
    }, [configs, searchQuery]);

    const stats = useMemo(() => {
        return {
            total: configs.length,
            errors: configs.filter(c => c.errors.some(e => e.severity === 'error')).length,
            warnings: configs.filter(c => c.errors.some(e => e.severity === 'warning')).length,
            valid: configs.filter(c => c.errors.length === 0).length,
        };
    }, [configs]);

    const hasContent = configs.length > 0;

    const selectedConfigData = configs.find(c => c.id === selectedConfig);

    return (
        <BaseCanvasContent hasContent={hasContent}>
            <Box className="h-full flex flex-col bg-[#fafafa]">
                <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)] bg-white">
                    <Box className="flex gap-4 mb-2">
                        <TextField
                            size="small"
                            placeholder="Search configs..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="flex-1"
                        />
                        <Button variant="outlined" size="small">
                            Validate All
                        </Button>
                    </Box>
                </Box>

                <Box className="flex-1 flex gap-4 overflow-hidden p-4">
                    <Box className="overflow-y-auto transition-all duration-300" style={{ width: selectedConfigData ? '35%' : '100%' }}>
                        {filteredConfigs.map(config => (
                            <Paper
                                key={config.id}
                                elevation={selectedConfig === config.id ? 4 : 2}
                                onClick={() => setSelectedConfig(config.id === selectedConfig ? null : config.id)}
                                className="p-3 mb-2 cursor-pointer transition-all duration-200 hover:shadow-lg" style={{ border: selectedConfig === config.id ? '3px solid #3B82F6' : '2px solid transparent', backgroundColor: getEnvColor(config.environment) }}
                            >
                                <Box className="flex gap-2 mb-1">
                                    <Box className="flex-1">
                                        <Typography variant="body2" className="text-[0.85rem] font-semibold mb-[2.4px]">
                                            {config.name}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary" className="text-[0.7rem] font-mono">
                                            {config.path}
                                        </Typography>
                                    </Box>
                                </Box>
                                <Box className="flex gap-1 flex-wrap mt-1">
                                    <Chip
                                        label={config.type}
                                        size="small"
                                    />
                                    <Chip
                                        label={config.environment}
                                        size="small"
                                        className="h-[18px] text-[0.65rem] text-white" />
                                    {config.errors.length > 0 && (
                                        <Chip
                                            label={`${config.errors.length} issue${config.errors.length > 1 ? 's' : ''}`}
                                            size="small"
                                            className="h-[18px] text-[0.65rem] bg-[#EF4444] text-white"
                                        />
                                    )}
                                </Box>
                            </Paper>
                        ))}
                    </Box>

                    {selectedConfigData && (
                        <Box className="flex flex-col w-[65%]">
                            <Paper elevation={3} className="p-4 mb-2">
                                <Box className="flex justify-between mb-2" style={{ alignItems: 'start' }} >
                                    <Box>
                                        <Typography variant="subtitle2" className="font-semibold mb-1">
                                            {selectedConfigData.name}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary" className="font-mono">
                                            {selectedConfigData.path}
                                        </Typography>
                                    </Box>
                                    <Box className="flex gap-1">
                                        <Chip
                                            label={selectedConfigData.type}
                                            size="small"
                                            className="text-white" style={{ backgroundColor: getTypeColor(selectedConfigData.type) }}
                                        />
                                        <Chip
                                            label={selectedConfigData.environment}
                                            size="small"
                                            className="text-white" style={{ backgroundColor: getEnvColor(selectedConfigData.environment) }}
                                        />
                                    </Box>
                                </Box>
                                <Typography variant="caption" color="text.secondary" display="block" className="mb-2">
                                    Last modified: {selectedConfigData.lastModified}
                                </Typography>
                                <Box className="flex gap-2">
                                    <Button variant="outlined" size="small">
                                        Validate
                                    </Button>
                                    <Button variant="outlined" size="small">
                                        Save
                                    </Button>
                                    <Button variant="outlined" size="small">
                                        Format
                                    </Button>
                                </Box>
                            </Paper>

                            <Paper elevation={3} className="flex-1 flex flex-col p-4">
                                <Typography variant="caption" className="font-semibold mb-2">
                                    Configuration
                                </Typography>
                                <Paper
                                    className="flex-1 text-[0.8rem] overflow-y-auto whitespace-pre-wrap p-4 bg-[#1E1E1E] text-[#D4D4D4] font-mono"
                                >
                                    {selectedConfigData.content}
                                </Paper>
                                {selectedConfigData.errors.length > 0 && (
                                    <Paper className="p-3 bg-[#FEE2E2] mt-2">
                                        <Typography variant="caption" className="font-semibold block mb-1">
                                            Issues ({selectedConfigData.errors.length})
                                        </Typography>
                                        {selectedConfigData.errors.map((error, idx) => (
                                            <Box key={idx} className="mb-1">
                                                <Typography variant="caption" color="error.main">
                                                    Line {error.line}: {error.message}
                                                </Typography>
                                            </Box>
                                        ))}
                                    </Paper>
                                )}
                            </Paper>
                        </Box>
                    )}
                </Box>

                <Box
                    className="absolute rounded top-[80px] right-[16px] bg-white p-4 shadow"
                >
                    <Typography variant="subtitle2" className="font-semibold mb-2">
                        Config Stats
                    </Typography>
                    <Typography variant="caption" display="block">
                        Total: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" className="text-[#10B981]">
                        Valid: {stats.valid}
                    </Typography>
                    <Typography variant="caption" display="block" className="text-[#EF4444]">
                        Errors: {stats.errors}
                    </Typography>
                    <Typography variant="caption" display="block" className="text-[#F59E0B]">
                        Warnings: {stats.warnings}
                    </Typography>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default ConfigEditorCanvas;
