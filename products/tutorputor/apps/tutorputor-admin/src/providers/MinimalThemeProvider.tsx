import React from 'react';

export interface MinimalThemeProviderProps {
    children: React.ReactNode;
}

/**
 * Minimal Theme Provider for TutorPutor Admin
 * Provides basic theming context for the application
 */
export const MinimalThemeProvider: React.FC<MinimalThemeProviderProps> = ({ children }) => {
    return <>{children}</>;
};
