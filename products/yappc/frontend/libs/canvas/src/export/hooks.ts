import { useState, useCallback, useRef } from 'react';

import { exportEngine } from './renderer';
import {
  CreateShareLinkRequest
} from '../schemas/export-schemas';

import type { CanvasData } from '../schemas/canvas-schemas';
import type {
  ExportOptions,
  ExportResult,
  ExportFormat,
  ShareLink,
  ShareLinkConfig,
  SecurityViolation} from '../schemas/export-schemas';

// Export management hook
/**
 *
 */
export interface UseExportConfig {
  onProgress?: (progress: number) => void;
  onError?: (error: string) => void;
  onSuccess?: (result: ExportResult) => void;
}

/**
 *
 */
export interface UseExportReturn {
  // Export state
  isExporting: boolean;
  progress: number;
  error: string | null;
  
  // Export results
  exportHistory: ExportResult[];
  lastExport: ExportResult | null;
  
  // Export methods
  exportCanvas: (canvas: CanvasData, options: ExportOptions) => Promise<ExportResult>;
  cancelExport: () => void;
  clearHistory: () => void;
  downloadResult: (result: ExportResult) => void;
  
  // Batch export
  batchExport: (canvas: CanvasData, formats: ExportFormat[]) => Promise<ExportResult[]>;
}

export const useExport = (config: UseExportConfig = {}): UseExportReturn => {
  const [isExporting, setIsExporting] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [exportHistory, setExportHistory] = useState<ExportResult[]>([]);
  const [lastExport, setLastExport] = useState<ExportResult | null>(null);
  
  const abortController = useRef<AbortController | null>(null);

  const exportCanvas = useCallback(
    async (canvas: CanvasData, options: ExportOptions): Promise<ExportResult> => {
      setIsExporting(true);
      setProgress(0);
      setError(null);
      
      // Create abort controller for cancellation
      abortController.current = new AbortController();
      
      try {
        // Simulate progress updates
        const progressInterval = setInterval(() => {
          setProgress(prev => Math.min(prev + 10, 90));
          config.onProgress?.(Math.min(progress + 10, 90));
        }, 200);
        
        const result = await exportEngine.exportCanvas(canvas, options);
        
        clearInterval(progressInterval);
        setProgress(100);
        config.onProgress?.(100);
        
        if (result.status === 'completed') {
          setExportHistory(prev => [result, ...prev.slice(0, 9)]); // Keep last 10
          setLastExport(result);
          config.onSuccess?.(result);
        } else {
          throw new Error(result.error || 'Export failed');
        }
        
        return result;
        
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Export failed';
        setError(errorMessage);
        config.onError?.(errorMessage);
        
        const failedResult: ExportResult = {
          id: `failed_${Date.now()}`,
          status: 'failed',
          format: options.format,
          createdAt: new Date().toISOString(),
          error: errorMessage,
        };
        
        return failedResult;
        
      } finally {
        setIsExporting(false);
        setProgress(0);
        abortController.current = null;
      }
    },
    [config, progress]
  );

  const cancelExport = useCallback(() => {
    if (abortController.current) {
      abortController.current.abort();
      setIsExporting(false);
      setProgress(0);
      setError('Export cancelled');
    }
  }, []);

  const clearHistory = useCallback(() => {
    setExportHistory([]);
    setLastExport(null);
    setError(null);
  }, []);

  const downloadResult = useCallback((result: ExportResult) => {
    if (!result.url) {
      console.error('No URL available for download');
      return;
    }
    
    try {
      // Create download link
      const link = document.createElement('a');
      link.href = result.url;
      link.download = result.filename || `export-${result.id}`;
      
      // Handle blob URLs
      if (result.url.startsWith('blob:')) {
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        
        // Cleanup blob URL after download
        setTimeout(() => {
          URL.revokeObjectURL(result.url!);
        }, 1000);
      } else {
        // Handle data URLs
        link.click();
      }
    } catch (err) {
      console.error('Download failed:', err);
      setError('Download failed');
    }
  }, []);

  const batchExport = useCallback(
    async (canvas: CanvasData, formats: ExportFormat[]): Promise<ExportResult[]> => {
      const results: ExportResult[] = [];
      const totalFormats = formats.length;
      
      setIsExporting(true);
      setError(null);
      
      try {
        for (let i = 0; i < formats.length; i++) {
          const format = formats[i];
          const baseProgress = (i / totalFormats) * 100;
          
          setProgress(baseProgress);
          config.onProgress?.(baseProgress);
          
          // Create default options for each format
          const options: ExportOptions = { format } as unknown;
          
          const result = await exportEngine.exportCanvas(canvas, options);
          results.push(result);
          
          if (result.status === 'failed') {
            console.warn(`Export failed for format ${format}:`, result.error);
          }
        }
        
        setProgress(100);
        config.onProgress?.(100);
        
        // Update history with successful exports
        const successfulResults = results.filter(r => r.status === 'completed');
        setExportHistory(prev => [...successfulResults, ...prev].slice(0, 10));
        
        if (successfulResults.length > 0) {
          setLastExport(successfulResults[0]);
        }
        
        return results;
        
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Batch export failed';
        setError(errorMessage);
        config.onError?.(errorMessage);
        throw err;
        
      } finally {
        setIsExporting(false);
        setProgress(0);
      }
    },
    [config]
  );

  return {
    isExporting,
    progress,
    error,
    exportHistory,
    lastExport,
    exportCanvas,
    cancelExport,
    clearHistory,
    downloadResult,
    batchExport,
  };
};

