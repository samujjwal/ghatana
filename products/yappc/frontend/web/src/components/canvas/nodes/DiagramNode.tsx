/**
 * Diagram Node Component
 * 
 * Canvas node for embedded Mermaid diagrams. Each instance stores its own
 * content in `node.data.diagramContent`, falling back to a default value
 * when no per-node content is set. Eliminates the shared global atom issue.
 * 
 * @doc.type component
 * @doc.purpose Per-instance diagram node
 * @doc.layer product
 * @doc.pattern ReactFlow Custom Node
 */

import React, { memo } from 'react';
import { Handle, Position, NodeResizer, type Node, type NodeProps } from '@xyflow/react';
import { useAtomValue } from 'jotai';
import { MermaidDiagram } from '../diagram/MermaidDiagram';
import { CanvasErrorBoundary } from '../CanvasErrorBoundary';
import { CanvasContentWrapper } from '../CanvasContentWrapper';
import { canvasInteractionModeAtom, cameraZoomAtom } from '../workspace';

export type DiagramNodeData = Record<string, unknown> & {
    label?: string;
    /** Per-node diagram content — avoids shared global state */
    diagramContent?: string;
    /** Per-node diagram zoom */
    diagramZoom?: number;
};

const DEFAULT_CONTENT = 'graph TD\n  A[Start] --> B[End]';

type DiagramCanvasNode = Node<DiagramNodeData, 'diagram'>;

const DiagramNodeInner = ({ data, selected }: NodeProps<DiagramCanvasNode>) => {
    const content = data.diagramContent ?? DEFAULT_CONTENT;
    const interactionMode = useAtomValue(canvasInteractionModeAtom);
    const canvasZoom = useAtomValue(cameraZoomAtom);

    return (
        <>
            <NodeResizer
                minWidth={400}
                minHeight={300}
                isVisible={selected && interactionMode === 'navigate'}
                handleStyle={{ width: 10 / canvasZoom, height: 10 / canvasZoom }}
            />
            <div
                role="group"
                aria-label={`Diagram: ${data.label || 'System Architecture Diagram'}`}
                aria-selected={selected}
                className={`bg-white dark:bg-surface rounded-xl shadow-lg border-2 w-full h-full ${
                    selected ? 'border-info-border ring-2 ring-blue-200' : 'border-border dark:border-border'
                }`}
            >
                <div className="text-sm text-fg-muted dark:text-fg-muted font-semibold p-3 border-b border-border dark:border-border drag-handle cursor-grab active:cursor-grabbing flex justify-between items-center bg-surface-muted dark:bg-surface rounded-t-xl nodrag">
                    <span>{data.label || 'System Architecture Diagram'}</span>
                </div>
                <CanvasContentWrapper className="p-4 relative">
                    <CanvasErrorBoundary label="Diagram">
                        <MermaidDiagram content={content} />
                    </CanvasErrorBoundary>
                </CanvasContentWrapper>
                <Handle type="target" position={Position.Top} className="w-3 h-3" style={{ background: 'var(--info-color, #1976d2)' }} />
                <Handle type="source" position={Position.Bottom} className="w-3 h-3" style={{ background: 'var(--info-color, #1976d2)' }} />
                <Handle type="target" position={Position.Left} id="left" className="w-3 h-3" style={{ background: 'var(--info-color, #1976d2)' }} />
                <Handle type="source" position={Position.Right} id="right" className="w-3 h-3" style={{ background: 'var(--info-color, #1976d2)' }} />
            </div>
        </>
    );
};

export const DiagramNode = memo(DiagramNodeInner, (prev, next) =>
    prev.selected === next.selected &&
    prev.data.diagramContent === next.data.diagramContent &&
    prev.data.diagramZoom === next.data.diagramZoom &&
    prev.data.label === next.data.label
);

export default DiagramNode;