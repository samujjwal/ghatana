/**
 * Canvas Workspace Provider
 * 
 * Wraps CanvasWorkspace with Jotai provider for centralized state management.
 * 
 * @doc.type component
 * @doc.purpose State provider for canvas workspace
 * @doc.layer product
 * @doc.pattern Provider Container
 */

import React from 'react';
import { Provider as JotaiProvider } from 'jotai';
import { CanvasWorkspace, type CanvasWorkspaceProps } from './CanvasWorkspace';
import { CanvasRegistryProvider } from './registry';

/**
 * CanvasWorkspaceProvider
 * 
 * Wraps CanvasWorkspace with Jotai provider to enable centralized Jotai atom state
 * management across all canvas components.
 */
export const CanvasWorkspaceProvider: React.FC<CanvasWorkspaceProps> = (props) => {
    return (
        <JotaiProvider>
            <CanvasRegistryProvider>
                <CanvasWorkspace {...props} />
            </CanvasRegistryProvider>
        </JotaiProvider>
    );
};

export default CanvasWorkspaceProvider;
