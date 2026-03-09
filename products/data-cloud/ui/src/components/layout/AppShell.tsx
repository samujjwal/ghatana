/**
 * App Shell Component
 * 
 * Main application layout wrapper that provides:
 * - Global search (Cmd/Ctrl + K)
 * - Keyboard shortcuts palette (Cmd/Ctrl + /)
 * - AI Assistant
 * - WebSocket connection management
 * 
 * @doc.type component
 * @doc.purpose Application layout wrapper
 * @doc.layer frontend
 * @doc.pattern Layout Component
 */

import React, { useEffect } from 'react';
import { GlobalSearch, useGlobalSearch } from '../common/GlobalSearch';
import { KeyboardShortcuts, useKeyboardShortcuts } from '../common/KeyboardShortcuts';
import { AiAssistant, useAiAssistant, AiAssistantTrigger } from '../ai/AiAssistant';
import { useWebSocketAutoConnect, useWebSocketState } from '../../lib/websocket';
import { cn, badgeStyles } from '../../lib/theme';

interface AppShellProps {
    children: React.ReactNode;
}

/**
 * App Shell Component
 * 
 * Wraps the application with global features like search, shortcuts, and AI assistant.
 */
export function AppShell({ children }: AppShellProps): React.ReactElement {
    const globalSearch = useGlobalSearch();
    const keyboardShortcuts = useKeyboardShortcuts();
    const aiAssistant = useAiAssistant();
    const wsState = useWebSocketState();

    const wsEnabled = import.meta.env.PROD || Boolean(import.meta.env.VITE_WS_URL);

    // Auto-connect WebSocket
    useWebSocketAutoConnect();

    return (
        <>
            {/* Main Content */}
            {children}

            {/* Global Search Modal */}
            <GlobalSearch
                isOpen={globalSearch.isOpen}
                onClose={globalSearch.close}
            />

            {/* Keyboard Shortcuts Modal */}
            <KeyboardShortcuts
                isOpen={keyboardShortcuts.isOpen}
                onClose={keyboardShortcuts.close}
            />

            {/* AI Assistant */}
            {aiAssistant.isOpen ? (
                <AiAssistant
                    isOpen={aiAssistant.isOpen}
                    onClose={aiAssistant.close}
                />
            ) : (
                <AiAssistantTrigger onClick={aiAssistant.open} />
            )}

            {/* WebSocket Status Indicator (optional, shown in dev) */}
            {import.meta.env.DEV && wsEnabled && wsState !== 'connected' && (
                <div className={cn(
                    'fixed bottom-4 left-4 z-40 px-3 py-1.5 rounded-full text-xs font-medium',
                    wsState === 'connecting' && badgeStyles.warning,
                    wsState === 'reconnecting' && badgeStyles.warning,
                    wsState === 'disconnected' && badgeStyles.danger
                )}>
                    WS: {wsState}
                </div>
            )}
        </>
    );
}

export default AppShell;
