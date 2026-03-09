import { Shield as SecurityIcon, AlertTriangle as WarningIcon, AlertCircle as ErrorIcon, Info as InfoIcon, CheckCircle as CheckCircleIcon, ChevronDown as ExpandMoreIcon, RefreshCw as RefreshIcon, Eye as VisibilityIcon, Code as CodeIcon, Image as ImageIcon, FileText as DescriptionIcon, Link as LinkIcon } from 'lucide-react';
import {
  Card,
  CardContent,
  CardActions,
  Typography,
  Box,
  Button,
  Chip,
  ListItem,
  ListItemText,
  ListItemIcon,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Badge,
  Tooltip,
  LinearProgress,
  InteractiveList as List,
} from '@ghatana/ui';
import { ListItemSecondaryAction, Collapse } from '@ghatana/ui';
import {
  Accordion,
  AccordionSummary,
  AccordionDetails,
  useTheme,
  resolveMuiColor,
} from '@ghatana/yappc-ui';
import React, { useState, useCallback, useMemo } from 'react';

import { useSecurityAudit } from './hooks';

import type { CanvasData } from '../schemas/canvas-schemas';
import type { SecurityViolation } from '../schemas/export-schemas';

// Security audit panel component
/**
 *
 */
export interface SecurityAuditPanelProps {
  canvas: CanvasData;
  onViolationClick?: (violation: SecurityViolation) => void;
  autoRefresh?: boolean;
  refreshInterval?: number;
}

