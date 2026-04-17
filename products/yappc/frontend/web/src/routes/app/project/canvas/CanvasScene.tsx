/**
 * CanvasScene - Legacy compatibility shim
 * @deprecated Use CanvasRoute. The 1252-line legacy component (canvasAtom,
 * @dnd-kit, useCanvasScene) is replaced by CanvasRoute / CanvasWorkspaceProvider.
 * This stub keeps existing describe.skip tests and Storybook paths compilable.
 * @doc.type component
 * @doc.purpose Legacy route compatibility shim
 * @doc.layer product
 * @doc.pattern Adapter
 */
import React from 'react';
import { CanvasRoute } from './CanvasRoute';

/** @deprecated Props kept for call-site compat; real canvas reads params from router. */
export interface CanvasSceneProps {
    projectId?: string;
    canvasId?: string;
}

/** @deprecated Use CanvasRoute directly. */
const CanvasScene: React.FC<CanvasSceneProps> = (_props) => <CanvasRoute />;

export default CanvasScene;