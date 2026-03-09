/**
 * @fileoverview Options Page Entry Point
 * 
 * Renders the ConnectorConfig component into the options page.
 */

/// <reference types="chrome"/>

import React from 'react';
import { createRoot } from 'react-dom/client';
import 'webextension-polyfill';

import { ConnectorConfig } from './ConnectorConfig';

// Mount the options UI
const container = document.getElementById('root');
if (container) {
    const root = createRoot(container);
    root.render(
        <React.StrictMode>
            <ConnectorConfig />
        </React.StrictMode>
    );
} else {
    console.error('Failed to find root element for options page');
}
