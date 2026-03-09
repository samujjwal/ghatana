/**
 * App Shell Layout
 * 
 * Main application shell layout that wraps all authenticated app routes.
 * Provides header navigation and workspace/project switching.
 * 
 * @doc.type layout
 * @doc.purpose Main application layout
 * @doc.layer product
 * @doc.pattern Layout Component
 */

import { Outlet, useNavigate } from "react-router";
import { useAtomValue, useSetAtom } from "jotai";
import {
    headerVisibleAtom,
    headerActionContextAtom
} from "../../state/atoms/layoutAtom";
import { useWorkspaceContext } from "../../hooks/useWorkspaceData";
import { RouteErrorBoundary } from "../../components/route/ErrorBoundary";
import { useEffect, useState } from "react";

export function Layout() {
    const navigate = useNavigate();
    const { currentWorkspace, ownedWorkspaces } = useWorkspaceContext();
    const isHeaderVisible = useAtomValue(headerVisibleAtom);
    const setHeaderActionContext = useSetAtom(headerActionContextAtom);
    const [darkMode, setDarkMode] = useState(false);

    // Update global header with app/global context
    useEffect(() => {
        setHeaderActionContext('global');
        return () => { };
    }, [setHeaderActionContext]);

    // Mock user data - TODO: Replace with actual auth context
    const user = {
        id: 'user-1',
        name: 'John Doe',
        email: 'john@example.com',
        initials: 'JD',
    };

    const workspaces = ownedWorkspaces.map(ws => ({
        id: ws.id,
        name: ws.name,
        isOwner: true,
    }));

    const currentWorkspaceInfo = currentWorkspace ? {
        id: currentWorkspace.id,
        name: currentWorkspace.name,
        isOwner: true,
    } : undefined;

    return (
        <RouteErrorBoundary>
            <div className="flex flex-col h-screen bg-background-primary">
                {/* Unified Header - REMOVED, managed by root _shell.tsx */}

                {/* Main Content */}
                <main className="flex-1 overflow-auto">
                    <Outlet />
                </main>
            </div>
        </RouteErrorBoundary>
    );
}

export default Layout;
