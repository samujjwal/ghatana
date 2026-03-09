/**
 * Deep Link Manager - URL-based navigation for canvas/node combinations
 * Enables direct navigation to specific canvas views and elements
 */

import { useEffect, useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

/**
 *
 */
export interface DeepLinkParams {
  canvasId?: string;
  nodeId?: string;
  x?: number;
  y?: number;
  zoom?: number;
  mode?: 'view' | 'edit' | 'focus';
}

/**
 *
 */
export interface DeepLinkConfig {
  basePath?: string;
  onNavigate?: (params: DeepLinkParams) => void;
  onInvalidLink?: (error: string) => void;
}

/**
 * Parse deep link parameters from URL
 */
export function parseDeepLink(search: string): DeepLinkParams {
  const params = new URLSearchParams(search);
  
  return {
    canvasId: params.get('canvas') || undefined,
    nodeId: params.get('node') || undefined,
    x: params.get('x') ? parseFloat(params.get('x')!) : undefined,
    y: params.get('y') ? parseFloat(params.get('y')!) : undefined,
    zoom: params.get('zoom') ? parseFloat(params.get('zoom')!) : undefined,
    mode: (params.get('mode') as DeepLinkParams['mode']) || 'view',
  };
}

/**
 * Generate deep link URL from parameters
 */
export function generateDeepLink(params: DeepLinkParams, basePath = '/canvas'): string {
  const searchParams = new URLSearchParams();
  
  if (params.canvasId) searchParams.set('canvas', params.canvasId);
  if (params.nodeId) searchParams.set('node', params.nodeId);
  if (params.x !== undefined) searchParams.set('x', params.x.toString());
  if (params.y !== undefined) searchParams.set('y', params.y.toString());
  if (params.zoom !== undefined) searchParams.set('zoom', params.zoom.toString());
  if (params.mode && params.mode !== 'view') searchParams.set('mode', params.mode);
  
  const query = searchParams.toString();
  return `${basePath}${query ? `?${query}` : ''}`;
}

/**
 * Hook for deep link management
 */
export function useDeepLink(config: DeepLinkConfig = {}) {
  const location = useLocation();
  const navigate = useNavigate();
  const { basePath = '/canvas', onNavigate, onInvalidLink } = config;

  // Parse current URL parameters
  const currentParams = parseDeepLink(location.search);

  // Navigate to deep link
  const navigateToDeepLink = useCallback((params: DeepLinkParams) => {
    const url = generateDeepLink(params, basePath);
    navigate(url);
    onNavigate?.(params);
  }, [navigate, basePath, onNavigate]);

  // Update URL without navigation
  const updateDeepLink = useCallback((params: Partial<DeepLinkParams>) => {
    const newParams = { ...currentParams, ...params };
    const url = generateDeepLink(newParams, basePath);
    navigate(url, { replace: true });
  }, [currentParams, basePath, navigate]);

  // Handle URL changes
  useEffect(() => {
    const params = parseDeepLink(location.search);
    
    // Validate parameters
    if (params.canvasId && !/^[a-zA-Z0-9_-]+$/.test(params.canvasId)) {
      onInvalidLink?.('Invalid canvas ID format');
      return;
    }
    
    if (params.nodeId && !/^[a-zA-Z0-9_-]+$/.test(params.nodeId)) {
      onInvalidLink?.('Invalid node ID format');
      return;
    }
    
    if (params.zoom !== undefined && (params.zoom < 0.1 || params.zoom > 5)) {
      onInvalidLink?.('Zoom level out of range (0.1-5)');
      return;
    }
    
    onNavigate?.(params);
  }, [location.search, onNavigate, onInvalidLink]);

  // Generate shareable link
  const generateShareableLink = useCallback((params: DeepLinkParams) => {
    const fullUrl = `${window.location.origin}${generateDeepLink(params, basePath)}`;
    return fullUrl;
  }, [basePath]);

  // Copy link to clipboard
  const copyLinkToClipboard = useCallback(async (params: DeepLinkParams) => {
    const link = generateShareableLink(params);
    
    try {
      await navigator.clipboard.writeText(link);
      return true;
    } catch (error) {
      // Fallback for older browsers
      const textarea = document.createElement('textarea');
      textarea.value = link;
      document.body.appendChild(textarea);
      textarea.select();
      const success = document.execCommand('copy');
      document.body.removeChild(textarea);
      return success;
    }
  }, [generateShareableLink]);

  return {
    currentParams,
    navigateToDeepLink,
    updateDeepLink,
    generateShareableLink,
    copyLinkToClipboard,
  };
}

/**
 * Deep Link Share Component
 */
export interface DeepLinkShareProps {
  params: DeepLinkParams;
  className?: string;
  showCopyButton?: boolean;
  onCopy?: (success: boolean) => void;
}

/**
 *
 */
export function DeepLinkShare({ 
  params, 
  className = '',
  showCopyButton = true,
  onCopy 
}: DeepLinkShareProps) {
  const { generateShareableLink, copyLinkToClipboard } = useDeepLink();
  
  const link = generateShareableLink(params);
  
  const handleCopy = async () => {
    const success = await copyLinkToClipboard(params);
    onCopy?.(success);
  };

  return (
    <div className={`flex items-center space-x-2 ${className}`}>
      <input
        type="text"
        value={link}
        readOnly
        className="flex-1 px-3 py-2 text-sm border border-gray-300 rounded-md bg-gray-50"
        data-testid="deep-link-input"
      />
      
      {showCopyButton && (
        <button
          onClick={handleCopy}
          className="px-3 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 transition-colors"
          data-testid="copy-link-button"
        >
          Copy
        </button>
      )}
    </div>
  );
}