/**
 * Unified Canvas Toolbar
 * 
 * A simplified toolbar that allows users to create different types of content
 * directly on the canvas without mode switching. Users can click a button
 * to add a sketch, diagram, code, or artifact node directly.
 * 
 * @doc.type component
 * @doc.purpose Simplified toolbar for unified canvas
 * @doc.layer product
 * @doc.pattern Contextual Toolbar
 */

import React, { useCallback } from 'react';
import { Box, IconButton, Tooltip, ToggleButtonGroup as ButtonGroup, Fab, Typography } from '@ghatana/ui';
import { Paintbrush as Brush, GitBranch as AccountTree, Code, FileText as Description, Plus as Add, ZoomIn, ZoomOut, Focus as CenterFocusStrong } from 'lucide-react';
import { useSetAtom } from 'jotai';
import { nodesAtom } from '../workspace/canvasAtoms';
import type { UnifiedNodeData, UnifiedContentType } from './UnifiedCanvasNode';

interface UnifiedCanvasToolbarProps {
    onZoomIn?: () => void;
    onZoomOut?: () => void;
    onFitView?: () => void;
    className?: string;
}

export const UnifiedCanvasToolbar: React.FC<UnifiedCanvasToolbarProps> = ({
    onZoomIn,
    onZoomOut,
    onFitView,
    className = ''
}) => {
    const setNodes = useSetAtom(nodesAtom);

    const createNode = useCallback((contentType: UnifiedContentType, position?: { x: number; y: number }) => {
        const newNode: UnifiedNodeData = {
            id: `node-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            type: contentType,
            title: `${contentType.charAt(0).toUpperCase() + contentType.slice(1)} Node`,
            status: 'draft',
            phase: 'current',
            width: 300,
            height: 200,
        };

        // Add content-specific default data
        switch (contentType) {
            case 'artifact':
                newNode.artifactData = {
                    id: newNode.id,
                    type: 'requirement',
                    title: newNode.title,
                    status: 'draft',
                    phase: 'current',
                };
                break;
            case 'sketch':
                newNode.sketchData = {
                    strokes: [],
                };
                break;
            case 'diagram':
                newNode.diagramData = {
                    type: 'mermaid',
                    content: 'graph TD\n  A[Start] --> B[Process]\n  B --> C[End]',
                };
                break;
            case 'code':
                newNode.codeData = {
                    language: 'javascript',
                    content: '// Write your code here\nfunction example() {\n  console.log("Hello World");\n}',
                };
                break;
        }

        // Add node to canvas at specified position or center
        const reactFlowNode = {
            id: newNode.id,
            type: 'unified',
            position: position || { x: 400, y: 200 },
            data: newNode,
        };

        setNodes(prev => [...prev, reactFlowNode]);
    }, [setNodes]);

    const handleAddSketch = useCallback(() => {
        createNode('sketch');
    }, [createNode]);

    const handleAddDiagram = useCallback(() => {
        createNode('diagram');
    }, [createNode]);

    const handleAddCode = useCallback(() => {
        createNode('code');
    }, [createNode]);

    const handleAddArtifact = useCallback(() => {
        createNode('artifact');
    }, [createNode]);

    return (
        <Box
            className={className}
            className="fixed flex flex-col gap-4 top-[80px] left-[16px] z-[1000]"
        >
            {/* Content Creation Buttons */}
            <Box className="flex flex-col gap-2">
                <Typography as="span" className="text-xs text-gray-500" className="font-bold mb-2 text-gray-500 dark:text-gray-400">
                    ADD CONTENT
                </Typography>

                <ButtonGroup orientation="vertical" size="sm">
                    <Tooltip title="Add Sketch" placement="right">
                        <IconButton
                            onClick={handleAddSketch}
                            className="text-white bg-purple-600" >
                            <Brush />
                        </IconButton>
                    </Tooltip>

                    <Tooltip title="Add Diagram" placement="right">
                        <IconButton
                            onClick={handleAddDiagram}
                            className="bg-green-600 text-white hover:bg-green-800"
                        >
                            <AccountTree />
                        </IconButton>
                    </Tooltip>

                    <Tooltip title="Add Code" placement="right">
                        <IconButton
                            onClick={handleAddCode}
                            className="bg-amber-600 text-gray-900 hover:bg-amber-800"
                        >
                            <Code />
                        </IconButton>
                    </Tooltip>

                    <Tooltip title="Add Artifact" placement="right">
                        <IconButton
                            onClick={handleAddArtifact}
                            className="bg-blue-600 text-white hover:bg-blue-800"
                        >
                            <Description />
                        </IconButton>
                    </Tooltip>
                </ButtonGroup>
            </Box>

            {/* Zoom Controls */}
            <Box className="flex flex-col gap-2">
                <Typography as="span" className="text-xs text-gray-500" className="font-bold mb-2 text-gray-500 dark:text-gray-400">
                    VIEW
                </Typography>

                <ButtonGroup orientation="vertical" size="sm">
                    <Tooltip title="Zoom In" placement="right">
                        <IconButton onClick={onZoomIn}>
                            <ZoomIn />
                        </IconButton>
                    </Tooltip>

                    <Tooltip title="Zoom Out" placement="right">
                        <IconButton onClick={onZoomOut}>
                            <ZoomOut />
                        </IconButton>
                    </Tooltip>

                    <Tooltip title="Fit View" placement="right">
                        <IconButton onClick={onFitView}>
                            <CenterFocusStrong />
                        </IconButton>
                    </Tooltip>
                </ButtonGroup>
            </Box>

            {/* Floating Add Button (Alternative) */}
            <Fab
                tone="primary"
                size="sm"
                onClick={handleAddArtifact}
                className="fixed bottom-[24px] right-[24px] z-[1000]"
            >
                <Add />
            </Fab>
        </Box>
    );
};
