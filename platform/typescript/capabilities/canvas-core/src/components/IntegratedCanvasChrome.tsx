/**
 * Integrated Canvas Chrome Component
 * 
 * Complete integration of all canvas chrome components with multi-layer system.
 * Provides unified interface for layer detection, action management, and panel control.
 * 
 * @doc.type component
 * @doc.purpose Integrated canvas chrome system
 * @doc.layer presentation
 */

import React, { useState, useEffect, ReactNode } from 'react';
import { Provider } from 'jotai';
import {
    CanvasChromeLayout,
    TopBar,
    LeftRail,
    LeftPanel,
    ZoomHUD,
    chromeLeftPanelAtom,
    LeftPanelType,
} from '../chrome';
import { useAtom } from 'jotai';
import { OutlinePanel } from './panels/OutlinePanel';
import { LayersPanel } from './panels/LayersPanel';
import { PalettePanel } from './panels/PalettePanel';
import { CommandPalette } from './CommandPalette';
import { EnhancedContextMenu } from './EnhancedContextMenu';
import { SmartContextBar } from './SmartContextBar';
import { useLayerDetection } from '../hooks/useLayerDetection';
import { useAvailableActions } from '../hooks/useAvailableActions';
import { initializeActionRegistry } from '../actions/action-initializer';
import { connectActionHandlers } from '../actions/action-handlers-connector';

interface IntegratedCanvasChromeProps {
    children: ReactNode;
    projectName?: string;
    onProjectChange?: () => void;
    enableLayerDetection?: boolean;
    enableCommandPalette?: boolean;
    enableContextMenu?: boolean;
}

/**
 * Internal component with Jotai context
 */
const IntegratedCanvasChromeInner: React.FC<IntegratedCanvasChromeProps> = ({
    children,
    projectName = 'Canvas Project',
    onProjectChange,
    enableLayerDetection = true,
    enableCommandPalette = true,
    enableContextMenu = true,
}) => {
    const [leftPanel, setLeftPanel] = useAtom(chromeLeftPanelAtom);
    const [commandPaletteOpen, setCommandPaletteOpen] = useState(false);
    const [contextMenuState, setContextMenuState] = useState<{
        isOpen: boolean;
        position: { x: number; y: number };
        selection?: 'none' | 'single' | 'multiple';
    }>({
        isOpen: false,
        position: { x: 0, y: 0 },
        selection: 'none',
    });

    // Initialize action system
    useEffect(() => {
        console.log('🚀 Initializing Canvas Multi-Layer System...');
        initializeActionRegistry();
        connectActionHandlers();
        console.log('✅ Canvas Multi-Layer System initialized');
    }, []);

    // Enable layer detection
    useLayerDetection({
        enabled: enableLayerDetection,
        onLayerChange: (layer, previousLayer) => {
            console.log(`🔍 Layer changed: ${previousLayer} → ${layer}`);
        },
    });

    // Load available actions
    useAvailableActions();

    // Handle keyboard shortcuts
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            // Command Palette (Cmd/Ctrl + K)
            if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
                e.preventDefault();
                if (enableCommandPalette) {
                    setCommandPaletteOpen(true);
                }
            }

            // Panel shortcuts
            if ((e.metaKey || e.ctrlKey) && !e.shiftKey) {
                switch (e.key) {
                    case '1':
                        e.preventDefault();
                        setLeftPanel(leftPanel === 'outline' ? null : 'outline');
                        break;
                    case '2':
                        e.preventDefault();
                        setLeftPanel(leftPanel === 'layers' ? null : 'layers');
                        break;
                    case '3':
                        e.preventDefault();
                        setLeftPanel(leftPanel === 'palette' ? null : 'palette');
                        break;
                    case '4':
                        e.preventDefault();
                        setLeftPanel(leftPanel === 'tasks' ? null : 'tasks');
                        break;
                    case '5':
                        e.preventDefault();
                        setLeftPanel(leftPanel === 'minimap' ? null : 'minimap');
                        break;
                }
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [leftPanel, setLeftPanel, enableCommandPalette]);

    // Handle right-click for context menu
    useEffect(() => {
        const handleContextMenu = (e: MouseEvent) => {
            if (enableContextMenu) {
                e.preventDefault();
                setContextMenuState({
                    isOpen: true,
                    position: { x: e.clientX, y: e.clientY },
                    selection: 'none',
                });
            }
        };

        window.addEventListener('contextmenu', handleContextMenu);
        return () => window.removeEventListener('contextmenu', handleContextMenu);
    }, [enableContextMenu]);

    // Render custom panel content
    const renderPanelContent = () => {
        switch (leftPanel) {
            case 'outline':
                return <OutlinePanel onClose={() => setLeftPanel(null)} />;
            case 'layers':
                return <LayersPanel onClose={() => setLeftPanel(null)} />;
            case 'palette':
                return <PalettePanel onClose={() => setLeftPanel(null)} />;
            case 'tasks':
                return (
                    <LeftPanel type="tasks" onClose={() => setLeftPanel(null)}>
                        <div style={{ padding: '16px' }}>
                            <p style={{ color: '#6b7280', fontSize: '14px' }}>
                                Tasks panel - Coming soon
                            </p>
                        </div>
                    </LeftPanel>
                );
            case 'minimap':
                return (
                    <LeftPanel type="minimap" onClose={() => setLeftPanel(null)}>
                        <div style={{ padding: '16px' }}>
                            <p style={{ color: '#6b7280', fontSize: '14px' }}>
                                Minimap - Coming soon
                            </p>
                        </div>
                    </LeftPanel>
                );
            default:
                return null;
        }
    };

    return (
        <div style={{ width: '100%', height: '100vh', position: 'relative' }}>
            {/* Top Bar */}
            <TopBar
                projectName={projectName}
                onProjectChange={onProjectChange}
                onSearch={() => setCommandPaletteOpen(true)}
                onShare={() => console.log('Share clicked')}
                onSettings={() => console.log('Settings clicked')}
            />

            {/* Main Layout */}
            <div style={{ display: 'flex', height: 'calc(100vh - 56px)' }}>
                {/* Left Rail */}
                <LeftRail visible={true} />

                {/* Left Panel */}
                {renderPanelContent()}

                {/* Canvas Content */}
                <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
                    {children}

                    {/* Smart Context Bar */}
                    <SmartContextBar position={{ x: 100, y: 100 }} />

                    {/* Zoom HUD */}
                    <ZoomHUD />
                </div>
            </div>

            {/* Command Palette */}
            {enableCommandPalette && (
                <CommandPalette
                    isOpen={commandPaletteOpen}
                    onClose={() => setCommandPaletteOpen(false)}
                />
            )}

            {/* Context Menu */}
            {enableContextMenu && (
                <EnhancedContextMenu
                    isOpen={contextMenuState.isOpen}
                    position={contextMenuState.position}
                    selection={contextMenuState.selection}
                    onClose={() =>
                        setContextMenuState({ ...contextMenuState, isOpen: false })
                    }
                />
            )}
        </div>
    );
};

/**
 * Integrated Canvas Chrome with Jotai Provider
 */
export const IntegratedCanvasChrome: React.FC<IntegratedCanvasChromeProps> = (
    props
) => {
    return (
        <Provider>
            <IntegratedCanvasChromeInner {...props} />
        </Provider>
    );
};
