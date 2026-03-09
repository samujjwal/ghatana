/**
 * Canvas Toolbar Component
 * 
 * Reusable toolbar with undo/redo/export functionality.
 * Can be integrated into any canvas implementation.
 * 
 * @doc.type component
 * @doc.purpose Reusable canvas toolbar with history and export
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState } from 'react';
import {
  Box,
  IconButton,
  Divider,
  Menu,
  Tooltip,
} from '@ghatana/ui';
import { MenuItem } from '@ghatana/ui';
import { RefreshCw as Autorenew, Code, Download as FileDownload, Image, FileType as PictureAsPdf, Redo2 as Redo, Undo2 as Undo } from 'lucide-react';
import { CanvasHistoryManager, useCanvasHistory } from '../../utils/canvasHistory';
import { useKeyboardShortcuts, getShortcutText } from '../../utils/keyboardShortcuts';
import { useCanvasExport, type ExportFormat } from '../../utils/canvasExport';

interface CanvasToolbarProps<T> {
    history: CanvasHistoryManager<T>;
    onUndo?: (state: T | null) => void;
    onRedo?: (state: T | null) => void;
    onExport?: (format: ExportFormat) => Promise<void>;
    canvasRef?: React.RefObject<HTMLElement>;
    showExport?: boolean;
    showHistory?: boolean;
}

export function CanvasToolbar<T>({
    history,
    onUndo,
    onRedo,
    onExport,
    showExport = true,
    showHistory = true,
}: CanvasToolbarProps<T>) {
    const [exportAnchor, setExportAnchor] = useState<null | HTMLElement>(null);
    const [exporting, setExporting] = useState(false);

    const handleUndo = () => {
        const newState = history.undo();
        if (newState && onUndo) {
            onUndo(newState);
        }
    };

    const handleRedo = () => {
        const newState = history.redo();
        if (newState && onRedo) {
            onRedo(newState);
        }
    };

    const handleExport = async (format: ExportFormat) => {
        setExportAnchor(null);
        if (!onExport) return;

        setExporting(true);
        try {
            await onExport(format);
        } catch (error) {
            console.error('Export failed:', error);
        } finally {
            setExporting(false);
        }
    };

    // Setup keyboard shortcuts
    useKeyboardShortcuts(history, {
        onUndo: (newState: T | null) => {
            if (newState && onUndo) {
                onUndo(newState);
            }
        },
        onRedo: (newState: T | null) => {
            if (newState && onRedo) {
                onRedo(newState);
            }
        },
        onExport: async (format: ExportFormat) => {
            if (onExport) {
                await handleExport(format);
            }
        },
    });

    return (
        <Box
            className="flex items-center gap-2 p-2"
        >
            {/* History Controls */}
            {showHistory && (
                <>
                    <Tooltip title={`Undo (${getShortcutText('undo')})`}>
                        <span>
                            <IconButton
                                size="small"
                                onClick={handleUndo}
                                disabled={!history.canUndo()}
                                aria-label="Undo"
                            >
                                <Undo size={16} />
                            </IconButton>
                        </span>
                    </Tooltip>

                    <Tooltip title={`Redo (${getShortcutText('redo')})`}>
                        <span>
                            <IconButton
                                size="small"
                                onClick={handleRedo}
                                disabled={!history.canRedo()}
                                aria-label="Redo"
                            >
                                <Redo size={16} />
                            </IconButton>
                        </span>
                    </Tooltip>

                    <Divider orientation="vertical" flexItem />
                </>
            )}

            {/* Export Controls */}
            {showExport && (
                <>
                    <Tooltip title="Export Canvas">
                        <IconButton
                            size="small"
                            onClick={(e) => setExportAnchor(e.currentTarget)}
                            disabled={exporting}
                            aria-label="Export"
                        >
                            {exporting ? <Autorenew size={16} /> : <FileDownload size={16} />}
                        </IconButton>
                    </Tooltip>

                    <Menu
                        anchorEl={exportAnchor}
                        open={Boolean(exportAnchor)}
                        onClose={() => setExportAnchor(null)}
                    >
                        <MenuItem onClick={() => handleExport('png')}>
                            <Image size={16} style={{ marginRight: 8 }} />
                            Export as PNG
                        </MenuItem>
                        <MenuItem onClick={() => handleExport('svg')}>
                            <Code size={16} style={{ marginRight: 8 }} />
                            Export as SVG
                        </MenuItem>
                        <MenuItem onClick={() => handleExport('pdf')}>
                            <PictureAsPdf size={16} style={{ marginRight: 8 }} />
                            Export as PDF
                        </MenuItem>
                    </Menu>
                </>
            )}
        </Box>
    );
}

/**
 * Hook to integrate toolbar functionality
 */
export function useCanvasToolbar<T>(
    initialState: T,
    canvasRef: React.RefObject<HTMLElement>,
    mode: string,
    level: string
) {
    const history = useCanvasHistory<T>(initialState);
    const { exportAs } = useCanvasExport(mode as unknown, level as unknown, canvasRef);

    const handleExport = async (format: ExportFormat) => {
        try {
            await exportAs(format);
        } catch (error) {
            if (error instanceof Error) {
                if (error.message.includes('html2canvas') || error.message.includes('jspdf')) {
                    throw new Error(
                        'Export feature requires additional dependencies. ' +
                        'Install with: npm install html2canvas jspdf'
                    );
                }
            }
            throw error;
        }
    };

    return {
        history,
        handleExport,
    };
}
