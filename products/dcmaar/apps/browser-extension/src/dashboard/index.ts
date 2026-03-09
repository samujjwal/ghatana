/**
 * @fileoverview Dashboard Entry Point
 *
 * Exports dashboard components for use in the extension.
 */
import React from 'react';
import ReactDOM from 'react-dom/client';
import '../styles/globals.css';
import { Dashboard } from './Dashboard';

const container =
    typeof document !== 'undefined' ? document.getElementById('root') : null;

if (container && !(import.meta as any).env?.VITEST) {
    const root = ReactDOM.createRoot(container as HTMLElement);
    root.render(React.createElement(Dashboard));
}

// Dashboard (simplified UX) - canonical export
export { Dashboard } from './Dashboard';

// Dashboard sub-components
export * from './components';
