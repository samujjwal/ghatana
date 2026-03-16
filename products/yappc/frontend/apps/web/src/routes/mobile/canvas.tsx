/**
 * Mobile Canvas Route
 * 
 * Touch-optimized canvas view for mobile devices.
 * Simplified interface with essential controls.
 * 
 * @doc.type route
 * @doc.purpose Mobile canvas editing experience
 * @doc.layer product
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useParams } from 'react-router';
import { Capacitor } from '@capacitor/core';
import { Haptics, ImpactStyle } from '@capacitor/haptics';
import { useIsDarkMode } from '@ghatana/theme';
import {
  Box,
  Typography,
  IconButton,
  Fab,
  Card,
  CardContent,
  Skeleton,
  Alert,
} from '@ghatana/ui';
import { Plus as Add, Undo2 as Undo, Redo2 as Redo, Save, ZoomIn, ZoomOut, Layers, Settings, MoreVertical as MoreVert, Pointer as TouchApp, CheckCircle } from 'lucide-react';
import { logger } from '../../utils/Logger';

interface CanvasNode {
    id: string;
    type: string;
    label: string;
    x: number;
    y: number;
}

/**
 * Mobile Canvas Component - Touch-optimized canvas interface
 */
export default function MobileCanvasRoute() {
    const { projectId } = useParams();
    const navigate = useNavigate();
    const isDarkMode = useIsDarkMode();
    const [nodes, setNodes] = useState<CanvasNode[]>([]);
    const [selectedNode, setSelectedNode] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [zoom, setZoom] = useState(1);
    const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle');

    // Local undo/redo history stack
    const historyRef = useRef<CanvasNode[][]>([]);
    const historyIndexRef = useRef(-1);
    const canUndo = historyIndexRef.current > 0;
    const canRedo = historyIndexRef.current < historyRef.current.length - 1;

    const pushHistory = useCallback((snapshot: CanvasNode[]) => {
        // Truncate any forward history on new change
        historyRef.current = historyRef.current.slice(0, historyIndexRef.current + 1);
        historyRef.current.push(snapshot);
        historyIndexRef.current = historyRef.current.length - 1;
    }, []);

    // Check if running in native mobile app
    const isNative = Capacitor && typeof Capacitor.isNativePlatform === 'function'
        ? Capacitor.isNativePlatform()
        : false;

    useEffect(() => {
        loadCanvasData();
    }, [projectId]);

    const loadCanvasData = async () => {
        setLoading(true);
        try {
            const res = await fetch(`/api/canvas/${projectId}`);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data: { nodes: CanvasNode[] } = await res.json();
            const loadedNodes = data.nodes ?? [];
            setNodes(loadedNodes);
            // Seed history with initial state
            historyRef.current = [loadedNodes];
            historyIndexRef.current = 0;
        } catch (error) {
            logger.error('Failed to load canvas', 'mobile-canvas', {
                projectId,
                error: error instanceof Error ? error.message : String(error),
            });
        } finally {
            setLoading(false);
        }
    };

    const handleHapticFeedback = async () => {
        if (isNative) {
            try {
                await Haptics.impact({ style: ImpactStyle.Light });
            } catch (error) {
                // Haptics not available
            }
        }
    };

    const handleAddNode = () => {
        void handleHapticFeedback();
        const newNode: CanvasNode = {
            id: `node-${Date.now()}`,
            type: 'component',
            label: 'New Component',
            x: 150,
            y: nodes.length * 100 + 50,
        };
        const updated = [...nodes, newNode];
        setNodes(updated);
        pushHistory(updated);
    };

    const handleUndo = () => {
        void handleHapticFeedback();
        if (historyIndexRef.current > 0) {
            historyIndexRef.current -= 1;
            setNodes([...historyRef.current[historyIndexRef.current]]);
        }
    };

    const handleRedo = () => {
        void handleHapticFeedback();
        if (historyIndexRef.current < historyRef.current.length - 1) {
            historyIndexRef.current += 1;
            setNodes([...historyRef.current[historyIndexRef.current]]);
        }
    };

    const handleSave = async () => {
        void handleHapticFeedback();
        setSaveStatus('saving');
        try {
            const res = await fetch(`/api/canvas/${projectId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ nodes }),
            });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            setSaveStatus('saved');
            setTimeout(() => setSaveStatus('idle'), 2000);
        } catch (error) {
            setSaveStatus('error');
            logger.error('Failed to save canvas', 'mobile-canvas', {
                projectId,
                error: error instanceof Error ? error.message : String(error),
            });
            setTimeout(() => setSaveStatus('idle'), 3000);
        }
    };

    const handleZoomIn = () => {
        void handleHapticFeedback();
        setZoom(prev => Math.min(prev + 0.1, 2));
    };

    const handleZoomOut = () => {
        void handleHapticFeedback();
        setZoom(prev => Math.max(prev - 0.1, 0.5));
    };

    const handleNodeSelect = (nodeId: string) => {
        void handleHapticFeedback();
        setSelectedNode(selectedNode === nodeId ? null : nodeId);
    };

    if (loading) {
        return (
            <Box className="p-4">
                <Skeleton variant="rectangular" height={60} className="mb-4" />
                <Skeleton variant="rectangular" height="calc(100vh - 200px)" />
            </Box>
        );
    }

    return (
        <Box
            className="h-screen flex flex-col" >
            {/* Header Toolbar */}
            <Box
                className={`flex items-center gap-2 p-2 border-b ${isDarkMode ? 'bg-zinc-900 border-zinc-700' : 'bg-white border-zinc-200'}`}
            >
                <Typography as="h6" className="flex-1 text-base">
                    Canvas
                </Typography>

                <IconButton size="sm" onClick={handleUndo} disabled={!canUndo}>
                    <Undo size={16} />
                </IconButton>

                <IconButton size="sm" onClick={handleRedo} disabled={!canRedo}>
                    <Redo size={16} />
                </IconButton>

                <IconButton size="sm" onClick={() => { void handleSave(); }} disabled={saveStatus === 'saving'}>
                    {saveStatus === 'saved' ? <CheckCircle size={16} className="text-green-500" /> : <Save size={16} />}
                </IconButton>

                <IconButton size="sm">
                    <MoreVert size={16} />
                </IconButton>
            </Box>

            {/* Canvas Area */}
            <Box
                className="flex-1 overflow-auto relative" >
                {/* Grid Pattern */}
                <Box
                    className="absolute inset-0"
                    style={{
                        backgroundImage: isDarkMode
                            ? 'linear-gradient(rgba(255,255,255,0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px)'
                            : 'linear-gradient(rgba(0,0,0,0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(0,0,0,0.05) 1px, transparent 1px)',
                        backgroundSize: '20px 20px',
                    }}
                />

                {/* Canvas Nodes */}
                <Box
                    className="relative min-h-full" style={{ transform: `scale(${zoom})` }}
                >
                    {nodes.map((node) => (
                        <Card
                            key={node.id}
                            onClick={() => handleNodeSelect(node.id)}
                            className={`absolute w-[200px] cursor-pointer transition-shadow ${
                                selectedNode === node.id ? 'ring-2 ring-violet-500' : ''
                            }`}
                            style={{ left: node.x, top: node.y }}
                        >
                            <CardContent className="p-4 last:pb-4">
                                <Typography as="p" className="text-sm font-medium" fontWeight={600}>
                                    {node.label}
                                </Typography>
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    {node.type}
                                </Typography>
                            </CardContent>
                        </Card>
                    ))}
                </Box>

                {/* Empty State */}
                {nodes.length === 0 && (
                    <Box
                        className="absolute text-center top-[50%] left-[50%] text-gray-500 dark:text-gray-400" >
                        <TouchApp className="mb-4 text-5xl opacity-[0.5]" />
                        <Typography as="p" className="text-sm">
                            Tap + to add components
                        </Typography>
                    </Box>
                )}
            </Box>

            {/* Bottom Action Bar */}
            <Box
                className={`flex items-center gap-2 p-3 border-t ${isDarkMode ? 'bg-zinc-900 border-zinc-700' : 'bg-white border-zinc-200'}`}
            >
                <IconButton size="sm" onClick={handleZoomOut}>
                    <ZoomOut size={16} />
                </IconButton>

                <Typography as="span" className="text-xs text-gray-500 px-2">
                    {Math.round(zoom * 100)}%
                </Typography>

                <IconButton size="sm" onClick={handleZoomIn}>
                    <ZoomIn size={16} />
                </IconButton>

                <Box className="flex-1" />

                <IconButton size="sm">
                    <Layers size={16} />
                </IconButton>

                <IconButton size="sm">
                    <Settings size={16} />
                </IconButton>
            </Box>

            {/* Floating Action Button */}
            <Fab
                tone="primary"
                onClick={handleAddNode}
                className="fixed bottom-[80px] right-[16px]"
            >
                <Add />
            </Fab>

            {/* Selection Info */}
            {selectedNode && (
                <Alert
                    severity="info"
                    onClose={() => setSelectedNode(null)}
                    className="fixed bottom-[80px] left-[16px] right-[80px]"
                >
                    Node selected. Tap to edit or drag to move.
                </Alert>
            )}
        </Box>
    );
}
