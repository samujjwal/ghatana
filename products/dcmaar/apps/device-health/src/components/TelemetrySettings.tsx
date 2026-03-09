/**
 * Telemetry Settings - Configuration UI for Telemetry and Observability
 * 
 * Provides user-friendly interface for configuring telemetry collection,
 * viewing metrics, and managing privacy settings.
 */

import React, { useState, useEffect } from 'react';
import { telemetryManager, TelemetryConfig, TelemetryMetrics } from '../services/TelemetryManager';
import { errorMonitor } from '../services/ErrorMonitor';
import { performanceMonitor } from '../services/PerformanceMonitor';
import './TelemetrySettings.css';

interface TelemetrySettingsProps {
  onConfigChange?: (config: TelemetryConfig) => void;
}

export const TelemetrySettings: React.FC<TelemetrySettingsProps> = ({ onConfigChange }) => {
  const [config, setConfig] = useState<TelemetryConfig>({
    enabled: true,
    consent: {
      performance: true,
      interactions: true,
      errors: true,
      analytics: false
    },
    retentionDays: 30,
    batchSize: 50,
    uploadInterval: 5 * 60 * 1000,
    debug: false,
    samplingRate: 0.1
  });

  const [metrics, setMetrics] = useState<TelemetryMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'config' | 'metrics' | 'privacy' | 'export'>('config');

  useEffect(() => {
    loadConfiguration();
    loadMetrics();
  }, []);

  const loadConfiguration = async () => {
    try {
      // Configuration would be loaded from telemetryManager
      // For now, using default config
      setLoading(false);
    } catch (error) {
      console.error('Failed to load telemetry configuration:', error);
      setLoading(false);
    }
  };

  const loadMetrics = async () => {
    try {
      const currentMetrics = await telemetryManager.getMetrics();
      setMetrics(currentMetrics);
    } catch (error) {
      console.error('Failed to load telemetry metrics:', error);
    }
  };

  const handleConfigChange = async (newConfig: Partial<TelemetryConfig>) => {
    const updatedConfig = { ...config, ...newConfig };
    setConfig(updatedConfig);
    
    try {
      await telemetryManager.updateConfig(updatedConfig);
      onConfigChange?.(updatedConfig);
    } catch (error) {
      console.error('Failed to update telemetry configuration:', error);
    }
  };

  const handleClearData = async () => {
    if (window.confirm('Are you sure you want to clear all telemetry data? This action cannot be undone.')) {
      try {
        await telemetryManager.clearData();
        await errorMonitor.clearData?.();
        await performanceMonitor.clearPerformanceData?.();
        await loadMetrics();
      } catch (error) {
        console.error('Failed to clear telemetry data:', error);
      }
    }
  };

  const handleExportData = async () => {
    try {
      const data = await telemetryManager.exportData();
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      
      const a = document.createElement('a');
      a.href = url;
      a.download = `dcmaar-telemetry-${new Date().toISOString().split('T')[0]}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Failed to export telemetry data:', error);
    }
  };

  if (loading) {
    return (
      <div className="telemetry-settings loading">
        <div className="loading-spinner"></div>
        <p>Loading telemetry settings...</p>
      </div>
    );
  }

  return (
    <div className="telemetry-settings">
      <header className="telemetry-header">
        <h2>Telemetry & Observability Settings</h2>
        <p>Configure data collection, privacy settings, and performance monitoring for DCMAAR extension.</p>
      </header>

      <nav className="telemetry-tabs">
        <button 
          className={`tab-button ${activeTab === 'config' ? 'active' : ''}`}
          onClick={() => setActiveTab('config')}
        >
          Configuration
        </button>
        <button 
          className={`tab-button ${activeTab === 'metrics' ? 'active' : ''}`}
          onClick={() => setActiveTab('metrics')}
        >
          Metrics
        </button>
        <button 
          className={`tab-button ${activeTab === 'privacy' ? 'active' : ''}`}
          onClick={() => setActiveTab('privacy')}
        >
          Privacy
        </button>
        <button 
          className={`tab-button ${activeTab === 'export' ? 'active' : ''}`}
          onClick={() => setActiveTab('export')}
        >
          Data Management
        </button>
      </nav>

      <div className="telemetry-content">
        {activeTab === 'config' && (
          <div className="config-panel">
            <h3>Telemetry Configuration</h3>
            
            <div className="config-section">
              <label className="config-toggle">
                <input
                  type="checkbox"
                  checked={config.enabled}
                  onChange={(e) => handleConfigChange({ enabled: e.target.checked })}
                />
                <span className="toggle-slider"></span>
                <div className="toggle-label">
                  <strong>Enable Telemetry</strong>
                  <p>Collect performance and usage data to improve the extension</p>
                </div>
              </label>
            </div>

            {config.enabled && (
              <>
                <div className="config-section">
                  <h4>Data Collection Consent</h4>
                  
                  <label className="config-toggle">
                    <input
                      type="checkbox"
                      checked={config.consent.performance}
                      onChange={(e) => handleConfigChange({ 
                        consent: { ...config.consent, performance: e.target.checked }
                      })}
                    />
                    <span className="toggle-slider"></span>
                    <div className="toggle-label">
                      <strong>Performance Metrics</strong>
                      <p>Load times, memory usage, and Core Web Vitals</p>
                    </div>
                  </label>

                  <label className="config-toggle">
                    <input
                      type="checkbox"
                      checked={config.consent.interactions}
                      onChange={(e) => handleConfigChange({ 
                        consent: { ...config.consent, interactions: e.target.checked }
                      })}
                    />
                    <span className="toggle-slider"></span>
                    <div className="toggle-label">
                      <strong>User Interactions</strong>
                      <p>Button clicks, feature usage, and navigation patterns</p>
                    </div>
                  </label>

                  <label className="config-toggle">
                    <input
                      type="checkbox"
                      checked={config.consent.errors}
                      onChange={(e) => handleConfigChange({ 
                        consent: { ...config.consent, errors: e.target.checked }
                      })}
                    />
                    <span className="toggle-slider"></span>
                    <div className="toggle-label">
                      <strong>Error Reports</strong>
                      <p>JavaScript errors and performance issues for debugging</p>
                    </div>
                  </label>

                  <label className="config-toggle">
                    <input
                      type="checkbox"
                      checked={config.consent.analytics}
                      onChange={(e) => handleConfigChange({ 
                        consent: { ...config.consent, analytics: e.target.checked }
                      })}
                    />
                    <span className="toggle-slider"></span>
                    <div className="toggle-label">
                      <strong>Usage Analytics</strong>
                      <p>Feature adoption and user behavior insights</p>
                    </div>
                  </label>
                </div>

                <div className="config-section">
                  <h4>Advanced Settings</h4>
                  
                  <div className="config-field">
                    <label>Data Retention (days)</label>
                    <input
                      type="number"
                      min="1"
                      max="365"
                      value={config.retentionDays}
                      onChange={(e) => handleConfigChange({ retentionDays: parseInt(e.target.value) })}
                    />
                    <small>How long to keep telemetry data locally</small>
                  </div>

                  <div className="config-field">
                    <label>Performance Sampling Rate</label>
                    <input
                      type="range"
                      min="0"
                      max="1"
                      step="0.1"
                      value={config.samplingRate}
                      onChange={(e) => handleConfigChange({ samplingRate: parseFloat(e.target.value) })}
                    />
                    <small>{Math.round(config.samplingRate * 100)}% of performance events collected</small>
                  </div>

                  <label className="config-toggle">
                    <input
                      type="checkbox"
                      checked={config.debug}
                      onChange={(e) => handleConfigChange({ debug: e.target.checked })}
                    />
                    <span className="toggle-slider"></span>
                    <div className="toggle-label">
                      <strong>Debug Mode</strong>
                      <p>Enable detailed logging for troubleshooting</p>
                    </div>
                  </label>
                </div>
              </>
            )}
          </div>
        )}

        {activeTab === 'metrics' && (
          <div className="metrics-panel">
            <h3>Telemetry Metrics</h3>
            
            {metrics ? (
              <div className="metrics-grid">
                <div className="metric-card">
                  <h4>Total Events</h4>
                  <div className="metric-value">{metrics.totalEvents.toLocaleString()}</div>
                </div>

                <div className="metric-card">
                  <h4>Error Rate</h4>
                  <div className="metric-value">{(metrics.errorRate * 100).toFixed(2)}%</div>
                </div>

                <div className="metric-card">
                  <h4>Storage Usage</h4>
                  <div className="metric-value">{formatBytes(metrics.storageUsage)}</div>
                </div>

                <div className="metric-card">
                  <h4>Last Upload</h4>
                  <div className="metric-value">
                    {metrics.lastUpload ? 
                      new Date(metrics.lastUpload).toLocaleString() : 
                      'Never'
                    }
                  </div>
                </div>
              </div>
            ) : (
              <p>No metrics available</p>
            )}

            {metrics && (
              <>
                <div className="metrics-section">
                  <h4>Events by Type</h4>
                  <div className="chart-container">
                    {Object.entries(metrics.eventsByType).map(([type, count]) => (
                      <div key={type} className="chart-bar">
                        <span className="bar-label">{type}</span>
                        <div className="bar-track">
                          <div 
                            className="bar-fill"
                            style={{ 
                              width: `${(count / metrics.totalEvents) * 100}%` 
                            }}
                          />
                        </div>
                        <span className="bar-value">{count}</span>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="metrics-section">
                  <h4>Events by Source</h4>
                  <div className="chart-container">
                    {Object.entries(metrics.eventsBySource).map(([source, count]) => (
                      <div key={source} className="chart-bar">
                        <span className="bar-label">{source}</span>
                        <div className="bar-track">
                          <div 
                            className="bar-fill"
                            style={{ 
                              width: `${(count / metrics.totalEvents) * 100}%` 
                            }}
                          />
                        </div>
                        <span className="bar-value">{count}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </>
            )}

            <div className="metrics-actions">
              <button onClick={loadMetrics} className="refresh-button">
                Refresh Metrics
              </button>
            </div>
          </div>
        )}

        {activeTab === 'privacy' && (
          <div className="privacy-panel">
            <h3>Privacy & Data Protection</h3>
            
            <div className="privacy-section">
              <h4>Data Collection Principles</h4>
              <ul className="privacy-list">
                <li>
                  <strong>Anonymous by Design:</strong> No personally identifiable information is collected.
                  All user identifiers are cryptographically hashed.
                </li>
                <li>
                  <strong>Minimal Data:</strong> Only essential performance and usage data is collected
                  to improve the extension functionality.
                </li>
                <li>
                  <strong>User Control:</strong> You have full control over what data is collected
                  and can disable telemetry at any time.
                </li>
                <li>
                  <strong>Local Storage:</strong> All telemetry data is stored locally on your device.
                  No data is transmitted to external servers without explicit consent.
                </li>
                <li>
                  <strong>Automatic Cleanup:</strong> Data is automatically deleted based on your
                  retention settings to minimize storage usage.
                </li>
              </ul>
            </div>

            <div className="privacy-section">
              <h4>Data Types Collected</h4>
              <div className="data-types">
                <div className="data-type">
                  <strong>Performance Metrics:</strong>
                  <p>Load times, memory usage, query execution time, Core Web Vitals</p>
                </div>
                <div className="data-type">
                  <strong>User Interactions:</strong>
                  <p>Button clicks, feature usage frequency, navigation patterns (no content data)</p>
                </div>
                <div className="data-type">
                  <strong>Error Reports:</strong>
                  <p>JavaScript errors, performance issues, system failures (sanitized stack traces)</p>
                </div>
                <div className="data-type">
                  <strong>System Information:</strong>
                  <p>Browser type, extension version, viewport size, timezone</p>
                </div>
              </div>
            </div>

            <div className="privacy-section">
              <h4>Data Protection Measures</h4>
              <ul className="privacy-list">
                <li>All URLs are sanitized to remove query parameters and sensitive information</li>
                <li>User identifiers are generated using cryptographic hashing (SHA-256)</li>
                <li>No personally identifiable information is stored or transmitted</li>
                <li>Data retention is limited and configurable by the user</li>
                <li>All data can be exported or deleted at any time</li>
              </ul>
            </div>
          </div>
        )}

        {activeTab === 'export' && (
          <div className="data-panel">
            <h3>Data Management</h3>
            
            <div className="data-section">
              <h4>Export Data</h4>
              <p>Download all collected telemetry data in JSON format for analysis or backup.</p>
              <button onClick={handleExportData} className="export-button">
                Export All Data
              </button>
            </div>

            <div className="data-section">
              <h4>Clear Data</h4>
              <p>
                Permanently delete all locally stored telemetry data. This includes performance metrics,
                error reports, and usage statistics. This action cannot be undone.
              </p>
              <button onClick={handleClearData} className="clear-button danger">
                Clear All Data
              </button>
            </div>

            <div className="data-section">
              <h4>Storage Information</h4>
              {metrics && (
                <div className="storage-info">
                  <p><strong>Current Usage:</strong> {formatBytes(metrics.storageUsage)}</p>
                  <p><strong>Total Events:</strong> {metrics.totalEvents.toLocaleString()}</p>
                  <p><strong>Average Event Size:</strong> {formatBytes(metrics.averageEventSize)}</p>
                  <p><strong>Retention Period:</strong> {config.retentionDays} days</p>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

// Helper function to format bytes
function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 Bytes';
  
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

export default TelemetrySettings;