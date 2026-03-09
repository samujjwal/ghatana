/**
 * ExportImportDialog - Canvas Export/Import UI
 * 
 * Provides export to JSON/SVG/PNG and import from JSON
 * 
 * @doc.type component
 * @doc.purpose Export/Import dialog
 * @doc.layer components
 * @doc.pattern Component
 */

import React, { useRef } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Stack,
  Box,
  Typography,
  Divider,
} from '@ghatana/ui';
import { Download as DownloadIcon, Upload as UploadIcon, Code as JsonIcon, Image as ImageIcon, FileText as SvgIcon } from 'lucide-react';

interface ExportImportDialogProps {
    open: boolean;
    onClose: () => void;
    onExportJSON: () => void;
    onExportSVG: () => void;
    onExportPNG: () => void;
    onImport: (file: File) => void;
}

export function ExportImportDialog({
    open,
    onClose,
    onExportJSON,
    onExportSVG,
    onExportPNG,
    onImport
}: ExportImportDialogProps) {
    const fileInputRef = useRef<HTMLInputElement>(null);

    const handleImportClick = () => {
        fileInputRef.current?.click();
    };

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (file) {
            onImport(file);
            // Reset input
            if (fileInputRef.current) {
                fileInputRef.current.value = '';
            }
        }
    };

    return (
        <>
            <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
                <DialogTitle>Export / Import Canvas</DialogTitle>
                <DialogContent>
                    <Stack spacing={3} className="pt-4">
                        {/* Export Section */}
                        <Box>
                            <Typography variant="subtitle2" fontWeight={600} gutterBottom>
                                Export Canvas
                            </Typography>
                            <Stack spacing={1.5}>
                                <Button
                                    variant="outlined"
                                    startIcon={<JsonIcon />}
                                    fullWidth
                                    onClick={onExportJSON}
                                    className="justify-start"
                                >
                                    <Box className="flex-1 text-left">
                                        <Typography variant="body2" fontWeight={500}>
                                            Export as JSON
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            Save all canvas data (nodes, connections, drawings)
                                        </Typography>
                                    </Box>
                                </Button>

                                <Button
                                    variant="outlined"
                                    startIcon={<SvgIcon />}
                                    fullWidth
                                    onClick={onExportSVG}
                                    className="justify-start"
                                >
                                    <Box className="flex-1 text-left">
                                        <Typography variant="body2" fontWeight={500}>
                                            Export as SVG
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            Vector graphics for high-quality printing
                                        </Typography>
                                    </Box>
                                </Button>

                                <Button
                                    variant="outlined"
                                    startIcon={<ImageIcon />}
                                    fullWidth
                                    onClick={onExportPNG}
                                    className="justify-start"
                                >
                                    <Box className="flex-1 text-left">
                                        <Typography variant="body2" fontWeight={500}>
                                            Export as PNG
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            Raster image for screenshots and sharing
                                        </Typography>
                                    </Box>
                                </Button>
                            </Stack>
                        </Box>

                        <Divider />

                        {/* Import Section */}
                        <Box>
                            <Typography variant="subtitle2" fontWeight={600} gutterBottom>
                                Import Canvas
                            </Typography>
                            <Button
                                variant="outlined"
                                startIcon={<UploadIcon />}
                                fullWidth
                                onClick={handleImportClick}
                                className="justify-start"
                            >
                                <Box className="flex-1 text-left">
                                    <Typography variant="body2" fontWeight={500}>
                                        Import from JSON
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        Load previously exported canvas data
                                    </Typography>
                                </Box>
                            </Button>
                        </Box>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={onClose}>Close</Button>
                </DialogActions>
            </Dialog>

            {/* Hidden file input */}
            <input
                ref={fileInputRef}
                type="file"
                accept=".json"
                style={{ display: 'none' }}
                onChange={handleFileChange}
            />
        </>
    );
}
