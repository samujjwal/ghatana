/**
 * CanvasScene compatibility adapter
 * @deprecated Use CanvasRoute directly.
 * @doc.type component
 * @doc.purpose Legacy route compatibility adapter
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