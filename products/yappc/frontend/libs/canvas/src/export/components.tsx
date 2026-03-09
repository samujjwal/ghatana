import { Download as DownloadIcon, Share2 as ShareIcon, Shield as SecurityIcon, Download as GetAppIcon, FileCopy as FileCopyIcon, Trash2 as DeleteIcon, Eye as VisibilityIcon, EyeOff as VisibilityOffIcon, AlertTriangle as WarningIcon, CheckCircle as CheckCircleIcon, XCircle as CancelIcon, MoreVertical as MoreVertIcon } from 'lucide-react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  FormControl,
  FormLabel,
  RadioGroup,
  FormControlLabel,
  Radio,
  Checkbox,
  Switch,
  Slider,
  Divider,
  Alert,
  LinearProgress,
  IconButton,
  Tooltip,
  Chip,
  Card,
  CardContent,
  ListItem,
  ListItemText,
  Menu,
  InteractiveList as List,
} from '@ghatana/ui';
import { TextField, ListItemSecondaryAction, MenuItem } from '@ghatana/ui';
import React, { useState, useCallback } from 'react';

import { useExport, useShareLinks, useSecurityAudit } from './hooks';

import type { CanvasData } from '../schemas/canvas-schemas';
import type {
  ExportFormat,
  ExportOptions,
  ImageExportOptions,
  PdfExportOptions,
  CodeExportOptions,
  JsonExportOptions,
  ShareLinkConfig,
} from '../schemas/export-schemas';

// Export dialog component
/**
 *
 */
export interface ExportDialogProps {
  open: boolean;
  onClose: () => void;
  canvas: CanvasData;
  onExportComplete?: (url: string, filename: string) => void;
}