// Share link management hook
/**
 *
 */
export interface UseShareLinksConfig {
  canvasId: string;
  onLinkCreated?: (link: ShareLink) => void;
  onError?: (error: string) => void;
}

/**
 *
 */
export interface UseShareLinksReturn {
  // Share state
  isCreating: boolean;
  shareLinks: ShareLink[];
  error: string | null;
  
  // Share methods
  createShareLink: (config: ShareLinkConfig) => Promise<ShareLink>;
  updateShareLink: (linkId: string, config: Partial<ShareLinkConfig>) => Promise<ShareLink>;
  deleteShareLink: (linkId: string) => Promise<void>;
  validateShareLink: (token: string) => Promise<ShareLink | null>;
  copyToClipboard: (link: ShareLink) => Promise<void>;
  
  // Utilities
  refresh: () => Promise<void>;
}

export const useShareLinks = ({
  canvasId,
  onLinkCreated,
  onError,
}: UseShareLinksConfig): UseShareLinksReturn => {
  const [isCreating, setIsCreating] = useState(false);
  const [shareLinks, setShareLinks] = useState<ShareLink[]>([]);
  const [error, setError] = useState<string | null>(null);

  const createShareLink = useCallback(
    async (config: ShareLinkConfig): Promise<ShareLink> => {
      setIsCreating(true);
      setError(null);
      
      try {
        // Mock implementation - in production, this would call your API
        const shareLink: ShareLink = {
          id: `share_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
          token: `st_${Math.random().toString(36).substr(2, 32)}`,
          canvasId,
          url: `${window.location.origin}/canvas/shared/${Math.random().toString(36).substr(2, 32)}`,
          config,
          createdBy: 'current-user', // Would come from auth context
          createdAt: new Date().toISOString(),
          views: 0,
          isActive: true,
        };
        
        setShareLinks(prev => [shareLink, ...prev]);
        onLinkCreated?.(shareLink);
        
        return shareLink;
        
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Failed to create share link';
        setError(errorMessage);
        onError?.(errorMessage);
        throw err;
        
      } finally {
        setIsCreating(false);
      }
    },
    [canvasId, onLinkCreated, onError]
  );

  const updateShareLink = useCallback(
    async (linkId: string, configUpdates: Partial<ShareLinkConfig>): Promise<ShareLink> => {
      const existing = shareLinks.find(link => link.id === linkId);
      if (!existing) {
        throw new Error('Share link not found');
      }
      
      const updated: ShareLink = {
        ...existing,
        config: { ...existing.config, ...configUpdates },
      };
      
      setShareLinks(prev => prev.map(link => link.id === linkId ? updated : link));
      
      return updated;
    },
    [shareLinks]
  );

  const deleteShareLink = useCallback(
    async (linkId: string): Promise<void> => {
      setShareLinks(prev => prev.filter(link => link.id !== linkId));
    },
    []
  );

  const validateShareLink = useCallback(
    async (token: string): Promise<ShareLink | null> => {
      const link = shareLinks.find(l => l.token === token);
      
      if (!link || !link.isActive) {
        return null;
      }
      
      // Check expiration
      if (link.config.expiresAt && new Date() > new Date(link.config.expiresAt)) {
        // Deactivate expired link
        setShareLinks(prev => 
          prev.map(l => l.id === link.id ? { ...l, isActive: false } : l)
        );
        return null;
      }
      
      // Check view limit
      if (link.config.maxViews && link.views >= link.config.maxViews) {
        setShareLinks(prev => 
          prev.map(l => l.id === link.id ? { ...l, isActive: false } : l)
        );
        return null;
      }
      
      // Increment view count
      const updatedLink = { ...link, views: link.views + 1 };
      setShareLinks(prev => 
        prev.map(l => l.id === link.id ? updatedLink : l)
      );
      
      return updatedLink;
    },
    [shareLinks]
  );

  const copyToClipboard = useCallback(
    async (link: ShareLink): Promise<void> => {
      try {
        await navigator.clipboard.writeText(link.url);
      } catch (err) {
        // Fallback for older browsers
        const textArea = document.createElement('textarea');
        textArea.value = link.url;
        document.body.appendChild(textArea);
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
      }
    },
    []
  );

  const refresh = useCallback(async (): Promise<void> => {
    // Mock refresh - in production, this would fetch from API
    setError(null);
  }, []);

  return {
    isCreating,
    shareLinks,
    error,
    createShareLink,
    updateShareLink,
    deleteShareLink,
    validateShareLink,
    copyToClipboard,
    refresh,
  };
};

// Security audit hook
/**
 *
 */
export interface UseSecurityAuditConfig {
  autoAudit?: boolean;
  refreshInterval?: number;
  onViolation?: (violations: SecurityViolation[]) => void;
}

/**
 *
 */
export interface UseSecurityAuditReturn {
  // Audit state
  isAuditing: boolean;
  violations: SecurityViolation[];
  riskLevel: 'low' | 'medium' | 'high';
  lastAuditTime: string | null;
  
  // Audit methods
  auditCanvas: (canvas: CanvasData) => Promise<SecurityViolation[]>;
  auditExportContent: (content: string) => Promise<SecurityViolation[]>;
  clearViolations: () => void;
  fixViolation: (violationId: string) => Promise<void>;
  batchExport: (
    canvases: CanvasData[],
    options: ExportOptions,
    onProgress?: (completed: number, total: number) => void
  ) => Promise<Array<{ canvas: CanvasData; result: unknown; error?: string }>>;
}

export const useSecurityAudit = ({
  autoAudit = false,
  onViolation,
}: UseSecurityAuditConfig = {}): UseSecurityAuditReturn => {
  const [isAuditing, setIsAuditing] = useState(false);
  const [violations, setViolations] = useState<SecurityViolation[]>([]);
  const [riskLevel, setRiskLevel] = useState<'low' | 'medium' | 'high'>('low');

  const auditCanvas = useCallback(
    async (canvas: CanvasData): Promise<SecurityViolation[]> => {
      setIsAuditing(true);
      
      try {
        const foundViolations: SecurityViolation[] = [];
        
        // Audit node content
        canvas.nodes.forEach((node, index) => {
          if (node.data?.label && typeof node.data.label === 'string') {
            const suspiciousPatterns = [
              { pattern: /<script/i, type: 'high' as const, message: 'Script tag detected in node label' },
              { pattern: /javascript:/i, type: 'high' as const, message: 'JavaScript URL detected in node' },
              { pattern: /on\w+\s*=/i, type: 'medium' as const, message: 'Event handler detected in node' },
            ];
            
            suspiciousPatterns.forEach(({ pattern, type, message }) => {
              if (pattern.test(node.data.label)) {
                foundViolations.push({
                  id: `violation-${Date.now()}-${Math.random()}`,
                  type,
                  category: type === 'high' ? 'script' : 'content',
                  rule: 'no-dangerous-content',
                  message: `${message} (Node ${index + 1})`,
                  element: `node-${node.id}`,
                  suggestion: 'Remove or sanitize potentially dangerous content',
                  canAutoFix: false,
                });
              }
            });
          }
        });
        
        // Audit edge content
        canvas.edges.forEach((edge, index) => {
          if (edge.data?.label && typeof edge.data.label === 'string') {
            if (/<script/i.test(edge.data.label)) {
              foundViolations.push({
                id: `violation-${Date.now()}-${Math.random()}`,
                type: 'high',
                category: 'script',
                rule: 'no-script-tags',
                message: `Script tag detected in edge label (Edge ${index + 1})`,
                element: `edge-${edge.id}`,
                suggestion: 'Remove script tags from edge labels',
                canAutoFix: false,
              });
            }
          }
        });
        
        // Determine risk level
        const highRisk = foundViolations.some(v => v.type === 'high');
        const mediumRisk = foundViolations.some(v => v.type === 'medium');
        const newRiskLevel = highRisk ? 'high' : mediumRisk ? 'medium' : 'low';
        
        setViolations(foundViolations);
        setRiskLevel(newRiskLevel);
        
        if (foundViolations.length > 0) {
          onViolation?.(foundViolations);
        }
        
        return foundViolations;
        
      } finally {
        setIsAuditing(false);
      }
    },
    [onViolation]
  );

  const auditExportContent = useCallback(
    async (content: string): Promise<SecurityViolation[]> => {
      setIsAuditing(true);
      
      try {
        const foundViolations: SecurityViolation[] = [];
        const lines = content.split('\n');
        
        const patterns = [
          { pattern: /<script/gi, type: 'high' as const, message: 'Script tag found' },
          { pattern: /javascript:/gi, type: 'high' as const, message: 'JavaScript URL found' },
          { pattern: /vbscript:/gi, type: 'high' as const, message: 'VBScript URL found' },
          { pattern: /on\w+\s*=/gi, type: 'medium' as const, message: 'Event handler attribute found' },
          { pattern: /eval\s*\(/gi, type: 'high' as const, message: 'eval() function found' },
          { pattern: /innerHTML/gi, type: 'medium' as const, message: 'innerHTML usage found' },
        ];
        
        lines.forEach((line, lineIndex) => {
          patterns.forEach(({ pattern, type, message }) => {
            if (pattern.test(line)) {
              foundViolations.push({
                id: `violation-${Date.now()}-${Math.random()}`,
                type,
                category: 'content',
                rule: 'content-audit',
                message,
                context: `Line ${lineIndex + 1}`,
                suggestion: 'Review and sanitize this content before export',
                canAutoFix: false,
              });
            }
          });
        });
        
        setViolations(foundViolations);
        
        return foundViolations;
        
      } finally {
        setIsAuditing(false);
      }
    },
    []
  );

  const clearViolations = useCallback(() => {
    setViolations([]);
    setRiskLevel('low');
  }, []);

  // Add missing state
  const [lastAuditTime, setLastAuditTime] = useState<string | null>(null);

  // Add missing methods
  const fixViolation = useCallback(async (violationId: string) => {
    // Mock implementation - in real app, this would apply fixes
    setViolations(prev => prev.filter(v => v.id !== violationId));
  }, []);

  const batchExport = useCallback(async (
    canvases: CanvasData[],
    options: ExportOptions,
    onProgress?: (completed: number, total: number) => void
  ) => {
    const results: Array<{ canvas: CanvasData; result: unknown; error?: string }> = [];
    
    for (let i = 0; i < canvases.length; i++) {
      try {
        const result = await exportEngine.exportCanvas(canvases[i], options);
        results.push({ canvas: canvases[i], result });
        onProgress?.(i + 1, canvases.length);
      } catch (error) {
        results.push({ 
          canvas: canvases[i], 
          result: null, 
          error: error instanceof Error ? error.message : 'Unknown error' 
        });
      }
    }
    
    return results;
  }, []);

  return {
    isAuditing,
    violations,
    riskLevel,
    lastAuditTime,
    auditCanvas,
    auditExportContent,
    clearViolations,
    fixViolation,
    batchExport,
  };
};