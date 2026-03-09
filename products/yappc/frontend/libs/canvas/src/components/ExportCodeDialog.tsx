/**
 * Export Code Dialog
 * 
 * Modal dialog for exporting generated code and runbooks from canvas nodes.
 * Provides options for test generation and runbook creation.
 * 
 * @module ExportCodeDialog
 */

import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  FormControlLabel,
  Checkbox,
  Typography,
  Alert,
  Spinner as CircularProgress,
} from '@ghatana/ui';
import type { CodeExportResult } from '../hooks/useTemplateActions';

/**
 *
 */
export interface ExportCodeDialogProps {
    open: boolean;
    onClose: () => void;
    onExport: (options: { generateTests: boolean; createRunbooks: boolean }) => Promise<CodeExportResult>;
    nodeCount: number;
}

/**
 * Dialog for exporting generated code
 */
export const ExportCodeDialog: React.FC<ExportCodeDialogProps> = ({
    open,
    onClose,
    onExport,
    nodeCount,
}) => {
    const [generateTests, setGenerateTests] = useState(true);
    const [createRunbooks, setCreateRunbooks] = useState(true);
    const [isExporting, setIsExporting] = useState(false);
    const [exportResult, setExportResult] = useState<CodeExportResult | null>(null);

    const handleExport = async () => {
        setIsExporting(true);
        setExportResult(null);

        try {
            const result = await onExport({ generateTests, createRunbooks });
            setExportResult(result);

            if (result.success) {
                // In a real implementation, this would trigger a download
                // For now, we'll just show the result
                const blob = new Blob(
                    [JSON.stringify(result.files, null, 2)],
                    { type: 'application/json' }
                );
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `canvas-export-${Date.now()}.json`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);
            }
        } catch (error) {
            setExportResult({
                success: false,
                files: [],
                error: error instanceof Error ? error.message : 'Export failed',
            });
        } finally {
            setIsExporting(false);
        }
    };

    const handleClose = () => {
        setExportResult(null);
        onClose();
    };

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
            <DialogTitle>Export Code</DialogTitle>
            <DialogContent>
                <Box className="mt-4 flex flex-col gap-4">
                    <Typography variant="body2" color="text.secondary">
                        Export code and configuration from {nodeCount} canvas node{nodeCount !== 1 ? 's' : ''}.
                    </Typography>

                    <Box>
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={generateTests}
                                    onChange={(e) => setGenerateTests(e.target.checked)}
                                    data-testid="generate-tests-checkbox"
                                />
                            }
                            label="Generate Tests"
                        />
                        <Typography variant="caption" display="block" className="ml-8 text-gray-500 dark:text-gray-400">
                            Include unit and integration tests for services
                        </Typography>
                    </Box>

                    <Box>
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={createRunbooks}
                                    onChange={(e) => setCreateRunbooks(e.target.checked)}
                                    data-testid="create-runbooks-checkbox"
                                />
                            }
                            label="Create DevSecOps Runbooks"
                        />
                        <Typography variant="caption" display="block" className="ml-8 text-gray-500 dark:text-gray-400">
                            Generate deployment and infrastructure runbooks
                        </Typography>
                    </Box>

                    {exportResult && !exportResult.success && (
                        <Alert severity="error" data-testid="export-error">
                            {exportResult.error}
                        </Alert>
                    )}

                    {exportResult && exportResult.success && (
                        <Alert severity="success" data-testid="export-success">
                            Successfully exported {exportResult.files.length} file{exportResult.files.length !== 1 ? 's' : ''}
                            {exportResult.runbooks && exportResult.runbooks.length > 0 && (
                                <> and {exportResult.runbooks.length} runbook{exportResult.runbooks.length !== 1 ? 's' : ''}</>
                            )}
                        </Alert>
                    )}
                </Box>
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>
                    {exportResult?.success ? 'Close' : 'Cancel'}
                </Button>
                {!exportResult?.success && (
                    <Button
                        onClick={handleExport}
                        variant="contained"
                        disabled={isExporting}
                        data-testid="export-code-button"
                        startIcon={isExporting ? <CircularProgress size={16} /> : undefined}
                    >
                        {isExporting ? 'Exporting...' : 'Export'}
                    </Button>
                )}
            </DialogActions>
        </Dialog>
    );
};
