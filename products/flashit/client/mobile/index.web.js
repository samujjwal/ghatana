/**
 * Web entry point for Flashit Mobile
 * Uses React DOM instead of Expo's registerRootComponent
 */

import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';

const container = document.getElementById('root');
if (!container) {
    throw new Error('Root element not found');
}

const root = createRoot(container);
root.render(<App />);
