/**
 * Canvas Playground
 *
 * Developer UI to exercise different flavors of the canvas quickly.
 * Provides buttons to insert sample nodes (sketch, drawing, diagram, code),
 * switch tools (pen, select, pan), run predictive layout, run codegen, and
 * toggle semantic zoom levels.
 *
 * Note: This is a dev-only component that operates at a lower level than the
 * Canvas API, using singletons directly to avoid requiring a CanvasProvider.
 * In production, use the Canvas API hooks instead.
 *
 * @doc.type component
 * @doc.purpose Dev playground to exercise canvas features
 * @doc.layer dev
 * @doc.pattern Tooling
 */

import React, { useCallback } from 'react';
import { Box } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Stack } from '@ghatana/ui';
import { Typography } from '@ghatana/ui';
import { Paintbrush as BrushIcon } from 'lucide-react';
import { PenTool as GestureIcon } from 'lucide-react';
import { GitBranch as AccountTreeIcon } from 'lucide-react';
import { Code as CodeIcon } from 'lucide-react';
import { MousePointer as MouseIcon } from 'lucide-react';
import { Hand as PanToolIcon } from 'lucide-react';
import { Wand2 as AutoFixHighIcon } from 'lucide-react';
import { Hammer as BuildIcon } from 'lucide-react';
import { ZoomIn as ZoomInIcon } from 'lucide-react';
import { ZoomOut as ZoomOutIcon } from 'lucide-react';
import { Trash as DeleteOutlineIcon } from 'lucide-react';

import { getArtifactRegistry } from '../registry/ArtifactRegistry';
import { ToolManager, BuiltInTools } from '../interaction/ToolManager';
import { getPredictiveLayout } from '../ai/PredictiveLayout';
import { getShadowCodegen } from '../ai/ShadowCodegen';
import { useSemanticZoom } from '../renderer/useSemanticZoom';
import type { UniversalNode } from '../model/contracts';

export interface CanvasPlaygroundProps {
    onInsertNode?: (kind: string) => void;
    onClearCanvas?: () => void;
    onRunPredictiveLayout?: () => void;
    onRunCodegen?: () => void;
    onSelectTool?: (tool: string) => void;
    onZoom?: (direction: 'in' | 'out') => void;
    onResync?: () => void;
}

