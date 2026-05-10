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

import React, { useState, useCallback } from 'react';
import {
  Box,
  IconButton,
  Divider,
  Menu,
  Tooltip,
  Typography,
  Button,
  Paper,
} from '@ghatana/design-system';
import { useI18n } from '../../i18n/I18nProvider';
import { MenuItem } from '@ghatana/design-system';
import { RefreshCw as Autorenew, Code, Download as FileDownload, Image, FileType as PictureAsPdf, Redo2 as Redo, Undo2 as Undo, AlertTriangle, X } from 'lucide-react';
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

interface ExportError {
    message: string;
    correlationId: string;
    format: ExportFormat;
}

export function CanvasToolbar<T>({
    history,
    onUndo,
    onRedo,
    onExport,
    showExport = true,
    showHistory = true,
}: CanvasToolbarProps<T>) {
    const { t } = useI18n();
    const [exportAnchor, setExportAnchor] = useState<null | HTMLElement>(null);
    const [exporting, setExporting] = useState(false);
    const [exportError, setExportError] = useState<ExportError | null>(null);
    const [lastExportFormat, setLastExportFormat] = useState<ExportFormat | null>(null);

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

    const handleExport = useCallback(async (format: ExportFormat) => {
        setExportAnchor(null);
        setExportError(null);
        setLastExportFormat(format);
        if (!onExport) return;

        setExporting(true);
        const correlationId = `export-${format}-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
        try {
            await onExport(format);
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : 'Unknown export error';
            setExportError({
                message: errorMessage,
                correlationId,
                format,
            });
        } finally {
            setExporting(false);
        }
    }, [onExport]);

    const handleRetryExport = useCallback(() => {
        if (lastExportFormat) {
            handleExport(lastExportFormat);
        }
    }, [lastExportFormat, handleExport]);

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
                                aria-label={t('canvasToolbar.undo')}
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
                                aria-label={t('canvasToolbar.redo')}
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
                            aria-label={t('canvasToolbar.export')}
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

            {/* Export Error Display */}
            {exportError && (
                <Paper
                    elevation={2}
                    className="flex items-start gap-2 p-3 border border-danger-border bg-danger-bg"
                    style={{ maxWidth: 400 }}
                >
                    <AlertTriangle size={16} className="text-danger mt-0.5 flex-shrink-0" />
                    <Box className="flex-1 min-w-0">
                        <Typography variant="body2" style={{ fontWeight: 600 }}>
                            Export failed
                        </Typography>
                        <Typography variant="caption" className="block mt-1 text-danger">
                            {exportError.message}
                        </Typography>
                        <Typography variant="caption" className="block mt-1 text-muted">
                            Correlation ID: {exportError.correlationId}
                        </Typography>
                        <Box className="flex gap-2 mt-2">
                            <Button
                                variant="outline"
                                size="small"
                                onClick={handleRetryExport}
                                disabled={exporting}
                            >
                                Retry
                            </Button>
                            <Button
                                variant="ghost"
                                size="small"
                                onClick={() => setExportError(null)}
                            >
                                Dismiss
                            </Button>
                        </Box>
                    </Box>
                    <IconButton
                        size="small"
                        onClick={() => setExportError(null)}
                        aria-label="Dismiss error"
                    >
                        <X size={14} />
                    </IconButton>
                </Paper>
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
