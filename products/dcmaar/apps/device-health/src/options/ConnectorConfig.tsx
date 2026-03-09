/**
 * @fileoverview ConnectorConfig - Options Page with Unified Dashboard
 * 
 * Updated to use the new unified Dashboard component for configuration and monitoring.
 */

import React from 'react';
import { Root } from '../Root';
import '../styles/globals.css';

// Export the ConnectorConfig component (used by options/index.tsx)
export const ConnectorConfig = () => {
  return (
    <div className="extension-options">
      <div className="flex-1 overflow-hidden">
        <Root />
      </div>
    </div>
  );
};

