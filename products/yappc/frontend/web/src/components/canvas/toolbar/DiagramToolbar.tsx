/**
 * Diagram Toolbar Component
 *
 * Provides diagram template selection and editing controls for the selected
 * DiagramNode. Reads and writes are scoped to the selected node's data via
 * the command pattern (UpdateNodeDataCommand) — not the disconnected global
 * diagramContentAtom.
 *
 * @doc.type component
 * @doc.purpose Diagram configuration UI — per-node read/write via command pattern
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React, { useEffect, useState } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { Surface as Paper, ToggleButtonGroup, ToggleButton, Box, Button, Tooltip, IconButton, Menu, type MenuItem as MenuItemData, Divider, TextField, Dialog, DialogTitle, DialogContent, DialogActions } from '@ghatana/design-system';
import { GitBranch as FlowchartIcon, ArrowLeftRight as SequenceIcon, FileCode as ClassIcon, Share2 as StateIcon, Activity as GanttIcon, HardDrive as ERIcon, Pencil as EditIcon, ZoomIn as ZoomInIcon, ZoomOut as ZoomOutIcon, RefreshCw as ResetIcon } from 'lucide-react';
import {
    diagramTypeAtom,
    diagramZoomAtom,
    selectedNodesAtom,
    nodesAtom,
} from '../workspace/canvasAtoms';
import { executeCommandAtom, UpdateNodeDataCommand } from '../workspace/canvasCommands';
import { MERMAID_TEMPLATES } from '../diagram/MermaidDiagram';

export interface DiagramToolbarProps {
    /** Optional additional class names for the toolbar wrapper */
    className?: string;
}

/**
 * DiagramToolbar - Contextual toolbar for diagram mode.
 *
 * Key design:
 * - Reads `diagramContent` from the **selected DiagramNode's data**, not from
 *   the global `diagramContentAtom` (which DiagramNode never reads from).
 * - Writes go through `UpdateNodeDataCommand` so they participate in undo/redo.
 * - Uses absolute positioning inside the canvas container instead of `fixed`
 *   (avoids stacking-context bugs inside CSS transform contexts).
 */
