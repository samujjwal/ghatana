import React, { useState, useEffect } from 'react';
import { Card, Button, Input, Select, Toggle, Badge } from '@ghatana/dcmaar-shared-ui-tailwind';

interface SourceConfig {
    type: string;
    address: string;
}

interface SinkConfig {
    type: string;
    enabled: boolean;
    storageLimit: number;
}

export const ConfigurationPage: React.FC = () => {
    const [sourceConfig, setSourceConfig] = useState<SourceConfig>({ type: 'ipc', address: '/tmp/dcmaar.sock' });
    const [sinkConfig, setSinkConfig] = useState<SinkConfig>({ type: 'extension-storage', enabled: true, storageLimit: 1 });
    const [connectionStatus, setConnectionStatus] = useState<'idle' | 'testing' | 'success' | 'error'>('idle');
    const [testError, setTestError] = useState<string | null>(null);

    const sourceOptions = [
        { value: 'ipc', label: 'IPC Socket' },
        { value: 'tcp', label: 'TCP' },
        { value: 'http', label: 'HTTP' },
    ];

    const sinkOptions = [
        { value: 'extension-storage', label: 'Extension Storage' },
        { value: 'indexeddb', label: 'IndexedDB' },
        { value: 'both', label: 'Both' },
    ];

    const handleTestConnection = async () => {
        setConnectionStatus('testing');
        setTestError(null);

        // Simulate connection test
        try {
            await new Promise(resolve => setTimeout(resolve, 1500));

            // Simulate random success/failure for demo
            if (Math.random() > 0.3) {
                setConnectionStatus('success');
            } else {
                setConnectionStatus('error');
                setTestError('Failed to connect to source. Please check the address.');
            }
        } catch (error) {
            setConnectionStatus('error');
            setTestError(String(error));
        }

        // Reset after 3 seconds
        setTimeout(() => setConnectionStatus('idle'), 3000);
    };

    const presets = [
        { name: 'Development', config: { type: 'ipc', address: 'localhost:8080' } },
        { name: 'Production', config: { type: 'tcp', address: 'prod.dcmaar.io:9000' } },
        { name: 'Local', config: { type: 'http', address: 'http://localhost:3000' } },
    ];

    return (
        <div className="p-6 space-y-6 max-w-4xl">
            {/* Source Configuration */}
            <Card title="Data Source Configuration" description="Configure where DCMAAR connects to">
                <div className="space-y-4">
                    <Select
                        label="Connection Type"
                        value={sourceConfig.type}
                        onChange={(e) => setSourceConfig({ ...sourceConfig, type: e.currentTarget.value })}
                        options={sourceOptions}
                        required
                    />

                    <Input
                        label="Source Address"
                        value={sourceConfig.address}
                        onChange={(e) => setSourceConfig({ ...sourceConfig, address: e.target.value })}
                        placeholder="e.g., /tmp/dcmaar.sock or localhost:8000"
                        required
                    />

                    <Button
                        onClick={handleTestConnection}
                        disabled={connectionStatus === 'testing'}
                        variant={connectionStatus === 'success' ? 'secondary' : 'primary'}
                        className="w-full"
                    >
                        {connectionStatus === 'testing' && '⏳ Testing...'}
                        {connectionStatus === 'success' && '✓ Connected'}
                        {connectionStatus === 'error' && '✗ Failed'}
                        {connectionStatus === 'idle' && 'Test Connection'}
                    </Button>

                    {testError && (
                        <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                            {testError}
                        </div>
                    )}
                </div>
            </Card>

            {/* Sink Configuration */}
            <Card title="Data Storage Configuration" description="Configure where events are stored">
                <div className="space-y-4">
                    <Select
                        label="Storage Backend"
                        value={sinkConfig.type}
                        onChange={(e) => setSinkConfig({ ...sinkConfig, type: e.currentTarget.value })}
                        options={sinkOptions}
                        required
                    />

                    <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                        <div>
                            <p className="text-sm font-medium text-gray-900">Enable Event Storage</p>
                            <p className="text-xs text-gray-600 mt-1">Store events locally for analysis</p>
                        </div>
                        <Toggle
                            checked={sinkConfig.enabled}
                            onChange={(checked) => setSinkConfig({ ...sinkConfig, enabled: checked })}
                        />
                    </div>

                    {sinkConfig.enabled && (
                        <Input
                            label="Storage Limit (MB)"
                            type="number"
                            value={sinkConfig.storageLimit.toString()}
                            onChange={(e) => setSinkConfig({ ...sinkConfig, storageLimit: parseInt(e.target.value) })}
                            required
                        />
                    )}
                </div>
            </Card>

            {/* Configuration Presets */}
            <Card title="Quick Presets" description="Apply common configurations">
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    {presets.map((preset) => (
                        <button
                            key={preset.name}
                            onClick={() => {
                                setSourceConfig(preset.config);
                            }}
                            className="p-4 border border-gray-300 rounded-lg hover:border-blue-400 hover:bg-blue-50 transition-all"
                        >
                            <p className="font-medium text-gray-900">{preset.name}</p>
                            <p className="text-xs text-gray-600 mt-1">{preset.config.type}</p>
                            <p className="text-xs text-gray-500 mt-2 truncate">{preset.config.address}</p>
                        </button>
                    ))}
                </div>
            </Card>

            {/* Auto-Discovery */}
            <Card title="Auto-Discovery" description="Automatically detect available sources">
                <div className="space-y-3">
                    <p className="text-sm text-gray-700">Available sources detected:</p>
                    <div className="space-y-2">
                        {['localhost:8000', 'localhost:9000', '127.0.0.1:5000'].map((addr) => (
                            <div key={addr} className="flex items-center justify-between p-2 bg-gray-50 rounded">
                                <span className="text-sm font-mono text-gray-700">{addr}</span>
                                <Badge variant="success" label="Ready" />
                            </div>
                        ))}
                    </div>
                </div>
            </Card>

            {/* Advanced Settings */}
            <Card title="Advanced Settings">
                <div className="space-y-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm font-medium text-gray-900">Auto-reconnect on disconnect</p>
                            <p className="text-xs text-gray-600 mt-1">Automatically reconnect if connection is lost</p>
                        </div>
                        <Toggle checked={true} onChange={() => { }} />
                    </div>

                    <div className="border-t border-gray-200 pt-4 flex items-center justify-between">
                        <div>
                            <p className="text-sm font-medium text-gray-900">Verbose Logging</p>
                            <p className="text-xs text-gray-600 mt-1">Log detailed connection information</p>
                        </div>
                        <Toggle checked={false} onChange={() => { }} />
                    </div>

                    <div className="border-t border-gray-200 pt-4 flex items-center justify-between">
                        <div>
                            <p className="text-sm font-medium text-gray-900">Compression</p>
                            <p className="text-xs text-gray-600 mt-1">Compress events before storage</p>
                        </div>
                        <Toggle checked={true} onChange={() => { }} />
                    </div>
                </div>
            </Card>

            {/* Action Buttons */}
            <div className="flex gap-3 justify-end">
                <Button variant="secondary">Cancel</Button>
                <Button variant="primary">Save Configuration</Button>
            </div>
        </div>
    );
};
