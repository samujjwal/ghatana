/**
 * Plugin Configuration Modal
 *
 * Modal for editing plugin configuration with JSON editor.
 * Features syntax highlighting, validation, and error handling.
 *
 * @doc.type component
 * @doc.purpose Plugin configuration editor
 * @doc.layer frontend
 */

import React, { useState, useEffect } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { 
  X, 
  Save, 
  RotateCcw, 
  AlertCircle, 
  CheckCircle, 
  Code, 
  FileJson, 
  Eye,
  EyeOff,
  Database,
  Key,
  Globe,
  Shield
} from 'lucide-react';
import { cn, buttonStyles, textStyles, inputStyles } from '../../lib/theme';
import { pluginService, type Plugin, type PluginConfiguration } from '../../api/plugin.service';

interface PluginConfigModalProps {
  plugin: Plugin;
  isOpen: boolean;
  onClose: () => void;
}

type ConfigMode = 'form' | 'json';

/**
 * Plugin Configuration Modal Component
 */
export function PluginConfigModal({ plugin, isOpen, onClose }: PluginConfigModalProps): React.ReactElement | null {
  const queryClient = useQueryClient();
  const [config, setConfig] = useState<string>('');
  const [originalConfig, setOriginalConfig] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [isDirty, setIsDirty] = useState(false);
  const [mode, setMode] = useState<ConfigMode>('form');
  const [showPasswords, setShowPasswords] = useState<Record<string, boolean>>({});

  // Fetch configuration when modal opens
  useEffect(() => {
    if (isOpen && plugin.id) {
      // Fetch current configuration
      const fetchConfig = async () => {
        try {
          const cfg = await pluginService.updatePluginConfiguration(plugin.id, {});
          const formatted = JSON.stringify(cfg || {}, null, 2);
          setConfig(formatted);
          setOriginalConfig(formatted);
          setError(null);
          setValidationError(null);
          setIsDirty(false);
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Failed to load configuration';
          setError(message);
        }
      };
      void fetchConfig();
    }
  }, [isOpen, plugin.id]);

  // Update configuration mutation
  const updateMutation = useMutation({
    mutationFn: async (newConfig: PluginConfiguration) => {
      return pluginService.updatePluginConfiguration(plugin.id, newConfig);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins', 'installed'] });
      setOriginalConfig(config);
      setIsDirty(false);
      onClose();
    },
    onError: (err: Error) => {
      setError(err.message || 'Failed to save configuration');
    },
  });

  // Validate and parse JSON
  const validateJSON = (value: string): PluginConfiguration | null => {
    try {
      const parsed = JSON.parse(value);
      setValidationError(null);
      return parsed;
    } catch (err) {
      if (err instanceof Error) {
        setValidationError(err.message);
      }
      return null;
    }
  };

  // Handle config change
  const handleConfigChange = (value: string) => {
    setConfig(value);
    setIsDirty(value !== originalConfig);
    validateJSON(value);
  };

  // Handle save
  const handleSave = () => {
    const parsed = validateJSON(config);
    if (parsed) {
      updateMutation.mutate(parsed);
    }
  };

  // Handle reset
  const handleReset = () => {
    setConfig(originalConfig);
    setIsDirty(false);
    setValidationError(null);
    setError(null);
  };

  // Handle format
  const handleFormat = () => {
    const parsed = validateJSON(config);
    if (parsed) {
      setConfig(JSON.stringify(parsed, null, 2));
    }
  };

  // Parse config for form rendering
  const parsedConfig = validateJSON(config) || {};

  // Handle form field change
  const handleFieldChange = (path: string, value: unknown) => {
    const parsed = validateJSON(config) || {};
    const keys = path.split('.');
    let current: Record<string, unknown> = parsed;
    
    // Navigate to the parent of the target field
    for (let i = 0; i < keys.length - 1; i++) {
      if (typeof current[keys[i]] !== 'object') {
        current[keys[i]] = {};
      }
      current = current[keys[i]] as Record<string, unknown>;
    }
    
    // Set the value
    current[keys[keys.length - 1]] = value;
    
    const newConfig = JSON.stringify(parsed, null, 2);
    setConfig(newConfig);
    setIsDirty(newConfig !== originalConfig);
  };

  // Get icon for config field
  const getFieldIcon = (key: string) => {
    const lowerKey = key.toLowerCase();
    if (lowerKey.includes('password') || lowerKey.includes('secret') || lowerKey.includes('token')) {
      return <Key className="h-4 w-4" />;
    }
    if (lowerKey.includes('database') || lowerKey.includes('db')) {
      return <Database className="h-4 w-4" />;
    }
    if (lowerKey.includes('url') || lowerKey.includes('host') || lowerKey.includes('endpoint')) {
      return <Globe className="h-4 w-4" />;
    }
    if (lowerKey.includes('auth') || lowerKey.includes('credential')) {
      return <Shield className="h-4 w-4" />;
    }
    return null;
  };

  // Render form field
  const renderFormField = (key: string, value: unknown, path = key) => {
    const isPassword = key.toLowerCase().includes('password') || 
                      key.toLowerCase().includes('secret') || 
                      key.toLowerCase().includes('token');
    
    if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
      // Render nested object as fieldset
      return (
        <fieldset key={path} className="border border-gray-300 dark:border-gray-600 rounded-lg p-4">
          <legend className="px-2 text-sm font-medium text-gray-700 dark:text-gray-300">
            {key}
          </legend>
          <div className="space-y-4">
            {Object.entries(value).map(([nestedKey, nestedValue]) =>
              renderFormField(nestedKey, nestedValue, `${path}.${nestedKey}`)
            )}
          </div>
        </fieldset>
      );
    }

    if (typeof value === 'boolean') {
      return (
        <div key={path} className="flex items-center justify-between py-2">
          <label className="text-sm font-medium text-gray-700 dark:text-gray-300 flex items-center gap-2">
            {getFieldIcon(key)}
            {key}
          </label>
          <button
            type="button"
            onClick={() => handleFieldChange(path, !value)}
            className={cn(
              'relative inline-flex h-6 w-11 items-center rounded-full transition-colors',
              value ? 'bg-primary-600' : 'bg-gray-300 dark:bg-gray-600'
            )}
          >
            <span
              className={cn(
                'inline-block h-4 w-4 transform rounded-full bg-white transition-transform',
                value ? 'translate-x-6' : 'translate-x-1'
              )}
            />
          </button>
        </div>
      );
    }

    if (typeof value === 'number') {
      return (
        <div key={path} className="space-y-2">
          <label className="text-sm font-medium text-gray-700 dark:text-gray-300 flex items-center gap-2">
            {getFieldIcon(key)}
            {key}
          </label>
          <input
            type="number"
            value={value}
            onChange={(e) => handleFieldChange(path, Number(e.target.value))}
            className={inputStyles.base}
          />
        </div>
      );
    }

    // String input (default)
    return (
      <div key={path} className="space-y-2">
        <label className="text-sm font-medium text-gray-700 dark:text-gray-300 flex items-center gap-2">
          {getFieldIcon(key)}
          {key}
        </label>
        <div className="relative">
          <input
            type={isPassword && !showPasswords[path] ? 'password' : 'text'}
            value={String(value || '')}
            onChange={(e) => handleFieldChange(path, e.target.value)}
            className={cn(inputStyles.base, isPassword && 'pr-10')}
          />
          {isPassword && (
            <button
              type="button"
              onClick={() => setShowPasswords(prev => ({ ...prev, [path]: !prev[path] }))}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
            >
              {showPasswords[path] ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          )}
        </div>
      </div>
    );
  };

  if (!isOpen) return null;

  const canSave = isDirty && !validationError && !updateMutation.isPending;

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/50 z-50 backdrop-blur-sm"
        onClick={onClose}
      />

      {/* Modal */}
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 pointer-events-none">
        <div
          className="bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-4xl w-full max-h-[90vh] flex flex-col pointer-events-auto"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-gray-100 dark:bg-gray-900/50 rounded-lg flex items-center justify-center">
                <Code className="h-5 w-5 text-gray-600 dark:text-gray-400" />
              </div>
              <div>
                <h2 className={textStyles.h3}>Configure Plugin</h2>
                <p className="text-sm text-gray-600 dark:text-gray-400">
                  {plugin.metadata.name} v{plugin.metadata.version}
                </p>
              </div>
            </div>
            <button
              onClick={onClose}
              className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {/* Mode Switcher */}
          <div className="px-6 pt-4 flex items-center gap-2 border-b border-gray-200 dark:border-gray-700">
            <button
              onClick={() => setMode('form')}
              className={cn(
                'px-4 py-2 text-sm font-medium rounded-t-lg transition-colors',
                mode === 'form'
                  ? 'bg-white dark:bg-gray-800 text-primary-600 border-b-2 border-primary-600'
                  : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200'
              )}
            >
              <FileJson className="h-4 w-4 inline mr-2" />
              Form Editor
            </button>
            <button
              onClick={() => setMode('json')}
              className={cn(
                'px-4 py-2 text-sm font-medium rounded-t-lg transition-colors',
                mode === 'json'
                  ? 'bg-white dark:bg-gray-800 text-primary-600 border-b-2 border-primary-600'
                  : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200'
              )}
            >
              <Code className="h-4 w-4 inline mr-2" />
              JSON Editor
            </button>
          </div>

          {/* Content */}
          <div className="flex-1 overflow-y-auto p-6">
            {error && (
              <div className="mb-4 p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg flex items-start gap-3">
                <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0 mt-0.5" />
                <div className="flex-1">
                  <h4 className="text-sm font-medium text-red-900 dark:text-red-100 mb-1">
                    Configuration Error
                  </h4>
                  <p className="text-sm text-red-800 dark:text-red-200">{error}</p>
                </div>
              </div>
            )}

            {mode === 'form' ? (
              /* Form Editor */
              <div className="space-y-6">
                <div className="p-3 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg">
                  <p className="text-xs text-blue-800 dark:text-blue-200">
                    <strong>Tip:</strong> Use the form below to configure the plugin. Switch to JSON editor for advanced configuration.
                  </p>
                </div>

                {Object.keys(parsedConfig).length > 0 ? (
                  <div className="space-y-4">
                    {Object.entries(parsedConfig).map(([key, value]) =>
                      renderFormField(key, value)
                    )}
                  </div>
                ) : (
                  <div className="text-center py-12">
                    <FileJson className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                    <p className="text-sm text-gray-600 dark:text-gray-400">
                      No configuration available. Switch to JSON editor to add configuration.
                    </p>
                  </div>
                )}

                {/* Plugin Info */}
                <div className="p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
                  <h4 className={cn(textStyles.h4, 'mb-3')}>Plugin Information</h4>
                  <dl className="grid grid-cols-2 gap-3 text-sm">
                    <div>
                      <dt className="text-gray-600 dark:text-gray-400 mb-1">ID</dt>
                      <dd className="font-mono text-xs">{plugin.id}</dd>
                    </div>
                    <div>
                      <dt className="text-gray-600 dark:text-gray-400 mb-1">Version</dt>
                      <dd className="font-mono text-xs">{plugin.metadata.version}</dd>
                    </div>
                    <div>
                      <dt className="text-gray-600 dark:text-gray-400 mb-1">Category</dt>
                      <dd className="font-mono text-xs">{plugin.metadata.category}</dd>
                    </div>
                    <div>
                      <dt className="text-gray-600 dark:text-gray-400 mb-1">Author</dt>
                      <dd className="font-mono text-xs">{plugin.metadata.author}</dd>
                    </div>
                  </dl>
                </div>
              </div>
            ) : (
              /* JSON Editor */
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <label className={cn(textStyles.label, 'mb-0')}>
                    Configuration (JSON)
                  </label>
                  <button
                    onClick={handleFormat}
                    disabled={Boolean(validationError)}
                    className={cn(
                      buttonStyles.ghost,
                      'px-3 py-1 text-xs',
                      validationError && 'opacity-50 cursor-not-allowed'
                    )}
                  >
                    Format
                  </button>
                </div>

                <div className="relative">
                  <textarea
                    value={config}
                    onChange={(e) => handleConfigChange(e.target.value)}
                    className={cn(
                      inputStyles.base,
                      'font-mono text-sm min-h-[400px] resize-y',
                      validationError && 'border-red-500 focus:border-red-500 focus:ring-red-500'
                    )}
                    placeholder='{\n  "key": "value"\n}'
                    spellCheck={false}
                  />

                  {/* Validation Status */}
                  {!validationError && config && (
                    <div className="absolute top-3 right-3 flex items-center gap-2 text-green-600">
                      <CheckCircle className="h-4 w-4" />
                      <span className="text-xs">Valid JSON</span>
                    </div>
                  )}
                </div>

                {/* Validation Error */}
                {validationError && (
                  <div className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
                    <div className="flex items-start gap-2">
                      <AlertCircle className="h-4 w-4 text-red-600 flex-shrink-0 mt-0.5" />
                      <div>
                        <p className="text-xs font-medium text-red-900 dark:text-red-100 mb-1">
                          Invalid JSON
                        </p>
                        <p className="text-xs text-red-800 dark:text-red-200 font-mono">
                          {validationError}
                        </p>
                      </div>
                    </div>
                  </div>
                )}

                {/* Info */}
                <div className="p-3 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg">
                  <p className="text-xs text-blue-800 dark:text-blue-200">
                    <strong>Tip:</strong> Edit the JSON configuration for this plugin. Changes will be
                    applied immediately after saving. Invalid JSON cannot be saved.
                  </p>
                </div>
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex items-center justify-between px-6 py-4 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50">
            <div className="flex items-center gap-2">
              {isDirty && (
                <span className="text-xs text-amber-600 dark:text-amber-400 flex items-center gap-1">
                  <AlertCircle className="h-3 w-3" />
                  Unsaved changes
                </span>
              )}
            </div>

            <div className="flex items-center gap-3">
              <button
                onClick={handleReset}
                disabled={!isDirty}
                className={cn(
                  buttonStyles.ghost,
                  'px-4 py-2',
                  !isDirty && 'opacity-50 cursor-not-allowed'
                )}
              >
                <RotateCcw className="h-4 w-4 mr-2" />
                Reset
              </button>
              <button onClick={onClose} className={cn(buttonStyles.secondary, 'px-4 py-2')}>
                Cancel
              </button>
              <button
                onClick={handleSave}
                disabled={!canSave}
                className={cn(
                  buttonStyles.primary,
                  'px-4 py-2',
                  !canSave && 'opacity-50 cursor-not-allowed'
                )}
              >
                {updateMutation.isPending ? (
                  <>
                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2" />
                    Saving...
                  </>
                ) : (
                  <>
                    <Save className="h-4 w-4 mr-2" />
                    Save Changes
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