export const DiagramToolbar: React.FC<DiagramToolbarProps> = ({ className }) => {
    // ── Global atoms (type + zoom are per-session config, not per-node) ──
    const [diagramType, setDiagramType] = useAtom(diagramTypeAtom);
    const [zoom, setZoom] = useAtom(diagramZoomAtom);

    // ── Per-node write path ────────────────────────────────────────────
    const selectedNodeIds = useAtomValue(selectedNodesAtom);
    const nodes = useAtomValue(nodesAtom);
    const executeCommand = useSetAtom(executeCommandAtom);

    // Find the first selected DiagramNode
    const selectedDiagramNode = nodes.find(
        (n) => n.type === 'diagram' && selectedNodeIds.includes(n.id),
    ) ?? nodes.find((n) => n.type === 'diagram'); // fallback: first diagram node

    const nodeId = selectedDiagramNode?.id ?? null;
    const nodeDiagramContent = (selectedDiagramNode?.data as Record<string, unknown>)?.diagramContent as string | undefined;

    // Local edit buffer for the code editor dialog (avoids live-typing dispatching commands)
    const [editBuffer, setEditBuffer] = useState(nodeDiagramContent ?? '');
    const [showEditor, setShowEditor] = useState(false);
    const [templateMenuOpen, setTemplateMenuOpen] = useState(false);

    const templateMenuItems: MenuItemData[] = [
        { key: 'flowchart', label: 'Basic Flowchart', icon: <FlowchartIcon size={16} />, onClick: () => { handleTemplateSelect('flowchart'); setTemplateMenuOpen(false); } },
        { key: 'sequence', label: 'Sequence Diagram', icon: <SequenceIcon size={16} />, onClick: () => { handleTemplateSelect('sequence'); setTemplateMenuOpen(false); } },
        { key: 'class', label: 'Class Diagram', icon: <ClassIcon size={16} />, onClick: () => { handleTemplateSelect('class'); setTemplateMenuOpen(false); } },
        { key: 'state', label: 'State Diagram', icon: <StateIcon size={16} />, onClick: () => { handleTemplateSelect('state'); setTemplateMenuOpen(false); } },
        { key: 'gantt', label: 'Gantt Chart', icon: <GanttIcon size={16} />, onClick: () => { handleTemplateSelect('gantt'); setTemplateMenuOpen(false); } },
        { key: 'erDiagram', label: 'ER Diagram', icon: <ERIcon size={16} />, onClick: () => { handleTemplateSelect('erDiagram'); setTemplateMenuOpen(false); } },
    ];

    // Sync buffer when the selected node changes or its content changes externally
    useEffect(() => {
        setEditBuffer(nodeDiagramContent ?? '');
    }, [nodeDiagramContent]);

    // ── Helpers ────────────────────────────────────────────────────────
    const writeContent = (newContent: string) => {
        if (!nodeId) return;
        const old = nodeDiagramContent ?? '';
        executeCommand(new UpdateNodeDataCommand(nodeId, { diagramContent: old }, { diagramContent: newContent }, 'Update diagram content'));
    };

    const handleDiagramTypeChange = (newType: string) => {
        if (!newType) return;
        setDiagramType(newType as typeof diagramType);
        // Auto-load template for the selected type and write to the node
        const templateKey = newType === 'mermaid' ? 'flowchart' : newType;
        if (templateKey in MERMAID_TEMPLATES) {
            writeContent(MERMAID_TEMPLATES[templateKey as keyof typeof MERMAID_TEMPLATES]);
        }
    };

    const handleTemplateSelect = (templateKey: keyof typeof MERMAID_TEMPLATES) => {
        writeContent(MERMAID_TEMPLATES[templateKey]);
        setTemplateMenuOpen(false);
    };

    const handleApplyEditor = () => {
        writeContent(editBuffer);
        setShowEditor(false);
    };

    const handleZoomIn = () => setZoom(Math.min(zoom + 0.1, 2));
    const handleZoomOut = () => setZoom(Math.max(zoom - 0.1, 0.5));
    const handleResetZoom = () => setZoom(1);

    return (
        <>
            {/*
             * Use absolute + bottom/left instead of `fixed` — `fixed` breaks
             * inside CSS transform contexts (the ReactFlow viewport element).
             * className prop is added via style to avoid the duplicate-className
             * JSX bug where the second className silently wins.
             */}
            <Paper
                elevation={3}
                style={{ position: 'absolute', bottom: 16, left: '50%', transform: 'translateX(-50%)' }}
                className={[
                    'p-3 flex gap-4 items-center z-[1000] bg-white/95 dark:bg-gray-900/95',
                    className ?? '',
                ].join(' ').trim()}
                data-testid="diagram-toolbar"
            >
                {/* Diagram Type Selector */}
                <ToggleButtonGroup
                    value={diagramType}
                    exclusive
                    onChange={(value) => {
                        if (typeof value === 'string') {
                            handleDiagramTypeChange(value);
                        }
                    }}
                    size="sm"
                    aria-label="diagram type"
                >
                    <ToggleButton value="flowchart" aria-label="flowchart">
                        <Tooltip title="Flowchart">
                            <FlowchartIcon />
                        </Tooltip>
                    </ToggleButton>
                    <ToggleButton value="sequence" aria-label="sequence diagram">
                        <Tooltip title="Sequence Diagram">
                            <SequenceIcon />
                        </Tooltip>
                    </ToggleButton>
                    <ToggleButton value="class" aria-label="class diagram">
                        <Tooltip title="Class Diagram">
                            <ClassIcon />
                        </Tooltip>
                    </ToggleButton>
                </ToggleButtonGroup>

                <Divider orientation="vertical" flexItem />

                {/* Template Selector */}
                <Menu
                    items={templateMenuItems}
                    open={templateMenuOpen}
                    onOpenChange={setTemplateMenuOpen}
                    trigger={
                        <Button
                            variant="outlined"
                            size="sm"
                            onClick={() => setTemplateMenuOpen(true)}
                            className="min-w-[120px]"
                            disabled={!nodeId}
                        >
                            Templates
                        </Button>
                    }
                />

                <Divider orientation="vertical" flexItem />

                {/* Editor Toggle */}
                <Button
                    variant={showEditor ? 'contained' : 'outlined'}
                    size="sm"
                    startIcon={<EditIcon />}
                    onClick={() => {
                        // Sync buffer from current node content before opening
                        setEditBuffer(nodeDiagramContent ?? '');
                        setShowEditor(!showEditor);
                    }}
                    disabled={!nodeId}
                >
                    {showEditor ? 'Hide' : 'Edit'} Code
                </Button>

                <Divider orientation="vertical" flexItem />

                {/* Zoom Controls */}
                <Box className="flex gap-1 items-center">
                    <Tooltip title="Zoom Out">
                        <IconButton size="sm" aria-label="Zoom Out" onClick={handleZoomOut} disabled={zoom <= 0.5}>
                            <ZoomOutIcon size={16} />
                        </IconButton>
                    </Tooltip>

                    <Box className="text-center text-sm font-medium min-w-[60px]">
                        {Math.round(zoom * 100)}%
                    </Box>

                    <Tooltip title="Zoom In">
                        <IconButton size="sm" aria-label="Zoom In" onClick={handleZoomIn} disabled={zoom >= 2}>
                            <ZoomInIcon size={16} />
                        </IconButton>
                    </Tooltip>

                    <Tooltip title="Reset Zoom">
                        <IconButton size="sm" aria-label="Reset Zoom" onClick={handleResetZoom}>
                            <ResetIcon size={16} />
                        </IconButton>
                    </Tooltip>
                </Box>
            </Paper>

            {/* Code Editor Dialog — writes to node via UpdateNodeDataCommand on Apply */}
            <Dialog
                open={showEditor}
                onClose={() => setShowEditor(false)}
                size="md"
                fullWidth
            >
                <DialogTitle>Edit Diagram Code</DialogTitle>
                <DialogContent>
                    <label htmlFor="mermaid-code-editor" className="block text-sm font-medium mb-1">Mermaid code</label>
                    <textarea
                        id="mermaid-code-editor"
                        aria-label="Mermaid code"
                        rows={15}
                        value={editBuffer}
                        onChange={(e) => setEditBuffer(e.target.value)}
                        placeholder="Enter Mermaid diagram code..."
                        className="w-full font-mono text-sm border border-gray-300 rounded p-2 resize-y"
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowEditor(false)}>Cancel</Button>
                    <Button
                        variant="solid"
                        onClick={handleApplyEditor}
                        disabled={!nodeId}
                    >
                        Apply
                    </Button>
                </DialogActions>
            </Dialog>
        </>
    );
};
