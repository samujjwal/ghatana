/**
 * Root Layout Component
 * 
 * Main application layout that wraps all routes.
 * Provides the AppShell, navigation, and outlet for nested routes.
 * 
 * @doc.type layout
 * @doc.purpose Root application layout
 * @doc.layer frontend
 */

import React from 'react';
import { Outlet, useNavigation } from 'react-router';
import { AppShell } from '../components/layout/AppShell';

/**
 * Loading indicator for route transitions
 */
function RouteLoadingIndicator(): React.ReactElement | null {
    const navigation = useNavigation();

    if (navigation.state === 'idle') {
        return null;
    }

    return (
        <div className="fixed top-0 left-0 right-0 z-50">
            <div className="h-1 bg-primary-600 animate-pulse" />
        </div>
    );
}

/**
 * Root Layout Component
 * 
 * Wraps all routes with the application shell and provides
 * route transition loading indicators.
 */
export function RootLayout(): React.ReactElement {
    return (
        <AppShell>
            <RouteLoadingIndicator />
            <Outlet />
        </AppShell>
    );
}

export default RootLayout;