export const ExportDialog: React.FC<ExportDialogProps> = ({
  open,
  onClose,
  canvas,
  onExportComplete,
}) => {
  const [selectedFormat, setSelectedFormat] = useState<ExportFormat>('png');
  const [exportOptions, setExportOptions] = useState<ExportOptions>({
    format: 'png',
    width: 800,
    height: 600,
    quality: 0.9,
    backgroundColor: '#ffffff',
  } as ImageExportOptions);

  const {
    isExporting,
    progress,
    error,
    exportCanvas,
    cancelExport,
    downloadResult,
  } = useExport({
    onSuccess: (result) => {
      if (result.url && result.filename) {
        onExportComplete?.(result.url, result.filename);
      }
    },
  });

  const { auditCanvas, violations, riskLevel } = useSecurityAudit({
    autoAudit: true,
  });

  const handleFormatChange = useCallback((format: ExportFormat) => {
    setSelectedFormat(format);
    
    // Set default options based on format
    switch (format) {
      case 'png':
      case 'svg':
        setExportOptions({
          format,
          width: 800,
          height: 600,
          quality: 0.9,
          backgroundColor: '#ffffff',
          includeLabels: true,
          scale: 1,
          padding: 10,
        } as ImageExportOptions);
        break;
      case 'pdf':
        setExportOptions({
          format,
          pageSize: 'A4',
          orientation: 'portrait',
          includeMetadata: true,
          margins: { top: 20, right: 20, bottom: 20, left: 20 },
        } as PdfExportOptions);
        break;
      case 'jsx':
      case 'html':
        setExportOptions({
          format,
          includeStyles: true,
          minify: false,
          typescript: true,
          componentName: 'CanvasExport',
          exportType: 'component',
          dependencies: [],
        } as CodeExportOptions);
        break;
      case 'json':
        setExportOptions({
          format,
          includeMetadata: true,
          includePositions: true,
          minify: false,
          version: '1.0',
        } as JsonExportOptions);
        break;
    }
  }, []);

  const handleExport = useCallback(async () => {
    // Run security audit first
    const auditViolations = await auditCanvas(canvas);
    if (auditViolations.some(v => v.type === 'high')) {
      return; // Block export on high-risk violations
    }

    try {
      const result = await exportCanvas(canvas, exportOptions);
      if (result.status === 'completed' && result.url) {
        downloadResult(result);
      }
    } catch (err) {
      console.error('Export failed:', err);
    }
  }, [canvas, exportOptions, exportCanvas, downloadResult, auditCanvas]);

  const renderFormatOptions = () => {
    switch (selectedFormat) {
      case 'png':
      case 'svg':
        const imageOptions = exportOptions as ImageExportOptions;
        return (
          <Box className="mt-4">
            <TextField
              label="Width"
              type="number"
              value={imageOptions.width || 800}
              onChange={(e) => setExportOptions({
                ...imageOptions,
                width: parseInt(e.target.value) || 800,
              })}
              className="mr-4 w-[120px]"
            />
            <TextField
              label="Height"
              type="number"
              value={imageOptions.height || 600}
              onChange={(e) => setExportOptions({
                ...imageOptions,
                height: parseInt(e.target.value) || 600,
              })}
              className="w-[120px]"
            />
            
            <Box className="mt-4">
              <Typography gutterBottom>Quality: {imageOptions.quality}</Typography>
              <Slider
                value={imageOptions.quality || 0.9}
                onChange={(_, value) => setExportOptions({
                  ...imageOptions,
                  quality: value as number,
                })}
                min={0.1}
                max={1}
                step={0.1}
                className="w-[200px]"
              />
            </Box>
            
            <TextField
              label="Background Color"
              value={imageOptions.backgroundColor || '#ffffff'}
              onChange={(e) => setExportOptions({
                ...imageOptions,
                backgroundColor: e.target.value,
              })}
              className="mt-4 w-[150px]"
            />
          </Box>
        );

      case 'pdf':
        const pdfOptions = exportOptions as PdfExportOptions;
        return (
          <Box className="mt-4">
            <FormControl className="mr-4">
              <FormLabel>Page Size</FormLabel>
              <RadioGroup
                value={pdfOptions.pageSize || 'A4'}
                onChange={(e) => setExportOptions({
                  ...pdfOptions,
                  pageSize: e.target.value as unknown,
                })}
                row
              >
                <FormControlLabel value="A4" control={<Radio />} label="A4" />
                <FormControlLabel value="A3" control={<Radio />} label="A3" />
                <FormControlLabel value="Letter" control={<Radio />} label="Letter" />
              </RadioGroup>
            </FormControl>
            
            <FormControl className="mt-4">
              <FormLabel>Orientation</FormLabel>
              <RadioGroup
                value={pdfOptions.orientation || 'portrait'}
                onChange={(e) => setExportOptions({
                  ...pdfOptions,
                  orientation: e.target.value as 'portrait' | 'landscape',
                })}
                row
              >
                <FormControlLabel value="portrait" control={<Radio />} label="Portrait" />
                <FormControlLabel value="landscape" control={<Radio />} label="Landscape" />
              </RadioGroup>
            </FormControl>
          </Box>
        );

      case 'jsx':
      case 'html':
        const codeOptions = exportOptions as CodeExportOptions;
        return (
          <Box className="mt-4">
            <TextField
              label="Component Name"
              value={codeOptions.componentName || 'CanvasExport'}
              onChange={(e) => setExportOptions({
                ...codeOptions,
                componentName: e.target.value,
              })}
              className="mb-4 w-full"
            />
            
            <Box className="flex gap-4 flex-wrap">
              <FormControlLabel
                control={
                  <Checkbox
                    checked={codeOptions.includeStyles || false}
                    onChange={(e) => setExportOptions({
                      ...codeOptions,
                      includeStyles: e.target.checked,
                    })}
                  />
                }
                label="Include Styles"
              />
              
              <FormControlLabel
                control={
                  <Checkbox
                    checked={codeOptions.typescript || false}
                    onChange={(e) => setExportOptions({
                      ...codeOptions,
                      typescript: e.target.checked,
                    })}
                  />
                }
                label="TypeScript"
              />
              
              <FormControlLabel
                control={
                  <Checkbox
                    checked={codeOptions.minify || false}
                    onChange={(e) => setExportOptions({
                      ...codeOptions,
                      minify: e.target.checked,
                    })}
                  />
                }
                label="Minify"
              />
            </Box>
          </Box>
        );

      case 'json':
        const jsonOptions = exportOptions as JsonExportOptions;
        return (
          <Box className="mt-4">
            <Box className="flex gap-4 flex-wrap">
              <FormControlLabel
                control={
                  <Checkbox
                    checked={jsonOptions.includeMetadata !== false}
                    onChange={(e) => setExportOptions({
                      ...jsonOptions,
                      includeMetadata: e.target.checked,
                    })}
                  />
                }
                label="Include Metadata"
              />
              
              <FormControlLabel
                control={
                  <Checkbox
                    checked={jsonOptions.includePositions !== false}
                    onChange={(e) => setExportOptions({
                      ...jsonOptions,
                      includePositions: e.target.checked,
                    })}
                  />
                }
                label="Include Positions"
              />
              
              <FormControlLabel
                control={
                  <Checkbox
                    checked={jsonOptions.minify || false}
                    onChange={(e) => setExportOptions({
                      ...jsonOptions,
                      minify: e.target.checked,
                    })}
                  />
                }
                label="Minify"
              />
            </Box>
            
            <TextField
              label="Version"
              value={jsonOptions.version || '1.0'}
              onChange={(e) => setExportOptions({
                ...jsonOptions,
                version: e.target.value,
              })}
              className="mt-4 w-[120px]"
            />
          </Box>
        );

      default:
        return null;
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        <Box className="flex items-center gap-2">
          <DownloadIcon />
          Export Canvas
        </Box>
      </DialogTitle>
      
      <DialogContent>
        {/* Security Status */}
        {violations.length > 0 && (
          <Alert 
            severity={riskLevel === 'high' ? 'error' : riskLevel === 'medium' ? 'warning' : 'info'}
            className="mb-4"
          >
            <Typography variant="body2">
              {riskLevel === 'high' && 'High security risk detected. Export blocked.'}
              {riskLevel === 'medium' && `${violations.length} potential security issues found.`}
              {riskLevel === 'low' && 'Minor security issues detected.'}
            </Typography>
          </Alert>
        )}
        
        {/* Format Selection */}
        <FormControl className="mb-6">
          <FormLabel>Export Format</FormLabel>
          <RadioGroup
            value={selectedFormat}
            onChange={(e) => handleFormatChange(e.target.value as ExportFormat)}
            row
          >
            <FormControlLabel value="png" control={<Radio />} label="PNG" />
            <FormControlLabel value="svg" control={<Radio />} label="SVG" />
            <FormControlLabel value="pdf" control={<Radio />} label="PDF" />
            <FormControlLabel value="jsx" control={<Radio />} label="JSX" />
            <FormControlLabel value="html" control={<Radio />} label="HTML" />
            <FormControlLabel value="json" control={<Radio />} label="JSON" />
          </RadioGroup>
        </FormControl>
        
        <Divider className="my-4" />
        
        {/* Format-specific Options */}
        {renderFormatOptions()}
        
        {/* Export Progress */}
        {isExporting && (
          <Box className="mt-6">
            <Box className="flex items-center justify-between mb-2">
              <Typography variant="body2">Exporting...</Typography>
              <Button
                size="small"
                onClick={cancelExport}
                startIcon={<CancelIcon />}
              >
                Cancel
              </Button>
            </Box>
            <LinearProgress variant="determinate" value={progress} />
          </Box>
        )}
        
        {/* Error Display */}
        {error && (
          <Alert severity="error" className="mt-4">
            {error}
          </Alert>
        )}
      </DialogContent>
      
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleExport}
          disabled={isExporting || (violations.some(v => v.type === 'high'))}
          startIcon={<DownloadIcon />}
        >
          {isExporting ? 'Exporting...' : 'Export'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// Share dialog component
/**
 *
 */
export interface ShareDialogProps {
  open: boolean;
  onClose: () => void;
  canvasId: string;
}

export const ShareDialog: React.FC<ShareDialogProps> = ({
  open,
  onClose,
  canvasId,
}) => {
  const [linkConfig, setLinkConfig] = useState<ShareLinkConfig>({
    permissions: {
      canView: true,
      canEdit: false,
      canComment: false,
      canExport: false,
    },
    requireAuth: false,
    allowedDomains: [],
  });

  const {
    isCreating,
    shareLinks,
    error,
    createShareLink,
    deleteShareLink,
    copyToClipboard,
  } = useShareLinks({
    canvasId,
    onLinkCreated: (link) => {
      console.log('Share link created:', link.url);
    },
  });

  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [selectedLink, setSelectedLink] = useState<string | null>(null);

  const handleCreateLink = useCallback(async () => {
    try {
      await createShareLink(linkConfig);
    } catch (err) {
      console.error('Failed to create share link:', err);
    }
  }, [createShareLink, linkConfig]);

  const handleCopyLink = useCallback(async (link: unknown) => {
    try {
      await copyToClipboard(link);
      // Show success message (you might want to use a snackbar here)
      console.log('Link copied to clipboard');
    } catch (err) {
      console.error('Failed to copy link:', err);
    }
  }, [copyToClipboard]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Box className="flex items-center gap-2">
          <ShareIcon />
          Share Canvas
        </Box>
      </DialogTitle>
      
      <DialogContent>
        {/* Permissions Configuration */}
        <Typography variant="h6" gutterBottom>
          Link Permissions
        </Typography>
        
        <Box className="mb-6">
          <FormControlLabel
            control={
              <Checkbox
                checked={linkConfig.permissions.canView}
                onChange={(e) => setLinkConfig(prev => ({
                  ...prev,
                  permissions: { ...prev.permissions, canView: e.target.checked },
                }))}
              />
            }
            label="Can View"
          />
          
          <FormControlLabel
            control={
              <Checkbox
                checked={linkConfig.permissions.canEdit}
                onChange={(e) => setLinkConfig(prev => ({
                  ...prev,
                  permissions: { ...prev.permissions, canEdit: e.target.checked },
                }))}
              />
            }
            label="Can Edit"
          />
          
          <FormControlLabel
            control={
              <Checkbox
                checked={linkConfig.permissions.canComment}
                onChange={(e) => setLinkConfig(prev => ({
                  ...prev,
                  permissions: { ...prev.permissions, canComment: e.target.checked },
                }))}
              />
            }
            label="Can Comment"
          />
          
          <FormControlLabel
            control={
              <Checkbox
                checked={linkConfig.permissions.canExport}
                onChange={(e) => setLinkConfig(prev => ({
                  ...prev,
                  permissions: { ...prev.permissions, canExport: e.target.checked },
                }))}
              />
            }
            label="Can Export"
          />
        </Box>
        
        {/* Additional Settings */}
        <Typography variant="h6" gutterBottom>
          Access Settings
        </Typography>
        
        <FormControlLabel
          control={
            <Switch
              checked={linkConfig.requireAuth}
              onChange={(e) => setLinkConfig(prev => ({
                ...prev,
                requireAuth: e.target.checked,
              }))}
            />
          }
          label="Require Authentication"
        />
        
        <TextField
          label="Expiration Date"
          type="datetime-local"
          value={linkConfig.expiresAt?.slice(0, 16) || ''}
          onChange={(e) => setLinkConfig(prev => ({
            ...prev,
            expiresAt: e.target.value ? new Date(e.target.value).toISOString() : undefined,
          }))}
          className="mt-4 w-full"
          InputLabelProps={{ shrink: true }}
        />
        
        <TextField
          label="Maximum Views"
          type="number"
          value={linkConfig.maxViews || ''}
          onChange={(e) => setLinkConfig(prev => ({
            ...prev,
            maxViews: e.target.value ? parseInt(e.target.value) : undefined,
          }))}
          className="mt-4 w-full"
        />
        
        <Button
          variant="contained"
          onClick={handleCreateLink}
          disabled={isCreating}
          className="mt-4"
        >
          {isCreating ? 'Creating...' : 'Create Share Link'}
        </Button>
        
        {error && (
          <Alert severity="error" className="mt-4">
            {error}
          </Alert>
        )}
        
        {/* Existing Links */}
        {shareLinks.length > 0 && (
          <Box className="mt-6">
            <Typography variant="h6" gutterBottom>
              Existing Share Links
            </Typography>
            
            <List>
              {shareLinks.map((link) => (
                <ListItem key={link.id} divider>
                  <ListItemText
                    primary={
                      <Box className="flex items-center gap-2">
                        <Typography variant="body2" className="font-mono">
                          {link.url.slice(-20)}...
                        </Typography>
                        <Chip
                          size="small"
                          label={link.isActive ? 'Active' : 'Inactive'}
                          color={link.isActive ? 'success' : 'default'}
                        />
                      </Box>
                    }
                    secondary={
                      <Box>
                        <Typography variant="caption">
                          Views: {link.views} • Created: {new Date(link.createdAt).toLocaleDateString()}
                        </Typography>
                        <Box className="mt-2">
                          {link.config.permissions.canView && <Chip label="View" size="small" className="mr-1" />}
                          {link.config.permissions.canEdit && <Chip label="Edit" size="small" className="mr-1" />}
                          {link.config.permissions.canComment && <Chip label="Comment" size="small" className="mr-1" />}
                          {link.config.permissions.canExport && <Chip label="Export" size="small" />}
                        </Box>
                      </Box>
                    }
                  />
                  
                  <ListItemSecondaryAction>
                    <Tooltip title="Copy Link">
                      <IconButton onClick={() => handleCopyLink(link)}>
                        <FileCopyIcon />
                      </IconButton>
                    </Tooltip>
                    
                    <IconButton
                      onClick={(e) => {
                        setMenuAnchor(e.currentTarget);
                        setSelectedLink(link.id);
                      }}
                    >
                      <MoreVertIcon />
                    </IconButton>
                  </ListItemSecondaryAction>
                </ListItem>
              ))}
            </List>
          </Box>
        )}
        
        {/* Link Actions Menu */}
        <Menu
          anchorEl={menuAnchor}
          open={Boolean(menuAnchor)}
          onClose={() => {
            setMenuAnchor(null);
            setSelectedLink(null);
          }}
        >
          <MenuItem
            onClick={async () => {
              if (selectedLink) {
                await deleteShareLink(selectedLink);
              }
              setMenuAnchor(null);
              setSelectedLink(null);
            }}
          >
            <DeleteIcon className="mr-2" />
            Delete Link
          </MenuItem>
        </Menu>
      </DialogContent>
      
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};