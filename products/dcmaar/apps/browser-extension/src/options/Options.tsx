/**
 * @fileoverview Options Page Entry Point
 * 
 * Main entry point for the settings/options page
 */

import React from 'react';
import { createRoot } from 'react-dom/client';
import { Settings } from '../components/Settings/Settings';
import '../styles/globals.css';

const container = document.getElementById('root');

if (container) {
    const root = createRoot(container);
    root.render(<Settings />);
}

export { };
