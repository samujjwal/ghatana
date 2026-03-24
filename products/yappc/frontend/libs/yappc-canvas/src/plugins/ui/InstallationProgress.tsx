/**
 * Installation Progress Component
 * 
 * Display real-time plugin installation progress
 */

import { useState, useEffect } from 'react';

import { getMarketplaceManager } from '../marketplaceManager';

import type { InstallationProgress as Progress } from '../marketplaceTypes';

/**
 *
 */
export interface InstallationProgressProps {
  /** Plugin ID being installed */
  pluginId: string;
  
  /** Custom CSS class */
  className?: string;
}

/**
 * InstallationProgress - Display plugin installation progress
 * 
 * @example
 * ```tsx
 * <InstallationProgress pluginId="my-plugin" />
 * ```
 */
export function InstallationProgress({
  pluginId,
  className = '',
}: InstallationProgressProps) {
  const [progress, setProgress] = useState<Progress | null>(null);
  const marketplace = getMarketplaceManager();
  
  useEffect(() => {
    const handleProgress = (id: string, prog: Progress) => {
      if (id === pluginId) {
        setProgress(prog);
      }
    };
    
    marketplace.on('install-progress', handleProgress);
    
    // Get initial state
    const current = marketplace.getInstallationProgress(pluginId);
    if (current) {
      setProgress(current);
    }
    
    return () => {
      marketplace.off('install-progress', handleProgress);
    };
  }, [pluginId, marketplace]);
  
  if (!progress) {
    return null;
  }
  
  const statusColors: Record<Progress['status'], string> = {
    pending: '#FFA500',
    downloading: '#2196F3',
    verifying: '#9C27B0',
    installing: '#4CAF50',
    installed: '#4CAF50',
    failed: '#F44336',
    cancelled: '#757575',
  };
  
  return (
    <div className={`installation-progress ${className}`}>
      <div className="progress-header">
        <span className="plugin-id">{pluginId}</span>
        <span className="status" style={{ color: statusColors[progress.status] }}>
          {progress.status}
        </span>
      </div>
      
      <div className="progress-bar-container">
        <div
          className="progress-bar"
          style={{
            width: `${progress.progress}%`,
            backgroundColor: statusColors[progress.status],
          }}
        />
      </div>
      
      <div className="progress-step">{progress.step}</div>
      
      {progress.error && (
        <div className="progress-error">
          Error: {progress.error}
        </div>
      )}
      
      {progress.status === 'installed' && (
        <div className="progress-success">
          ✓ Installation complete!
        </div>
      )}
    </div>
  );
}
