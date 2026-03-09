/**
 * Unified Playground Toolbar
 * 
 * A simple toolbar that allows users to add different types of content
 * directly to the canvas without mode switching. This integrates with
 * the existing playground system.
 * 
 * @doc.type component
 * @doc.purpose Simple toolbar for unified canvas content creation
 * @doc.layer product
 * @doc.pattern Integration Component
 */

import React, { useCallback } from 'react';
import { Box, IconButton, Tooltip, ToggleButtonGroup as ButtonGroup, Typography } from '@ghatana/ui';
import { Paintbrush as Brush, GitBranch as AccountTree, Code, FileText as Description, Plus as Add, ZoomIn, ZoomOut, Focus as CenterFocusStrong } from 'lucide-react';
import { useSetAtom } from 'jotai';
import { nodesAtom } from './workspace/canvasAtoms';
import type { Node } from '@xyflow/react';

// Simple node data interface that works with existing system
interface SimpleNodeData extends Record<string, unknown> {
    id: string;
    title: string;
    type: string;
    content: unknown;
}

interface UnifiedPlaygroundToolbarProps {
    onZoomIn?: () => void;
    onZoomOut?: () => void;
    onFitView?: () => void;
    className?: string;
}

export const UnifiedPlaygroundToolbar: React.FC<UnifiedPlaygroundToolbarProps> = ({
    onZoomIn,
    onZoomOut,
    onFitView,
    className = ''
}) => {
    const setNodes = useSetAtom(nodesAtom);

    const createSimpleNode = useCallback((type: string, title: string, content: unknown, position?: { x: number; y: number }) => {
        const newNode: Node<SimpleNodeData> = {
            id: `node-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            type: 'enhancedUnified', // Use enhanced unified node type
            position: position || {
                x: Math.random() * 400 + 100,
                y: Math.random() * 300 + 100
            },
            data: {
                id: `data-${Date.now()}`,
                title,
                type: type,
                contentType: type as unknown,
                content,
                isEditing: false,
                isFavorite: false,
                tags: [type, 'playground'],
                collaborators: [
                    { id: '1', name: 'You', avatar: '' }
                ],
                lastModified: new Date().toISOString(),
                version: 1,
            } as SimpleNodeData,
        };

        setNodes(prev => [...prev, newNode as unknown]);
    }, [setNodes]);

    const handleAddSketch = useCallback(() => {
        createSimpleNode(
            'sketch',
            'Sketch Canvas',
            { strokes: [], tool: 'pen' },
            { x: 200, y: 200 }
        );
    }, [createSimpleNode]);

    const handleAddDiagram = useCallback(() => {
        createSimpleNode(
            'diagram',
            'Mermaid Diagram',
            {
                type: 'mermaid',
                content: 'graph TD\n  A[Start] --> B[Process]\n  B --> C[End]'
            },
            { x: 400, y: 200 }
        );
    }, [createSimpleNode]);

    const handleAddCode = useCallback(() => {
        createSimpleNode(
            'code',
            'Code Snippet',
            {
                language: 'javascript',
                content: '// Write your code here\nfunction example() {\n  console.log("Hello World");\n}'
            },
            { x: 600, y: 200 }
        );
    }, [createSimpleNode]);

    const handleAddArtifact = useCallback(() => {
        createSimpleNode(
            'artifact',
            'Artifact Node',
            {
                artifactType: 'requirement',
                status: 'draft',
                description: 'New requirement artifact'
            },
            { x: 800, y: 200 }
        );
    }, [createSimpleNode]);

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
        </Box>
    );
};