export const SecurityAuditPanel: React.FC<SecurityAuditPanelProps> = ({
  canvas,
  onViolationClick,
  autoRefresh = false,
  refreshInterval = 30000,
}) => {
  const [selectedViolation, setSelectedViolation] = useState<SecurityViolation | null>(null);
  const [showDetails, setShowDetails] = useState(false);

  const {
    isAuditing,
    violations,
    riskLevel,
    lastAuditTime,
    auditCanvas,
    fixViolation,
  } = useSecurityAudit({
    autoAudit: true,
    refreshInterval: autoRefresh ? refreshInterval : undefined,
  });

  const violationsByType = useMemo(() => {
    const grouped: Record<string, SecurityViolation[]> = {
      high: [],
      medium: [],
      low: [],
    };
    
    violations.forEach(violation => {
      grouped[violation.type].push(violation);
    });
    
    return grouped;
  }, [violations]);

  const handleRefresh = useCallback(async () => {
    await auditCanvas(canvas);
  }, [auditCanvas, canvas]);

  const handleViolationClick = useCallback((violation: SecurityViolation) => {
    setSelectedViolation(violation);
    setShowDetails(true);
    onViolationClick?.(violation);
  }, [onViolationClick]);

  const handleFixViolation = useCallback(async (violation: SecurityViolation) => {
    try {
      await fixViolation(violation.id);
      // Refresh audit after fix
      await auditCanvas(canvas);
    } catch (err) {
      console.error('Failed to fix violation:', err);
    }
  }, [fixViolation, auditCanvas, canvas]);

  const theme = useTheme();

  const getSeverityIcon = (type: string) => {
    switch (type) {
      case 'high':
        return <ErrorIcon color={resolveMuiColor(theme, 'error', 'default') as unknown} />;
      case 'medium':
        return <WarningIcon color={resolveMuiColor(theme, 'warning', 'default') as unknown} />;
      case 'low':
        return <InfoIcon color={resolveMuiColor(theme, 'info', 'default') as unknown} />;
      default:
        return <SecurityIcon />;
    }
  };

  const getSeverityColor = (type: string): 'error' | 'warning' | 'info' | 'success' => {
    switch (type) {
      case 'high':
        return 'error';
      case 'medium':
        return 'warning';
      case 'low':
        return 'info';
      default:
        return 'success';
    }
  };

  return (
    <Card>
      <CardContent>
        <Box className="flex items-center justify-between mb-4">
          <Box className="flex items-center gap-2">
            <SecurityIcon />
            <Typography variant="h6">Security Audit</Typography>
            <Badge badgeContent={violations.length} color={resolveMuiColor(theme, 'error', 'default') as unknown}>
              <Chip
                label={riskLevel.toUpperCase()}
                color={resolveMuiColor(theme, String(getSeverityColor(riskLevel)), 'default') as unknown}
                size="small"
              />
            </Badge>
          </Box>
          
          <Tooltip title="Refresh Audit">
            <IconButton onClick={handleRefresh} disabled={isAuditing}>
              <RefreshIcon />
            </IconButton>
          </Tooltip>
        </Box>
        
        {isAuditing && (
          <Box className="mb-4">
            <Typography variant="body2" color="text.secondary" gutterBottom>
              Running security audit...
            </Typography>
            <LinearProgress />
          </Box>
        )}
        
        {lastAuditTime && (
          <Typography variant="caption" color="text.secondary" className="mb-4 block">
            Last audit: {new Date(lastAuditTime).toLocaleString()}
          </Typography>
        )}
        
        {violations.length === 0 ? (
          <Box className="flex items-center gap-2 py-4">
            <CheckCircleIcon color={resolveMuiColor(theme, 'success', 'default') as unknown} />
            <Typography color="success.main">
              No security violations detected
            </Typography>
          </Box>
        ) : (
          <Box>
            {/* High Severity Violations */}
            {violationsByType.high.length > 0 && (
              <Accordion defaultExpanded>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Box className="flex items-center gap-2">
                    <ErrorIcon color={resolveMuiColor(theme, 'error', 'default') as unknown} />
                    <Typography variant="subtitle1">
                      High Risk ({violationsByType.high.length})
                    </Typography>
                  </Box>
                </AccordionSummary>
                <AccordionDetails>
                  <List dense>
                    {violationsByType.high.map((violation) => (
                      <ListItem
                        key={violation.id}
                        button
                        onClick={() => handleViolationClick(violation)}
                      >
                        <ListItemIcon>
                          {violation.category === 'content' && <DescriptionIcon />}
                          {violation.category === 'script' && <CodeIcon />}
                          {violation.category === 'style' && <ImageIcon />}
                          {violation.category === 'url' && <LinkIcon />}
                        </ListItemIcon>
                        <ListItemText
                          primary={violation.message}
                          secondary={`Element: ${violation.element || 'Unknown'} • Rule: ${violation.rule}`}
                        />
                        <ListItemSecondaryAction>
                          <Button
                            size="small"
                            variant="outlined"
                            color={resolveMuiColor(theme, 'error', 'default') as unknown}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleFixViolation(violation);
                            }}
                          >
                            Fix
                          </Button>
                        </ListItemSecondaryAction>
                      </ListItem>
                    ))}
                  </List>
                </AccordionDetails>
              </Accordion>
            )}
            
            {/* Medium Severity Violations */}
            {violationsByType.medium.length > 0 && (
              <Accordion>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Box className="flex items-center gap-2">
                    <WarningIcon color={resolveMuiColor(theme, 'warning', 'default') as unknown} />
                    <Typography variant="subtitle1">
                      Medium Risk ({violationsByType.medium.length})
                    </Typography>
                  </Box>
                </AccordionSummary>
                <AccordionDetails>
                  <List dense>
                    {violationsByType.medium.map((violation) => (
                      <ListItem
                        key={violation.id}
                        button
                        onClick={() => handleViolationClick(violation)}
                      >
                        <ListItemIcon>
                          {violation.category === 'content' && <DescriptionIcon />}
                          {violation.category === 'script' && <CodeIcon />}
                          {violation.category === 'style' && <ImageIcon />}
                          {violation.category === 'url' && <LinkIcon />}
                        </ListItemIcon>
                        <ListItemText
                          primary={violation.message}
                          secondary={`Element: ${violation.element || 'Unknown'} • Rule: ${violation.rule}`}
                        />
                        <ListItemSecondaryAction>
                          <Button
                            size="small"
                            variant="outlined"
                            color={resolveMuiColor(theme, 'warning', 'default') as unknown}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleFixViolation(violation);
                            }}
                          >
                            Fix
                          </Button>
                        </ListItemSecondaryAction>
                      </ListItem>
                    ))}
                  </List>
                </AccordionDetails>
              </Accordion>
            )}
            
            {/* Low Severity Violations */}
            {violationsByType.low.length > 0 && (
              <Accordion>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Box className="flex items-center gap-2">
                    <InfoIcon color={resolveMuiColor(theme, 'info', 'default') as unknown} />
                    <Typography variant="subtitle1">
                      Low Risk ({violationsByType.low.length})
                    </Typography>
                  </Box>
                </AccordionSummary>
                <AccordionDetails>
                  <List dense>
                    {violationsByType.low.map((violation) => (
                      <ListItem
                        key={violation.id}
                        button
                        onClick={() => handleViolationClick(violation)}
                      >
                        <ListItemIcon>
                          {violation.category === 'content' && <DescriptionIcon />}
                          {violation.category === 'script' && <CodeIcon />}
                          {violation.category === 'style' && <ImageIcon />}
                          {violation.category === 'url' && <LinkIcon />}
                        </ListItemIcon>
                        <ListItemText
                          primary={violation.message}
                          secondary={`Element: ${violation.element || 'Unknown'} • Rule: ${violation.rule}`}
                        />
                        <ListItemSecondaryAction>
                          <Button
                            size="small"
                            variant="text"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleFixViolation(violation);
                            }}
                          >
                            Fix
                          </Button>
                        </ListItemSecondaryAction>
                      </ListItem>
                    ))}
                  </List>
                </AccordionDetails>
              </Accordion>
            )}
          </Box>
        )}
      </CardContent>
      
      {/* Violation Details Dialog */}
      <Dialog
        open={showDetails && selectedViolation !== null}
        onClose={() => setShowDetails(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Security Violation Details</DialogTitle>
        <DialogContent>
          {selectedViolation && (
            <Box>
              <Alert severity={getSeverityColor(selectedViolation.type)} className="mb-4">
                <Typography variant="h6">
                  {selectedViolation.message}
                </Typography>
              </Alert>
              
              <Typography variant="subtitle1" gutterBottom>
                Details
              </Typography>
              <Typography variant="body2" paragraph>
                <strong>Category:</strong> {selectedViolation.category}
              </Typography>
              <Typography variant="body2" paragraph>
                <strong>Rule:</strong> {selectedViolation.rule}
              </Typography>
              <Typography variant="body2" paragraph>
                <strong>Element:</strong> {selectedViolation.element || 'N/A'}
              </Typography>
              <Typography variant="body2" paragraph>
                <strong>Context:</strong> {selectedViolation.context || 'N/A'}
              </Typography>
              
              {selectedViolation.suggestion && (
                <Box className="mt-4">
                  <Typography variant="subtitle1" gutterBottom>
                    Suggested Fix
                  </Typography>
                  <Alert severity="info">
                    {selectedViolation.suggestion}
                  </Alert>
                </Box>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowDetails(false)}>Close</Button>
          {selectedViolation && (
            <Button
              variant="contained"
              onClick={() => {
                handleFixViolation(selectedViolation);
                setShowDetails(false);
              }}
            >
              Apply Fix
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </Card>
  );
};

// Export progress indicator component
/**
 *
 */
export interface ExportProgressProps {
  isExporting: boolean;
  progress: number;
  currentStep?: string;
  totalSteps?: number;
  onCancel?: () => void;
}

export const ExportProgress: React.FC<ExportProgressProps> = ({
  isExporting,
  progress,
  currentStep,
  totalSteps,
  onCancel,
}) => {
  if (!isExporting) return null;

  return (
    <Card className="mt-4">
      <CardContent>
        <Box className="flex items-center justify-between mb-4">
          <Typography variant="h6">Export Progress</Typography>
          {onCancel && (
            <Button onClick={onCancel} size="small">
              Cancel
            </Button>
          )}
        </Box>
        
        <LinearProgress variant="determinate" value={progress} className="mb-2" />
        
        <Box className="flex justify-between items-center">
          <Typography variant="body2" color="text.secondary">
            {currentStep || 'Processing...'}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {Math.round(progress)}%
            {totalSteps && ` (Step ${Math.ceil(progress / (100 / totalSteps))} of ${totalSteps})`}
          </Typography>
        </Box>
      </CardContent>
    </Card>
  );
};

// Batch export component
/**
 *
 */
export interface BatchExportProps {
  canvases: CanvasData[];
  onExportComplete?: (results: Array<{ canvas: CanvasData; result: unknown; error?: string }>) => void;
}

export const BatchExport: React.FC<BatchExportProps> = ({
  canvases,
  onExportComplete,
}) => {
  const [isExporting, setIsExporting] = useState(false);
  const [progress, setProgress] = useState(0);
  const [results, setResults] = useState<Array<{ canvas: CanvasData; result: unknown; error?: string }>>([]);

  const { batchExport } = useSecurityAudit({});

  const handleBatchExport = useCallback(async () => {
    setIsExporting(true);
    setResults([]);
    
    try {
      const batchResults = await batchExport(
        canvases,
        {
          format: 'png',
          width: 800,
          height: 600,
          quality: 0.9,
          backgroundColor: '#ffffff',
          includeLabels: true,
          scale: 1,
          padding: 10,
        },
        (completed, total) => {
          setProgress((completed / total) * 100);
        }
      );
      
      setResults(batchResults);
      onExportComplete?.(batchResults);
    } catch (err) {
      console.error('Batch export failed:', err);
    } finally {
      setIsExporting(false);
    }
  }, [canvases, batchExport, onExportComplete]);

  return (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Batch Export ({canvases.length} canvases)
        </Typography>
        
        {!isExporting && results.length === 0 && (
          <Button
            variant="contained"
            onClick={handleBatchExport}
            disabled={canvases.length === 0}
          >
            Start Batch Export
          </Button>
        )}
        
        {isExporting && (
          <ExportProgress
            isExporting={isExporting}
            progress={progress}
            currentStep={`Exporting canvas ${Math.ceil(progress / (100 / canvases.length))} of ${canvases.length}`}
            totalSteps={canvases.length}
          />
        )}
        
        {results.length > 0 && (
          <Box className="mt-4">
            <Typography variant="subtitle1" gutterBottom>
              Export Results
            </Typography>
            <List>
              {results.map((result, index) => (
                <ListItem key={index}>
                  <ListItemIcon>
                    {result.error ? (
                      <ErrorIcon color="error" />
                    ) : (
                      <CheckCircleIcon color="success" />
                    )}
                  </ListItemIcon>
                  <ListItemText
                    primary={result.canvas.metadata.name || `Canvas ${index + 1}`}
                    secondary={result.error || 'Export completed successfully'}
                  />
                </ListItem>
              ))}
            </List>
          </Box>
        )}
      </CardContent>
    </Card>
  );
};