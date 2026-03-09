import React, { useState, useEffect } from 'react';
import { useUpdateConfig } from '../hooks/useConfig';
import { getBootstrapPreset, PRESET_METADATA } from '../../options/presets';

interface ConnectionOptions {
  channel?: string;
  url?: string;
  [key: string]: any;
}

interface SourceConfig {
  sourceType: string;
  sourceId: string;
  connectionOptions: ConnectionOptions;
  autoReconnect: boolean;
}

interface BootstrapConfig {
  source: SourceConfig;
  [key: string]: any;
}

interface Config {
  bootstrap: BootstrapConfig;
  [key: string]: any;
}

interface SettingsFormProps {
  config: Config;
  mode?: 'popup' | 'options'; // 'popup' = compact, 'options' = full
  showPresets?: boolean; // Show preset quick-select
}

export const SettingsForm: React.FC<SettingsFormProps> = ({ 
  config, 
  mode = 'popup',
  showPresets = mode === 'options' 
}) => {
  const updateConfig = useUpdateConfig();
  const [showPresetPanel, setShowPresetPanel] = useState(false);
  const [localConfig, setLocalConfig] = useState<Config>(() => ({
    ...config,
    bootstrap: {
      ...config.bootstrap,
      source: {
        sourceType: config.bootstrap?.source?.sourceType || 'ipc',
        sourceId: config.bootstrap?.source?.sourceId || '',
        connectionOptions: {
          ...(config.bootstrap?.source?.connectionOptions || {}),
          channel: config.bootstrap?.source?.connectionOptions?.channel || '',
        },
        autoReconnect: config.bootstrap?.source?.autoReconnect || false,
      },
    },
  }));

  useEffect(() => {
    setLocalConfig(prev => ({
      ...prev,
      ...config,
      bootstrap: {
        ...prev.bootstrap,
        ...config.bootstrap,
        source: {
          ...prev.bootstrap.source,
          ...config.bootstrap?.source,
          connectionOptions: {
            ...(prev.bootstrap.source?.connectionOptions || {}),
            ...(config.bootstrap?.source?.connectionOptions || {}),
          },
        },
      },
    }));
  }, [config]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateConfig.mutate(localConfig);
  };

  const handleConnectionChange = (key: string, value: string) => {
    setLocalConfig(prev => ({
      ...prev,
      bootstrap: {
        ...prev.bootstrap,
        source: {
          ...prev.bootstrap.source,
          connectionOptions: {
            ...prev.bootstrap.source.connectionOptions,
            [key]: value,
          },
        },
      },
    }));
  };

  const sourceTypes = [
    { value: 'ipc', label: 'IPC' },
    { value: 'http', label: 'HTTP' },
    { value: 'websocket', label: 'WebSocket' },
    { value: 'custom', label: 'Custom' },
  ];

  const handleSourceTypeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const { value } = e.target;
    setLocalConfig(prev => ({
      ...prev,
      bootstrap: {
        ...prev.bootstrap,
        source: {
          ...prev.bootstrap.source,
          sourceType: value,
          connectionOptions: {
            ...prev.bootstrap.source.connectionOptions,
            type: value
          }
        }
      }
    }));
  };

  const handleSourceIdChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { value } = e.target;
    setLocalConfig(prev => ({
      ...prev,
      bootstrap: {
        ...prev.bootstrap,
        source: {
          ...prev.bootstrap.source,
          sourceId: value
        }
      }
    }));
  };

  const handleAutoReconnectChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { checked } = e.target;
    setLocalConfig(prev => ({
      ...prev,
      bootstrap: {
        ...prev.bootstrap,
        source: {
          ...prev.bootstrap.source,
          autoReconnect: checked
        }
      }
    }));
  };

  const applyPreset = (presetName: string) => {
    const preset = getBootstrapPreset(presetName);
    if (preset && (preset as any).configSources && (preset as any).configSources.length > 0) {
      const source = (preset as any).configSources[0];
      setLocalConfig(prev => ({
        ...prev,
        bootstrap: {
          ...prev.bootstrap,
          source: {
            sourceType: source.type,
            sourceId: presetName,
            connectionOptions: source.config as ConnectionOptions || {},
            autoReconnect: true,
          },
        },
      }));
      setShowPresetPanel(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className={`space-y-4 ${mode === 'options' ? 'p-6' : ''}`}>
      <div className="flex items-center justify-between mb-4">
        <h2 className={`font-semibold ${mode === 'options' ? 'text-xl' : 'text-lg'} mb-0`}>
          Source Configuration
        </h2>
        {showPresets && (
          <button
            type="button"
            onClick={() => setShowPresetPanel(!showPresetPanel)}
            className="text-xs px-2 py-1 bg-gray-200 hover:bg-gray-300 rounded transition-colors"
          >
            {showPresetPanel ? 'Hide Presets' : 'Show Presets'}
          </button>
        )}
      </div>

      {showPresetPanel && mode === 'options' && (
        <div className="mb-6 p-4 bg-blue-50 border border-blue-200 rounded">
          <h3 className="font-semibold text-sm mb-3">Quick Presets</h3>
          <div className="grid grid-cols-1 gap-2">
            {Object.entries(PRESET_METADATA.bootstrap).map(([key, meta]) => (
              <button
                key={key}
                type="button"
                onClick={() => applyPreset(key)}
                className="text-left p-2 bg-white border border-blue-300 hover:bg-blue-100 rounded transition-colors"
              >
                <div className="font-medium text-sm">{meta.name}</div>
                <div className="text-xs text-gray-600">{meta.description}</div>
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Source Type
          </label>
          <select
            value={localConfig.bootstrap.source.sourceType}
            onChange={handleSourceTypeChange}
            className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          >
            {sourceTypes.map(type => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Source ID
          </label>
          <input
            type="text"
            value={localConfig.bootstrap.source.sourceId}
            onChange={handleSourceIdChange}
            className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder="Enter source identifier"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {localConfig.bootstrap.source.sourceType === 'ipc'
              ? 'Channel Name'
              : localConfig.bootstrap.source.sourceType === 'http'
              ? 'Endpoint URL'
              : 'Connection String'}
          </label>
          <input
            type="text"
            value={localConfig.bootstrap.source.connectionOptions.channel || ''}
            onChange={(e) => handleConnectionChange('channel', e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder={`Enter ${localConfig.bootstrap.source.sourceType} connection details`}
          />
        </div>

        <div className="flex items-center">
          <input
            type="checkbox"
            id="autoReconnect"
            checked={localConfig.bootstrap.source.autoReconnect}
            onChange={handleAutoReconnectChange}
            className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
          />
          <label htmlFor="autoReconnect" className="ml-2 block text-sm text-gray-700">
            Auto-reconnect on disconnect
          </label>
        </div>
      </div>

      <div className="pt-4 border-t border-gray-200">
        <button
          type="submit"
          disabled={updateConfig.isPending}
          className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {updateConfig.isPending ? 'Saving...' : 'Save Settings'}
        </button>
      </div>
    </form>
  );
};