export const CanvasPlayground: React.FC<CanvasPlaygroundProps> = ({
    onInsertNode,
    onClearCanvas,
    onRunPredictiveLayout,
    onRunCodegen,
    onSelectTool,
    onZoom
}) => {
    const registry = getArtifactRegistry();
    const toolManager = ToolManager.getInstance();
    const predictive = getPredictiveLayout();
    const codegen = getShadowCodegen();
    const { getNextZoomTarget } = useSemanticZoom(1);

    // Stub implementations: In dev mode, these buttons don't have a backing Canvas API.
    // In production, CanvasPlayground should be removed or wrapped in a CanvasProvider.
    const insertNode = useCallback((kind: string, _overrides?: Partial<UniversalNode>) => {
        if (onInsertNode) {
            onInsertNode(kind);
            return;
        }
        // eslint-disable-next-line no-console
        console.log(`[Dev Playground] Would insert node of kind: ${kind}`);
        alert(`Dev mode: Would insert ${kind} node. Attach playground inside a CanvasProvider to enable.`);
    }, [onInsertNode]);

    // Convenience wrappers for specific sample nodes
    const insertSketch = useCallback(() => insertNode('drawing:sketch'), [insertNode]);
    const insertDrawing = useCallback(() => insertNode('drawing:freehand'), [insertNode]);
    const insertDiagram = useCallback(() => insertNode('diagram:flowchart'), [insertNode]);
    const insertCode = useCallback(() => insertNode('code:block'), [insertNode]);

    const clearCanvas = useCallback(() => {
        if (onClearCanvas) {
            onClearCanvas();
            return;
        }
        // eslint-disable-next-line no-console
        console.log('[Dev Playground] Would clear canvas');
        alert('Dev mode: Would clear canvas. Attach playground inside a CanvasProvider to enable.');
    }, [onClearCanvas]);

    const switchToPen = useCallback(() => {
        if (onSelectTool) {
            onSelectTool(BuiltInTools.PEN);
            return;
        }
        toolManager.activateTool(BuiltInTools.PEN);
    }, [toolManager, onSelectTool]);

    const switchToSelect = useCallback(() => {
        if (onSelectTool) {
            onSelectTool(BuiltInTools.SELECT);
            return;
        }
        toolManager.activateTool(BuiltInTools.SELECT);
    }, [toolManager, onSelectTool]);

    const switchToPan = useCallback(() => {
        if (onSelectTool) {
            onSelectTool(BuiltInTools.PAN);
            return;
        }
        toolManager.activateTool(BuiltInTools.PAN);
    }, [toolManager, onSelectTool]);

    const applyPredictive = useCallback(() => {
        if (onRunPredictiveLayout) {
            onRunPredictiveLayout();
            return;
        }
        // eslint-disable-next-line no-console
        console.log('[Dev Playground] Would apply predictive layout');
        alert('Dev mode: Would apply predictive layout. Attach playground inside a CanvasProvider to enable.');
    }, [onRunPredictiveLayout]);

    const runCodegen = useCallback(async () => {
        if (onRunCodegen) {
            onRunCodegen();
            return;
        }
        // eslint-disable-next-line no-console
        console.log('[Dev Playground] Would run codegen');
        alert('Dev mode: Would run codegen. Attach playground inside a CanvasProvider to enable.');
    }, [onRunCodegen]);

    const zoomInSemantic = useCallback(() => {
        if (onZoom) {
            onZoom('in');
            return;
        }
        const next = getNextZoomTarget('in');
        if (next) {
            // eslint-disable-next-line no-console
            console.log(`[Dev Playground] Would zoom in to ${next}`);
        }
    }, [getNextZoomTarget, onZoom]);

    const zoomOutSemantic = useCallback(() => {
        if (onZoom) {
            onZoom('out');
            return;
        }
        const next = getNextZoomTarget('out');
        if (next) {
            // eslint-disable-next-line no-console
            console.log(`[Dev Playground] Would zoom out to ${next}`);
        }
    }, [getNextZoomTarget, onZoom]);

    return (
        <Box className="p-2 flex flex-col gap-2 rounded overflow-y-auto max-w-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 shadow max-h-[calc(100vh - 120px)]">
            <Typography as="p" className="text-sm font-medium" className="font-semibold mb-1">Canvas Playground</Typography>

            {/* Insert Nodes Section */}
            <Stack direction="row" spacing={0.5} flexWrap="wrap" alignItems="center">
                <Typography as="span" className="text-xs text-gray-500" className="w-full mb-1 text-gray-500 dark:text-gray-400">Insert:</Typography>
                <Button size="sm" variant="outlined" startIcon={<BrushIcon />} onClick={insertSketch} aria-label="Insert Sketch">Sketch</Button>
                <Button size="sm" variant="outlined" startIcon={<GestureIcon />} onClick={insertDrawing} aria-label="Insert Drawing">Drawing</Button>
                <Button size="sm" variant="outlined" startIcon={<AccountTreeIcon />} onClick={insertDiagram} aria-label="Insert Diagram">Diagram</Button>
                <Button size="sm" variant="outlined" startIcon={<CodeIcon />} onClick={insertCode} aria-label="Insert Code Block">Code</Button>
            </Stack>

            {/* Tools Section */}
            <Stack direction="row" spacing={0.5} flexWrap="wrap" alignItems="center">
                <Typography as="span" className="text-xs text-gray-500" className="w-full mb-1 text-gray-500 dark:text-gray-400">Tools:</Typography>
                <Button size="sm" variant="ghost" startIcon={<BrushIcon />} onClick={switchToPen} aria-label="Switch to Pen Tool">Pen</Button>
                <Button size="sm" variant="ghost" startIcon={<MouseIcon />} onClick={switchToSelect} aria-label="Switch to Select Tool">Select</Button>
                <Button size="sm" variant="ghost" startIcon={<PanToolIcon />} onClick={switchToPan} aria-label="Switch to Pan Tool">Pan</Button>
            </Stack>

            {/* Actions Section */}
            <Stack direction="row" spacing={0.5} flexWrap="wrap" alignItems="center">
                <Typography as="span" className="text-xs text-gray-500" className="w-full mb-1 text-gray-500 dark:text-gray-400">Actions:</Typography>
                <Button size="sm" variant="outlined" startIcon={<AutoFixHighIcon />} onClick={applyPredictive} aria-label="Apply Predictive Layout">Layout</Button>
                <Button size="sm" variant="outlined" startIcon={<BuildIcon />} onClick={runCodegen} aria-label="Run Codegen">Codegen</Button>
                <Button size="sm" variant="outlined" startIcon={<ZoomInIcon />} onClick={zoomInSemantic} aria-label="Semantic Zoom In">Zoom+</Button>
                <Button size="sm" variant="outlined" startIcon={<ZoomOutIcon />} onClick={zoomOutSemantic} aria-label="Semantic Zoom Out">Zoom-</Button>
            </Stack>

            {/* Canvas Operations Section */}
            <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="space-between" alignItems="center" className="pt-1 border-gray-200 dark:border-gray-700 border-t" >
                <Button size="sm" variant="outlined" tone="danger" startIcon={<DeleteOutlineIcon />} onClick={clearCanvas} aria-label="Clear Canvas">Clear</Button>
                <Button size="sm" variant="ghost" onClick={() => { if (onResync) onResync(); else alert('Dev mode: Resync not available'); }} aria-label="Resync Canvas">Resync</Button>
            </Stack>
        </Box>
    );
};

export default CanvasPlayground;
