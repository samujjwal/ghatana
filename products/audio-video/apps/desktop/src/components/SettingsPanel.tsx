/**
 * @doc.type component
 * @doc.purpose Settings panel component
 * @doc.layer application
 * @doc.pattern settings component
 */

import React from 'react';
import { Card, Button, Input } from '@ghatana/audio-video-ui';

const SettingsPanel: React.FC = () => {
  const [settings, setSettings] = React.useState({
    theme: 'auto',
    language: 'en-US',
    fontSize: 'medium',
    enableGPU: false,
    maxConcurrentRequests: 5,
    cacheSize: 1024
  });

  const handleSave = () => {
    // TODO: Save settings
    console.log('Saving settings:', settings);
  };

  return (
    <div className="space-y-6">
      <Card title="Settings" subtitle="Configure application preferences">
        <div className="space-y-6">
          {/* UI Settings */}
          <div className="space-y-4">
            <h3 className="text-lg font-medium text-gray-900 dark:text-white">User Interface</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                  Theme
                </label>
                <select
                  className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                  value={settings.theme}
                  onChange={(e) => setSettings({ ...settings, theme: e.target.value })}
                >
                  <option value="light">Light</option>
                  <option value="dark">Dark</option>
                  <option value="auto">Auto</option>
                </select>
              </div>
              
              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                  Language
                </label>
                <select
                  className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                  value={settings.language}
                  onChange={(e) => setSettings({ ...settings, language: e.target.value })}
                >
                  <option value="en-US">English (US)</option>
                  <option value="es-ES">Spanish</option>
                  <option value="fr-FR">French</option>
                  <option value="de-DE">German</option>
                </select>
              </div>
            </div>
          </div>

          {/* Performance Settings */}
          <div className="space-y-4">
            <h3 className="text-lg font-medium text-gray-900 dark:text-white">Performance</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                  Max Concurrent Requests
                </label>
                <Input
                  type="number"
                  value={settings.maxConcurrentRequests.toString()}
                  onChange={(value) => setSettings({ ...settings, maxConcurrentRequests: parseInt(value) || 5 })}
                />
              </div>
              
              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                  Cache Size (MB)
                </label>
                <Input
                  type="number"
                  value={settings.cacheSize.toString()}
                  onChange={(value) => setSettings({ ...settings, cacheSize: parseInt(value) || 1024 })}
                />
              </div>
            </div>
          </div>

          {/* Service Endpoints */}
          <div className="space-y-4">
            <h3 className="text-lg font-medium text-gray-900 dark:text-white">Service Endpoints</h3>
            <div className="space-y-3">
              <Input
                label="STT Service"
                placeholder="http://localhost:50051"
                value="http://localhost:50051"
                onChange={() => {}}
              />
              <Input
                label="TTS Service"
                placeholder="http://localhost:50052"
                value="http://localhost:50052"
                onChange={() => {}}
              />
              <Input
                label="AI Voice Service"
                placeholder="http://localhost:50053"
                value="http://localhost:50053"
                onChange={() => {}}
              />
            </div>
          </div>

          {/* Save Button */}
          <div className="flex justify-end">
            <Button onClick={handleSave}>
              Save Settings
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
};

export default SettingsPanel;
