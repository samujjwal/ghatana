import React from 'react';
import { ThemeProvider as GhatanaThemeProvider } from '@ghatana/theme';

export interface MinimalThemeProviderProps {
    children: React.ReactNode;
    storageKey?: string;
}

/**
 * Minimal Theme Provider for TutorPutor Admin
 * Provides basic theming context for the application
 */
export const MinimalThemeProvider: React.FC<MinimalThemeProviderProps> = ({ children, storageKey: _storageKey }) => {
    return <GhatanaThemeProvider>{children}</GhatanaThemeProvider>;
};
