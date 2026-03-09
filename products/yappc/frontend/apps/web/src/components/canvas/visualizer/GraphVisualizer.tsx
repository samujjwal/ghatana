/**
 * Graph Visualizer Component
 * 
 * Auto-generates system diagrams from canvas artifacts and relationships.
 * 
 * @doc.type component
 * @doc.purpose High-level system visualization
 * @doc.layer product
 * @doc.pattern Visualization Component
 */

import React, { useState, useEffect } from 'react';
import { Box, Typography, Button, Surface as Paper, Stack, Spinner as CircularProgress, Tooltip, IconButton } from '@ghatana/ui';
import { Download, RefreshCw as Refresh, Maximize2 as Fullscreen, Download as FileDownload, Sparkles as AutoAwesome } from 'lucide-react';

export interface GraphVisualizerProps {
    nodes: unknown[];
    edges: unknown[];
    onExport?: (format: 'png' | 'svg' | 'mermaid') => void;
}

/**
 * GraphVisualizer Component
 */
export const GraphVisualizer: React.FC<GraphVisualizerProps> = ({ nodes, edges, onExport }) => {
    const [generating, setGenerating] = useState(false);
    const [mermaidCode, setMermaidCode] = useState('');

    useEffect(() => {
        // Simple Mermaid string generation logic
        const generateMermaid = () => {
            let code = 'graph TD\n';

            // Add nodes
            nodes.forEach(node => {
                const label = node.data?.title || node.id;
                // Escape characters for mermaid
                const safeLabel = label.replace(/[()]/g, '');
                code += `  ${node.id}["${safeLabel}"]\n`;
            });

            // Add edges
            edges.forEach(edge => {
                code += `  ${edge.source} --> ${edge.target}\n`;
            });

            setMermaidCode(code);
        };

        generateMermaid();
    }, [nodes, edges]);

    const handleGenerate = () => {
        setGenerating(true);
        setTimeout(() => {
            setGenerating(false);
        }, 1500);
    };

    return (
        <Box className="py-2">
            <Box className="flex justify-between items-center mb-4">
                <Typography as="span" className="text-xs uppercase tracking-wider" color="text.secondary" fontWeight="800">
                    System Graph (Mermaid)
                </Typography>
                <Stack direction="row" spacing={1}>
                    <Tooltip title="Refresh diagram">
                        <IconButton size="sm" onClick={handleGenerate}>
                            <Refresh className="text-lg" />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Export SVG">
                        <IconButton size="sm" onClick={() => onExport?.('svg')}>
                            <FileDownload className="text-lg" />
                        </IconButton>
                    </Tooltip>
                </Stack>
            </Box>

            <Paper
                variant="outlined"
                className="p-4 rounded-lg relative flex flex-col justify-center items-center overflow-hidden min-h-[200px]"
                style={{ backgroundColor: 'rgba(148, 163, 184, 0.08)' }}
            >
                {generating ? (
                    <Stack alignItems="center" spacing={2}>
                        <CircularProgress size={24} />
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            Analyzing architecture...
                        </Typography>
                    </Stack>
                ) : (
                    <Box className="w-full">
                        <Box className="p-4 rounded mb-4 text-xs overflow-auto bg-white dark:bg-gray-900 border border-solid border-gray-200 dark:border-gray-700 font-mono max-h-[200px] whitespace-pre">
                            {mermaidCode}
                        </Box>

                        <Box className="text-center">
                            <Button
                                variant="outlined"
                                size="sm"
                                startIcon={<AutoAwesome />}
                                onClick={handleGenerate}
                                className="rounded-lg"
                            >
                                Render Preview
                            </Button>
                        </Box>
                    </Box>
                )}
            </Paper>

            <Typography as="span" className="text-xs text-gray-500 mt-4 block" color="text.disabled">
                * Auto-generated from {nodes.length} nodes and {edges.length} relationships.
            </Typography>
        </Box>
    );
};
